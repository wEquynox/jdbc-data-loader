EXPLAIN (ANALYZE, BUFFERS)
SELECT id, user_id, status FROM registrations WHERE event_id = :event_id;
