package br.com.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    // pode ser trocado pelas variáveis de ambiente DB_URL, DB_USER e DB_PASSWORD
    private static final String URL = env(
        "DB_URL",
        "jdbc:postgresql://localhost:5432/GustavoAtv"
    );

    private static final String USER = env("DB_USER", "postgres");
    private static final String PASSWORD = env("DB_PASSWORD", "postgres");

    public static Connection connect()
            throws SQLException {

        // reWriteBatchedInserts junta o lote em um único INSERT com vários VALUES
        String url = URL + (URL.contains("?") ? "&" : "?")
            + "reWriteBatchedInserts=true";

        return DriverManager.getConnection(
            url,
            USER,
            PASSWORD
        );
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
