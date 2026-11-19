# Análise do trabalho de Banco de Dados

## Escopo e ponto de partida

Revisão da `main` no commit `94663d7`, merge da branch `otavio` (`657ee54`).
Foram preservados o JDBC em lotes, os parâmetros, as distribuições aleatórias,
a geração de pares únicos, o uso de variáveis de ambiente e a medição por etapa.
Também foram preservados os dados de exemplo, a procedure, a função
`event_occupancy` e os triggers de auditoria.

O enunciado recebido termina em “uma tentativa que não”. Foi interpretado como
uma tentativa de otimização que não trouxe benefício; confirmar o restante do
texto antes da entrega. As consultas completas do professor não foram anexadas,
portanto foram implementados os exemplos descritos. “Unidade” foi interpretada
como `venues.id`, pois não há entidade de unidade no schema.

## Situação das 12 etapas

| Etapa | Situação inicial | Implementação e evidência atual | Pendência de entrega |
|---|---|---|---|
| 1. Gerar e carregar | Parcial; gerador do Otávio já existia | Preservados JDBC, lotes, parâmetros e semente; corrigido comentário XML que impedia o Maven de ler o POM; carga adicional sem colisão nos nomes únicos; timestamps de eventos fixos; seed separado | Executar 5 milhões de usuários, 100 mil eventos e 10 milhões de inscrições; definidos 1 milhão de comentários e 1 milhão de avaliações |
| 2. Regras e tempo | Parcial | Mantidas FKs, CHECKs, pares únicos e avaliação por participante presente; validação exige evento completed e terminado, inclusive após atualização; falhas revertem a carga; tempos no console/CSV | Confirmar recursos e tempo da carga completa |
| 3. Validação | Parcial; comparação de deltas já existia | Contagens agora conferidas antes do commit e divergência aborta; `validate.sql` conta tabelas, lista duplicatas e avaliações inválidas e aceita totais esperados | Rodar com os totais completos |
| 4. Desempenho inicial | Ausente | Q1–Q7, parâmetros fixos, versão/configuração, `EXPLAIN (ANALYZE, BUFFERS)` antes dos índices adicionais | Medir no volume do enunciado; PK/UNIQUE permanecem no baseline |
| 5. B-tree | Ausente | Simples em starts_at/event_id e composto em (venue_id, starts_at), com planos e tamanhos | Decisão final de manutenção após carga completa |
| 6. GiST | Ausente | GiST(period), contenção e sobreposição, planos reais | Repetir no volume completo |
| 7. Hash × B-tree | Ausente | Mesma Q7 por event_id, alternando os métodos sem coexistência dos dois candidatos | Repetir igualdade com diferentes seletividades no volume completo |
| 8. View | Incorreta: junção multiplicava inscrições por avaliações | Agregação separada; seed mostra 3 inscritos e média 4,67, em vez de 9 inscritos; teste cobre evento vazio e média nula | Nenhuma funcional identificada |
| 9. Procedure | Implementada | Preservada; testes de cancelamento, inexistente, repetido e inscrição attended; consulta posterior do status | Nenhuma funcional identificada |
| 10. Triggers | Implementados, sem testes | Preservada auditoria; testes conferem o JSON completo antigo/novo em INSERT/UPDATE/DELETE de eventos e inscrições | Nenhuma funcional identificada |
| 11. Custo dos índices | Ausente | Lote adicional de eventos/inscrições, planos de escrita com WAL, auditoria ligada, leitura e tamanho; experimentos revertidos e VACUUM entre cenários | Lotes maiores e decisão definitiva de remoção com justificativa medida |
| 12. Análise | Ausente | Este relatório, planos brutos, tempos reais, limites e comandos no README | Completar relatório de entrega com medições grandes e confirmar trecho truncado do enunciado |

## Consultas e hipóteses

- **Q1:** local por igualdade, data mínima e ordenação. `(venue_id, starts_at)`
  permite localizar o local e então a faixa de datas; `status` continua um filtro.
  Inverter para `(starts_at, venue_id)` altera a organização e o intervalo percorrido;
  a igualdade na primeira coluna seguida da faixa na segunda é adequada a Q1.
