package com.securedocumentexchange.service;

import com.securedocumentexchange.dao.LogDAO;
import com.securedocumentexchange.model.Log;

import java.sql.SQLException;

public class LogService {

    private final LogDAO logDAO;

    public LogService() {
        this.logDAO = new LogDAO();
    }

    // =========================================================
    // LOG INFO SIMPLE
    // =========================================================

    public void addInfoLog(
            int userId,
            String action
    ) throws SQLException {

        Log log = new Log(
                userId,
                action,
                "INFO"
        );

        logDAO.insert(log);
    }


    // =========================================================
    // LOG ERROR SIMPLE
    // =========================================================

    public void addErrorLog(
            int userId,
            String action
    ) throws SQLException {

        Log log = new Log(
                userId,
                action,
                "ERROR"
        );

        logDAO.insert(log);
    }


    // =========================================================
    // LOG AVEC DOCUMENT
    // =========================================================

    public void addInfoLog(
            int userId,
            int documentId,
            String action,
            String description,
            String ipAddress
    ) throws SQLException {

        Log log = new Log();

        log.setUserId(userId);
        log.setDocumentId(documentId);
        log.setAction(action);
        log.setLevel("INFO");
        log.setDescription(description);
        log.setIpAddress(ipAddress);

        logDAO.insert(log);
    }


    // =========================================================
    // LOG ERROR AVEC DOCUMENT
    // =========================================================

    public void addErrorLog(
            int userId,
            int documentId,
            String action,
            String description,
            String ipAddress
    ) throws SQLException {

        Log log = new Log();

        log.setUserId(userId);
        log.setDocumentId(documentId);
        log.setAction(action);
        log.setLevel("ERROR");
        log.setDescription(description);
        log.setIpAddress(ipAddress);

        logDAO.insert(log);
    }
}