package com.bte.repository;

import com.bte.entity.MatchResult;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {

	List<MatchResult> findByTournamentIdOrderByMatchNumberAsc(Long tournamentId);

	boolean existsByTournamentIdAndMatchNumber(Long tournamentId, Integer matchNumber);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select result from MatchResult result join fetch result.tournament where result.id = :id")
	java.util.Optional<MatchResult> findByIdForUpdate(@Param("id") Long id);

	List<MatchResult> findByVerificationStatusAndDisputeWindowClosedFalse(
			com.bte.enums.VerificationStatus verificationStatus);

	List<MatchResult> findByDisputeWindowClosedFalseAndDisputeDeadlineLessThanEqual(
			java.time.LocalDateTime now);
}
