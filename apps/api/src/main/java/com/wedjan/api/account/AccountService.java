package com.wedjan.api.account;

import com.wedjan.api.common.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final ProfileRepository profileRepository;

    public AccountService(
            AccountRepository accountRepository,
            AccountRoleRepository accountRoleRepository,
            ProfileRepository profileRepository) {
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public AccountDtos.MeResponse me(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.unauthorized("AUTH_ACCOUNT_MISSING", "Account not found"));
        Profile profile = profileRepository.findById(accountId)
                .orElseGet(() -> profileRepository.save(Profile.create(accountId)));
        List<String> roles = accountRoleRepository.findByAccountIdAndDeletedAtIsNull(accountId).stream()
                .map(role -> role.getRole().name())
                .toList();
        return new AccountDtos.MeResponse(
                AccountDtos.AccountDto.from(account), roles, AccountDtos.ProfileDto.from(profile));
    }

    @Transactional
    public AccountDtos.MeResponse updateMe(UUID accountId, AccountDtos.UpdateProfileRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.unauthorized("AUTH_ACCOUNT_MISSING", "Account not found"));
        Profile profile = profileRepository.findById(accountId)
                .orElseGet(() -> profileRepository.save(Profile.create(accountId)));

        if (request.displayName() != null) profile.setDisplayName(request.displayName());
        if (request.avatarMediaId() != null) profile.setAvatarMediaId(request.avatarMediaId());
        if (request.city() != null) profile.setCity(request.city());
        if (request.country() != null) profile.setCountry(request.country().toUpperCase());
        if (request.timezone() != null) profile.setTimezone(request.timezone());
        if (request.locale() != null) account.setLocale(request.locale());
        if (request.defaultCurrency() != null) account.setDefaultCurrency(request.defaultCurrency());
        if (request.marketingOptIn() != null) account.setMarketingOptIn(request.marketingOptIn());

        accountRepository.save(account);
        profileRepository.save(profile);
        return me(accountId);
    }
}
