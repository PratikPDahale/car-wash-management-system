package com.washcare.crm.auth;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    List<UserAccount> findByRoleInAndStatus(Collection<UserRole> roles, String status);

    List<UserAccount> findByRoleAndStatus(UserRole role, String status);
}
