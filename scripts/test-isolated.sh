#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Cluster novo e privado; não usa o serviço PostgreSQL nem o banco pessoal.
cluster=$(mktemp -d /tmp/jdbc-loader-test.XXXXXX)
port=${TEST_PGPORT:-55439}
cleanup() {
    if [[ -f "$cluster/data/postmaster.pid" ]]; then
        pg_ctl -D "$cluster/data" -m fast -w stop > "$cluster/stop.log" 2>&1
    fi
    echo "Cluster descartável parado. Arquivos para inspeção: $cluster"
}
trap cleanup EXIT
initdb -D "$cluster/data" -U loader_test --auth=trust --encoding=UTF8 --no-locale > "$cluster/initdb.log"
pg_ctl -D "$cluster/data" -l "$cluster/server.log" \
    -o "-h 127.0.0.1 -p $port -k $cluster" -w start
export PGHOST=127.0.0.1 PGPORT="$port" PGUSER=loader_test PGDATABASE=jdbc_loader_test
export PGPASSWORD=''
createdb jdbc_loader_test
export DB_URL="jdbc:postgresql://127.0.0.1:$port/jdbc_loader_test"
export DB_USER=loader_test DB_PASSWORD=''
export TEST_DB_URL="$DB_URL"
mvn clean test
mkdir -p target/isolated-results
psql -X -v ON_ERROR_STOP=1 -f sql/schema.sql > target/isolated-results/schema.txt
# Carga pequena: jamais executar os padrões de milhões neste teste.
mvn -q exec:java -Dexec.args="--users=2000 --venues=50 --events=1000 --registrations=10000 --comments=1000 --ratings=500 --batch=137 --seed=42" \
    | tee target/isolated-results/load.txt
psql -X -v ON_ERROR_STOP=1 -v expected_users=2000 -v expected_venues=50 \
    -v expected_events=1000 -v expected_registrations=10000 \
    -v expected_comments=1000 -v expected_ratings=500 \
    -f sql/validate.sql -f sql/validate_view.sql | tee target/isolated-results/validation.txt
for repetition in 1 2 3; do
    psql -X -v ON_ERROR_STOP=1 -v write_rows=100 -f sql/benchmark.sql \
        > "target/isolated-results/benchmark-$repetition.txt" 2>&1
done
# As medições devem preservar dados, índices iniciais e triggers ativos.
psql -X -v ON_ERROR_STOP=1 -v expected_users=2000 -v expected_venues=50 \
    -v expected_events=1000 -v expected_registrations=10000 \
    -v expected_comments=1000 -v expected_ratings=500 -f sql/validate.sql \
    > target/isolated-results/after-benchmark.txt
psql -X -v ON_ERROR_STOP=1 <<'SQL' >> target/isolated-results/after-benchmark.txt
DO $$ BEGIN
    IF (SELECT count(*) FROM audit_log) <> 11000 THEN
        RAISE EXCEPTION 'Auditoria alterada pelo benchmark';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname LIKE 'idx_%') THEN
        RAISE EXCEPTION 'Índices experimentais persistiram';
    END IF;
    IF (SELECT count(*) FROM pg_trigger WHERE tgname IN
        ('events_audit_trigger','registrations_audit_trigger') AND tgenabled='O') <> 2 THEN
        RAISE EXCEPTION 'Auditoria desativada';
    END IF;
END $$;
SQL
printf 'Testes e 3 medições pequenas concluídos. Evidências: target/isolated-results/\n'
