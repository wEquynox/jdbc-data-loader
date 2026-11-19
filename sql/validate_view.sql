\set ON_ERROR_STOP on
-- Consultas diretas separadas evitam multiplicar inscrições por avaliações.
WITH direct_registrations AS (
    SELECT event_id, count(*) AS n FROM registrations
    WHERE status IN ('registered', 'attended') GROUP BY event_id
), direct_ratings AS (
    SELECT event_id, round(avg(score)::numeric, 2) AS avg_score
    FROM ratings GROUP BY event_id
)
SELECT count(*) AS divergencias
FROM event_summary s
JOIN events e ON e.id = s.event_id
JOIN venues v ON v.id = e.venue_id
LEFT JOIN direct_registrations r ON r.event_id = e.id
LEFT JOIN direct_ratings rt ON rt.event_id = e.id
WHERE s.registered_count <> coalesce(r.n, 0)
   OR s.average_rating IS DISTINCT FROM rt.avg_score
   OR s.venue_name IS DISTINCT FROM v.name
\gset
\echo Divergências na view: :divergencias
SELECT :divergencias = 0 AND (SELECT count(*) FROM event_summary) = (SELECT count(*) FROM events) AS view_ok \gset
\if :view_ok
\else
\echo ERRO: resumo divergente das tabelas.
\quit 1
\endif

-- Exemplo visível: inclusive eventos sem inscrições e sem avaliações.
SELECT * FROM event_summary ORDER BY event_id LIMIT 10;
