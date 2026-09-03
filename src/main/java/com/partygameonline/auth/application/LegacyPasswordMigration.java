package com.partygameonline.auth.application;

import com.partygameonline.user.infrastructure.UserEntity;
import com.partygameonline.user.infrastructure.UserJpaRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time bridge for production rows written by the previous AES password
 * implementation. Successful decryptions are immediately replaced by BCrypt.
 */
@Component
public class LegacyPasswordMigration {

    private static final Logger log = LoggerFactory.getLogger(LegacyPasswordMigration.class);

    private final UserJpaRepository users;
    private final AesPasswordCipher passwordCipher;

    public LegacyPasswordMigration(UserJpaRepository users, AesPasswordCipher passwordCipher) {
        this.users = users;
        this.passwordCipher = passwordCipher;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        List<UserEntity> upgraded = new ArrayList<>();
        int skipped = 0;
        for (UserEntity user : users.findAll()) {
            if (!passwordCipher.needsUpgrade(user.getPasswordAes())) {
                continue;
            }
            var rawPassword = passwordCipher.decryptLegacy(user.getPasswordAes());
            if (rawPassword.isEmpty()) {
                skipped++;
                continue;
            }
            user.upgradePassword(passwordCipher.encrypt(rawPassword.get()));
            upgraded.add(user);
        }
        if (!upgraded.isEmpty()) {
            users.saveAll(upgraded);
        }
        log.info("Legacy password migration completed upgraded={} skipped={}", upgraded.size(), skipped);
    }
}
