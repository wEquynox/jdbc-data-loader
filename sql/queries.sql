-- Executar via benchmark.sql, que define os parâmetros e coleta os planos.
\echo Q1: próximos eventos de uma unidade/local
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, title, starts_at FROM events
WHERE venue_id = :venue_id AND status = 'scheduled'
  AND starts_at >= :'reference'::timestamptz
ORDER BY starts_at LIMIT 20;

\echo Q2: inscrições de um usuário
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.event_id, e.title, r.status FROM registrations r
JOIN events e ON e.id = r.event_id
WHERE r.user_id = :user_id ORDER BY r.event_id;

\echo Q3: eventos mais populares
EXPLAIN (ANALYZE, BUFFERS)
SELECT e.id, e.title, count(*) AS inscritos
FROM registrations r JOIN events e ON e.id = r.event_id
WHERE r.status IN ('registered', 'attended')
GROUP BY e.id, e.title ORDER BY inscritos DESC, e.id LIMIT 20;

\echo Q4: eventos iniciados em intervalo (B-tree simples)
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, starts_at FROM events
WHERE starts_at >= :'range_from'::timestamptz
  AND starts_at < :'range_to'::timestamptz ORDER BY starts_at;

\echo Q5: eventos contidos no intervalo (GiST)
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, title FROM events
WHERE period <@ tstzrange(:'range_from', :'range_to', '[)');

\echo Q6: sobreposição de horários (GiST)
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, title FROM events
WHERE period && tstzrange(:'range_from', :'range_to', '[)');

\echo Q7: inscrições de um evento por igualdade (Hash versus B-tree)
\ir query_equality.sql