- **Q2:** inscrições por usuário, com junção ao evento. Os UNIQUE
  `(user_id, event_id)` e `(user_id, event_id, status)` já fornecem índices com
  prefixo `user_id`; um terceiro índice só deve ser mantido se houver benefício medido.
- **Q3:** popularidade global com contagem e ordenação. Precisa processar grande
  parte das inscrições, de modo que um índice usado em consulta seletiva pode
  não ajudar nesta agregação.
- **Q4:** início de evento dentro de faixa, com B-tree simples em `starts_at`.
- **Q5/Q6:** `period <@ tstzrange(...)` e `period && tstzrange(...)`, usando GiST.
  Os limites `[)` excluem o instante final: horários adjacentes não se sobrepõem.
- **Q7:** igualdade `registrations.event_id = parâmetro`. Compara B-tree e Hash
  sobre a mesma coluna e projeção. Hash atende igualdade; B-tree também atende
  intervalos e pode fornecer ordenação. Não se pressupõe que Hash seja mais rápido.
- **Q8:** teste de um B-tree adicional em `user_id`, removido no próprio experimento
  para comparar com os índices de constraints que já existem.

Essas propriedades se apoiam na documentação do PostgreSQL:
[tipos de índice](https://www.postgresql.org/docs/18/indexes-types.html),
[ordem das colunas](https://www.postgresql.org/docs/18/indexes-multicolumn.html) e
[interpretação de EXPLAIN](https://www.postgresql.org/docs/18/using-explain.html).
São hipóteses para seleção dos candidatos; os resultados abaixo vêm do servidor real.

## Método de medição

O script `sql/benchmark.sql` exige banco descartável com prefixo `jdbc_loader_`.
Cada cenário executa uma transação própria, removendo somente candidatos conhecidos,
criando os índices em avaliação e revertendo tudo ao terminar. PK/UNIQUE, FKs e
triggers permanecem ativos. Não foram usados `--reset`, `--no-audit` ou
`enable_seqscan=off`. O lote de escrita contém o mesmo número de eventos e inscrições
em cada cenário e é revertido. Os planos incluem o tempo dos triggers.

Há `VACUUM (ANALYZE)` antes de cada cenário para limpar as versões mortas dos lotes
revertidos e atualizar estatísticas. As sequências continuam avançando. O cache não
é esvaziado e a ordem A–E é fixa; portanto as repetições não equivalem a ensaios
independentes com cache frio. O tempo de `EXPLAIN ANALYZE` tem custo de instrumentação.
Os tempos de escrita medem INSERTs com constraints, índices e auditoria, mas não
medem o commit de uma transação persistida. `\timing` inclui o tempo observado pelo
cliente; as comparações usam o `Execution Time` do servidor.

Uma primeira versão do experimento deixou versões mortas entre cenários na mesma
transação. Foi corrigida antes de registrar os resultados abaixo; aqueles planos
preliminares não são usados nas conclusões. Para uma análise final rigorosa, repetir
em cópias equivalentes da carga completa, alternar a ordem dos cenários, usar mais
repetições e registrar condições de cache e atividade concorrente.

## Resultados realmente medidos em 19/11/2026

Ambiente: PostgreSQL **18.6**, Linux x86_64, OpenJDK **27.0.1.1**,
compilação com `maven.compiler.release=17`. CPU Intel Core i5-5257U a 2,70 GHz,
4 CPUs lógicas visíveis. Banco `jdbc_loader_test`, cluster
novo e isolado, sem acesso ao banco pessoal. As configurações do servidor e os
horários constam no início de cada log de benchmark.

Carga: **2.000 usuários, 50 locais, 1.000 eventos, 10.000 inscrições,
1.000 comentários e 500 avaliações**, lote 137, semente 42, past-ratio 0,3.
Tempo total observado: **1,738 s**, incluindo conexão, geração/inserção,
contagens e commit; não inclui inicialização do Maven. Foram registradas
**11.000 linhas de auditoria**, **zero duplicatas**, **zero avaliações inválidas**
e **zero divergências na view**.

São medições pequenas para conferir o experimento, **não resultados da carga
completa nem estimativas do tempo para milhões de linhas**.

### Leitura: mediana de três execuções (ms)

Parâmetros: local 1, usuário 1, evento 932 (150 inscrições), referência
`2026-11-01 00:00:00-03`, intervalo `[2027-06-01, 2027-06-08)` no fuso -03.

| Consulta | A: sem adicionais | B: B-tree | C: B-tree + GiST | D: Hash no lugar de B-tree(event_id) |
|---|---:|---:|---:|---:|
| Q1 | 0.164 | 0.081 | 0.073 | não executada |
| Q2 | 0.053 | 0.033 | 0.037 | não executada |
| Q3 | 8.175 | 6.205 | 5.603 | não executada |
| Q4 | 0.151 | 0.117 | 0.086 | não executada |
| Q5 | 0.186 | 0.340 | 0.067 | não executada |
| Q6 | 0.203 | 0.351 | 0.051 | não executada |
| Q7 | 0.562 | 0.267 | 0.125 | 0.152 |

**Otimização que funcionou nesta amostra:** Q1 passou de `Seq Scan` para
`Bitmap Index Scan on idx_events_venue_starts`, com mediana de 0,164 para 0,081 ms.
Q5 e Q6 usaram efetivamente `idx_events_period_gist` no cenário C; a sobreposição
Q6 passou de 0,203 ms no baseline para 0,051 ms com o GiST. Isso sustenta manter
esses candidatos no próximo experimento; não prova o mesmo ganho na carga completa.

**Tentativa que não trouxe benefício nesta amostra:** o índice adicional
`idx_registrations_user_id` não foi usado em Q8. Com e sem ele, o plano manteve
`Index Only Scan using registrations_user_id_event_id_status_key`. As três
execuções com o candidato levaram 0,036/0,026/0,029 ms (mediana **0,029**);
sem ele, 0,032/0,024/0,025 ms (mediana **0,025**). O candidato ocupou **136 KiB**
na primeira repetição, sem vantagem nessa consulta. Foi removido no cenário E
e não foi incluído em `indexes_btree.sql`. Nenhum índice obrigatório foi removido.

**Hash × B-tree:** comparar C e D mantém os demais candidatos iguais. Q7 usou
ambos os índices em suas respectivas fases, com mediana **0,125 ms (B-tree)**
e **0,152 ms (Hash)**. Nesta amostra, substituir o B-tree pelo Hash não melhorou
a leitura; o Hash também ocupou mais espaço. Isso não permite concluir que Hash
seja sempre pior. Ficam pendentes outras seletividades e o volume completo.

Q2 e Q3 também variaram entre fases mesmo sem uma nova otimização específica
no plano. Cache, ordem das execuções e ruído são explicações possíveis;
essas diferenças não foram atribuídas automaticamente aos novos índices.

### Escrita: mediana de três execuções (ms)

Cada lote insere 100 eventos e 100 inscrições, com auditoria e constraints.
São tempos separados de duas instruções INSERT, não o tempo de uma carga JDBC.

| Cenário | INSERT eventos | INSERT inscrições |
|---|---:|---:|
| A | 10.935 | 10.735 |
| B | 6.866 | 9.627 |
| C | 8.548 | 7.461 |
| D | 10.663 | 7.029 |

A variação foi grande: na primeira repetição, o cenário A levou 35,237 ms para
os eventos e 31,356 ms para as inscrições. Não se pode concluir dessas amostras
que adicionar índices acelerou INSERTs, nem quantificar com confiança o custo
marginal de cada índice. Os valores completos foram preservados; é necessário
repetir com lotes maiores e condições equivalentes para a conclusão da etapa 11.

### Espaço dos candidatos

Valores medidos antes do lote de escrita na primeira repetição (KiB = 1024 bytes).
Todos os índices obrigatórios também estão listados nos logs brutos.

| Índice | Bytes | KiB |
|---|---:|---:|
| idx_events_starts_at | 40960 | 40 |
| idx_events_venue_starts | 49152 | 48 |
| idx_registrations_event_id | 106496 | 104 |
| idx_events_period_gist | 57344 | 56 |
| idx_registrations_event_hash | 614400 | 600 |
| idx_registrations_user_id | 139264 | 136 |

### Evidências e testes

- [Carga e tempos por etapa](results/load.txt).
- [Contagens, integridade e conferência da view](results/validation.txt).
- Planos completos, buffers, tempos, WAL e tamanhos:
  [repetição 1](results/benchmark-1.txt),
  [repetição 2](results/benchmark-2.txt),
  [repetição 3](results/benchmark-3.txt).
- [17 testes de configuração](results/br.com.database.AppTest.txt) e
  [7 testes PostgreSQL](results/br.com.database.PostgresTest.txt):
  **24 executados, zero falhas, zero erros, zero ignorados**, após `mvn clean test`.
- [Validação após os benchmarks](results/after-benchmark.txt): dados e 11.000
  auditorias preservados, nenhum candidato persistido, ambos os triggers de auditoria ligados.
- `bash -n scripts/test-isolated.sh` e a verificação de whitespace do código e
  dos scripts: aprovados. Os logs do psql preservam os espaços finais da saída original.

Os testes PostgreSQL cobrem carga em lotes e carga adicional; comparação de todas
as linhas de negócio geradas com a mesma semente em schemas vazios; rollback de
uma configuração sem presentes suficientes; regressão da view; cancelamento;
FKs, duplicatas, notas inválidas, evento futuro e mudança de evento avaliado;
e JSON antigo/novo exato para seis combinações de tabela/operação de auditoria.

Na primeira tentativa, o servidor não pôde abrir socket dentro do sandbox;
o teste foi executado com permissão fora dele. O erro XML do POM foi encontrado
e corrigido antes dos testes aprovados. A tentativa de Maven sem permissão de
escrita no cache local também falhou; a execução autorizada completou a bateria.
Essas tentativas falhas não foram contabilizadas como testes aprovados.

## Limitações e próxima execução

1. A carga completa ainda não foi executada. Memória, disco, WAL, duração e planos
   nesse volume não estão comprovados. A carga mantém arrays e uma transação grande.
2. A geração pressupõe execução exclusiva, sem outra carga concorrente. A mesma
   semente reproduz dados em bancos com o mesmo estado inicial; auditoria mede a hora real.
3. Datas fixas originais foram mantidas para repetibilidade. A referência das consultas
   deve ser informada para distinguir o experimento de uma consulta de agenda atual.
4. Não houve migração das tabelas do banco pessoal. O schema corrigido se aplica a
   um laboratório novo; os dados de exemplo são opcionais e estão separados.
5. As decisões de índices são provisórias e limitadas à amostra. O candidato extra
   em user_id foi descartado nesta consulta; a seleção definitiva dos demais depende
   da carga completa, de mais repetições e do equilíbrio entre leitura, escrita e espaço.
6. Confirmar o restante da etapa 12 e eventuais SQLs exatos do professor. Não há
   tempos ou planos inventados para preencher essas lacunas.

Os comandos de preparação, carga completa, validação, demonstração e encerramento
do laboratório estão no [README](../README.md). Após a revisão, as alterações
foram organizadas em commits locais a pedido do usuário. Nenhum push foi realizado.

## Arquivos alterados e adicionados

- `.gitignore`
- `README.md`
- `docs/RELATORIO.md`
- `docs/results/after-benchmark.txt`
- `docs/results/benchmark-1.txt`
- `docs/results/benchmark-2.txt`
- `docs/results/benchmark-3.txt`
- `docs/results/br.com.database.AppTest.txt`
- `docs/results/br.com.database.PostgresTest.txt`
- `docs/results/load.txt`
- `docs/results/validation.txt`
- `pom.xml`
- `scripts/test-isolated.sh`
- `sql/benchmark.sql`
- `sql/benchmark_begin.sql`
- `sql/benchmark_write.sql`
- `sql/index_sizes.sql`
- `sql/indexes_btree.sql`
- `sql/indexes_gist.sql`
- `sql/queries.sql`
- `sql/query_equality.sql`
- `sql/schema.sql`
- `sql/seed.sql`
- `sql/validate.sql`
- `sql/validate_view.sql`
- `src/main/java/br/com/database/App.java`
- `src/main/java/br/com/database/DataGenerator.java`
- `src/main/java/br/com/database/DatabaseConnection.java`
- `src/main/java/br/com/database/LoadConfig.java`
- `src/test/java/br/com/database/AppTest.java`
- `src/test/java/br/com/database/PostgresTest.java`
