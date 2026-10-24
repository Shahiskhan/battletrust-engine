package com.bte.service;
import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.exception.*;
import com.bte.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.*;

@Service @RequiredArgsConstructor
public class VoucherService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PaymentVoucherRepository vouchers;
    private final RegistrationRepository registrations;
    private final TournamentRepository tournaments;
    private final UserRepository users;
    private final RegistrationFundingService funding;
    private final RegistrationService registrationService;
    private final jakarta.persistence.EntityManager em;
    private final org.springframework.core.env.Environment env;
    private final TournamentAccessService access;

    private PaymentVoucher newVoucher(Tournament tournament, int position) {
        var voucher = new PaymentVoucher(); String id;
        do { id = String.valueOf(RANDOM.nextLong(1_000_000_000L, 10_000_000_000L)); } while (vouchers.existsById(id));
        voucher.setId(id); voucher.setTournament(tournament); voucher.setInventoryNumber(position);
        voucher.setAmount(tournament.getEntryFeePkr().multiply(BigDecimal.valueOf(tournament.getSquadSize())));
        voucher.setExpiresAt(tournament.getStartTime()); voucher.setStatus("AVAILABLE"); return voucher;
    }
    public void prepareInventory(Tournament tournament) {
        int capacity = Math.min(25, tournament.getMaxPlayers()/tournament.getSquadSize());
        var existing = vouchers.findByTournamentIdOrderByInventoryNumberAsc(tournament.getId());
        var positions = new HashSet<Integer>();
        for (var v : existing) {
            if (v.getInventoryNumber() == null) continue;
            positions.add(v.getInventoryNumber());
            if (v.getRegistration() == null) { v.setAmount(tournament.getEntryFeePkr().multiply(BigDecimal.valueOf(tournament.getSquadSize()))); v.setExpiresAt(tournament.getStartTime()); v.setStatus(v.getInventoryNumber() <= capacity ? "AVAILABLE" : "VOID"); }
        }
        for (int i=1;i<=capacity;i++) if (!positions.contains(i)) vouchers.save(newVoucher(tournament,i));
    }
    public void expireReservations(Tournament tournament) {
        var now=LocalDateTime.now();
        for(var v:vouchers.findByTournamentIdOrderByInventoryNumberAsc(tournament.getId())) {
            var r=v.getRegistration();
            if(r==null || !v.getStatus().equals("PENDING") || v.getExpiresAt().isAfter(now) || r.getTransactionId()!=null || r.getPaymentStatus()!=PaymentStatus.PENDING)continue;
            r.setPaymentStatus(PaymentStatus.CANCELLED);v.setStatus("EXPIRED");
            Integer position=v.getInventoryNumber();
            if(position!=null && tournament.getStatus()==TournamentStatus.UPCOMING && tournament.getStartTime().isAfter(now)) {
                v.setInventoryNumber(null);vouchers.flush();vouchers.save(newVoucher(tournament,position));
            }
        }
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void expireRoom(Long id) {var t=tournaments.findByIdForUpdate(id).orElseThrow();expireReservations(t);}

    public void create(Registration r) {
        var voucher = vouchers.findFirstByTournamentIdAndRegistrationIsNullAndStatusOrderByInventoryNumberAsc(r.getTournament().getId(),"AVAILABLE").orElseGet(() -> {
            if (vouchers.findByTournamentIdOrderByInventoryNumberAsc(r.getTournament().getId()).stream().anyMatch(v -> v.getInventoryNumber()!=null)) throw new BadRequestException("No unissued vouchers remain in this room");
            var legacy = newVoucher(r.getTournament(), 1); legacy.setInventoryNumber(null); return legacy;
        });
        voucher.setRegistration(r); voucher.setStatus("PENDING");
        voucher.setAmount(r.getTournament().getEntryFeePkr().multiply(BigDecimal.valueOf(r.reservedPlayers())));
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(15);
        voucher.setExpiresAt(expiry.isBefore(r.getTournament().getStartTime()) ? expiry : r.getTournament().getStartTime());
        if (r.getPaymentStatus() == PaymentStatus.PAID) voucher.setStatus("PAID");
        vouchers.save(voucher);
    }
    public void authorize(Registration r, Authentication auth, String ticket) {
        if (ticket != null && ticket.equals(r.getTicketCode())) return;
        if (auth != null && users.findByEmail(auth.getName()).map(u -> u.getId().equals(r.getOwnerUserId())).orElse(false)) return;
        throw new UnauthorizedException("Private registration ticket or entry ownership is required");
    }
    public record VoucherResponse(String voucherId, Long registrationId, BigDecimal amount, LocalDateTime expiresAt, String status, Integer slotNumber, String qrDataUrl, boolean simulated, boolean simulationAvailable) {}
    public record InventoryItem(String voucherId, Integer inventoryNumber, BigDecimal amount, String status, LocalDateTime expiresAt, Long registrationId) {}
    @Transactional(readOnly=true)
    public java.util.List<InventoryItem> inventory(Long tournamentId, Authentication auth) {
        var tournament = tournaments.findById(tournamentId).orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));
        if (!access.manages(tournament,auth)) throw new UnauthorizedException("Organizer access required");
        return vouchers.findByTournamentIdOrderByInventoryNumberAsc(tournamentId).stream().map(v -> new InventoryItem(v.getId(),v.getInventoryNumber(),v.getAmount(),v.getStatus(),v.getExpiresAt(),v.getRegistration()==null?null:v.getRegistration().getId())).toList();
    }
    @Transactional(readOnly=true)
    public java.util.Map<String,String> inventoryQr(Long tournamentId, String id, Authentication auth) {
        inventory(tournamentId,auth);
        var voucher = vouchers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Voucher not found"));
        if (voucher.getTournament()==null || !voucher.getTournament().getId().equals(tournamentId)) throw new ResourceNotFoundException("Voucher not found in room");
        return java.util.Map.of("qrDataUrl",qr(voucher));
    }
    @Transactional(readOnly = true)
    public VoucherResponse get(Long registrationId, Authentication auth, String ticket) {
        var voucher = vouchers.findByRegistrationId(registrationId).orElseThrow(() -> new ResourceNotFoundException("Voucher not found for this entry"));
        authorize(voucher.getRegistration(), auth, ticket);
        String status = voucher.getStatus().equals("PENDING") && !voucher.getExpiresAt().isAfter(LocalDateTime.now()) ? "EXPIRED" : voucher.getStatus();
        return new VoucherResponse(voucher.getId(), registrationId, voucher.getAmount(), voucher.getExpiresAt(), status, voucher.getRegistration().getSlotNumber(), qr(voucher), true,
                env.matchesProfiles("dev") && env.getProperty("app.payments.simulation-enabled", Boolean.class, false));
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public com.bte.dto.RegistrationResponse simulate(String id, Authentication auth, String ticket) {
        var candidate = vouchers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Voucher not found"));
        if (candidate.getRegistration()==null) throw new UnauthorizedException("Register a squad before paying its voucher");
        var tournament = tournaments.findByIdForUpdate(candidate.getRegistration().getTournament().getId()).orElseThrow();
        var r = registrations.findByIdForUpdate(candidate.getRegistration().getId()).orElseThrow();
        em.refresh(r, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        em.refresh(candidate);
        authorize(r, auth, ticket);
        if (r.getPaymentStatus() == PaymentStatus.PAID) return registrationService.toResponse(r);
        if (!candidate.getStatus().equals("PENDING") || !candidate.getExpiresAt().isAfter(LocalDateTime.now())) throw new BadRequestException("Voucher expired or cancelled");
        funding.confirm(r, "sim-1bill:" + id, null, true);
        return registrationService.toResponse(r);
    }
    private String qr(PaymentVoucher voucher) {
        try {
            String payload = "BTE-SIMULATED-1BILL:" + voucher.getId() + ":PKR:" + voucher.getAmount().toPlainString();
            var matrix = new com.google.zxing.qrcode.QRCodeWriter().encode(payload, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0);
            var svg = new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='220' height='220' shape-rendering='crispEdges' viewBox='0 0 " + matrix.getWidth() + " " + matrix.getHeight() + "'><rect width='100%' height='100%' fill='white'/><g fill='black'>");
            for (int y = 0; y < matrix.getHeight(); y++) for (int x = 0; x < matrix.getWidth(); x++) if (matrix.get(x,y)) svg.append("<rect x='").append(x).append("' y='").append(y).append("' width='1' height='1'/>");
            svg.append("</g></svg>");
            return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (com.google.zxing.WriterException e) { throw new IllegalStateException("Unable to generate voucher QR", e); }
    }
}
