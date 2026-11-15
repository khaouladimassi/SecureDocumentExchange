package com.securedocumentexchange.security;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import java.io.InputStream;
import java.security.KeyStore;
import java.util.Properties;

/**
 * ================================================================
 * TLS MANAGER
 * ================================================================
 *
 * Gestion TLS de l'application.
 *
 * Serveur :
 *      server-keystore.jks
 *
 * Client :
 *      client-truststore.jks
 *
 * Configuration :
 *      src/main/resources/app.properties
 */
public final class TLSManager {

    // =========================================================
    // CONFIGURATION
    // =========================================================

    private static final Properties properties =
            new Properties();

    // =========================================================
    // PROTOCOLES TLS AUTORISÉS
    // =========================================================

    private static final String[] ENABLED_PROTOCOLS = {
            "TLSv1.3",
            "TLSv1.2"
    };

    // =========================================================
    // INITIALISATION
    // =========================================================

    static {

        try {

            InputStream input =
                    TLSManager.class
                            .getClassLoader()
                            .getResourceAsStream(
                                    "app.properties"
                            );

            if (input == null) {

                throw new IllegalStateException(
                        "app.properties introuvable dans " +
                                "src/main/resources/"
                );
            }

            try (InputStream configInput = input) {

                properties.load(configInput);
            }

            System.out.println(
                    "✓ Configuration TLS chargée"
            );

        } catch (Exception e) {

            throw new ExceptionInInitializerError(
                    "Impossible de charger app.properties : "
                            + e.getMessage()
            );
        }
    }

    // =========================================================
    // CONSTRUCTEUR
    // =========================================================

    private TLSManager() {
        // Classe utilitaire
    }

    // =========================================================
    // SERVEUR TLS
    // =========================================================

    public static SSLContext createServerSSLContext()
            throws Exception {

        System.out.println();
        System.out.println(
                "================================="
        );
        System.out.println(
                "INITIALISATION TLS SERVEUR"
        );
        System.out.println(
                "================================="
        );

        // -----------------------------------------------------
        // PROPRIÉTÉS
        // -----------------------------------------------------

        String keystorePath =
                getRequiredProperty(
                        "tls.keystore"
                );

        String password =
                getRequiredProperty(
                        "tls.password"
                );

        System.out.println(
                "Keystore serveur : "
                        + keystorePath
        );

        // -----------------------------------------------------
        // KEYSTORE
        // -----------------------------------------------------

        KeyStore keyStore =
                KeyStore.getInstance("JKS");

        InputStream input =
                TLSManager.class
                        .getClassLoader()
                        .getResourceAsStream(
                                keystorePath
                        );

        if (input == null) {

            throw new IllegalStateException(
                    "Keystore serveur introuvable : "
                            + keystorePath
            );
        }

        try (InputStream keyStoreInput = input) {

            keyStore.load(
                    keyStoreInput,
                    password.toCharArray()
            );
        }

        System.out.println(
                "✓ Keystore serveur chargé"
        );

        // -----------------------------------------------------
        // KEY MANAGER
        // -----------------------------------------------------

        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(
                        KeyManagerFactory
                                .getDefaultAlgorithm()
                );

        keyManagerFactory.init(
                keyStore,
                password.toCharArray()
        );

        System.out.println(
                "✓ KeyManager initialisé"
        );

        // -----------------------------------------------------
        // SSL CONTEXT
        // -----------------------------------------------------

        SSLContext sslContext =
                SSLContext.getInstance("TLS");

        sslContext.init(
                keyManagerFactory.getKeyManagers(),
                null,
                null
        );

        System.out.println(
                "✓ SSLContext serveur créé"
        );

        System.out.println(
                "================================="
        );

        return sslContext;
    }

    // =========================================================
    // CLIENT TLS
    // =========================================================

    public static SSLContext createClientSSLContext()
            throws Exception {

        System.out.println();
        System.out.println(
                "================================="
        );
        System.out.println(
                "INITIALISATION TLS CLIENT"
        );
        System.out.println(
                "================================="
        );

        // -----------------------------------------------------
        // PROPRIÉTÉS
        // -----------------------------------------------------

        String truststorePath =
                getRequiredProperty(
                        "tls.truststore"
                );

        String password =
                getRequiredProperty(
                        "tls.truststore.password"
                );

        System.out.println(
                "Truststore client : "
                        + truststorePath
        );

        // -----------------------------------------------------
        // TRUSTSTORE
        // -----------------------------------------------------

        KeyStore trustStore =
                KeyStore.getInstance("JKS");

        InputStream input =
                TLSManager.class
                        .getClassLoader()
                        .getResourceAsStream(
                                truststorePath
                        );

        if (input == null) {

            throw new IllegalStateException(
                    "Truststore client introuvable : "
                            + truststorePath
            );
        }

        try (InputStream trustStoreInput = input) {

            trustStore.load(
                    trustStoreInput,
                    password.toCharArray()
            );
        }

        System.out.println(
                "✓ Truststore client chargé"
        );

        // -----------------------------------------------------
        // TRUST MANAGER
        // -----------------------------------------------------

        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(
                        TrustManagerFactory
                                .getDefaultAlgorithm()
                );

        trustManagerFactory.init(
                trustStore
        );

        System.out.println(
                "✓ TrustManager initialisé"
        );

        // -----------------------------------------------------
        // SSL CONTEXT
        // -----------------------------------------------------

        SSLContext sslContext =
                SSLContext.getInstance("TLS");

        sslContext.init(
                null,
                trustManagerFactory.getTrustManagers(),
                null
        );

        System.out.println(
                "✓ SSLContext client créé"
        );

        System.out.println(
                "================================="
        );

        return sslContext;
    }

    // =========================================================
    // PROPRIÉTÉ OBLIGATOIRE
    // =========================================================

    private static String getRequiredProperty(
            String name
    ) {

        if (name == null
                || name.isBlank()) {

            throw new IllegalArgumentException(
                    "Nom de propriété TLS invalide."
            );
        }

        String value =
                properties.getProperty(name);

        if (value == null) {

            throw new IllegalStateException(
                    "Propriété TLS manquante : "
                            + name
            );
        }

        value =
                value.trim();

        if (value.isEmpty()) {

            throw new IllegalStateException(
                    "Propriété TLS vide : "
                            + name
            );
        }

        return value;
    }

    // =========================================================
    // PROTOCOLES
    // =========================================================

    public static String[] getEnabledProtocols() {

        return ENABLED_PROTOCOLS.clone();
    }

    // =========================================================
    // DEBUG CONFIGURATION
    // =========================================================

    public static void printConfiguration() {

        System.out.println();
        System.out.println(
                "========== TLS CONFIGURATION =========="
        );

        printProperty(
                "tls.keystore"
        );

        printProperty(
                "tls.password"
        );

        printProperty(
                "tls.truststore"
        );

        printProperty(
                "tls.truststore.password"
        );

        System.out.println(
                "======================================="
        );
    }

    private static void printProperty(
            String name
    ) {

        String value =
                properties.getProperty(name);

        if (value == null) {

            System.out.println(
                    name + " = NULL"
            );

        } else if (
                name.toLowerCase()
                        .contains("password")
        ) {

            System.out.println(
                    name + " = ********"
            );

        } else {

            System.out.println(
                    name + " = " + value
            );
        }
    }
}