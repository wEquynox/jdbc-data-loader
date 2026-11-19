SELECT tablename, indexname, pg_relation_size(indexname::regclass) AS bytes,
       pg_size_pretty(pg_relation_size(indexname::regclass)) AS tamanho,
       indexdef
FROM pg_indexes WHERE schemaname = current_schema()
ORDER BY tablename, indexname;
