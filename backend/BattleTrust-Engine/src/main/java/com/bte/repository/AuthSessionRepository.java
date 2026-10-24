package com.bte.repository;

import com.bte.entity.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select session from AuthSession session where session.id = :id")
	Optional<AuthSession> findByIdForUpdate(@Param("id") String id);

	@Query("select session from AuthSession session join fetch session.user "
			+ "where session.id = :id and session.revoked = false and session.expiresAt > :now")
	Optional<AuthSession> findActiveWithUser(@Param("id") String id, @Param("now") Instant now);
}
