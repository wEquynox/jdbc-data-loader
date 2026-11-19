
package br.com.database;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;

/**
 * Gera os dados respeitando as regras do schema:
 * - toda inscrição aponta para um usuário e um evento criados aqui,
 *   sem repetir o par (user_id, event_id);
 * - eventos passados ficam como 'completed' e parte das inscrições deles
 *   como 'attended';
 * - avaliações só são criadas para inscrições 'attended' (a FK composta
 *   exige isso) de eventos que já terminaram (o trigger validate_rating
 *   exige isso).
 *
 * Toda a aleatoriedade sai de um único Random com semente fixa e as datas
 * são calculadas a partir de uma data de referência fixa, então a mesma
 * semente gera sempre os mesmos dados.
 */
public class DataGenerator {

    private static final ZoneOffset ZONE = ZoneOffset.ofHours(-3);

    // usada no lugar de NOW() para a carga ser reproduzível
    private static final long REFERENCE =
        epoch(2026, 11, 1);

    // eventos passados: terminam bem antes da data de referência
    private static final long PAST_FROM = epoch(2025, 1, 1);
    private static final long PAST_TO = epoch(2026, 9, 30);

    private static final long FUTURE_FROM = epoch(2027, 1, 1);
    private static final long FUTURE_TO = epoch(2027, 12, 31);

    private static final long DAY = 86_400;

    private static final byte SCHEDULED = 0;
    private static final byte COMPLETED = 1;
    private static final byte CANCELLED = 2;

    private static final String[] FIRST_NAMES = {
        "Ana", "Bruno", "Carla", "Diego", "Eduarda", "Felipe", "Gabriela",
        "Henrique", "Isabela", "João", "Larissa", "Marcos", "Natália",
        "Otávio", "Paula", "Rafael", "Sofia", "Thiago", "Vitória", "Lucas"
    };

    private static final String[] LAST_NAMES = {
        "Souza", "Lima", "Mendes", "Rocha", "Silva", "Santos", "Oliveira",
        "Pereira", "Costa", "Almeida", "Ferreira", "Gomes", "Ribeiro",
        "Carvalho", "Martins", "Araújo", "Barbosa", "Cardoso"
    };

    private static final String[] TOPICS = {
        "PostgreSQL", "Otimização SQL", "Desenvolvimento Mobile",
        "Inteligência Artificial", "Segurança da Informação",
        "Engenharia de Software", "Computação em Nuvem", "Ciência de Dados",
        "Redes de Computadores", "Empreendedorismo", "Design de Interfaces"
    };

    private static final String[] FORMATS = {
        "Palestra", "Oficina", "Minicurso", "Encontro", "Mesa-redonda"
    };

    private static final String[] TAGS = {
        "banco de dados", "sql", "postgresql", "mobile", "programacao",
        "projetos", "ia", "seguranca", "nuvem", "dados", "carreira", "design"
    };

    private static final String[] LEVELS = {
        "beginner", "intermediate", "advanced"
    };

    private static final String[] COMMENT_TEXTS = {
        "Ótimo conteúdo, recomendo.",
        "A parte prática foi muito útil.",
        "Estou animado para participar.",
        "Poderia ter mais exemplos.",
        "O local estava cheio, mas valeu a pena.",
        "Alguém sabe se haverá certificado?",
        "Os slides serão disponibilizados?",
        "Excelente organização."
    };

    private static final String[] RATING_TEXTS = {
        "Conteúdo claro e bem organizado.",
        "Gostei bastante dos exemplos.",
        "Excelente palestra.",
        "Esperava algo mais aprofundado.",
        "Bom, mas passou do horário."
    };

    // distribuição das notas, puxada para as notas altas
    private static final int[] SCORES = {1, 2, 3, 3, 4, 4, 4, 5, 5, 5};

    private final Connection connection;
    private final LoadConfig config;
    private final Random random;

    private long[] userIds;
    private long[] venueIds;
    private int[] venueCapacities;

    private long[] eventIds;
    private long[] eventStarts;
    private long[] eventEnds;
    private byte[] eventStatus;

    // cada par é (índice do evento << 32) | índice do usuário
    private long[] registrationPairs;
    private long[] attendedPairs;
    private int attendedCount;

    public DataGenerator(Connection connection, LoadConfig config) {
        this.connection = connection;
        this.config = config;
        this.random = new Random(config.seed);
    }

    public int attendedCount() {
        return attendedCount;
    }

