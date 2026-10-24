package com.bte.repository;

import com.bte.entity.WithdrawalRequest;
import com.bte.enums.WithdrawalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {

	Page<WithdrawalRequest> findByStatusOrderByRequestedAtAsc(WithdrawalStatus status, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select withdrawal from WithdrawalRequest withdrawal "
			+ "join fetch withdrawal.user where withdrawal.id = :id")
	Optional<WithdrawalRequest> findByIdForUpdate(@Param("id") Long id);
}
