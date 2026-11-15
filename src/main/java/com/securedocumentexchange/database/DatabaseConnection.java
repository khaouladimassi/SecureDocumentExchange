package com.securedocumentexchange.database;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * ================================================================
 * DATABASE CONNECTION
 * ================================================================
 *
 * Gestion centralisée de la connexion PostgreSQL.
 *
 * La configuration est chargée depuis :
 *
 * src/main/resources/database.properties
 *
 * Exemple :
 *
 * db.url=jdbc:postgresql://localhost:5433/secure_exchange
 * db.user=postgres
 * db.password=mot_de_passe
 */
public final class DatabaseConnection {

    private static final Logger logger =
            LogManager.getLogger(DatabaseConnection.class);

    private static final String CONFIG_FILE =
            "database.properties";

    private static final Properties properties =
            new Properties();

    // =========================================================
    // INITIALISATION
    // =========================================================

    static {

        try (InputStream input =
                     DatabaseConnection.class
                             .getClassLoader()
                             .getResourceAsStream(CONFIG_FILE)) {

            if (input == null) {

                throw new IllegalStateException(
                        "Fichier " + CONFIG_FILE
                                + " introuvable dans src/main/resources."
                );
            }

            properties.load(input);

            validateConfiguration();

            logger.info(
                    "Configuration PostgreSQL chargée avec succès."
            );

        } catch (IOException | IllegalStateException e) {

            logger.error(
                    "Erreur lors du chargement de la configuration PostgreSQL.",
                    e
            );

            throw new ExceptionInInitializerError(e);
        }
    }

    // =========================================================
    // CONSTRUCTEUR
    // =========================================================

    private DatabaseConnection() {
        // Classe utilitaire : aucune instance nécessaire.
    }

    // =========================================================
    // GET CONNECTION
    // =========================================================

    /**
     * Crée une nouvelle connexion PostgreSQL.
     *
     * @return connexion PostgreSQL
     * @throws SQLException si la connexion échoue
     */
    public static Connection getConnection()
            throws SQLException {

        String url =
                properties.getProperty("db.url");

        String user =
                properties.getProperty("db.user");

        String password =
                properties.getProperty("db.password");

        return DriverManager.getConnection(
                url,
                user,
                password
        );
    }

    // =========================================================
    // TEST CONNECTION
    // =========================================================

    /**
     * Teste la connexion à PostgreSQL.
     */
    public static boolean testConnection() {

        try (Connection connection =
                     getConnection()) {

            logger.info(
                    "Connexion PostgreSQL réussie !"
            );

            return true;

        } catch (SQLException e) {

            logger.error(
                    "Erreur de connexion PostgreSQL.",
                    e
            );

            return false;
        }
    }

    // =========================================================
    // VALIDATION CONFIGURATION
    // =========================================================

    private static void validateConfiguration() {

        String url =
                properties.getProperty("db.url");

        String user =
                properties.getProperty("db.user");

        String password =
                properties.getProperty("db.password");

        if (url == null || url.isBlank()) {

            throw new IllegalStateException(
                    "La propriété db.url est absente."
            );
        }

        if (user == null || user.isBlank()) {

            throw new IllegalStateException(
                    "La propriété db.user est absente."
            );
        }

        if (password == null) {

            throw new IllegalStateException(
                    "La propriété db.password est absente."
            );
        }
    }

    // =========================================================
    // MAIN
    // =========================================================

    public static void main(String[] args) {

        if (testConnection()) {

            System.out.println(
                    "========================================"
            );

            System.out.println(
                    " PostgreSQL : CONNEXION RÉUSSIE"
            );

            System.out.println(
                    "========================================"
            );

        } else {

            System.out.println(
                    "========================================"
            );

            System.out.println(
                    " PostgreSQL : ÉCHEC DE CONNEXION"
            );

            System.out.println(
                    "========================================"
            );
        }
    }
}