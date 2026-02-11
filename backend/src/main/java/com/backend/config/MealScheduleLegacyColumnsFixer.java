package com.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Legacy schema fixer for removed columns in meal_schedule.
 * - active_plan / plan_version were removed from the entity
 * - DB may still have NOT NULL + no default, which breaks inserts
 * This fixer makes those columns nullable with defaults if they exist.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MealScheduleLegacyColumnsFixer {

    private final DataSource dataSource;

    @PostConstruct
    public void init() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            if (!tableExists(meta, "meal_schedule")) {
                log.info("[MealScheduleLegacyColumnsFixer] meal_schedule not found. Skip.");
                return;
            }

            // If legacy columns exist, DROP them to clean up schema
            if (columnExists(meta, "meal_schedule", "active_plan")) {
                alterColumn(conn,
                        "ALTER TABLE meal_schedule DROP COLUMN active_plan");
            }
            if (columnExists(meta, "meal_schedule", "plan_version")) {
                alterColumn(conn,
                        "ALTER TABLE meal_schedule DROP COLUMN plan_version");
            }
        } catch (SQLException e) {
            log.error("[MealScheduleLegacyColumnsFixer] failed: {}", e.getMessage());
        }
    }

    private boolean tableExists(DatabaseMetaData meta, String tableName) throws SQLException {
        try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private boolean columnExists(DatabaseMetaData meta, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = meta.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    private void alterColumn(Connection conn, String sql) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("[MealScheduleLegacyColumnsFixer] executed: {}", sql);
        } catch (SQLException e) {
            log.warn("[MealScheduleLegacyColumnsFixer] failed: {} ({})", sql, e.getMessage());
        }
    }
}
