package com.bte.repository;

import com.bte.entity.Tournament;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {
    java.util.List<Tournament> findByStatus(com.bte.enums.TournamentStatus status);
    java.util.List<Tournament> findByAutomaticPayoutsEnabledTrueAndStatusIn(java.util.Collection<com.bte.enums.TournamentStatus> statuses);
	java.util.List<Tournament> findByLeaderEmailOrderByCreatedAtDesc(String email);
	boolean existsByTitle(String title);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from Tournament t where t.id = :id")
	Optional<Tournament> findByIdForUpdate(@Param("id") Long id);

	java.util.List<Tournament> findByStatusAndStartTimeLessThanEqual(
			com.bte.enums.TournamentStatus status, java.time.LocalDateTime now);

	java.util.List<Tournament> findByStartTimeBetweenAndRoomIdIsNotNullAndRoomPasswordIsNotNullAndRoomCredentialsSentAtIsNull(
			java.time.LocalDateTime from, java.time.LocalDateTime to);

	@Query("select tournament from Tournament tournament "
			+ "where tournament.id = :id and tournament.leader.email = :leaderEmail")
	Optional<Tournament> findByIdAndLeaderEmail(
			@Param("id") Long id, @Param("leaderEmail") String leaderEmail);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select tournament from Tournament tournament where tournament.id = :id")
	Optional<Tournament> findForUpdate(@Param("id") Long id);
}
