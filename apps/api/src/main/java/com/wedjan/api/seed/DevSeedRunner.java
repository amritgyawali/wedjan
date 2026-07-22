package com.wedjan.api.seed;

import com.wedjan.api.account.Account;
import com.wedjan.api.account.AccountRepository;
import com.wedjan.api.account.AccountRole;
import com.wedjan.api.account.AccountRoleRepository;
import com.wedjan.api.account.Profile;
import com.wedjan.api.account.ProfileRepository;
import com.wedjan.api.common.Uuidv7;
import com.wedjan.api.config.WedjanProperties;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Local seed: role accounts plus 20 public suppliers across the launch corridor. */
@Component
@ConditionalOnProperty(prefix = "wedjan.seed", name = "enabled", havingValue = "true")
public class DevSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final WedjanProperties properties;
    private final JdbcTemplate jdbc;

    public DevSeedRunner(AccountRepository accountRepository, AccountRoleRepository accountRoleRepository,
            ProfileRepository profileRepository, PasswordEncoder passwordEncoder,
            WedjanProperties properties, JdbcTemplate jdbc) {
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.jdbc = jdbc;
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
        seedVendorDirectory();
        log.info("Seed complete - demo password: {}", properties.seed().demoPassword());
    }

    private void seed(String email, AccountRole.Role role, String displayName, String city,
            String country, String timezone, String currency) {
        if (accountRepository.findActiveByEmail(email).isPresent()) return;
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

    private void seedVendorDirectory() {
        Long existing = jdbc.queryForObject(
                "SELECT count(*) FROM vendor_profiles WHERE slug LIKE 'demo-%'", Long.class);
        if (existing != null && existing >= 20) return;

        List<String> categorySlugs = List.of("venues", "wedding-photography", "videography", "catering",
                "music-djs", "decor-florals", "planning-coordination", "mua", "cakes-desserts", "transport");
        String sharedPasswordHash = passwordEncoder.encode(properties.seed().demoPassword());
        for (int i = 1; i <= 20; i++) {
            boolean kathmandu = i <= 10;
            String city = kathmandu ? "Kathmandu" : "Melbourne";
            String country = kathmandu ? "NP" : "AU";
            String currency = kathmandu ? "NPR" : "AUD";
            String categorySlug = categorySlugs.get((i - 1) % categorySlugs.size());
            String name = "Demo " + titleCase(categorySlug) + " " + i;
            String email = "demo.vendor" + i + "@wedjan.local";
            Account account = accountRepository.findActiveByEmail(email).orElseGet(() -> {
                Account created = Account.create(email, sharedPasswordHash);
                created.setStatus(Account.Status.ACTIVE);
                created.setDefaultCurrency(currency);
                accountRepository.saveAndFlush(created);
                accountRoleRepository.saveAndFlush(AccountRole.grant(created.getId(), AccountRole.Role.VENDOR));
                Profile profile = Profile.create(created.getId());
                profile.setDisplayName(name);
                profile.setCity(city);
                profile.setCountry(country);
                profile.setTimezone(kathmandu ? "Asia/Kathmandu" : "Australia/Melbourne");
                profileRepository.saveAndFlush(profile);
                return created;
            });
            UUID categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE slug=?", UUID.class,
                    categorySlug);
            if (categoryId == null) continue;
            String slug = "demo-" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("-$", "");
            jdbc.update("""
                    INSERT INTO vendor_profiles(account_id,business_name,slug,tagline,about,team_size,
                        languages,base_city,base_country,status,is_public,onboarding_step,currency,timezone,created_by)
                    VALUES (?,?,?,?,?,5,ARRAY['English'],?,?,'VERIFIED',true,7,?,?,?)
                    ON CONFLICT (account_id) DO NOTHING
                    """, account.getId(), name, slug, "Thoughtful event services in " + city,
                    "A trusted wedjan demo supplier with transparent packages, an experienced local team, "
                            + "and a practical approach to memorable celebrations. We plan carefully, communicate "
                            + "clearly, and tailor every detail while keeping the final price visible from day one. "
                            + "Our team serves couples, families, and event hosts across the city and nearby areas.",
                    city, country, currency,
                    kathmandu ? "Asia/Kathmandu" : "Australia/Melbourne", account.getId());
            jdbc.update("""
                    INSERT INTO vendor_categories(id,vendor_id,category_id,is_primary,created_by)
                    VALUES (?,?,?,?,?) ON CONFLICT (vendor_id,category_id) DO NOTHING
                    """, Uuidv7.next(), account.getId(), categoryId, true, account.getId());
            if (jdbc.queryForObject("SELECT count(*) FROM service_areas WHERE vendor_id=?", Long.class,
                    account.getId()) == 0) {
                jdbc.update("""
                        INSERT INTO service_areas(id,vendor_id,mode,city,country,lat,lng,radius_km,created_by)
                        VALUES (?,?,?,?,?,?,?,?,?)
                        """, Uuidv7.next(), account.getId(), "CITY_RADIUS", city, country,
                        kathmandu ? 27.7172 : -37.8136, kathmandu ? 85.3240 : 144.9631, 60, account.getId());
            }
            jdbc.update("""
                    INSERT INTO packages(id,vendor_id,category_id,title,slug,description_md,price_cents,
                        currency,pricing_model,whats_included_md,booking_mode,deposit_pct,
                        cancellation_policy,status,created_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,'REQUEST',25,'MODERATE','PUBLISHED',?)
                    ON CONFLICT (vendor_id,slug) DO NOTHING
                    """, Uuidv7.next(), account.getId(), categoryId, "Signature celebration package",
                    "signature-celebration-package", "A complete, clearly priced event package.",
                    kathmandu ? 8500000L + i * 100000L : 180000L + i * 5000L, currency, "FLAT",
                    "Planning consultation\nEvent-day service\nPost-event support", account.getId());
        }
    }

    private static String titleCase(String value) {
        StringBuilder result = new StringBuilder();
        for (String word : value.split("-")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