    public void generateUsers() throws SQLException {

        long maxBefore = maxId("users");

        String sql = """
            INSERT INTO users (full_name, email, created_at)
            VALUES (?, ?, ?)
            """;

        try (PreparedStatement stmt =
                connection.prepareStatement(sql)) {

            int pending = 0;

            for (int i = 1; i <= config.users; i++) {

                String name =
                    pick(FIRST_NAMES) + " " + pick(LAST_NAMES);

                stmt.setString(1, name);
                stmt.setString(2, "usuario" + (maxBefore + i) + "@example.com");
                stmt.setObject(
                    3,
                    toDateTime(REFERENCE - random.nextInt(730) * DAY)
                );

                pending = addToBatch(stmt, pending);
            }

            flush(stmt, pending);
        }

        userIds = newIds("users", maxBefore);

        System.out.println(userIds.length + " usuários inseridos!");
    }

    public void generateVenues() throws SQLException {

        long maxBefore = maxId("venues");

        String sql = """
            INSERT INTO venues
            (name, building, room, capacity, accessible)
            VALUES (?, ?, ?, ?, ?)
            """;

        venueCapacities = new int[config.venues];

        try (PreparedStatement stmt =
                connection.prepareStatement(sql)) {

            int pending = 0;

            for (int i = 0; i < config.venues; i++) {

                // 20 salas por bloco; "Gerado" evita colidir com os blocos do seed
                long number = maxBefore + i;
                long building = number / 20 + 1;
                int capacity = 50 + random.nextInt(10) * 50;

                venueCapacities[i] = capacity;

                stmt.setString(1, "Local " + (i + 1));
                stmt.setString(2, "Bloco Gerado " + building);
                stmt.setString(3, "G" + building + "-" + (number % 20 + 1));
                stmt.setInt(4, capacity);
                stmt.setBoolean(5, random.nextInt(10) < 7);

                pending = addToBatch(stmt, pending);
            }

            flush(stmt, pending);
        }

        venueIds = newIds("venues", maxBefore);

        System.out.println(venueIds.length + " locais inseridos!");
    }

    public void generateEvents() throws SQLException {

        long maxBefore = maxId("events");

        int total = config.events;

        // quantas inscrições cada evento vai ter; decidido antes para a
        // capacidade do evento comportar os inscritos
        int[] registrationsPerEvent = allocateRegistrations();

        eventStarts = new long[total];
        eventEnds = new long[total];
        eventStatus = new byte[total];

        String sql = """
            INSERT INTO events
            (title, description, organizer_id, venue_id,
            starts_at, ends_at, capacity, status, tags, metadata, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """;

        try (PreparedStatement stmt =
                connection.prepareStatement(sql)) {

            int pending = 0;

            for (int i = 0; i < total; i++) {

                int venue = random.nextInt(venueIds.length);
                long organizerId =
                    userIds[random.nextInt(userIds.length)];

                boolean past =
                    random.nextDouble() < config.pastEventRatio;

                long start = past
                    ? randomStart(PAST_FROM, PAST_TO)
                    : randomStart(FUTURE_FROM, FUTURE_TO);

                // de 1h a 4h, em passos de 30 minutos
                long end = start + (2 + random.nextInt(7)) * 1800L;

                byte status;
                if (past) {
                    status = COMPLETED;
                } else if (random.nextInt(100) < 5) {
                    status = CANCELLED;
                } else {
                    status = SCHEDULED;
                }

                eventStarts[i] = start;
                eventEnds[i] = end;
                eventStatus[i] = status;

                int capacity = Math.max(
                    venueCapacities[venue],
                    registrationsPerEvent[i]
                );

                String topic = pick(TOPICS);
                String format = pick(FORMATS);

                stmt.setString(1, format + " de " + topic + " #" + (i + 1));
                stmt.setString(
                    2,
                    format + " sobre " + topic + " gerado pela carga."
                );
                stmt.setLong(3, organizerId);
                stmt.setLong(4, venueIds[venue]);
                stmt.setObject(5, toDateTime(start));
                stmt.setObject(6, toDateTime(end));
                stmt.setInt(7, capacity);
                stmt.setString(8, statusName(status));
                stmt.setArray(9, randomTags());
                stmt.setString(
                    10,
                    "{\"level\": \"" + pick(LEVELS)
                        + "\", \"format\": \"in-person\"}"
                );
                stmt.setObject(11, toDateTime(REFERENCE - 730 * DAY));

                pending = addToBatch(stmt, pending);
            }

            flush(stmt, pending);
        }

        eventIds = newIds("events", maxBefore);

        buildRegistrationPairs(registrationsPerEvent);

        System.out.println(eventIds.length + " eventos inseridos!");
    }

