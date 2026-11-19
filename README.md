# JDBC data loader — trabalho de Banco de Dados

Java 17 ou superior, Maven e PostgreSQL (testado com PostgreSQL 18). A implementação
mantém as classes de geração e a configuração integradas pelo Otávio na `main`.
O relatório das 12 etapas está em [docs/RELATORIO.md](docs/RELATORIO.md).

## Testar sem usar o banco pessoal

```bash
bash scripts/test-isolated.sh
```

O script cria um **novo cluster** em `/tmp/jdbc-loader-test.*`, escuta somente em
`127.0.0.1:55439`, usa o banco `jdbc_loader_test` e encerra o servidor ao sair,
inclusive se um teste falhar. Pode-se escolher outra porta com `TEST_PGPORT=55441`.
Não conecta ao serviço PostgreSQL existente nem ao banco `GustavoAtv`.
Requer `initdb`, `pg_ctl`, `createdb` e `psql` no PATH e execução por usuário não root.
A autenticação trust existe apenas nesse cluster temporário local.

Executa testes JUnit, uma carga de 2.000 usuários, 50 locais, 1.000 eventos,
10.000 inscrições, 1.000 comentários e 500 avaliações, validações SQL e três
medições pequenas. Não usa `--reset` nem `--no-audit`.
As evidências novas ficam em `target/isolated-results/` e
`target/surefire-reports/`; o tempo da carga fica em `load-times.csv`.
Os dados temporários permanecem no diretório informado para inspeção, com o
servidor parado. `mvn clean` remove os resultados em `target/`.

```bash
mvn test
```

Sem `TEST_DB_URL`, esse comando executa os testes de parâmetros e **pula** os
testes PostgreSQL. O script acima fornece a conexão e executa todos os testes.

## Preparar um laboratório separado para a apresentação

Execute a partir da raiz do repositório. Estes comandos criam outro cluster,
independente do usado no teste automático e do PostgreSQL pessoal:

```bash
lab_dir=$(mktemp -d /tmp/jdbc-loader-lab.XXXXXX)
export PGHOST=127.0.0.1 PGPORT=55440 PGUSER="$USER" PGDATABASE=jdbc_loader_lab
export PGPASSWORD=''
initdb -D "$lab_dir/data" -U "$PGUSER" --auth=trust --encoding=UTF8 --no-locale
pg_ctl -D "$lab_dir/data" -l "$lab_dir/server.log" \
  -o "-h 127.0.0.1 -p $PGPORT -k $lab_dir" -w start
createdb jdbc_loader_lab
psql -X -v ON_ERROR_STOP=1 -f sql/schema.sql
export DB_URL="jdbc:postgresql://127.0.0.1:$PGPORT/$PGDATABASE"
export DB_USER="$PGUSER" DB_PASSWORD=''
```

`DB_URL` é obrigatória: foi removida a conexão implícita ao banco pessoal.
Em um servidor com senha, informe `DB_USER`, `DB_PASSWORD` e `PGPASSWORD` reais.

O `schema.sql` agora cria somente a estrutura. Os exemplos originais foram
preservados em `sql/seed.sql`. Use-os **em outro banco vazio** para demonstração:

```bash
psql -X -v ON_ERROR_STOP=1 -f sql/seed.sql
```

Não aplique o seed antes da carga de entrega se quiser totais exatos. Ele adiciona
4 usuários, 3 locais, 3 eventos, 7 inscrições, 3 comentários e 3 avaliações.
Não reaplique `schema.sql` sobre tabelas existentes. Para usar as correções,
prepare um banco novo; não foi feita migração no banco pessoal.

## Carga pequena ou completa

Para ensaiar no laboratório vazio:

```bash
mvn compile exec:java -Dexec.args="--users=2000 --venues=50 --events=1000 --registrations=10000 --comments=1000 --ratings=500 --batch=137 --seed=42"
psql -X -v ON_ERROR_STOP=1 \
  -v expected_users=2000 -v expected_venues=50 -v expected_events=1000 \
  -v expected_registrations=10000 -v expected_comments=1000 -v expected_ratings=500 \
  -f sql/validate.sql -f sql/validate_view.sql
```

**Carga completa preparada, mas não executada neste trabalho de implementação.**
Em um laboratório vazio, sem seed e sem a carga pequena anterior:

```bash
MAVEN_OPTS="-Xmx2g" mvn compile exec:java \
  -Dexec.args="--users=5000000 --venues=500 --events=100000 --registrations=10000000 --comments=1000000 --ratings=1000000 --batch=5000 --seed=42 --past-ratio=0.3"
psql -X -v ON_ERROR_STOP=1 \
  -v expected_users=5000000 -v expected_venues=500 -v expected_events=100000 \
  -v expected_registrations=10000000 -v expected_comments=1000000 -v expected_ratings=1000000 \
  -f sql/validate.sql -f sql/validate_view.sql
```

Foi definida a quantidade de **1 milhão de comentários e 1 milhão de avaliações**.
Os parâmetros são quantidades adicionais. Repetir uma carga acrescenta dados;
usuários/locais recebem identificadores diferentes para evitar colisões.
A aplicação compara deltas antes de confirmar a transação e faz rollback se
houver erro ou divergência de contagem. Não executar cargas simultâneas: a
recuperação dos IDs mantém o método original baseado no maior ID anterior.

