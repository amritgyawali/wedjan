package com.wedjan.api.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    @Query("select a from Account a where lower(a.email) = lower(:email) and a.deletedAt is null")
    Optional<Account> findActiveByEmail(@Param("email") String email);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);
}
