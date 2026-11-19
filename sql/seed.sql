-- Dados de exemplo originais do Otávio; opcionais, não usar antes da carga completa.
INSERT INTO users (id, full_name, email, created_at, active) VALUES
(1, 'Ana Souza', 'ana.souza@example.com', '2026-08-01 10:00:00-03', TRUE),
(2, 'Bruno Lima', 'bruno.lima@example.com', '2026-08-03 14:30:00-03', TRUE),
(3, 'Carla Mendes', 'carla.mendes@example.com', '2026-08-05 09:15:00-03', TRUE),
(4, 'Diego Rocha', 'diego.rocha@example.com', '2026-08-10 16:45:00-03', TRUE);

INSERT INTO venues (id, name, building, room, capacity, accessible) VALUES
(1, 'Auditório Central', 'Bloco A', 'A101', 200, TRUE),
(2, 'Laboratório de Computação', 'Bloco B', 'B204', 40, TRUE),
(3, 'Sala de Projetos', 'Bloco C', 'C305', 30, FALSE);

INSERT INTO events (
    id, title, description, organizer_id, venue_id,
    starts_at, ends_at, capacity, status, tags, metadata
) VALUES
(
    1,
    'Introdução ao PostgreSQL',
    'Palestra sobre modelagem e consultas em PostgreSQL.',
    1,
    1,
    '2026-10-15 19:00:00-03',
    '2026-10-15 21:00:00-03',
    200,
    'completed',
    ARRAY['banco de dados', 'postgresql', 'palestra'],
    '{"level": "beginner", "format": "in-person"}'
),
(
    2,
    'Oficina de Otimização SQL',
    'Atividade prática sobre análise de consultas e índices.',
    2,
    2,
    '2027-03-10 19:00:00-03',
    '2027-03-10 21:30:00-03',
    40,
    'scheduled',
    ARRAY['banco de dados', 'sql', 'oficina'],
    '{"level": "intermediate", "format": "in-person"}'
),
(
    3,
    'Desenvolvimento de Aplicativos',
    'Encontro sobre desenvolvimento de aplicativos móveis.',
    3,
    3,
    '2027-03-15 18:30:00-03',
    '2027-03-15 20:30:00-03',
    30,
    'scheduled',
    ARRAY['mobile', 'programacao', 'projetos'],
    '{"level": "intermediate", "format": "in-person"}'
);

INSERT INTO registrations (id, user_id, event_id, status, registered_at) VALUES
(1, 1, 1, 'attended', '2026-10-01 10:00:00-03'),
(2, 2, 1, 'attended', '2026-10-02 11:30:00-03'),
(3, 3, 1, 'attended', '2026-10-05 15:20:00-03'),
(4, 4, 1, 'cancelled', '2026-10-06 09:00:00-03'),
(5, 1, 2, 'registered', '2026-11-01 08:30:00-03'),
(6, 3, 2, 'registered', '2026-11-02 13:10:00-03'),
(7, 2, 3, 'registered', '2026-11-03 17:45:00-03');

INSERT INTO comments (id, user_id, event_id, body, created_at, updated_at) VALUES
(1, 1, 1, 'Ótima introdução ao PostgreSQL.', '2026-10-15 21:10:00-03', '2026-10-15 21:10:00-03'),
(2, 2, 1, 'A parte de modelagem foi muito útil.', '2026-10-15 21:20:00-03', '2026-10-15 21:20:00-03'),
(3, 3, 2, 'Estou animada para participar da oficina.', '2026-11-02 12:00:00-03', '2026-11-02 12:00:00-03');

INSERT INTO ratings (
    id, user_id, event_id, registration_status, score, comment, created_at, updated_at
) VALUES
(1, 1, 1, 'attended', 5, 'Conteúdo claro e bem organizado.', '2026-10-15 21:15:00-03', '2026-10-15 21:15:00-03'),
(2, 2, 1, 'attended', 4, 'Gostei bastante dos exemplos.', '2026-10-15 21:25:00-03', '2026-10-15 21:25:00-03'),
(3, 3, 1, 'attended', 5, 'Excelente palestra.', '2026-10-15 21:30:00-03', '2026-10-15 21:30:00-03');

INSERT INTO audit_log (
    table_name, entity_id, action, old_data, new_data, changed_at
) VALUES (
    'seed',
    '1',
    'INSERT',
    NULL,
    '{"description": "Registro inicial de auditoria para demonstrar a tabela."}',
    NOW()
);

SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users));
SELECT setval(pg_get_serial_sequence('venues', 'id'), (SELECT MAX(id) FROM venues));
SELECT setval(pg_get_serial_sequence('events', 'id'), (SELECT MAX(id) FROM events));
SELECT setval(pg_get_serial_sequence('registrations', 'id'), (SELECT MAX(id) FROM registrations));
SELECT setval(pg_get_serial_sequence('comments', 'id'), (SELECT MAX(id) FROM comments));
SELECT setval(pg_get_serial_sequence('ratings', 'id'), (SELECT MAX(id) FROM ratings));
