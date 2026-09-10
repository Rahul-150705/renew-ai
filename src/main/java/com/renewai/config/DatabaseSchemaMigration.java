package com.renewai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

/**
 * Applies one-time schema corrections that cannot be handled by Hibernate's
 * non-destructive update mode.
 */
@Component
public class DatabaseSchemaMigration {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public DatabaseSchemaMigration(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void removeGlobalPolicyNumberConstraint() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                return;
            }
        }

        List<String> constraintNames = jdbcTemplate.queryForList("""
                SELECT tc.constraint_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                 AND tc.table_name = kcu.table_name
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = 'policies'
                  AND tc.constraint_type = 'UNIQUE'
                GROUP BY tc.constraint_name
                HAVING COUNT(*) = 1
                   AND MAX(kcu.column_name) = 'policy_number'
                """, String.class);

        for (String constraintName : constraintNames) {
            String quotedConstraintName = "\"" + constraintName.replace("\"", "\"\"") + "\"";
            jdbcTemplate.execute("ALTER TABLE policies DROP CONSTRAINT " + quotedConstraintName);
            logger.info("Removed obsolete global policy-number constraint: {}", constraintName);
        }
    }
}
