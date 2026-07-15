package com.wedjan.api.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRoleRepository extends JpaRepository<AccountRole, UUID> {

    List<AccountRole> findByAccountIdAndDeletedAtIsNull(UUID accountId);

    Optional<AccountRole> findByAccountIdAndRoleAndDeletedAtIsNull(UUID accountId, AccountRole.Role role);
}
