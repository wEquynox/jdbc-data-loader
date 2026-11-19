package br.com.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String URL =
        "jdbc:postgresql://localhost:5432/GustavoAtv";

    private static final String USER = "postgres";
    private static final String PASSWORD = "TA ACHANDO QUE EU SOU IDIOTA SEU FILHO DA PUTA";

    public static Connection connect()
            throws SQLException {

        return DriverManager.getConnection(
            URL,
            USER,
            PASSWORD
        );
    }
}
