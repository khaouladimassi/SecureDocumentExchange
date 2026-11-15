package com.securedocumentexchange.service;

import com.securedocumentexchange.network.ClientHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;

/**
 * Service de transfert des documents.
 *
 * Responsabilités :
 *
 * - vérifier l'expéditeur
 * - vérifier le destinataire
 * - enregistrer le document
 * - enregistrer la permission
 * - envoyer le fichier via ClientHandler
 *
 * Le chiffrement AES-256-GCM est réalisé par ClientHandler.
 */
public class FileTransferService {

    // =========================================================
    // CONFIGURATION
    // =========================================================

    private static final long MAX_FILE_SIZE =
            500L * 1024L * 1024L;


    // =========================================================
    // DATABASE
    // =========================================================

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;


    // =========================================================
    // CONSTRUCTEUR
    // =========================================================

    public FileTransferService(
            String dbUrl,
            String dbUser,
            String dbPassword
    ) {

        this.dbUrl =
                dbUrl;

        this.dbUser =
                dbUser;

        this.dbPassword =
                dbPassword;
    }


    // =========================================================
    // SEND FILE
    // =========================================================

    public void sendFile(
            File file,
            int senderId,
            int receiverId,
            ClientHandler client,
            boolean sensitive
    ) throws Exception {

        // =====================================================
        // VALIDATION FICHIER
        // =====================================================

        if (file == null
                || !file.isFile()) {

            throw new FileNotFoundException(
                    "Fichier invalide."
            );
        }


        if (!file.canRead()) {

            throw new IOException(
                    "Impossible de lire le fichier."
            );
        }


        if (file.length() > MAX_FILE_SIZE) {

            throw new IOException(
                    "Le fichier dépasse 500 MB."
            );
        }


        // =====================================================
        // VALIDATION IDS
        // =====================================================

        if (senderId <= 0) {

            throw new IllegalArgumentException(
                    "senderId invalide : "
                            + senderId
            );
        }


        if (receiverId <= 0) {

            throw new IllegalArgumentException(
                    "receiverId invalide : "
                            + receiverId
            );
        }


        if (client == null) {

            throw new IllegalArgumentException(
                    "ClientHandler null."
            );
        }


        // =====================================================
        // DATABASE
        // =====================================================

        try (Connection connection =
                     DriverManager.getConnection(
                             dbUrl,
                             dbUser,
                             dbPassword
                     )) {

            connection.setAutoCommit(false);


            try {

                // =================================================
                // 1. VERIFIER EXPEDITEUR
                // =================================================

                verifyUser(
                        connection,
                        senderId
                );


                // =================================================
                // 2. VERIFIER DESTINATAIRE
                // =================================================

                verifyUser(
                        connection,
                        receiverId
                );


                // =================================================
                // 3. INSERT DOCUMENT
                // =================================================

                int documentId =
                        insertDocument(
                                connection,
                                file.getName(),
                                senderId,
                                sensitive
                        );


                System.out.println(
                        "✓ Document créé : ID = "
                                + documentId
                );


                // =================================================
                // 4. INSERT PERMISSION
                // =================================================

                insertPermission(
                        connection,
                        documentId,
                        receiverId
                );


                System.out.println(
                        "✓ Permission créée"
                );


                // =================================================
                // 5. ENVOI FICHIER
                // =================================================
                /*
                 * IMPORTANT :
                 *
                 * ClientHandler possède actuellement :
                 *
                 * sendFile(File file, boolean sensitive)
                 *
                 * Il réalise lui-même :
                 *
                 * - génération AES-256
                 * - chiffrement AES-GCM
                 * - envoi de la clé via TLS
                 * - envoi du fichier
                 */

                client.sendFile(
                        file,
                        sensitive
                );


                System.out.println(
                        "✓ Fichier envoyé au client"
                );


                // =================================================
                // 6. LOG
                // =================================================

                insertLog(
                        connection,
                        senderId,
                        "SEND_FILE"
                );


                // =================================================
                // 7. COMMIT
                // =================================================

                connection.commit();


                System.out.println();
                System.out.println(
                        "========================================"
                );

                System.out.println(
                        "✓ TRANSFERT COMPLET"
                );

                System.out.println(
                        "Document ID : "
                                + documentId
                );

                System.out.println(
                        "Sender ID   : "
                                + senderId
                );

                System.out.println(
                        "Receiver ID : "
                                + receiverId
                );

                System.out.println(
                        "Fichier     : "
                                + file.getName()
                );

                System.out.println(
                        "Sensible    : "
                                + sensitive
                );

                System.out.println(
                        "========================================"
                );
            }


            catch (Exception e) {

                // =================================================
                // ROLLBACK
                // =================================================

                try {

                    connection.rollback();

                } catch (SQLException rollbackError) {

                    System.err.println(
                            "Erreur rollback : "
                                    + rollbackError.getMessage()
                    );
                }


                System.err.println(
                        "❌ Erreur transfert : "
                                + e.getMessage()
                );


                throw e;
            }
        }
    }


