package com.washcare.crm.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class OwnerBootstrap {

    @Bean
    ApplicationRunner bootstrapOwner(
            UserAccountRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${washcare.owner.email:}") String email,
            @Value("${washcare.owner.password:}") String password,
            @Value("${washcare.owner.name:Shop Owner}") String name) {
        return arguments -> {
            if (email.isBlank() != password.isBlank()) {
                throw new IllegalStateException("Set both WASHCARE_OWNER_EMAIL and WASHCARE_OWNER_PASSWORD to bootstrap an owner.");
            }
            if (!email.isBlank() && !users.existsByEmail(UserAccount.normalizeEmail(email))) {
                if (password.length() < 10 || password.length() > 72) {
                    throw new IllegalStateException("WASHCARE_OWNER_PASSWORD must be between 10 and 72 characters.");
                }
                users.save(new UserAccount(name.trim(), email, passwordEncoder.encode(password), UserRole.OWNER));
            }
        };
    }
}
