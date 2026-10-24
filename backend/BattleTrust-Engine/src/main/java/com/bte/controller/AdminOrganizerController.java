package com.bte.controller;

import com.bte.entity.*;import com.bte.enums.*;import com.bte.repository.*;import com.bte.service.*;import com.bte.exception.*;import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.web.bind.annotation.*;import org.springframework.transaction.annotation.Transactional;import org.springframework.security.core.Authentication;import java.util.*;import java.math.BigDecimal;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
public class AdminOrganizerController {
 private final UserRepository users;private final TournamentRepository tournaments;private final PlatformVaultRepository vaults;private final LedgerTransactionRepository ledger;private final LeaderTournamentService tournamentService;private final org.springframework.jdbc.core.JdbcTemplate jdbc;private final AdminOperationsController operations;private final tools.jackson.databind.ObjectMapper mapper;
 public record Organizer(Long id,String name,String email,boolean blocked,String blockReason,java.time.LocalDateTime createdAt){}
 public record Block(@NotNull Boolean blocked,@NotBlank @Size(max=1000)String reason){}
 private Organizer view(User u){return new Organizer(u.getId(),u.getName(),u.getEmail(),u.isBlocked(),u.getBlockReason(),u.getCreatedAt());}
 private User organizer(Long id,boolean lock){var u=(lock?users.findByIdForUpdate(id):users.findById(id)).orElseThrow(()->new ResourceNotFoundException("Organizer not found"));if(u.getRole()!=Role.ROLE_ORGANIZER)throw new BadRequestException("Only organizer accounts can be managed here");return u;}
 @GetMapping("/organizers") @Transactional(readOnly=true)
 public List<Organizer> list(){return users.findAll().stream().filter(u->u.getRole()==Role.ROLE_ORGANIZER && !u.isDemoAccount()).sorted(Comparator.comparing(User::getCreatedAt).reversed()).map(this::view).toList();}
 @PutMapping("/organizers/{id}/block") @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public Organizer block(@PathVariable Long id,@Valid @RequestBody Block body,Authentication auth){var u=organizer(id,true);u.setBlocked(body.blocked());u.setBlockReason(body.reason().trim());u.setBlockedBy(auth.getName());u.setBlockedAt(java.time.Instant.now());if(body.blocked())jdbc.update("update auth_sessions set revoked=true,revoked_at=UTC_TIMESTAMP(6) where user_id=? and revoked=false",id);return view(u);}
 @GetMapping("/organizers/{id}") @Transactional(readOnly=true)
 public Map<String,Object> profile(@PathVariable Long id){var u=organizer(id,false);var rooms=tournaments.findByLeaderEmailOrderByCreatedAtDesc(u.getEmail()).stream().filter(t->!t.isArchived()).toList();var ids=rooms.stream().map(Tournament::getId).toList();var collected=new HashMap<Long,BigDecimal>();if(!ids.isEmpty())for(var row:ledger.totals(ids))if(row[1]==LedgerTransactionType.ESCROW_HOLD || row[1]==LedgerTransactionType.ESCROW_LOCK)collected.merge((Long)row[0],(BigDecimal)row[2],BigDecimal::add);
  var entries=new ArrayList<Map<String,Object>>();for(var t:rooms){Map<String,Object> item=mapper.convertValue(tournamentService.toResponse(t),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});item.put("entryFeesCollected",collected.getOrDefault(t.getId(),BigDecimal.ZERO));item.put("escrowPool",vaults.findByTournamentId(t.getId()).stream().filter(v->!Boolean.TRUE.equals(v.getIsDisbursed())).map(PlatformVault::getTotalCollectedPkr).reduce(BigDecimal.ZERO,BigDecimal::add));entries.add(item);}Map<String,Object> result=new LinkedHashMap<>();result.put("id",u.getId());result.put("name",u.getName());result.put("email",u.getEmail());result.put("blocked",u.isBlocked());result.put("blockReason",u.getBlockReason());result.put("createdAt",u.getCreatedAt());result.put("tournaments",entries);result.put("totalEntryFees",collected.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));return result;
 }
 @GetMapping(value="/tournaments/{id}/export-csv",produces="text/csv")
 public org.springframework.http.ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> export(@PathVariable Long id){return operations.csv(id);}
}
