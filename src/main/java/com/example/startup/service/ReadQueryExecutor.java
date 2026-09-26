package com.example.startup.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class ReadQueryExecutor {
    private static final Logger log = LoggerFactory.getLogger(ReadQueryExecutor.class);

    private final JdbcTemplate primary;
    private final HikariDataSource replicaDataSource;
    private final JdbcTemplate replica;

    @Autowired
    public ReadQueryExecutor(JdbcTemplate primary,
            @Value("${app.datasource.replica.url:}") String replicaUrl,
            @Value("${app.datasource.replica.username:}") String replicaUsername,
            @Value("${app.datasource.replica.password:}") String replicaPassword,
            @Value("${app.datasource.replica.maximum-pool-size:4}") int maximumPoolSize,
            @Value("${spring.datasource.username}") String primaryUsername,
            @Value("${spring.datasource.password}") String primaryPassword) {
        this.primary = primary;

        if (replicaUrl == null || replicaUrl.isBlank()) {
            this.replicaDataSource = null;
            this.replica = null;
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("replica-read-pool");
        config.setJdbcUrl(replicaUrl);
        config.setUsername(replicaUsername.isBlank() ? primaryUsername : replicaUsername);
        config.setPassword(replicaPassword.isBlank() ? primaryPassword : replicaPassword);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3000);
        config.setReadOnly(true);
        this.replicaDataSource = new HikariDataSource(config);
        this.replica = new JdbcTemplate(replicaDataSource);
    }

    ReadQueryExecutor(JdbcTemplate primary, JdbcTemplate replica) {
        this.primary = primary;
        this.replica = replica;
        this.replicaDataSource = null;
    }

    public <T> T execute(Function<JdbcTemplate, T> query) {
        if (replica == null) {
            return query.apply(primary);
        }

        try {
            return query.apply(replica);
        } catch (DataAccessException exception) {
            log.warn("Replica query failed. Retrying on the primary database.", exception);
            return query.apply(primary);
        }
    }

    public boolean isReplicaConfigured() {
        return replica != null;
    }

    @PreDestroy
    void closeReplicaPool() {
        if (replicaDataSource != null) {
            replicaDataSource.close();
        }
    }
}