A geração usa arrays de usuários, eventos e pares de inscrição e uma transação
para toda a carga. `-Xmx2g` é um ponto de partida, **não uma capacidade validada**
para os milhões de registros. Será preciso observar memória, espaço, WAL e tempo
na máquina da apresentação. Com auditoria, a carga completa acrescenta **10.100.000
linhas em audit_log**, além das seis tabelas de dados.

A mesma semente, parâmetros, schema vazio e sequências iniciais produzem os mesmos
dados de negócio (testado). IDs mudam ao acrescentar cargas a um banco já usado;
os timestamps da auditoria e os tempos medidos refletem a execução real.
As datas fixas originais foram mantidas: passados entre 2025 e setembro de 2026,
futuros em 2027, referência de consultas em novembro de 2026. Essa referência
fixa permite repetir o experimento; para consultar os próximos eventos na data
real, forneça outra referência ao benchmark. Não representa uma agenda sempre atual.

A quantidade de presentes em eventos passados depende do sorteio. Se não houver
pares suficientes para `--ratings`, a carga informa o motivo e é revertida;
aumente `--past-ratio`/`--registrations` ou reduza `--ratings`.

Os argumentos antigos `--reset` e `--no-audit` foram preservados, mas só são
aceitos em bancos descartáveis cujo nome começa com `jdbc_loader_test`. Não são
necessários para os procedimentos acima e não foram executados. Reset agora é
transacional; a auditoria permanece ligada em todos os testes e experimentos.

## Medir índices e consultar os objetos SQL

As consultas exatas adicionais do professor não foram anexadas. `sql/queries.sql`
implementa os exemplos do enunciado. Como o schema não contém tabela `units`,
“unidade” foi interpretada como o local (`venue_id`). Ajuste essa interpretação
se o professor fornecer outra definição.

```bash
mkdir -p resultados
for repeticao in 1 2 3; do
  psql -X -v ON_ERROR_STOP=1 -v write_rows=1000 \
    -f sql/benchmark.sql > "resultados/benchmark-$repeticao.txt" 2>&1
done
```

O benchmark registra versão, parâmetros, contagens, configurações, planos
`EXPLAIN (ANALYZE, BUFFERS)`, tempos, espaço de **todos** os índices e escrita
com WAL e auditoria. Compara:

1. PK/UNIQUE originais, sem índices adicionais;
2. B-tree simples em `starts_at`/`event_id` e composto `(venue_id, starts_at)`;
3. GiST em `period`, com contenção `<@` e sobreposição `&&`;
4. Hash em `registrations(event_id)` no lugar do B-tree equivalente;
5. tentativa de B-tree extra em `user_id`, já coberto pelo prefixo dos UNIQUE.

O lote de escrita tem eventos e inscrições e é revertido em cada cenário. Os
índices experimentais também são revertidos ao final de cada cenário. Se houver erro, o fechamento
da conexão desfaz a transação. Há VACUUM ANALYZE entre os cenários para limpar
as versões mortas e atualizar as estatísticas. Sequências avançam apesar de rollback; por isso o
script exige banco descartável com prefixo `jdbc_loader_`. Não desativa triggers
nem remove índices de constraints. Não use essas medições em um banco concorrente.

Pode-se fixar `-v venue_id=1 -v user_id=1 -v event_id=1`,
`-v reference='2026-11-01 00:00:00-03'`, `-v range_from='2027-06-01 00:00:00-03'`
e `-v range_to='2027-06-08 00:00:00-03'`. Sem IDs explícitos, usa os menores IDs
de usuário/local e o evento com mais inscrições. O banco precisa estar carregado.

Verifique o nome do índice nos nós `Index Scan`, `Index Only Scan` ou
`Bitmap Index Scan`; a criação de um índice **não garante** que o otimizador o use.
Não foi usado `enable_seqscan=off`. PK e UNIQUE já são índices B-tree e continuam
presentes no cenário inicial.

Depois das medições completas, escolha os candidatos úteis. Para criá-los fora
do experimento, no laboratório:

```bash
psql -X -v ON_ERROR_STOP=1 -f sql/indexes_btree.sql -f sql/indexes_gist.sql
psql -X -v ON_ERROR_STOP=1 -f sql/index_sizes.sql
```

Esses arquivos criam candidatos, não afirmam que todos devem ser mantidos.
Aplique apenas as instruções dos candidatos justificados no relatório final.
Para remover um candidato sem benefício comprovado, use `DROP INDEX` com o nome
exato, por exemplo `DROP INDEX IF EXISTS idx_registrations_user_id;`. Nunca remova
os índices de PK/UNIQUE do modelo. O benchmark faz esse teste sem persistir alterações.

A view e a procedure podem ser demonstradas com os dados de exemplo:

```sql
SELECT * FROM event_summary ORDER BY event_id;
CALL cancel_registration(1, 2);
SELECT * FROM registrations WHERE user_id = 1 AND event_id = 2;
SELECT table_name, entity_id, action, old_data, new_data
FROM audit_log ORDER BY id DESC LIMIT 10;
```

Ao terminar o laboratório criado neste README:

```bash
pg_ctl -D "$lab_dir/data" -m fast -w stop
```
