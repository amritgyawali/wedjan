package com.wedjan.api.seed;

import com.wedjan.api.account.Account;
import com.wedjan.api.account.AccountRepository;
import com.wedjan.api.account.AccountRole;
import com.wedjan.api.account.AccountRoleRepository;
import com.wedjan.api.account.Profile;
import com.wedjan.api.account.ProfileRepository;
import com.wedjan.api.config.WedjanProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local/dev seed: admin + one demo account per role, pre-verified.
 * Enabled with SEED_ENABLED=true (never in production).
 * Demo cities cover the launch corridor: Kathmandu + Melbourne.
 */
@Component
@ConditionalOnProperty(prefix = "wedjan.seed", name = "enabled", havingValue = "true")
public class DevSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final WedjanProperties properties;

    public DevSeedRunner(AccountRepository accountRepository, AccountRoleRepository accountRoleRepository,
            ProfileRepository profileRepository, PasswordEncoder passwordEncoder,
            WedjanProperties properties) {
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seed("admin@wedjan.local", AccountRole.Role.ADMIN, "wedjan Admin", "Melbourne", "AU",
                "Australia/Melbourne", "AUD");
        seed("customer@wedjan.local", AccountRole.Role.CUSTOMER, "Demo Customer", "Melbourne", "AU",
                "Australia/Melbourne", "AUD");
        seed("vendor@wedjan.local", AccountRole.Role.VENDOR, "Demo Vendor", "Kathmandu", "NP",
                "Asia/Kathmandu", "NPR");
        seed("freelancer@wedjan.local", AccountRole.Role.FREELANCER, "Demo Freelancer", "Kathmandu", "NP",
                "Asia/Kathmandu", "NPR");
        log.info("Seed complete — demo password: {}", properties.seed().demoPassword());
    }

    private void seed(String email, AccountRole.Role role, String displayName, String city,
            String country, String timezone, String currency) {
        if (accountRepository.findActiveByEmail(email).isPresent()) {
            return;
        }
        Account account = Account.create(email, passwordEncoder.encode(properties.seed().demoPassword()));
        account.setStatus(Account.Status.ACTIVE);
        account.setDefaultCurrency(currency);
        accountRepository.save(account);
        accountRoleRepository.save(AccountRole.grant(account.getId(), role));
        Profile profile = Profile.create(account.getId());
        profile.setDisplayName(displayName);
        profile.setCity(city);
        profile.setCountry(country);
        profile.setTimezone(timezone);
        profileRepository.save(profile);
        log.info("Seeded {} account: {}", role, email);
    }
}
