package com.securedocumentexchange.model;

import java.time.LocalDateTime;

public class Log {

    private int idLog;

    private int userId;

    private int documentId;

    private String action;

    private String level;

    private String description;

    private String ipAddress;

    private LocalDateTime dateAction;


    // =========================================================
    // CONSTRUCTEUR VIDE
    // =========================================================

    public Log() {
    }


    // =========================================================
    // CONSTRUCTEUR SIMPLE
    // =========================================================

    public Log(
            int userId,
            String action,
            String level
    ) {

        this.userId = userId;
        this.action = action;
        this.level = level;
        this.dateAction = LocalDateTime.now();
    }


    // =========================================================
    // CONSTRUCTEUR COMPLET
    // =========================================================

    public Log(
            int idLog,
            int userId,
            int documentId,
            String action,
            String level,
            String description,
            String ipAddress,
            LocalDateTime dateAction
    ) {

        this.idLog = idLog;
        this.userId = userId;
        this.documentId = documentId;
        this.action = action;
        this.level = level;
        this.description = description;
        this.ipAddress = ipAddress;
        this.dateAction = dateAction;
    }


    // =========================================================
    // ID
    // =========================================================

    public int getIdLog() {
        return idLog;
    }

    public void setIdLog(int idLog) {
        this.idLog = idLog;
    }


    // =========================================================
    // USER
    // =========================================================

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }


    // =========================================================
    // DOCUMENT
    // =========================================================

    public int getDocumentId() {
        return documentId;
    }

    public void setDocumentId(int documentId) {
        this.documentId = documentId;
    }


    // =========================================================
    // ACTION
    // =========================================================

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }


    // =========================================================
    // LEVEL
    // =========================================================

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }


    // =========================================================
    // DESCRIPTION
    // =========================================================

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    // =========================================================
    // IP
    // =========================================================

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }


    // =========================================================
    // DATE
    // =========================================================

    public LocalDateTime getDateAction() {
        return dateAction;
    }

    public void setDateAction(LocalDateTime dateAction) {
        this.dateAction = dateAction;
    }


    @Override
    public String toString() {

        return "Log{" +
                "idLog=" + idLog +
                ", userId=" + userId +
                ", documentId=" + documentId +
                ", action='" + action + '\'' +
                ", level='" + level + '\'' +
                ", description='" + description + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", dateAction=" + dateAction +
                '}';
    }
}