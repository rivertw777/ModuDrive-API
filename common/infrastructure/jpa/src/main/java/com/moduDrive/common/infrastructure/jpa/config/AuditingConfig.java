package com.moduDrive.common.infrastructure.jpa.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import javax.sql.DataSource;

@EnableJpaAuditing
@Configuration
@ConditionalOnClass(DataSource.class)
public class AuditingConfig {
}
