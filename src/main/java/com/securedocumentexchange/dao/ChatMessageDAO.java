package com.securedocumentexchange.dao;

import com.securedocumentexchange.database.DatabaseConnection;
import com.securedocumentexchange.model.ChatMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ================================================================
 * CHAT MESSAGE DAO
 * ================================================================
 *
 * Gestion des messages du chat.
 *
 * Table PostgreSQL :
 *
 * chat_messages
 *
 * Colonnes utilisées :
 *
 * - id_message
 * - id_conversation
 * - id_user
 * - message_type
 * - content
 * - id_document
 * - sent_at
 *
 * Types supportés :
 *
 * - TEXT
 * - FILE
 * - SYSTEM
 */
public class ChatMessageDAO {

    // =========================================================
    // MESSAGE TYPES
    // =========================================================

    public static final String TYPE_TEXT =
            ChatMessage.TYPE_TEXT;

    public static final String TYPE_FILE =
            ChatMessage.TYPE_FILE;

    public static final String TYPE_SYSTEM =
            ChatMessage.TYPE_SYSTEM;


    // =========================================================
    // INSERT
    // =========================================================

    /**
     * Insère un message dans PostgreSQL.
     *
     * Le type est normalisé et validé avant insertion.
     *
     * @param message message à enregistrer
     * @return identifiant généré du message
     */
    public long insert(
            ChatMessage message
    ) throws SQLException {

        if (message == null) {

            throw new IllegalArgumentException(
                    "Le message ne peut pas être null."
            );
        }


        // -----------------------------------------------------
        // VALIDATION CONVERSATION
        // -----------------------------------------------------

        if (!message.hasValidConversation()) {

            throw new IllegalArgumentException(
                    "Identifiant de conversation invalide."
            );
        }


        // -----------------------------------------------------
        // VALIDATION EXPEDITEUR
        // -----------------------------------------------------

        if (!message.hasValidSender()) {

            throw new IllegalArgumentException(
                    "Identifiant de l'expéditeur invalide."
            );
        }


        // -----------------------------------------------------
        // TYPE
        // -----------------------------------------------------

        String type =
                normalizeMessageType(
                        message.getType()
                );


        // -----------------------------------------------------
        // CONTENU
        // -----------------------------------------------------

        String content =
                message.getContent();

        if (content == null) {

            content = "";
        }

        content = content.trim();


        /*
         * Un message ne peut pas être vide.
         */
        if (content.isEmpty()) {

            throw new IllegalArgumentException(
                    "Le contenu du message ne peut pas être vide."
            );
        }


        // -----------------------------------------------------
        // VALIDATION DOCUMENT
        // -----------------------------------------------------

        /*
         * Un message FILE doit référencer un document valide.
         */
        if (
                TYPE_FILE.equals(type)
                        && message.getDocumentId() <= 0
        ) {

            throw new IllegalArgumentException(
                    "Un message FILE doit référencer un document valide."
            );
        }


        /*
         * Un message TEXT ou SYSTEM ne doit normalement
         * pas contenir de document.
         *
         * On ne bloque cependant pas ici un document éventuel
         * afin de rester compatible avec les données existantes.
         */


        // -----------------------------------------------------
        // SQL
        // -----------------------------------------------------

        String sql =
                "INSERT INTO chat_messages " +
                        "(id_conversation, id_user, message_type, content, id_document) " +
                        "VALUES (?, ?, ?, ?, ?) " +
                        "RETURNING id_message, sent_at";


        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {


            // -------------------------------------------------
            // CONVERSATION
            // -------------------------------------------------

            statement.setInt(
                    1,
                    message.getIdConversation()
            );


            // -------------------------------------------------
            // EXPEDITEUR
            // -------------------------------------------------

            statement.setInt(
                    2,
                    message.getSenderId()
            );


            // -------------------------------------------------
            // TYPE
            // -------------------------------------------------

            statement.setString(
                    3,
                    type
            );


            // -------------------------------------------------
            // CONTENU
            // -------------------------------------------------

            statement.setString(
                    4,
                    content
            );


            // -------------------------------------------------
            // DOCUMENT
            // -------------------------------------------------

            if (message.getDocumentId() > 0) {

                statement.setInt(
                        5,
                        message.getDocumentId()
                );

            } else {

                statement.setNull(
                        5,
                        Types.INTEGER
                );
            }


            // -------------------------------------------------
            // EXECUTION
            // -------------------------------------------------

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (resultSet.next()) {

                    long idMessage =
                            resultSet.getLong(
                                    "id_message"
                            );


                    message.setIdMessage(
                            idMessage
                    );


                    Timestamp timestamp =
                            resultSet.getTimestamp(
                                    "sent_at"
                            );


                    if (timestamp != null) {

                        message.setSentAt(
                                timestamp.toLocalDateTime()
                        );
                    }


                    /*
                     * Remet les valeurs normalisées dans
                     * l'objet Java.
                     */
                    message.setType(
                            type
                    );

                    message.setContent(
                            content
                    );


                    return idMessage;
                }
            }
        }


