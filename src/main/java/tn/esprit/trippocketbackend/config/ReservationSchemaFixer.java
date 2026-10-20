package tn.esprit.trippocketbackend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One-time, automatic fix for databases created before the Reservation module.
 *
 * Hibernate creates @Enumerated(STRING) columns as MySQL ENUM('A','B',...) and "ddl-auto=update"
 * never changes an existing column. Without this fix, saving the new values TRIP or COMPLETED
 * fails with "Data truncated for column". This runner converts the reservation columns to VARCHAR
 * (and widens notes to 500) only when needed, so each teammate's database is fixed on startup.
 *
 * It only touches the "reservation" table, does nothing on other databases or when already fixed,
 * and never blocks the application from starting.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationSchemaFixer implements ApplicationRunner {

    private static final Set<String> ENUM_COLUMNS = Set.of("type", "status", "payment_method");
    private static final int NOTES_LENGTH = 500;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            String product = jdbcTemplate.execute(
                    (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
            String db = product == null ? "" : product.toLowerCase(Locale.ROOT);
            if (!db.contains("mysql") && !db.contains("mariadb")) {
                return;
            }

            List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                    SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND LOWER(TABLE_NAME) = 'reservation'
                      AND LOWER(COLUMN_NAME) IN ('type', 'status', 'payment_method', 'notes')
                    """);

            List<String> changes = new ArrayList<>();
            for (Map<String, Object> column : columns) {
                String name = String.valueOf(column.get("COLUMN_NAME")).toLowerCase(Locale.ROOT);
                String dataType = String.valueOf(column.get("DATA_TYPE")).toLowerCase(Locale.ROOT);
                Object maxLength = column.get("CHARACTER_MAXIMUM_LENGTH");

                if (ENUM_COLUMNS.contains(name) && "enum".equals(dataType)) {
                    changes.add("MODIFY COLUMN `" + name + "` VARCHAR(20)");
                }
                if ("notes".equals(name) && maxLength instanceof Number length && length.longValue() < NOTES_LENGTH) {
                    changes.add("MODIFY COLUMN `notes` VARCHAR(" + NOTES_LENGTH + ")");
                }
            }

            if (changes.isEmpty()) {
                return;
            }

            String sql = "ALTER TABLE reservation " + String.join(", ", changes);
            jdbcTemplate.execute(sql);
            log.info("Reservation table columns updated: {}", sql);
        } catch (Exception e) {
            log.warn("Could not check the reservation table columns (the app keeps running): {}", e.getMessage());
        }
    }
}
