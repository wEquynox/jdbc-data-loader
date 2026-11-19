
package br.com.database;

import java.sql.Connection;
import java.sql.SQLException;

public class App {

    public static void main(String[] args) {

        long start = System.nanoTime();

        try (Connection connection =
                DatabaseConnection.connect()) {

            connection.setAutoCommit(false);

            try {
                DataGenerator.generateUsers(
                    connection,
                    LoadConfig.USERS
                );

                DataGenerator.generateVenues(
                    connection,
                    LoadConfig.VENUES
                );

                DataGenerator.generateEvents(
                    connection,
                    LoadConfig.EVENTS
                );

                DataGenerator.generateRegistrations(
                    connection,
                    LoadConfig.REGISTRATIONS
                );

                connection.commit();

                long end = System.nanoTime();

                double seconds =
                    (end - start) / 1_000_000_000.0;

                System.out.printf(
                    "Tempo total: %.3f segundos%n",
                    seconds
                );

            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }

        } catch (SQLException e) {
            System.err.println(
                "Erro: " + e.getMessage()
            );
        }
    }
}