    // =========================================================
    // VERIFY USER
    // =========================================================

    private void verifyUser(
            Connection connection,
            int userId
    ) throws SQLException {

        String sql =
                """
                SELECT id_user
                FROM users
                WHERE id_user = ?
                """;


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    userId
            );


            try (ResultSet result =
                         statement.executeQuery()) {

                if (!result.next()) {

                    throw new SQLException(
                            "Utilisateur introuvable : "
                                    + userId
                    );
                }
            }
        }
    }


    // =========================================================
    // INSERT DOCUMENT
    // =========================================================

    private int insertDocument(
            Connection connection,
            String filename,
            int senderId,
            boolean sensitive
    ) throws SQLException {

        String sql =
                """
                INSERT INTO documents
                (
                    filename,
                    sender_id,
                    sensitive,
                    date_upload
                )
                VALUES
                (
                    ?,
                    ?,
                    ?,
                    ?
                )
                RETURNING id_document
                """;


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    filename
            );


            /*
             * IMPORTANT :
             *
             * C'est ici que le problème
             *
             * sender_id = 0
             *
             * est corrigé.
             */

            statement.setInt(
                    2,
                    senderId
            );


            statement.setBoolean(
                    3,
                    sensitive
            );


            statement.setTimestamp(
                    4,
                    Timestamp.valueOf(
                            LocalDateTime.now()
                    )
            );


            try (ResultSet result =
                         statement.executeQuery()) {

                if (!result.next()) {

                    throw new SQLException(
                            "Impossible de créer le document."
                    );
                }


                return result.getInt(
                        "id_document"
                );
            }
        }
    }


    // =========================================================
    // INSERT PERMISSION
    // =========================================================

    private void insertPermission(
            Connection connection,
            int documentId,
            int receiverId
    ) throws SQLException {

        /*
         * Cette requête suppose que ta table possède :
         *
         * document_id
         * user_id
         *
         * Si tes colonnes portent d'autres noms,
         * il faudra les adapter au schéma réel.
         */

        String sql =
                """
                INSERT INTO permissions
                (
                    document_id,
                    user_id
                )
                VALUES
                (
                    ?,
                    ?
                )
                """;


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    documentId
            );


            statement.setInt(
                    2,
                    receiverId
            );


            statement.executeUpdate();
        }
    }


    // =========================================================
    // INSERT LOG
    // =========================================================

    private void insertLog(
            Connection connection,
            int userId,
            String action
    ) throws SQLException {

        /*
         * IMPORTANT :
         *
         * On n'utilise PAS :
         *
         * details
         * created_at
         *
         * car ces colonnes n'existent pas dans
         * ton schéma actuel.
         *
         * Ici on utilise uniquement :
         *
         * user_id
         * action
         *
         * À adapter si ta table logs utilise
         * d'autres noms.
         */

        String sql =
                """
                INSERT INTO logs
                (
                    user_id,
                    action
                )
                VALUES
                (
                    ?,
                    ?
                )
                """;


        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    userId
            );


            statement.setString(
                    2,
                    action
            );


            statement.executeUpdate();
        }
    }
}