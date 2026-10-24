package com.bte.repository;

import com.bte.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select rt from RefreshToken rt join fetch rt.session where rt.tokenHash = :hash")
	Optional<RefreshToken> findByHashForUpdate(@Param("hash") String hash);
}
