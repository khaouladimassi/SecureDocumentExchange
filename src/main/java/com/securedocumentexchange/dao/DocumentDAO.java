package com.securedocumentexchange.dao;

import com.securedocumentexchange.database.DatabaseConnection;
import com.securedocumentexchange.model.Document;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DocumentDAO {

    // =========================================================
    // INSERT DOCUMENT
    // =========================================================

    public int insert(Document document) throws SQLException {

        if (document == null) {
            throw new IllegalArgumentException(
                    "Document null."
            );
        }

        if (document.getSenderId() <= 0) {
            throw new SQLException(
                    "senderId invalide : "
                            + document.getSenderId()
            );
        }

        if (document.getFilename() == null
                || document.getFilename().isBlank()) {

            throw new SQLException(
                    "Nom de fichier invalide."
            );
        }

        String sql =
                """
                INSERT INTO documents
                (
                    filename,
                    sender_id,
                    recipient_id,
                    sensitive,
                    date_upload,
                    key_alias,
                    file_path,
                    file_size,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id_document
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(
                    1,
                    document.getFilename()
            );

            statement.setInt(
                    2,
                    document.getSenderId()
            );

            statement.setInt(
                    3,
                    document.getRecipientId()
            );

            statement.setBoolean(
                    4,
                    document.isSensitive()
            );

            LocalDateTimeHelper.setTimestamp(
                    statement,
                    5,
                    document.getDateUpload()
            );

            statement.setString(
                    6,
                    document.getKeyAlias()
            );

            statement.setString(
                    7,
                    document.getFilePath()
            );

            statement.setLong(
                    8,
                    document.getFileSize()
            );

            String status =
                    document.getStatus();

            if (status == null || status.isBlank()) {
                status = "AVAILABLE";
            }

            statement.setString(
                    9,
                    status
            );

            try (
                    ResultSet rs =
                            statement.executeQuery()
            ) {

                if (rs.next()) {

                    int id =
                            rs.getInt("id_document");

                    document.setIdDocument(id);

                    return id;
                }
            }
        }

        throw new SQLException(
                "Impossible de créer le document."
        );
    }


    // =========================================================
    // FIND BY ID
    // =========================================================

    public Document findById(
            int documentId
    ) throws SQLException {

        String sql =
                """
                SELECT
                    id_document,
                    filename,
                    sender_id,
                    recipient_id,
                    sensitive,
                    date_upload,
                    key_alias,
                    file_path,
                    file_size,
                    status
                FROM documents
                WHERE id_document = ?
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(1, documentId);

            try (
                    ResultSet rs =
                            statement.executeQuery()
            ) {

                if (rs.next()) {
                    return mapDocument(rs);
                }
            }
        }

        return null;
    }


    // =========================================================
    // FIND ALL
    // =========================================================

    public List<Document> findAll()
            throws SQLException {

        List<Document> documents =
                new ArrayList<>();

        String sql =
                """
                SELECT
                    id_document,
                    filename,
                    sender_id,
                    recipient_id,
                    sensitive,
                    date_upload,
                    key_alias,
                    file_path,
                    file_size,
                    status
                FROM documents
                ORDER BY date_upload DESC
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet rs =
                        statement.executeQuery()
        ) {

            while (rs.next()) {
                documents.add(
                        mapDocument(rs)
                );
            }
        }

        return documents;
    }


    // =========================================================
    // FIND BY SENDER
    // =========================================================

    public List<Document> findBySenderId(
            int senderId
    ) throws SQLException {

        List<Document> documents =
                new ArrayList<>();

        String sql =
                """
                SELECT
                    id_document,
                    filename,
                    sender_id,
                    recipient_id,
                    sensitive,
                    date_upload,
                    key_alias,
                    file_path,
                    file_size,
                    status
                FROM documents
                WHERE sender_id = ?
                ORDER BY date_upload DESC
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(1, senderId);

            try (
                    ResultSet rs =
                            statement.executeQuery()
            ) {

                while (rs.next()) {
                    documents.add(
                            mapDocument(rs)
                    );
                }
            }
        }

        return documents;
    }


    // =========================================================
    // FIND SENSITIVE
    // =========================================================

    public List<Document> findSensitiveDocuments()
            throws SQLException {

        List<Document> documents =
                new ArrayList<>();

        String sql =
                """
                SELECT
                    id_document,
                    filename,
                    sender_id,
                    recipient_id,
                    sensitive,
                    date_upload,
                    key_alias,
                    file_path,
                    file_size,
                    status
                FROM documents
                WHERE sensitive = TRUE
                ORDER BY date_upload DESC
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet rs =
                        statement.executeQuery()
        ) {

            while (rs.next()) {
                documents.add(
                        mapDocument(rs)
                );
            }
        }

        return documents;
    }


    // =========================================================
    // UPDATE
    // =========================================================

    public boolean update(
            Document document
    ) throws SQLException {

        String sql =
                """
                UPDATE documents
                SET
                    filename = ?,
                    recipient_id = ?,
                    sensitive = ?,
                    key_alias = ?,
                    file_path = ?,
                    file_size = ?,
                    status = ?
                WHERE id_document = ?
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(
                    1,
                    document.getFilename()
            );

            statement.setInt(
                    2,
                    document.getRecipientId()
            );

            statement.setBoolean(
                    3,
                    document.isSensitive()
            );

            statement.setString(
                    4,
                    document.getKeyAlias()
            );

            statement.setString(
                    5,
                    document.getFilePath()
            );

            statement.setLong(
                    6,
                    document.getFileSize()
            );

            statement.setString(
                    7,
                    document.getStatus()
            );

            statement.setInt(
                    8,
                    document.getIdDocument()
            );

            return statement.executeUpdate() == 1;
        }
    }


    // =========================================================
    // DELETE
    // =========================================================

    public boolean delete(
            int documentId
    ) throws SQLException {

        String sql =
                """
                DELETE FROM documents
                WHERE id_document = ?
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(1, documentId);

            return statement.executeUpdate() == 1;
        }
    }


    // =========================================================
    // MAP
    // =========================================================

    private Document mapDocument(
            ResultSet rs
    ) throws SQLException {

        Document document =
                new Document();

        document.setIdDocument(
                rs.getInt("id_document")
        );

        document.setFilename(
                rs.getString("filename")
        );

        document.setSenderId(
                rs.getInt("sender_id")
        );

        document.setRecipientId(
                rs.getInt("recipient_id")
        );

        document.setSensitive(
                rs.getBoolean("sensitive")
        );

        Timestamp timestamp =
                rs.getTimestamp("date_upload");

        if (timestamp != null) {

            document.setDateUpload(
                    timestamp.toLocalDateTime()
            );
        }

        document.setKeyAlias(
                rs.getString("key_alias")
        );

        document.setFilePath(
                rs.getString("file_path")
        );

        document.setFileSize(
                rs.getLong("file_size")
        );

        document.setStatus(
                rs.getString("status")
        );

        return document;
    }


    // =========================================================
    // HELPER TIMESTAMP
    // =========================================================

    private static class LocalDateTimeHelper {

        static void setTimestamp(
                PreparedStatement statement,
                int index,
                java.time.LocalDateTime value
        ) throws SQLException {

            if (value == null) {

                statement.setTimestamp(
                        index,
                        Timestamp.valueOf(
                                java.time.LocalDateTime.now()
                        )
                );

            } else {

                statement.setTimestamp(
                        index,
                        Timestamp.valueOf(value)
                );
            }
        }
    }
}
