package com.bte.service;
import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.repository.*;
import com.bte.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import java.util.*;
@Service @RequiredArgsConstructor
public class SquadWalletService {
    private final RegistrationRepository registrations;
    private final TournamentRepository tournaments;
    private final UserRepository users;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;
    private final VoucherService vouchers;
    private final GuestAccountTransferService transfer;
    private final jakarta.persistence.EntityManager em;
    public void initialize(Registration r) {
        for(int i=0;i<r.getSquadMembers().size();i++) {
            var member=r.getSquadMembers().get(i);
            Long id=r.getOwnerUserId();
            if(i>0) id=users.save(User.builder().name(member.getName()).email(UUID.randomUUID()+"@guest.battletrust.invalid").password(encoder.encode(UUID.randomUUID().toString())).role(Role.ROLE_GUEST).build()).getId();
            member.setWalletUserId(id);member.setOriginalGuestUserId(id);if(i>0)member.setClaimTicket(UUID.randomUUID().toString());
        }
    }
    public record MemberWallet(int memberIndex,String name,String inGameId,Long walletUserId,String claimTicket){}
    @Transactional(readOnly=true)
    public List<MemberWallet> details(Long id,Authentication auth,String ticket) {
        var r=registrations.findById(id).orElseThrow(()->new ResourceNotFoundException("Registration not found"));vouchers.authorize(r,auth,ticket);
        var result=new ArrayList<MemberWallet>();
        for(int i=0;i<r.getSquadMembers().size();i++){var m=r.getSquadMembers().get(i);result.add(new MemberWallet(i,m.getName(),m.getInGameId(),i==0?r.getOwnerUserId():m.getWalletUserId(),i==0?null:m.getClaimTicket()));}
        return result;
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MemberWallet claim(String ticket,String email) {
        var candidate=registrations.findByMemberClaimTicket(ticket).orElseThrow(()->new UnauthorizedException("Invalid member claim code"));
        tournaments.findByIdForUpdate(candidate.getTournament().getId()).orElseThrow();
        var r=registrations.findByIdForUpdate(candidate.getId()).orElseThrow();em.refresh(r,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var target=users.findByEmail(email).orElseThrow();
        for(int i=1;i<r.getSquadMembers().size();i++) {
            var member=r.getSquadMembers().get(i);if(!ticket.equals(member.getClaimTicket()))continue;
            if(target.getId().equals(member.getWalletUserId()))return new MemberWallet(i,member.getName(),member.getInGameId(),target.getId(),null);
            if(!Objects.equals(member.getWalletUserId(),member.getOriginalGuestUserId()))throw new UnauthorizedException("Member already linked to another account");
            boolean alreadyInRoom=registrations.findByTournamentId(r.getTournament().getId()).stream().filter(other -> other.getPaymentStatus()==PaymentStatus.PENDING || other.getPaymentStatus()==PaymentStatus.PAID).anyMatch(other -> target.getId().equals(other.getOwnerUserId()) || other.getSquadMembers().stream().anyMatch(m -> target.getId().equals(m.getWalletUserId())));
            if(alreadyInRoom)throw new BadRequestException("An account may represent only one player in a room");
            transfer.merge(member.getWalletUserId(),target.getId(),r.getTournament().getId(),"member-claim:"+r.getId()+":"+i);
            member.setWalletUserId(target.getId());return new MemberWallet(i,member.getName(),member.getInGameId(),target.getId(),null);
        }
        throw new UnauthorizedException("Invalid member claim code");
    }
    public record Membership(Long registrationId,Long tournamentId,String tournamentTitle,String teamName,String playerName,Integer slotNumber,String paymentStatus){}
    @Transactional(readOnly=true)
    public List<Membership> memberships(String email) {
        var user=users.findByEmail(email).orElseThrow();var result=new ArrayList<Membership>();
        for(var r:registrations.findMemberEntries(user.getId()))for(int i=1;i<r.getSquadMembers().size();i++)if(user.getId().equals(r.getSquadMembers().get(i).getWalletUserId()))result.add(new Membership(r.getId(),r.getTournament().getId(),r.getTournament().getTitle(),r.getTeamName(),r.getSquadMembers().get(i).getName(),r.getSlotNumber(),r.getPaymentStatus().name()));
        return result;
    }
}
