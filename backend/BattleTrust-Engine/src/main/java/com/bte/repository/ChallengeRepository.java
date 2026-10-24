package com.bte.repository;

import com.bte.entity.Challenge;
import com.bte.enums.ChallengeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

	@EntityGraph(attributePaths = {"challenger", "opponent"})
	Optional<Challenge> findById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select challenge from Challenge challenge "
			+ "join fetch challenge.challenger join fetch challenge.opponent "
			+ "where challenge.id = :id")
	Optional<Challenge> findByIdForUpdate(@Param("id") Long id);

	@Query("select count(challenge) > 0 from Challenge challenge "
			+ "where challenge.status in :statuses "
			+ "and ((challenge.challenger.id = :firstUserId and challenge.opponent.id = :secondUserId) "
			+ "or (challenge.challenger.id = :secondUserId and challenge.opponent.id = :firstUserId))")
	boolean existsOpenChallengeBetween(
			@Param("firstUserId") Long firstUserId,
			@Param("secondUserId") Long secondUserId,
			@Param("statuses") Collection<ChallengeStatus> statuses);
}
