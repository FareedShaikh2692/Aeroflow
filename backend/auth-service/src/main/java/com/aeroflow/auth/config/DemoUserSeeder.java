package com.aeroflow.auth.config;

import com.aeroflow.auth.domain.User;
import com.aeroflow.auth.repo.UserRepository;
import com.aeroflow.auth.service.AuthService;
import com.aeroflow.common.security.Roles;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds one demo account per role (demo environments only: SEED_DEMO_DATA=false disables it). */
@Component
@ConditionalOnProperty(name = "aeroflow.seed.demo-users", havingValue = "true")
public class DemoUserSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final String password;

    public DemoUserSeeder(UserRepository users, PasswordEncoder encoder, AuthService auth,
                          @Value("${aeroflow.seed.demo-password}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.auth = auth;
        this.password = password;
    }

    record Demo(String email, String first, String last, String phone, String role) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Demo> demos = List.of(
                new Demo("superadmin@aeroflow.dev", "Layla", "Haddad", "+971500000001", Roles.SUPER_ADMIN),
                new Demo("admin@aeroflow.dev", "Omar", "Rahman", "+971500000002", Roles.ADMIN),
                new Demo("ops@aeroflow.dev", "Priya", "Nair", "+971500000003", Roles.OPERATIONS_MANAGER),
                new Demo("agent@aeroflow.dev", "Daniel", "Okafor", "+971500000004", Roles.FLIGHT_AGENT),
                new Demo("checkin@aeroflow.dev", "Mei", "Tanaka", "+971500000005", Roles.CHECKIN_AGENT),
                new Demo("passenger@aeroflow.dev", "Sarah", "Ahmed", "+971500000006", Roles.PASSENGER));
        String hash = null;
        for (Demo d : demos) {
            if (users.existsByEmailIgnoreCase(d.email())) continue;
            if (hash == null) hash = encoder.encode(password);
            User u = users.save(new User(d.email(), hash, d.first(), d.last(), d.phone(), Set.of(d.role())));
            if (Roles.PASSENGER.equals(d.role())) auth.publishRegistered(u);
            log.info("Seeded demo user {} ({})", d.email(), d.role());
        }
    }
}
