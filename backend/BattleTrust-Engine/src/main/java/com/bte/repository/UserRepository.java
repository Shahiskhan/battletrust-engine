package com.bte.repository;

import com.bte.entity.User;
import com.bte.enums.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Page<User> findByRoleInAndDemoAccountFalse(Collection<Role> roles,Pageable pageable);
    long countByDemoAccountFalseAndRoleNot(Role role);

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
	boolean existsByRole(Role role);

	@Query("select user from User user where lower(user.email) in :emails")
	List<User> findByNormalizedEmailIn(@Param("emails") Collection<String> emails);

	Page<User> findByRoleIn(Collection<Role> roles, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from User user where user.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from User user where user.email = :email")
	Optional<User> findByEmailForUpdate(@Param("email") String email);
}
