package com.bte.repository;

import com.bte.entity.PlatformVault;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlatformVaultRepository extends JpaRepository<PlatformVault, Long> {

	List<PlatformVault> findByTournamentId(Long tournamentId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select vault from PlatformVault vault where vault.tournament.id = :tournamentId")
	java.util.Optional<PlatformVault> findByTournamentIdForUpdate(
			@Param("tournamentId") Long tournamentId);
}
