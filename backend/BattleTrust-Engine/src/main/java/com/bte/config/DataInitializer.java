package com.bte.config;

import com.bte.entity.*;
import com.bte.enums.*;
import com.bte.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final Environment env;
    private final UserRepository users;
    private final WalletRepository wallets;
    private final TournamentRepository tournaments;
    private final LedgerTransactionRepository ledger;
    private final PasswordEncoder encoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final SystemSettingRepository settings;
    private final PlatformTreasuryRepository treasury;
    private final FinancialPolicyRepository financialPolicy;

    @Override
    @Transactional
    public void run(String... args) {
        if (!financialPolicy.existsById(1L)) financialPolicy.save(new FinancialPolicy());
        if (!settings.existsById("organizer.staking")) settings.save(new SystemSetting());
        if (!treasury.existsById(1L)) treasury.save(new PlatformTreasury());
        if (env.matchesProfiles("dev")) {
            Integer oldVoucherConstraint=jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name='payment_vouchers' and column_name='registration_id' and is_nullable='NO'",Integer.class);
            if(oldVoucherConstraint!=null && oldVoucherConstraint>0)jdbc.execute("alter table payment_vouchers modify column registration_id bigint null");
            jdbc.execute("update payment_vouchers v join registrations r on r.id=v.registration_id set v.tournament_id=r.tournament_id where v.tournament_id is null");
            // Older local schemas used a required `guest` column. Hibernate now maps `is_guest`.
            // Keep old rows intact and allow new inserts; production changes use reviewed SQL migrations.
            Integer legacy = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = database() and table_name = 'chat_messages' and column_name = 'guest'", Integer.class);
            if (legacy != null && legacy > 0) jdbc.execute("alter table chat_messages alter column guest set default 0");
        }
        String email = env.getProperty("ADMIN_EMAIL", env.getProperty("ADMIN_DEFAULT_EMAIL", "")).trim().toLowerCase(Locale.ROOT);
        String password = env.getProperty("ADMIN_PASSWORD", env.getProperty("ADMIN_DEFAULT_PASSWORD", ""));
        users.findByEmail(email).filter(u -> u.getRole()!=Role.ROLE_ADMIN).ifPresent(u -> {throw new IllegalStateException("Admin bootstrap email already belongs to another account");});
        if (!email.isBlank() && !password.isBlank() && !users.existsByEmail(email)) {
            if (password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new IllegalStateException("Admin bootstrap password must be 12 to 72 UTF-8 bytes");
            users.save(User.builder().name("System Admin").email(email).emailVerified(true).password(encoder.encode(password)).role(Role.ROLE_ADMIN).build());
        }
        // Demo money and fixtures are explicitly confined to an opt-in development profile.
        users.findByEmail(email).filter(u -> u.getRole()==Role.ROLE_ADMIN).ifPresent(u -> u.setEmailVerified(true));
    }
}
