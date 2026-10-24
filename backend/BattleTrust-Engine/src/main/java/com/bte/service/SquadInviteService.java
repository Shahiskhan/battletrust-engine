package com.bte.service;
import com.bte.repository.*;
import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;

@Service @RequiredArgsConstructor
public class SquadInviteService {
 private final RegistrationRepository registrations;private final TournamentRepository tournaments;private final UserRepository users;private final VoucherService vouchers;private final InviteQrService qr;private final jakarta.persistence.EntityManager em;
 @Value("${app.auth.allowed-origin}")private String origin;
 public record Invite(String teamName,Long tournamentId,int occupiedSeats,int totalSeats,String paymentStatus,String link,String qrDataUrl){}
 public record Seat(int seatNumber,Long tournamentId,boolean confirmed,String roomPath){}
 @Transactional(readOnly=true)
 public Invite captain(Long id,Authentication auth,String ticket){var r=registrations.findById(id).orElseThrow(()->new ResourceNotFoundException("Squad not found"));vouchers.authorize(r,auth,ticket);if(r.getSquadInviteToken()==null)throw new BadRequestException("This roster is already complete");String link=origin+"/squads/join/"+r.getSquadInviteToken();return new Invite(r.getTeamName(),r.getTournament().getId(),r.getSquadMembers().size(),r.getTournament().getSquadSize(),r.getPaymentStatus().name(),link,qr.image(link));}
 @Transactional(readOnly=true)
 public Invite preview(String token){var r=find(token);if(r.getTournament().isArchived() || r.getPaymentStatus()==PaymentStatus.CANCELLED)throw new ResourceNotFoundException("Squad invite unavailable");return new Invite(r.getTeamName(),r.getTournament().getId(),r.getSquadMembers().size(),r.getTournament().getSquadSize(),r.getPaymentStatus().name(),null,null);}
 private Registration find(String token){if(token==null || !token.matches("[a-fA-F0-9-]{36}"))throw new ResourceNotFoundException("Squad invite not found");return registrations.findBySquadInviteToken(token).orElseThrow(()->new ResourceNotFoundException("Squad invite not found"));}
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public Seat join(String token,String name,String uid,Authentication auth){
  var candidate=find(token);var t=tournaments.findByIdForUpdate(candidate.getTournament().getId()).orElseThrow();vouchers.expireReservations(t);var r=registrations.findByIdForUpdate(candidate.getId()).orElseThrow();em.refresh(r,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  var user=users.findByEmail(auth.getName()).orElseThrow();
  if(t.isArchived() || t.getStatus()!=TournamentStatus.UPCOMING || !t.getStartTime().isAfter(java.time.LocalDateTime.now()) || (r.getPaymentStatus()!=PaymentStatus.PAID && r.getPaymentStatus()!=PaymentStatus.PENDING))throw new BadRequestException("This squad invite has closed");
  if(user.getId().equals(r.getOwnerUserId()))return seat(r,1);
  for(int i=1;i<r.getSquadMembers().size();i++)if(user.getId().equals(r.getSquadMembers().get(i).getWalletUserId()))return seat(r,i+1);
  if(r.getSquadMembers().size()>=t.getSquadSize())throw new BadRequestException("All squad seats are occupied");
  if(registrations.findByTournamentId(t.getId()).stream().filter(other->other.getPaymentStatus()==PaymentStatus.PENDING || other.getPaymentStatus()==PaymentStatus.PAID).anyMatch(other->user.getId().equals(other.getOwnerUserId()) || other.getSquadMembers().stream().anyMatch(m->user.getId().equals(m.getWalletUserId()))))throw new BadRequestException("You already hold a seat in this room");
  if(r.getSquadMembers().stream().anyMatch(m->m.getInGameId().equalsIgnoreCase(uid.trim())))throw new BadRequestException("UID is already in this squad");
  var member=new SquadMember(name.trim(),uid.trim());member.setWalletUserId(user.getId());r.getSquadMembers().add(member);return seat(r,r.getSquadMembers().size());
 }
 private Seat seat(Registration r,int seat){return new Seat(seat,r.getTournament().getId(),r.getPaymentStatus()==PaymentStatus.PAID,"/rooms/"+r.getTournament().getId());}
}