        throw new SQLException(
                "Impossible d'insérer le message."
        );
    }


    // =========================================================
    // NORMALIZE MESSAGE TYPE
    // =========================================================

    /**
     * Vérifie et normalise le type du message.
     *
     * Types autorisés :
     *
     * TEXT
     * FILE
     * SYSTEM
     */
    private String normalizeMessageType(
            String type
    ) {

        if (type == null) {

            throw new IllegalArgumentException(
                    "Le type du message ne peut pas être null."
            );
        }


        String normalized =
                type
                        .trim()
                        .toUpperCase();


        switch (normalized) {

            case TYPE_TEXT:

                return TYPE_TEXT;


            case TYPE_FILE:

                return TYPE_FILE;


            case TYPE_SYSTEM:

                return TYPE_SYSTEM;


            default:

                throw new IllegalArgumentException(
                        "Type de message invalide : "
                                + type
                                + ". Types autorisés : "
                                + TYPE_TEXT
                                + ", "
                                + TYPE_FILE
                                + ", "
                                + TYPE_SYSTEM
                                + "."
                );
        }
    }


    // =========================================================
    // FIND BY ID
    // =========================================================

    /**
     * Recherche un message par son identifiant.
     */
    public ChatMessage findById(
            long messageId
    ) throws SQLException {

        if (messageId <= 0) {

            return null;
        }


        String sql =
                "SELECT " +
                        "m.id_message, " +
                        "m.id_conversation, " +
                        "m.id_user, " +
                        "u.username AS sender_username, " +
                        "m.message_type AS type, " +
                        "m.content, " +
                        "m.id_document, " +
                        "m.sent_at " +
                        "FROM chat_messages m " +
                        "LEFT JOIN users u " +
                        "ON u.id_user = m.id_user " +
                        "WHERE m.id_message = ?";


        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setLong(
                    1,
                    messageId
            );


            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (resultSet.next()) {

                    return map(resultSet);
                }
            }
        }


        return null;
    }


    // =========================================================
    // HISTORY
    // =========================================================

    /**
     * Récupère tout l'historique d'une conversation.
     *
     * Ordre :
     *
     * ancien -> récent
     */
    public List<ChatMessage> getHistory(
            int conversationId
    ) throws SQLException {

        return getMessages(
                conversationId,
                0
        );
    }


    // =========================================================
    // GET MESSAGES
    // =========================================================

    /**
     * Récupère les messages d'une conversation.
     *
     * @param conversationId identifiant de conversation
     * @param limit nombre maximum de messages
     *
     * Si limit <= 0 :
     * tous les messages sont récupérés.
     */
    public List<ChatMessage> getMessages(
            int conversationId,
            int limit
    ) throws SQLException {

        List<ChatMessage> messages =
                new ArrayList<>();


        if (conversationId <= 0) {

            return messages;
        }


        if (limit > 500) {

            limit = 500;
        }


        StringBuilder sql =
                new StringBuilder();


        sql.append(
                "SELECT "
        );


        sql.append(
                "m.id_message, "
        );


        sql.append(
                "m.id_conversation, "
        );


        sql.append(
                "m.id_user, "
        );


        sql.append(
                "u.username AS sender_username, "
        );


        sql.append(
                "m.message_type AS type, "
        );


        sql.append(
                "m.content, "
        );


        sql.append(
                "m.id_document, "
        );


        sql.append(
                "m.sent_at "
        );


        sql.append(
                "FROM chat_messages m "
        );


        sql.append(
                "LEFT JOIN users u "
        );


        sql.append(
                "ON u.id_user = m.id_user "
        );


        sql.append(
                "WHERE m.id_conversation = ? "
        );


        sql.append(
                "ORDER BY m.sent_at ASC, m.id_message ASC"
        );


        if (limit > 0) {

            sql.append(
                    " LIMIT ?"
            );
        }


        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql.toString()
                        )
        ) {

            statement.setInt(
                    1,
                    conversationId
            );


            if (limit > 0) {

                statement.setInt(
                        2,
                        limit
                );
            }


            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                while (resultSet.next()) {

                    messages.add(
                            map(resultSet)
                    );
                }
            }
        }


        return messages;
    }


    // =========================================================
    // LAST MESSAGES
    // =========================================================

    /**
     * Récupère les derniers messages d'une conversation.
     *
     * La liste finale est retournée dans l'ordre :
     *
     * ancien -> récent
     */
    public List<ChatMessage> getLastMessages(
            int conversationId,
            int limit
    ) throws SQLException {

        List<ChatMessage> messages =
                new ArrayList<>();


        if (conversationId <= 0) {

            return messages;
        }


        if (limit <= 0) {

            limit = 50;
        }


        if (limit > 500) {

            limit = 500;
        }


        String sql =
                "SELECT " +
                        "m.id_message, " +
                        "m.id_conversation, " +
                        "m.id_user, " +
                        "u.username AS sender_username, " +
                        "m.message_type AS type, " +
                        "m.content, " +
                        "m.id_document, " +
                        "m.sent_at " +
                        "FROM chat_messages m " +
                        "LEFT JOIN users u " +
                        "ON u.id_user = m.id_user " +
                        "WHERE m.id_conversation = ? " +
                        "ORDER BY m.sent_at DESC, " +
                        "m.id_message DESC " +
                        "LIMIT ?";


        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(
                    1,
                    conversationId
            );


            statement.setInt(
                    2,
                    limit
            );


            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                while (resultSet.next()) {

                    messages.add(
                            map(resultSet)
                    );
                }
            }
        }


        Collections.reverse(
                messages
        );


        return messages;
    }


    // =========================================================
    // UPDATE MESSAGE
    // =========================================================

    /**
     * Modifie uniquement un message TEXT.
     *
     * Seul son expéditeur peut le modifier.
     */
    public boolean updateMessage(
            long messageId,
            int senderId,
            String newText
    ) throws SQLException {

        if (
                messageId <= 0
                        || senderId <= 0
        ) {

            return false;
        }


        if (
                newText == null
                        || newText.trim().isEmpty()
        ) {

            return false;
        }


        String sql =
                "UPDATE chat_messages " +
                        "SET content = ? " +
                        "WHERE id_message = ? " +
                        "AND id_user = ? " +
                        "AND UPPER(message_type) = 'TEXT'";


        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(
                    1,
                    newText.trim()
            );


            statement.setLong(
                    2,
                    messageId
            );


            statement.setInt(
                    3,
                    senderId
            );


            return statement.executeUpdate() > 0;
        }
    }


    // =========================================================
    // DELETE MESSAGE
    // =========================================================

    /**
     * Supprime un message appartenant à l'utilisateur.
     */
    public boolean deleteMessage(
            long messageId,
            int senderId
    ) throws SQLException {

        if (
                messageId <= 0
                        || senderId <= 0
        ) {

            return false;
        }


        String sql =
                "DELETE FROM chat_messages " +
                        "WHERE id_message = ? " +
                        "AND id_user = ?";


        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setLong(
                    1,
                    messageId
            );


            statement.setInt(
                    2,
                    senderId
            );


            return statement.executeUpdate() > 0;
        }
    }


    // =========================================================
    // COUNT
    // =========================================================

    /**
     * Retourne le nombre de messages d'une conversation.
     */
    public int countMessages(
            int conversationId
    ) throws SQLException {

        if (conversationId <= 0) {

            return 0;
        }


        String sql =
                "SELECT COUNT(*) " +
                        "FROM chat_messages " +
                        "WHERE id_conversation = ?";


        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(
                    1,
                    conversationId
            );


            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (resultSet.next()) {

                    return resultSet.getInt(1);
                }
            }
        }


        return 0;
    }


    // =========================================================
    // MAP RESULTSET -> CHAT MESSAGE
    // =========================================================

    /**
     * Transforme une ligne SQL en objet ChatMessage.
     */
    private ChatMessage map(
            ResultSet resultSet
    ) throws SQLException {

        ChatMessage message =
                new ChatMessage();


        // -----------------------------------------------------
        // ID MESSAGE
        // -----------------------------------------------------

        message.setIdMessage(
                resultSet.getLong(
                        "id_message"
                )
        );


        // -----------------------------------------------------
        // CONVERSATION
        // -----------------------------------------------------

        message.setIdConversation(
                resultSet.getInt(
                        "id_conversation"
                )
        );


        // -----------------------------------------------------
        // EXPEDITEUR
        // -----------------------------------------------------

        message.setSenderId(
                resultSet.getInt(
                        "id_user"
                )
        );


        // -----------------------------------------------------
        // USERNAME
        // -----------------------------------------------------

        message.setSenderUsername(
                resultSet.getString(
                        "sender_username"
                )
        );


        // -----------------------------------------------------
        // TYPE
        // -----------------------------------------------------

        String type =
                resultSet.getString(
                        "type"
                );


        if (type != null) {

            type =
                    type
                            .trim()
                            .toUpperCase();
        }


        message.setType(
                type
        );


        // -----------------------------------------------------
        // CONTENU
        // -----------------------------------------------------

        message.setContent(
                resultSet.getString(
                        "content"
                )
        );


        // -----------------------------------------------------
        // DOCUMENT
        // -----------------------------------------------------

        int documentId =
                resultSet.getInt(
                        "id_document"
                );


        if (resultSet.wasNull()) {

            documentId = 0;
        }


        message.setDocumentId(
                documentId
        );


        // -----------------------------------------------------
        // DATE
        // -----------------------------------------------------

        Timestamp timestamp =
                resultSet.getTimestamp(
                        "sent_at"
                );


        if (timestamp != null) {

            message.setSentAt(
                    timestamp.toLocalDateTime()
            );
        }


        return message;
    }
}