-- Lote idêntico em cada cenário; ROLLBACK remove linhas e auditoria do lote.
-- Sequências avançam mesmo com rollback. Execute só no laboratório descartável.
SAVEPOINT write_batch;
SELECT coalesce(max(id), 0) AS event_before FROM events \gset
\echo Escrita: eventos, incluindo auditoria
EXPLAIN (ANALYZE, BUFFERS, WAL)
INSERT INTO events (title, description, organizer_id, venue_id,
                    starts_at, ends_at, capacity, status, created_at)
SELECT 'Lote de medição ' || n, 'Etapa 11', :user_id, :venue_id,
       '2027-06-01 10:00-03'::timestamptz + n * interval '1 minute',
       '2027-06-01 11:00-03'::timestamptz + n * interval '1 minute',
       100, 'scheduled', '2026-11-01 00:00-03'::timestamptz
FROM generate_series(1, :write_rows) n;
\echo Escrita: inscrições, incluindo auditoria
EXPLAIN (ANALYZE, BUFFERS, WAL)
INSERT INTO registrations (user_id, event_id, status, registered_at)
SELECT :user_id, id, 'registered', '2026-11-01 00:00-03'::timestamptz
FROM events WHERE id > :event_before;
ROLLBACK TO SAVEPOINT write_batch;
RELEASE SAVEPOINT write_batch;
