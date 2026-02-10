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
 * Schema fixer: add "changed" columns to meal_schedule if missing.
 * - changed: 내부 상태 코드 (NONE/REPLACED_OUT/REPLACED_IN)
 * - changed_at: 최신 변동 1건(끼니별) 판별을 위한 시각
 *
 * NOTE: H2 테스트에서는 Hibernate ddl-auto로 컬럼 생성이 가능하므로 스킵합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MealScheduleChangedColumnsFixer {

    private final DataSource dataSource;

    @PostConstruct
    public void init() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            String product = "";
            try {
                product = String.valueOf(meta.getDatabaseProductName());
            } catch (Exception ignored) {
                product = "";
            }
            String productLower = product.toLowerCase();
            if (productLower.contains("h2")) {
                return;
            }

            if (!tableExists(meta, "meal_schedule")) {
                return;
            }

            if (!columnExists(meta, "meal_schedule", "changed")) {
                // MariaDB/MySQL compatible
                exec(conn, "ALTER TABLE meal_schedule ADD COLUMN changed VARCHAR(30) NULL DEFAULT 'NONE'");
            }
            if (!columnExists(meta, "meal_schedule", "changed_at")) {
                exec(conn, "ALTER TABLE meal_schedule ADD COLUMN changed_at DATETIME(6) NULL");
            }
        } catch (SQLException e) {
            log.error("[MealScheduleChangedColumnsFixer] failed: {}", e.getMessage());
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

    private void exec(Connection conn, String sql) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("[MealScheduleChangedColumnsFixer] executed: {}", sql);
        } catch (SQLException e) {
            log.warn("[MealScheduleChangedColumnsFixer] failed: {} ({})", sql, e.getMessage());
        }
    }
}







