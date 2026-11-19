
package br.com.database;

/**
 * Quantidades e parâmetros da carga. Os valores padrão são os do enunciado
 * e podem ser trocados por argumentos, por exemplo:
 * --users=1000 --events=100 --registrations=2000 --seed=7
 */
public class LoadConfig {

    public int users = 5_000_000;
    public int venues = 500;
    public int events = 100_000;
    public int registrations = 10_000_000;
    public int comments = 1_000_000;
    public int ratings = 1_000_000;

    public int batchSize = 5_000;

    public long seed = 42L;

    // fração dos eventos que já terminaram (status completed)
    public double pastEventRatio = 0.3;

    // apaga as tabelas e recria tudo a partir do schema antes de carregar
    public boolean reset = false;
    public String schemaFile = "sql/schema.sql";

    // desliga os triggers de auditoria durante a carga
    public boolean noAudit = false;

    public static LoadConfig fromArgs(String[] args) {

        LoadConfig config = new LoadConfig();

        for (String arg : args) {

            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException(
                    "Argumento inválido: " + arg
                );
            }

            String[] parts = arg.substring(2).split("=", 2);
            String name = parts[0];
            String value = parts.length > 1 ? parts[1] : null;

            switch (name) {
                case "users" -> config.users = parseInt(value);
                case "venues" -> config.venues = parseInt(value);
                case "events" -> config.events = parseInt(value);
                case "registrations" -> config.registrations = parseInt(value);
                case "comments" -> config.comments = parseInt(value);
                case "ratings" -> config.ratings = parseInt(value);
                case "batch" -> config.batchSize = parseInt(value);
                case "seed" -> config.seed = Long.parseLong(value);
                case "past-ratio" -> config.pastEventRatio = Double.parseDouble(value);
                case "schema" -> config.schemaFile = value;
                case "reset" -> config.reset = true;
                case "no-audit" -> config.noAudit = true;
                default -> throw new IllegalArgumentException(
                    "Argumento desconhecido: " + arg
                );
            }
        }

        config.validate();

        return config;
    }

    private static int parseInt(String value) {
        return Integer.parseInt(value.replace("_", ""));
    }

    private void validate() {

        if (users < 1 || venues < 1 || events < 1 || batchSize < 1) {
            throw new IllegalArgumentException(
                "users, venues, events e batch devem ser maiores que zero."
            );
        }

        if (registrations < 0 || comments < 0 || ratings < 0) {
            throw new IllegalArgumentException(
                "As quantidades não podem ser negativas."
            );
        }

        if ((long) users * events < registrations) {
            throw new IllegalArgumentException(
                "Quantidade de inscrições excede as combinações possíveis."
            );
        }

        if (comments > 0 && registrations == 0) {
            throw new IllegalArgumentException(
                "Comentários precisam de inscrições."
            );
        }

        if (pastEventRatio < 0 || pastEventRatio > 1) {
            throw new IllegalArgumentException(
                "past-ratio deve estar entre 0 e 1."
            );
        }
    }

    @Override
    public String toString() {
        return String.format(
            "usuários=%d, locais=%d, eventos=%d, inscrições=%d, "
                + "comentários=%d, avaliações=%d, lote=%d, semente=%d",
            users, venues, events, registrations,
            comments, ratings, batchSize, seed
        );
    }
}