    public void generateRegistrations() throws SQLException {

        String sql = """
            INSERT INTO registrations
            (user_id, event_id, status, registered_at)
            VALUES (?, ?, ?, ?)
            """;

        attendedPairs = new long[1024];
        attendedCount = 0;

        try (PreparedStatement stmt =
                connection.prepareStatement(sql)) {

            int pending = 0;

            for (long pair : registrationPairs) {

                int event = eventOf(pair);
                int user = userOf(pair);

                String status;
                long registeredAt;

                switch (eventStatus[event]) {
                    case COMPLETED -> {
                        status = random.nextInt(100) < 85
                            ? "attended"
                            : "cancelled";
                        registeredAt = eventStarts[event]
                            - (1 + random.nextInt(60)) * DAY;
                    }
                    case CANCELLED -> {
                        status = "cancelled";
                        registeredAt = REFERENCE
                            - random.nextInt(120) * DAY;
                    }
                    default -> {
                        status = random.nextInt(100) < 90
                            ? "registered"
                            : "cancelled";
                        registeredAt = REFERENCE
                            - random.nextInt(120) * DAY;
                    }
                }

                if (status.equals("attended")) {
                    addAttended(pair);
                }

                stmt.setLong(1, userIds[user]);
                stmt.setLong(2, eventIds[event]);
                stmt.setString(3, status);
                stmt.setObject(4, toDateTime(registeredAt));

                pending = addToBatch(stmt, pending);
            }

            flush(stmt, pending);
        }

        System.out.println(
            registrationPairs.length + " inscrições inseridas ("
                + attendedCount + " com presença)!"
        );
    }

    public void generateComments() throws SQLException {

        String sql = """
            INSERT INTO comments
            (user_id, event_id, body, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt =
                connection.prepareStatement(sql)) {

            int pending = 0;

            for (int i = 0; i < config.comments; i++) {

                // comentários são feitos por quem se inscreveu no evento
                long pair = registrationPairs[
                    random.nextInt(registrationPairs.length)
                ];

                int event = eventOf(pair);

                long createdAt = eventStatus[event] == COMPLETED
                    ? eventEnds[event] + random.nextInt(7 * 86_400)
                    : REFERENCE - random.nextInt(30 * 86_400);

                OffsetDateTime created = toDateTime(createdAt);

                stmt.setLong(1, userIds[userOf(pair)]);
                stmt.setLong(2, eventIds[event]);
                stmt.setString(3, pick(COMMENT_TEXTS));
                stmt.setObject(4, created);
                stmt.setObject(5, created);

                pending = addToBatch(stmt, pending);
            }

            flush(stmt, pending);
        }

        System.out.println(config.comments + " comentários inseridos!");
    }

    public void generateRatings() throws SQLException {

        if (config.ratings > attendedCount) {
            throw new SQLException(
                "Foram pedidas " + config.ratings + " avaliações, mas só há "
                    + attendedCount + " inscrições com presença em eventos "
                    + "encerrados. Diminua --ratings ou aumente "
                    + "--registrations/--past-ratio."
            );
        }

        String sql = """
            INSERT INTO ratings
            (user_id, event_id, registration_status, score, comment,
            created_at, updated_at)
            VALUES (?, ?, 'attended', ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt =
                connection.prepareStatement(sql)) {

            int pending = 0;

            for (int i = 0; i < config.ratings; i++) {

                // Fisher-Yates parcial: sorteia inscrições sem repetir,
                // o que garante o UNIQUE (user_id, event_id)
                int j = i + random.nextInt(attendedCount - i);
                long pair = attendedPairs[j];
                attendedPairs[j] = attendedPairs[i];
                attendedPairs[i] = pair;

                int event = eventOf(pair);

                OffsetDateTime created = toDateTime(
                    eventEnds[event] + random.nextInt(14 * 86_400)
                );

                stmt.setLong(1, userIds[userOf(pair)]);
                stmt.setLong(2, eventIds[event]);
                stmt.setShort(3, (short) SCORES[random.nextInt(SCORES.length)]);
                stmt.setString(
                    4,
                    random.nextBoolean() ? pick(RATING_TEXTS) : null
                );
                stmt.setObject(5, created);
                stmt.setObject(6, created);

                pending = addToBatch(stmt, pending);
            }

            flush(stmt, pending);
        }

        System.out.println(config.ratings + " avaliações inseridas!");
    }

