package com.moduDrive.file.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** No application code reads or writes this entity — it exists purely so Hibernate's
 * ddl-auto=update (this project has no Flyway/Liquibase) creates the "shedlock" table that
 * ShedLock's JdbcTemplateLockProvider needs. ShedLock manages the actual rows itself via raw
 * SQL, bypassing JPA entirely; this class only pins down the schema. Column names/types match
 * ShedLock's own default DDL (see its "Postgres" schema in the ShedLock docs) so the table it
 * finds is the one it expects regardless of which DB vendor is behind the DataSource. */
@Entity
@Table(name = "shedlock")
class ShedLockJpaEntity {

    @Id
    @Column(length = 64)
    private String name;

    @Column(name = "lock_until", nullable = false)
    private Instant lockUntil;

    @Column(name = "locked_at", nullable = false)
    private Instant lockedAt;

    @Column(name = "locked_by", nullable = false, length = 255)
    private String lockedBy;
}
