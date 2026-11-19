
package br.com.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;

public class DataGenerator {

    private static final int BATCH_SIZE =
    LoadConfig.BATCH_SIZE;

    public static void generateUsers(
            Connection connection,
            int totalUsers) throws SQLException {

        String sql = """
            INSERT INTO users (full_name, email)
            VALUES (?, ?)
            """;

        try (PreparedStatement stmt =
                connection.prepareStatement(sql)) {

            for (int i = 1; i <= totalUsers; i++) {

                stmt.setString(1, "Usuario " + i);

                stmt.setString(
                    2,
                    "usuario" + i + "@example.com"
                );

                stmt.addBatch();

                if (i % BATCH_SIZE == 0) {
                    stmt.executeBatch();
                    stmt.clearBatch();
                }
            }

            stmt.executeBatch();
            stmt.clearBatch();
        }

        System.out.println(
            totalUsers + " usuários inseridos!"
        );
    }

    public static void generateVenues(
            Connection connection,
            int totalVenues) throws SQLException {

        String sql = """
            INSERT INTO venues
            (name, building, room, capacity, accessible)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt =
                connection.prepareStatement(sql)) {

            for (int i = 1; i <= totalVenues; i++) {

                stmt.setString(1, "Local Gerado " + i);
                stmt.setString(2, "Bloco Gerado " + i);
                stmt.setString(3, "Sala " + i);
                stmt.setInt(4, 50 + (i % 5) * 50);
                stmt.setBoolean(5, i % 2 == 0);

                stmt.addBatch();

                if (i % LoadConfig.BATCH_SIZE == 0) {
                    stmt.executeBatch();
                    stmt.clearBatch();
                }
            }

            stmt.executeBatch();
            stmt.clearBatch();
        }

        System.out.println(
            totalVenues + " locais inseridos!"
        );
    }

    private static List<Long> getIds(
            Connection connection,
            String table) throws SQLException {

        if (!table.equals("users")
        && !table.equals("venues")
        && !table.equals("events")) {
            throw new IllegalArgumentException(
                "Tabela não permitida: " + table
            );
        }

        List<Long> ids = new ArrayList<>();

        String sql = "SELECT id FROM " + table
                + " ORDER BY id";

        try (Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                ids.add(rs.getLong("id"));
            }
        }

        return ids;
    }

    public static void generateEvents(
            Connection connection,
            int totalEvents) throws SQLException {

        List<Long> userIds = getIds(connection, "users");
        List<Long> venueIds = getIds(connection, "venues");

        if (userIds.isEmpty() || venueIds.isEmpty()) {
            throw new SQLException(
                "É necessário ter usuários e locais."
            );
        }

        Random random = new Random(LoadConfig.SEED);

        String sql = """
            INSERT INTO events
            (title, description, organizer_id, venue_id,
            starts_at, ends_at, capacity, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt =
                connection.prepareStatement(sql)) {

            for (int i = 1; i <= totalEvents; i++) {

                long organizerId = userIds.get(
                    random.nextInt(userIds.size())
                );

                long venueId = venueIds.get(
                    random.nextInt(venueIds.size())
                );

                LocalDateTime start =
                    LocalDateTime.of(2027, 1, 1, 9, 0)
                        .plusDays(random.nextInt(365));

                LocalDateTime end = start.plusHours(2);

                stmt.setString(1, "Evento Gerado " + i);
                stmt.setString(2, "Descrição do evento " + i);
                stmt.setLong(3, organizerId);
                stmt.setLong(4, venueId);
                stmt.setTimestamp(5, Timestamp.valueOf(start));
                stmt.setTimestamp(6, Timestamp.valueOf(end));
                stmt.setInt(7, 30);
                stmt.setString(8, "scheduled");

                stmt.addBatch();

                if (i % LoadConfig.BATCH_SIZE == 0) {
                    stmt.executeBatch();
                    stmt.clearBatch();
                }
            }

            stmt.executeBatch();
            stmt.clearBatch();
        }

        System.out.println(
            totalEvents + " eventos inseridos!"
        );
    }


    public static void generateRegistrations(
            Connection connection,
            int totalRegistrations) throws SQLException {

        List<Long> userIds = getIds(connection, "users");
        List<Long> eventIds = getIds(connection, "events");

        long maxCombinations =
            (long) userIds.size() * eventIds.size();

        if (totalRegistrations > maxCombinations) {
            throw new IllegalArgumentException(
                "Quantidade de inscrições excede as combinações possíveis."
            );
        }

        Random random = new Random(LoadConfig.SEED);

        Set<String> combinations = new HashSet<>();

        String sql = """
            INSERT INTO registrations
            (user_id, event_id, status)
            VALUES (?, ?, ?)
            """;

        try (PreparedStatement stmt =
                connection.prepareStatement(sql)) {

            int pending = 0;

            while (combinations.size() < totalRegistrations) {

                long userId = userIds.get(
                    random.nextInt(userIds.size())
                );

                long eventId = eventIds.get(
                    random.nextInt(eventIds.size())
                );

                String key = userId + ":" + eventId;

                if (!combinations.add(key)) {
                    continue;
                }

                stmt.setLong(1, userId);
                stmt.setLong(2, eventId);
                stmt.setString(3, "registered");

                stmt.addBatch();
                pending++;

                if (pending == LoadConfig.BATCH_SIZE) {
                    stmt.executeBatch();
                    stmt.clearBatch();
                    pending = 0;
                }
            }

            if (pending > 0) {
                stmt.executeBatch();
                stmt.clearBatch();
            }
        }

        System.out.println(
            totalRegistrations + " inscrições inseridas!"
        );
    }

}
