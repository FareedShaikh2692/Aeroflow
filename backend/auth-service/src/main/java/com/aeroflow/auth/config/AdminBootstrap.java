package com.aeroflow.auth.config;

import com.aeroflow.auth.api.AuthDtos;
import com.aeroflow.auth.domain.User;
import com.aeroflow.auth.repo.UserRepository;
import com.aeroflow.common.security.Roles;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the initial SUPER_ADMIN from ADMIN_EMAIL / ADMIN_PASSWORD when that account does not exist yet.
 * Idempotent and never overwrites an existing password. Refuses weak passwords.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder,
                          @Value("${aeroflow.bootstrap.admin-email:}") String email,
                          @Value("${aeroflow.bootstrap.admin-password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.email = email == null ? "" : email.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isEmpty() || password.isEmpty()) return;
        if (users.existsByEmailIgnoreCase(email)) return;
        if (!password.matches(AuthDtos.PASSWORD_RULE)) {
            log.error("ADMIN_PASSWORD does not meet the password policy - bootstrap admin NOT created");
            return;
        }
        users.save(new User(email, encoder.encode(password), "System", "Administrator", null, Set.of(Roles.SUPER_ADMIN)));
        log.info("Bootstrap SUPER_ADMIN created for {}", email);
    }
}
