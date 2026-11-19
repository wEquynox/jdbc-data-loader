package br.com.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.*;

class PostgresTest {
    private Connection db;

    @BeforeEach
    void createIsolatedSchema() throws Exception {
        String url = System.getenv("TEST_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "Use scripts/test-isolated.sh para testar PostgreSQL");
        db = DriverManager.getConnection(url, System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
        assertTrue(value("SELECT current_database()").startsWith("jdbc_loader_test"));
        db.setAutoCommit(false);
        String schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET LOCAL search_path TO " + schema);
        execute(Files.readString(Path.of("sql/schema.sql")));
    }

    @AfterEach
    void rollback() throws Exception {
        if (db != null) {
            db.rollback();
            db.close();
        }
    }

    private void execute(String sql) throws SQLException {
        try (Statement s = db.createStatement()) { s.execute(sql); }
    }

    private String value(String sql) throws SQLException {
        try (Statement s = db.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private void rejected(String sql) throws SQLException {
        Savepoint point = db.setSavepoint();
        assertThrows(SQLException.class, () -> execute(sql));
        db.rollback(point);
        db.releaseSavepoint(point);
    }

    private LoadConfig smallConfig() {
        return LoadConfig.fromArgs(new String[]{"--users=100", "--venues=5", "--events=20",
            "--registrations=300", "--comments=50", "--ratings=30", "--batch=17",
            "--past-ratio=1", "--seed=42"});
    }

    private void generate(LoadConfig c) throws SQLException {
        DataGenerator g = new DataGenerator(db, c);
        g.generateUsers();
        g.generateVenues();
        g.generateEvents();
        g.generateRegistrations();
        g.generateComments();
        g.generateRatings();
    }

    private void seed() throws Exception {
        execute(Files.readString(Path.of("sql/seed.sql")));
    }

    @Test
    void batchLoadAndAdditionalLoadRespectCountsAndRules() throws Exception {
        generate(smallConfig());
        generate(smallConfig());
        String[] tables = {"users", "venues", "events", "registrations", "comments", "ratings", "audit_log"};
        int[] expected = {200, 10, 40, 600, 100, 60, 640};
        for (int i = 0; i < tables.length; i++) {
            assertEquals("" + expected[i], value("SELECT count(*) FROM " + tables[i]), tables[i]);
        }
        assertEquals("0", value("SELECT count(*) FROM (SELECT user_id,event_id FROM registrations "
            + "GROUP BY user_id,event_id HAVING count(*)>1) d"));
        assertEquals("0", value("SELECT count(*) FROM ratings rt LEFT JOIN registrations r "
            + "ON (r.user_id,r.event_id)=(rt.user_id,rt.event_id) JOIN events e ON e.id=rt.event_id "
            + "WHERE r.id IS NULL OR r.status<>'attended' OR e.ends_at>now() OR e.status<>'completed'"));
    }

    @Test
    void sameSeedReproducesBusinessData() throws Exception {
        generate(smallConfig());
        String[] tables = {"users", "venues", "events", "registrations", "comments", "ratings"};
        String[] snapshots = new String[tables.length];
        for (int i = 0; i < tables.length; i++) {
            snapshots[i] = value("SELECT jsonb_agg(to_jsonb(t) ORDER BY id)::text FROM " + tables[i] + " t");
        }
        // Segundo schema vazio no mesmo banco de testes, sem apagar o primeiro.
        execute("CREATE SCHEMA replay");
        execute("SET LOCAL search_path TO replay");
        execute(Files.readString(Path.of("sql/schema.sql")));
        generate(smallConfig());
        for (int i = 0; i < tables.length; i++) {
            assertEquals(snapshots[i], value("SELECT jsonb_agg(to_jsonb(t) ORDER BY id)::text FROM " + tables[i] + " t"), tables[i]);
        }
    }

    @Test
    void viewDoesNotMultiplyRegistrationsByRatings() throws Exception {
        seed();
        assertEquals("3", value("SELECT registered_count FROM event_summary WHERE event_id=1"));
        assertEquals("4.67", value("SELECT average_rating FROM event_summary WHERE event_id=1"));
        assertNull(value("SELECT average_rating FROM event_summary WHERE event_id=2"));
        execute("DELETE FROM registrations WHERE event_id=3");
        assertEquals("0", value("SELECT registered_count FROM event_summary WHERE event_id=3"));
        assertEquals("Auditório Central", value("SELECT venue_name FROM event_summary WHERE event_id=1"));
    }

    @Test
    void procedureCancelsOnlyActiveRegistration() throws Exception {
        seed();
        execute("CALL cancel_registration(1,2)");
        assertEquals("cancelled", value("SELECT status FROM registrations WHERE user_id=1 AND event_id=2"));
        rejected("CALL cancel_registration(1,2)");
        rejected("CALL cancel_registration(999,2)");
        rejected("CALL cancel_registration(1,1)");
        assertEquals("attended", value("SELECT status FROM registrations WHERE user_id=1 AND event_id=1"));
    }

    @Test
    void constraintsRejectInvalidRelationsAndRatings() throws Exception {
        seed();
        rejected("INSERT INTO registrations(user_id,event_id) VALUES (999,1)");
        rejected("INSERT INTO registrations(user_id,event_id) VALUES (1,999)");
        rejected("INSERT INTO registrations(user_id,event_id) VALUES (1,1)");
        rejected("INSERT INTO ratings(user_id,event_id,score) VALUES (4,1,4)");
        rejected("UPDATE ratings SET score=6 WHERE id=1");
        rejected("UPDATE ratings SET registration_status='registered' WHERE id=1");
        execute("UPDATE registrations SET status='attended' WHERE user_id=1 AND event_id=2");
        execute("UPDATE events SET ends_at=now()+interval '10 years' WHERE id=2");
        rejected("INSERT INTO ratings(user_id,event_id,score) VALUES (1,2,5)");
        rejected("UPDATE events SET ends_at=now()+interval '10 years' WHERE id=1");
        rejected("UPDATE events SET status='cancelled' WHERE id=1");
        rejected("UPDATE registrations SET status='cancelled' WHERE id=1");
    }

    private void assertAudit(String table, String id, String action, String oldRow, String newRow) throws SQLException {
        try (PreparedStatement s = db.prepareStatement("SELECT count(*) FROM audit_log WHERE table_name=? "
                + "AND entity_id=? AND action=? AND old_data IS NOT DISTINCT FROM ?::jsonb "
                + "AND new_data IS NOT DISTINCT FROM ?::jsonb")) {
            s.setString(1, table); s.setString(2, id); s.setString(3, action);
            s.setString(4, oldRow); s.setString(5, newRow);
            try (ResultSet rs = s.executeQuery()) { rs.next(); assertEquals(1, rs.getInt(1)); }
        }
    }

    @Test
    void auditStoresExactOldAndNewRowsForAllOperations() throws Exception {
        seed();
        execute("INSERT INTO events(title,description,organizer_id,venue_id,starts_at,ends_at,capacity) "
            + "VALUES ('Teste','Auditoria',1,1,'2027-01-01 10:00-03','2027-01-01 11:00-03',10)");
        String eventId = value("SELECT max(id) FROM events");
        execute("INSERT INTO registrations(user_id,event_id) VALUES (1," + eventId + ")");
        String registrationId = value("SELECT max(id) FROM registrations");
        String[] tables = {"events", "registrations"};
        String[] ids = {eventId, registrationId};
        for (int i = 0; i < tables.length; i++) {
            String table = tables[i], id = ids[i];
            String oldRow = value("SELECT to_jsonb(t)::text FROM " + table + " t WHERE id=" + id);
            assertAudit(table, id, "INSERT", null, oldRow);
            execute("UPDATE " + table + " SET status='cancelled' WHERE id=" + id);
            String newRow = value("SELECT to_jsonb(t)::text FROM " + table + " t WHERE id=" + id);
            assertAudit(table, id, "UPDATE", oldRow, newRow);
        }
        // Excluir inscrição antes do evento para conferir DELETE independente.
        for (int i = tables.length - 1; i >= 0; i--) {
            String oldRow = value("SELECT to_jsonb(t)::text FROM " + tables[i] + " t WHERE id=" + ids[i]);
            execute("DELETE FROM " + tables[i] + " WHERE id=" + ids[i]);
            assertAudit(tables[i], ids[i], "DELETE", oldRow, null);
        }
    }

    @Test
    void impossibleRandomRatingCountCanBeRolledBack() throws Exception {
        LoadConfig c = LoadConfig.fromArgs(new String[]{"--users=10", "--venues=1", "--events=1",
            "--registrations=1", "--comments=0", "--ratings=1", "--past-ratio=0.000000001"});
        Savepoint before = db.setSavepoint();
        assertThrows(SQLException.class, () -> generate(c));
        db.rollback(before);
        assertEquals("0", value("SELECT count(*) FROM users"));
        assertEquals("0", value("SELECT count(*) FROM audit_log"));
    }
}
