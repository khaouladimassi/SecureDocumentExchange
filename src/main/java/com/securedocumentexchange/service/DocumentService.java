package com.securedocumentexchange.service;

import com.securedocumentexchange.dao.DocumentDAO;
import com.securedocumentexchange.dao.UserDAO;
import com.securedocumentexchange.model.Document;
import com.securedocumentexchange.model.User;

import java.sql.SQLException;
import java.util.List;

public class DocumentService {

    private final DocumentDAO documentDAO;
    private final UserDAO userDAO;

    public DocumentService() {
        this.documentDAO = new DocumentDAO();
        this.userDAO = new UserDAO();
    }

    // =========================================================
    // UPLOAD
    // =========================================================

    public int uploadDocument(
            Document document
    ) throws SQLException {

        validateDocument(document);

        User sender =
                userDAO.findById(
                        document.getSenderId()
                );

        if (sender == null) {

            throw new SQLException(
                    "L'utilisateur expéditeur n'existe pas : "
                            + document.getSenderId()
            );
        }

        return documentDAO.insert(document);
    }


    // =========================================================
    // FIND BY ID
    // =========================================================

    public Document getDocument(
            int documentId
    ) throws SQLException {

        if (documentId <= 0) {

            throw new IllegalArgumentException(
                    "ID document invalide."
            );
        }

        return documentDAO.findById(
                documentId
        );
    }


    // =========================================================
    // FIND ALL
    // =========================================================

    public List<Document> getAllDocuments()
            throws SQLException {

        return documentDAO.findAll();
    }


    // =========================================================
    // DOCUMENTS USER
    // =========================================================

    public List<Document> getUserDocuments(
            int userId
    ) throws SQLException {

        if (userId <= 0) {

            throw new IllegalArgumentException(
                    "ID utilisateur invalide."
            );
        }

        return documentDAO.findBySenderId(
                userId
        );
    }


    // =========================================================
    // DOCUMENTS SENSIBLES
    // =========================================================

    public List<Document> getSensitiveDocuments()
            throws SQLException {

        return documentDAO.findSensitiveDocuments();
    }


    // =========================================================
    // UPDATE
    // =========================================================

    public boolean updateDocument(
            Document document
    ) throws SQLException {

        validateDocument(document);

        User sender =
                userDAO.findById(
                        document.getSenderId()
                );

        if (sender == null) {

            throw new SQLException(
                    "L'utilisateur expéditeur n'existe pas : "
                            + document.getSenderId()
            );
        }

        return documentDAO.update(
                document
        );
    }


    // =========================================================
    // DELETE
    // =========================================================

    public boolean deleteDocument(
            int documentId
    ) throws SQLException {

        if (documentId <= 0) {

            throw new IllegalArgumentException(
                    "ID document invalide."
            );
        }

        return documentDAO.delete(
                documentId
        );
    }


    // =========================================================
    // VALIDATION
    // =========================================================

    private void validateDocument(
            Document document
    ) {

        if (document == null) {

            throw new IllegalArgumentException(
                    "Document null."
            );
        }

        if (document.getSenderId() <= 0) {

            throw new IllegalArgumentException(
                    "senderId invalide : "
                            + document.getSenderId()
            );
        }

        if (document.getFilename() == null
                || document.getFilename().isBlank()) {

            throw new IllegalArgumentException(
                    "Filename invalide."
            );
        }
    }
}