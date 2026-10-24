package com.bte.repository;

import com.bte.entity.PlayerCategoryStats;
import com.bte.enums.GameCategory;
import com.bte.enums.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PlayerCategoryStatsRepository extends JpaRepository<PlayerCategoryStats, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select stats from PlayerCategoryStats stats where stats.user.id = :userId "
			+ "and stats.gameCategory = :gameCategory")
	Optional<PlayerCategoryStats> findByUserIdAndGameCategoryForUpdate(
			@Param("userId") Long userId,
			@Param("gameCategory") GameCategory gameCategory);

	@EntityGraph(attributePaths = "user")
	Page<PlayerCategoryStats> findByGameCategoryAndUserDemoAccountFalseAndUserRoleIn(
			GameCategory gameCategory,
			Collection<Role> roles,
			Pageable pageable);
}
