package com.bte.repository;

import com.bte.entity.Registration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    java.util.Optional<Registration> findBySquadInviteToken(String token);
    @Query("select distinct r from Registration r join r.squadMembers m where m.walletUserId = :userId")
    List<Registration> findMemberEntries(Long userId);
    @Query("select r from Registration r join r.squadMembers m where m.claimTicket = :ticket")
    Optional<Registration> findByMemberClaimTicket(String ticket);
	Optional<Registration> findByTicketCode(String ticketCode);
	List<Registration> findByOwnerUserId(Long ownerUserId);
	boolean existsByTournamentIdAndOwnerUserId(Long tournamentId, Long ownerUserId);
	boolean existsByTournamentIdAndOwnerUserIdAndPaymentStatus(Long tournamentId, Long ownerUserId, com.bte.enums.PaymentStatus status);

	List<Registration> findByTournamentId(Long tournamentId);

	Optional<Registration> findByTransactionId(String transactionId);

	long countByTournamentId(Long tournamentId);

	boolean existsByTransactionId(String transactionId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select registration from Registration registration "
			+ "join fetch registration.tournament where registration.id = :id")
	Optional<Registration> findByIdForUpdate(@Param("id") Long id);

	long countByTournamentIdAndPaymentStatus(
			Long tournamentId, com.bte.enums.PaymentStatus paymentStatus);

	List<Registration> findByTournamentIdAndPaymentStatus(
			Long tournamentId, com.bte.enums.PaymentStatus paymentStatus);

	boolean existsByTournamentIdAndCaptainEmail(Long tournamentId, String captainEmail);

	boolean existsByTournamentIdAndEmail(Long tournamentId, String email);
}
