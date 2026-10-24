package com.bte.repository;

import com.bte.entity.Dispute;
import com.bte.enums.DisputeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

	List<Dispute> findByTournamentId(Long tournamentId);

	long countByMatchResultIdAndStatus(Long matchResultId, DisputeStatus status);

	List<Dispute> findByMatchResultIdAndStatus(Long matchResultId, DisputeStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select dispute from Dispute dispute join fetch dispute.matchResult "
			+ "where dispute.id = :id")
	java.util.Optional<Dispute> findByIdForUpdate(@Param("id") Long id);
}
