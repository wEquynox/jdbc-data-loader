\set ON_ERROR_STOP on
\pset pager off
\timing on
-- O script não se conecta a nenhum banco automaticamente.
SELECT current_database() LIKE 'jdbc\_loader\_%' ESCAPE '\' AS lab_database \gset
\if :lab_database
\else
\echo Use exclusivamente um banco descartável com prefixo jdbc_loader_.
\quit 1
\endif
\if :{?reference}
\else
\set reference '2026-11-01 00:00:00-03'
\endif
\if :{?range_from}
\else
\set range_from '2027-06-01 00:00:00-03'
\endif
\if :{?range_to}
\else
\set range_to '2027-06-08 00:00:00-03'
\endif
\if :{?write_rows}
\else
\set write_rows 1000
\endif
-- Parâmetros podem ser fixados com -v para comparar exatamente a mesma consulta.
\if :{?venue_id}
\else
SELECT min(id) AS venue_id FROM venues \gset
\endif
\if :{?user_id}
\else
SELECT min(id) AS user_id FROM users \gset
\endif
\if :{?event_id}
\else
SELECT event_id FROM registrations GROUP BY event_id
ORDER BY count(*) DESC, event_id LIMIT 1 \gset
\endif
SELECT version(), current_database(), clock_timestamp();
SHOW shared_buffers;
SHOW work_mem;
SHOW random_page_cost;
SHOW effective_cache_size;
SHOW max_parallel_workers_per_gather;
\echo Parâmetros: local=:venue_id usuário=:user_id evento=:event_id referência=:reference intervalo=:range_from/:range_to lote=:write_rows
SELECT 'users' AS tabela, count(*) FROM users
UNION ALL SELECT 'events', count(*) FROM events
UNION ALL SELECT 'registrations', count(*) FROM registrations;

-- VACUUM entre transações remove versões mortas dos lotes revertidos.
-- Cada cenário restaura os índices originais antes de iniciar o seguinte.
\echo CENÁRIO A: antes dos índices adicionais (PK e UNIQUE mantidos)
\ir benchmark_begin.sql
\ir queries.sql
\ir index_sizes.sql
\ir benchmark_write.sql
ROLLBACK;

\echo CENÁRIO B: B-tree simples e composto
\ir benchmark_begin.sql
\ir indexes_btree.sql
\ir queries.sql
\ir index_sizes.sql
\ir benchmark_write.sql
ROLLBACK;

\echo CENÁRIO C: B-tree e GiST
\ir benchmark_begin.sql
\ir indexes_btree.sql
\ir indexes_gist.sql
\ir queries.sql
\ir index_sizes.sql
\ir benchmark_write.sql
ROLLBACK;

\echo CENÁRIO D: igualdade com Hash em vez de B-tree(event_id)
\ir benchmark_begin.sql
\ir indexes_btree.sql
\ir indexes_gist.sql
DROP INDEX idx_registrations_event_id;
CREATE INDEX idx_registrations_event_hash ON registrations USING hash (event_id);
\echo Q7: igualdade com Hash
\ir query_equality.sql
\ir index_sizes.sql
\ir benchmark_write.sql
ROLLBACK;

\echo CENÁRIO E: tentativa de B-tree(user_id) adicional aos UNIQUE existentes
\ir benchmark_begin.sql
CREATE INDEX idx_registrations_user_id ON registrations (user_id);
\echo Q8: com B-tree adicional em user_id
EXPLAIN (ANALYZE, BUFFERS)
SELECT event_id, status FROM registrations WHERE user_id = :user_id ORDER BY event_id;
\ir index_sizes.sql
DROP INDEX idx_registrations_user_id;
\echo Q8: sem B-tree adicional em user_id
EXPLAIN (ANALYZE, BUFFERS)
SELECT event_id, status FROM registrations WHERE user_id = :user_id ORDER BY event_id;
-- Não há decisão permanente de remoção aqui: analisar as repetições primeiro.
ROLLBACK;
-- Índices originais e linhas preservados; sequências avançam e VACUUM atualiza estatísticas.