    /**
     * Distribui as inscrições entre os eventos com popularidade desigual
     * (peso exponencial ao quadrado), para a consulta de eventos mais
     * populares ter resultado interessante.
     */
    private int[] allocateRegistrations() {

        int events = config.events;
        int maxPerEvent = config.users;

        double[] weights = new double[events];
        double sum = 0;

        for (int i = 0; i < events; i++) {
            double w = -Math.log(1 - random.nextDouble());
            weights[i] = w * w;
            sum += weights[i];
        }

        int[] counts = new int[events];
        long assigned = 0;

        for (int i = 0; i < events; i++) {
            counts[i] = (int) Math.min(
                maxPerEvent,
                (long) (config.registrations * weights[i] / sum)
            );
            assigned += counts[i];
        }

        long remaining = config.registrations - assigned;

        while (remaining > 0) {
            int i = random.nextInt(events);
            if (counts[i] < maxPerEvent) {
                counts[i]++;
                remaining--;
            }
        }

        return counts;
    }

    /**
     * Sorteia usuários distintos para cada evento e embaralha os pares,
     * para as inscrições não ficarem gravadas agrupadas por evento
     * (o que distorceria os testes de índice).
     */
    private void buildRegistrationPairs(int[] registrationsPerEvent) {

        registrationPairs = new long[config.registrations];

        Set<Integer> chosen = new HashSet<>();
        int index = 0;

        for (int event = 0; event < registrationsPerEvent.length; event++) {

            chosen.clear();

            while (chosen.size() < registrationsPerEvent[event]) {

                int user = random.nextInt(userIds.length);

                if (chosen.add(user)) {
                    registrationPairs[index++] =
                        ((long) event << 32) | user;
                }
            }
        }

        for (int i = registrationPairs.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            long tmp = registrationPairs[i];
            registrationPairs[i] = registrationPairs[j];
            registrationPairs[j] = tmp;
        }
    }

    private void addAttended(long pair) {

        if (attendedCount == attendedPairs.length) {
            attendedPairs = Arrays.copyOf(
                attendedPairs,
                attendedPairs.length * 2
            );
        }

        attendedPairs[attendedCount++] = pair;
    }

    private int addToBatch(PreparedStatement stmt, int pending)
            throws SQLException {

        stmt.addBatch();
        pending++;

        if (pending == config.batchSize) {
            stmt.executeBatch();
            stmt.clearBatch();
            return 0;
        }

        return pending;
    }

    private void flush(PreparedStatement stmt, int pending)
            throws SQLException {

        if (pending > 0) {
            stmt.executeBatch();
            stmt.clearBatch();
        }
    }

    private long maxId(String table) throws SQLException {

        checkTable(table);

        try (Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(
                "SELECT COALESCE(MAX(id), 0) FROM " + table
            )) {

            rs.next();
            return rs.getLong(1);
        }
    }

    /**
     * Ids gerados nesta carga, na ordem de inserção: o i-ésimo id
     * corresponde à i-ésima linha inserida.
     */
    private long[] newIds(String table, long maxBefore)
            throws SQLException {

        checkTable(table);

        long[] ids = new long[16];
        int count = 0;

        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT id FROM " + table + " WHERE id > ? ORDER BY id")) {

            stmt.setLong(1, maxBefore);
            stmt.setFetchSize(100_000);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if (count == ids.length) {
                        ids = Arrays.copyOf(ids, ids.length * 2);
                    }
                    ids[count++] = rs.getLong(1);
                }
            }
        }

        return Arrays.copyOf(ids, count);
    }

    private static void checkTable(String table) {

        if (!table.equals("users")
        && !table.equals("venues")
        && !table.equals("events")) {
            throw new IllegalArgumentException(
                "Tabela não permitida: " + table
            );
        }
    }

    private Array randomTags() throws SQLException {

        int count = 1 + random.nextInt(3);
        String[] tags = new String[count];

        for (int i = 0; i < count; i++) {
            tags[i] = pick(TAGS);
        }

        return connection.createArrayOf("text", tags);
    }

    // início entre 8h e 20h, em hora cheia ou meia hora
    private long randomStart(long from, long to) {

        long days = (to - from) / DAY;
        long day = from + random.nextInt((int) days) * DAY;

        return day + 8 * 3600 + random.nextInt(25) * 1800L;
    }

    private String pick(String[] values) {
        return values[random.nextInt(values.length)];
    }

    private static int eventOf(long pair) {
        return (int) (pair >>> 32);
    }

    private static int userOf(long pair) {
        return (int) pair;
    }

    private static String statusName(byte status) {
        return switch (status) {
            case COMPLETED -> "completed";
            case CANCELLED -> "cancelled";
            default -> "scheduled";
        };
    }

    private static long epoch(int year, int month, int day) {
        return OffsetDateTime.of(year, month, day, 0, 0, 0, 0, ZONE)
            .toEpochSecond();
    }

    private static OffsetDateTime toDateTime(long epochSecond) {
        return OffsetDateTime.ofInstant(
            Instant.ofEpochSecond(epochSecond),
            ZONE
        );
    }
}
