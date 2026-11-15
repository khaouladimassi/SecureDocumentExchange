package com.securedocumentexchange.network;

import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.util.Properties;

public class TLSClient {

    private static final Properties properties =
            new Properties();

    static {

        try {

            InputStream input =
                    TLSClient.class
                            .getClassLoader()
                            .getResourceAsStream(
                                    "app.properties"
                            );

            if (input == null) {
                throw new RuntimeException(
                        "app.properties introuvable"
                );
            }

            properties.load(input);

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    public static SSLContext createSSLContext()
            throws Exception {

        String trustStorePath =
                properties.getProperty(
                        "tls.truststore"
                );

        String trustStorePassword =
                properties.getProperty(
                        "tls.truststore.password"
                );

        if (trustStorePath == null ||
                trustStorePassword == null) {

            throw new RuntimeException(
                    "Configuration truststore manquante"
            );
        }


        KeyStore trustStore =
                KeyStore.getInstance("JKS");

        trustStore.load(
                new FileInputStream(
                        trustStorePath
                ),
                trustStorePassword.toCharArray()
        );


        TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(
                        TrustManagerFactory
                                .getDefaultAlgorithm()
                );

        tmf.init(trustStore);


        SSLContext context =
                SSLContext.getInstance("TLS");

        context.init(
                null,
                tmf.getTrustManagers(),
                null
        );

        return context;
    }


    public static void connect(
            String serverAddress,
            String username
    ) throws Exception {

        System.out.println();
        System.out.println("=================================");
        System.out.println("          TLS CLIENT");
        System.out.println("=================================");

        System.out.println(
                "Serveur : " + serverAddress
        );

        System.out.println(
                "Port    : 5000"
        );


        SSLContext context =
                createSSLContext();

        System.out.println(
                "✓ SSLContext client créé"
        );


        SSLSocketFactory factory =
                context.getSocketFactory();


        SSLSocket socket =
                (SSLSocket)
                        factory.createSocket(
                                serverAddress,
                                5000
                        );


        System.out.println(
                "✓ Connexion TCP établie"
        );


        socket.startHandshake();


        System.out.println(
                "✓ Handshake TLS réussi"
        );

        System.out.println(
                "TLS Version : "
                        + socket.getSession()
                        .getProtocol()
        );

        System.out.println(
                "Cipher Suite: "
                        + socket.getSession()
                        .getCipherSuite()
        );


        // ================================
        // ENVOYER LA DEMANDE
        // ================================

        PrintWriter writer =
                new PrintWriter(
                        socket.getOutputStream(),
                        true
                );


        String request =
                "CONNECTION_REQUEST|"
                        + username;


        writer.println(request);


        System.out.println();
        System.out.println(
                "✓ Demande de connexion envoyée"
        );

        System.out.println(
                "Utilisateur : "
                        + username
        );


        // Pour le moment on garde la connexion
        // ouverte.
    }
}