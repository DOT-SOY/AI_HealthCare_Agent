package com.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 기존 DB 스키마가 Routine.status를 숫자/짧은 문자열로 갖고 있는 경우,
 * EnumType.STRING("EXPECTED", "IN_PROGRESS", "COMPLETED") 삽입 시 MariaDB가
 * "Data truncated for column 'status'"로 실패할 수 있습니다.
 *
 * - ddl-auto=update는 컬럼 타입/길이 변경을 항상 수행하지 않으므로,
 *   런타임에서 안전하게(가능하면) status 컬럼을 VARCHAR(20)으로 보정합니다.
 *
 * NOTE: 권한이 없으면 보정이 실패할 수 있으며, 그 경우 경고 로그만 남깁니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoutineStatusColumnFixer {

    private static final int MIN_LEN = 20;
    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void fixRoutineStatusColumnIfNeeded() {
        try {
            // information_schema는 MariaDB/MySQL 공통으로 사용 가능
            var row = jdbcTemplate.queryForMap(
                    """
                    SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'routine'
                      AND COLUMN_NAME = 'status'
                    """);

            String dataType = String.valueOf(row.getOrDefault("DATA_TYPE", "")).toLowerCase();
            Object lenRaw = row.get("CHARACTER_MAXIMUM_LENGTH");
            Integer len = null;
            if (lenRaw instanceof Number n) {
                len = n.intValue();
            } else if (lenRaw != null) {
                try {
                    len = Integer.parseInt(String.valueOf(lenRaw));
                } catch (Exception ignored) {
                    len = null;
                }
            }

            boolean needsFix = !"varchar".equals(dataType) || (len != null && len < MIN_LEN);
            if (!needsFix) {
                return;
            }

            log.warn("[SchemaFix] routine.status column type/len seems incompatible (type={}, len={}). Trying to alter to VARCHAR({})...",
                    dataType, len, MIN_LEN);

            // NULL/DEFAULT는 보수적으로 건드리지 않음(명시하지 않으면 DB 기본값에 따름)
            jdbcTemplate.execute("ALTER TABLE routine MODIFY COLUMN status VARCHAR(" + MIN_LEN + ")");
            log.info("[SchemaFix] routine.status column altered to VARCHAR({}).", MIN_LEN);
        } catch (EmptyResultDataAccessException e) {
            // 테이블이 아직 없으면 건너뜀 (ddl-auto=create/update에서 나중에 생길 수 있음)
            log.debug("[SchemaFix] routine.status column not found yet. Skipping.");
        } catch (Exception e) {
            log.warn("[SchemaFix] Failed to alter routine.status column: {}", e.getMessage());
        }
    }
}


