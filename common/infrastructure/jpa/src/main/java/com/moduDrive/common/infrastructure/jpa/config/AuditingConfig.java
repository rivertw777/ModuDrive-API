package com.moduDrive.common.infrastructure.jpa.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@Configuration
@ConditionalOnClass(DataSource.class)
public class AuditingConfig {

    private static final String USER_ID_HEADER = "X_USER_ID";

    /** Feeds {@code @CreatedBy}/{@code @LastModifiedBy} from the gateway's {@code X_USER_ID} header.
     * Empty outside an HTTP request or when the header is missing or malformed. */
    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
                return Optional.empty();
            }
            HttpServletRequest request = attrs.getRequest();
            String header = request.getHeader(USER_ID_HEADER);
            if (header == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(header));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        };
    }
}
