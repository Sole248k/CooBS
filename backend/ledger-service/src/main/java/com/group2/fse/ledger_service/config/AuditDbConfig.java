package com.group2.fse.ledger_service.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class AuditDbConfig {

    private static final int AUDIT_TIMEOUT_MS = 2000;

    @Bean(name = "auditPostgresDataSource")
    public DataSource auditPostgresDataSource(
            @Value("${postgres.datasource.url:#{null}}") String explicitUrl,
            @Value("${POSTGRES_HOST:${postgres.datasource.host:localhost}}") String host,
            @Value("${POSTGRES_PORT:${postgres.datasource.port:5434}}") String port,
            @Value("${POSTGRES_DB:audit_store}") String db,
            @Value("${postgres.datasource.username:${POSTGRES_USER:postgres}}") String user,
            @Value("${postgres.datasource.password:${POSTGRES_PASSWORD}}") String password) {

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        String jdbcUrl = (explicitUrl != null && !explicitUrl.isBlank())
                ? explicitUrl
                : "jdbc:postgresql://" + host + ":" + port + "/" + db;
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);
        dataSource.setPoolName("HikariPool-PostgresAudit");
        // The audit row is written while the posting holds Oracle row locks. Without these a slow
        // audit store keeps those locks for Hikari's default 30 s; the saga gives up after 2 s.
        dataSource.setConnectionTimeout(AUDIT_TIMEOUT_MS);
        dataSource.setValidationTimeout(1000);

        return dataSource;
    }

    @Bean(name = "auditJdbcTemplate")
    public JdbcTemplate auditJdbcTemplate(@Qualifier("auditPostgresDataSource") DataSource auditPostgresDataSource) {
        JdbcTemplate template = new JdbcTemplate(auditPostgresDataSource);
        template.setQueryTimeout(AUDIT_TIMEOUT_MS / 1000);
        return template;
    }
}