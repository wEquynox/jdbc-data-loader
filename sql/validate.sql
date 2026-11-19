\set ON_ERROR_STOP on
-- Somente leitura. Valores esperados opcionais: informar os seis parâmetros juntos.
SELECT 'users' AS tabela, count(*) AS quantidade FROM users
UNION ALL SELECT 'venues', count(*) FROM venues
UNION ALL SELECT 'events', count(*) FROM events
UNION ALL SELECT 'registrations', count(*) FROM registrations
UNION ALL SELECT 'comments', count(*) FROM comments
UNION ALL SELECT 'ratings', count(*) FROM ratings
UNION ALL SELECT 'audit_log', count(*) FROM audit_log;

SELECT user_id, event_id, count(*) AS quantidade
FROM registrations GROUP BY user_id, event_id HAVING count(*) > 1;

SELECT rt.id AS avaliacao_invalida
FROM ratings rt
LEFT JOIN registrations r ON (r.user_id, r.event_id) = (rt.user_id, rt.event_id)
LEFT JOIN events e ON e.id = rt.event_id
WHERE r.id IS NULL OR r.status <> 'attended'
   OR e.id IS NULL OR e.ends_at > now() OR e.status <> 'completed';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM registrations GROUP BY user_id, event_id HAVING count(*) > 1)
       OR EXISTS (
           SELECT 1 FROM ratings rt
           LEFT JOIN registrations r ON (r.user_id, r.event_id) = (rt.user_id, rt.event_id)
           LEFT JOIN events e ON e.id = rt.event_id
           WHERE r.id IS NULL OR r.status <> 'attended'
              OR e.id IS NULL OR e.ends_at > now() OR e.status <> 'completed'
       ) THEN
        RAISE EXCEPTION 'Carga com inscrições duplicadas ou avaliações inválidas';
    END IF;
END $$;

\if :{?expected_users}
SELECT (SELECT count(*) FROM users) = :expected_users
   AND (SELECT count(*) FROM venues) = :expected_venues
   AND (SELECT count(*) FROM events) = :expected_events
   AND (SELECT count(*) FROM registrations) = :expected_registrations
   AND (SELECT count(*) FROM comments) = :expected_comments
   AND (SELECT count(*) FROM ratings) = :expected_ratings AS counts_ok \gset
\if :counts_ok
\echo Contagens conferem com os valores esperados.
\else
\echo ERRO: contagens diferentes das esperadas.
\quit 1
\endif
\endif
