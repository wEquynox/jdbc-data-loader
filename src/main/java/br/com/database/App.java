
package br.com.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Uso: java br.com.database.App [--users=N] [--venues=N] [--events=N]
 *      [--registrations=N] [--comments=N] [--ratings=N] [--batch=N]
 *      [--seed=N] [--past-ratio=0.3] [--reset] [--no-audit]
 */
public class App {

    private static final Path TIMES_FILE = Path.of("load-times.csv");

    @FunctionalInterface
    private interface Step {
        void run() throws SQLException;
    }

    public static void main(String[] args) {

        LoadConfig config;

        try {
            config = LoadConfig.fromArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Erro: " + e.getMessage());
            System.exit(2);
            return;
        }

        System.out.println("Configuração: " + config);

        Map<String, Double> times = new LinkedHashMap<>();

        long start = System.nanoTime();

        try (Connection connection =
                DatabaseConnection.connect()) {

            if (config.reset) {
                timed(times, "reset", () ->
                    resetSchema(connection, config.schemaFile));
            }

            Map<String, Long> countsBefore = countRows(connection);

            connection.setAutoCommit(false);

            try {
                if (config.noAudit) {
                    setAuditTriggers(connection, false);
                }

                DataGenerator generator =
                    new DataGenerator(connection, config);

                timed(times, "users", generator::generateUsers);
                timed(times, "venues", generator::generateVenues);
                timed(times, "events", generator::generateEvents);
                timed(times, "registrations",
                    generator::generateRegistrations);
                timed(times, "comments", generator::generateComments);
                timed(times, "ratings", generator::generateRatings);

                if (config.noAudit) {
                    setAuditTriggers(connection, true);
                }

                timed(times, "commit", connection::commit);

            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }

            connection.setAutoCommit(true);

            double total = (System.nanoTime() - start) / 1_000_000_000.0;

            System.out.printf("Tempo total: %.3f segundos%n", total);

            compareCounts(config, countsBefore, countRows(connection));

            saveTimes(config, times, total);

        } catch (SQLException e) {
            System.err.println("Erro: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void timed(
            Map<String, Double> times,
            String name,
            Step step) throws SQLException {

        long start = System.nanoTime();

        step.run();

        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        times.put(name, seconds);

        System.out.printf("  [%s] %.3f s%n", name, seconds);
    }

    /**
     * Apaga os objetos do schema e roda o arquivo do schema de novo
     * (tabelas, funções, triggers, view, procedure e dados de exemplo).
     */
    private static void resetSchema(Connection connection, String schemaFile)
            throws SQLException {

        String ddl;

        try {
            ddl = Files.readString(Path.of(schemaFile));
        } catch (IOException e) {
            throw new SQLException(
                "Não foi possível ler " + schemaFile + ": " + e.getMessage()
            );
        }

        try (Statement stmt = connection.createStatement()) {

            stmt.execute("""
                DROP VIEW IF EXISTS event_summary;
                DROP TABLE IF EXISTS ratings, comments, registrations,
                    events, venues, users, audit_log CASCADE;
                DROP PROCEDURE IF EXISTS cancel_registration(BIGINT, BIGINT);
                DROP FUNCTION IF EXISTS event_occupancy(BIGINT);
                DROP FUNCTION IF EXISTS validate_rating();
                DROP FUNCTION IF EXISTS audit_row_change();
                """);

            stmt.execute(ddl);
        }

        System.out.println("Schema recriado a partir de " + schemaFile);
    }

    private static void setAuditTriggers(Connection connection, boolean on)
            throws SQLException {

        String action = on ? "ENABLE" : "DISABLE";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE events " + action
                + " TRIGGER events_audit_trigger");
            stmt.execute("ALTER TABLE registrations " + action
                + " TRIGGER registrations_audit_trigger");
        }

        System.out.println("Triggers de auditoria: "
            + (on ? "ligados" : "desligados durante a carga"));
    }

    private static Map<String, Long> countRows(Connection connection)
            throws SQLException {

        Map<String, Long> counts = new LinkedHashMap<>();

        String[] tables = {
            "users", "venues", "events", "registrations",
            "comments", "ratings", "audit_log"
        };

        try (Statement stmt = connection.createStatement()) {
            for (String table : tables) {
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM " + table)) {
                    rs.next();
                    counts.put(table, rs.getLong(1));
                }
            }
        }

        return counts;
    }

    private static void compareCounts(
            LoadConfig config,
            Map<String, Long> before,
            Map<String, Long> after) {

        Map<String, Long> expected = new LinkedHashMap<>();
        expected.put("users", (long) config.users);
        expected.put("venues", (long) config.venues);
        expected.put("events", (long) config.events);
        expected.put("registrations", (long) config.registrations);
        expected.put("comments", (long) config.comments);
        expected.put("ratings", (long) config.ratings);
        expected.put("audit_log", config.noAudit
            ? 0L
            : (long) config.events + config.registrations);

        System.out.println();
        System.out.printf("%-14s %12s %12s %8s%n",
            "tabela", "esperado", "inserido", "ok?");

        for (Map.Entry<String, Long> entry : expected.entrySet()) {

            String table = entry.getKey();
            long inserted = after.get(table) - before.get(table);

            System.out.printf("%-14s %12d %12d %8s%n",
                table, entry.getValue(), inserted,
                inserted == entry.getValue() ? "sim" : "NÃO");
        }
    }

    private static void saveTimes(
            LoadConfig config,
            Map<String, Double> times,
            double total) {

        try {
            if (Files.notExists(TIMES_FILE)) {
                Files.writeString(
                    TIMES_FILE,
                    "data,config,etapa,segundos\n"
                );
            }

            StringBuilder lines = new StringBuilder();
            String when = LocalDateTime.now().withNano(0).toString();
            String cfg = "\"" + config + (config.noAudit ? ", sem auditoria" : "") + "\"";

            times.forEach((step, seconds) -> lines.append(
                String.format(Locale.ROOT, "%s,%s,%s,%.3f%n", when, cfg, step, seconds)
            ));
            lines.append(
                String.format(Locale.ROOT, "%s,%s,total,%.3f%n", when, cfg, total)
            );

            Files.writeString(
                TIMES_FILE,
                lines,
                StandardOpenOption.APPEND
            );

            System.out.println("\nTempos registrados em " + TIMES_FILE);

        } catch (IOException e) {
            System.err.println(
                "Não foi possível salvar os tempos: " + e.getMessage()
            );
        }
    }
}
