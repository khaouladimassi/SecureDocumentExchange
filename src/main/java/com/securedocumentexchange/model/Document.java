package com.securedocumentexchange.model;

import java.time.LocalDateTime;

public class Document {

    private int idDocument;

    private String filename;

    private int senderId;

    private int recipientId;

    private boolean sensitive;

    private LocalDateTime dateUpload;

    private String keyAlias;

    private String filePath;

    private long fileSize;

    private String status;


    // =========================================================
    // CONSTRUCTEUR VIDE
    // =========================================================

    public Document() {
    }


    // =========================================================
    // CONSTRUCTEUR SIMPLE
    // =========================================================

    public Document(
            String filename,
            int senderId,
            int recipientId,
            boolean sensitive
    ) {

        this.filename = filename;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.sensitive = sensitive;
        this.status = "AVAILABLE";
    }


    // =========================================================
    // CONSTRUCTEUR COMPLET
    // =========================================================

    public Document(
            int idDocument,
            String filename,
            int senderId,
            int recipientId,
            boolean sensitive,
            LocalDateTime dateUpload,
            String keyAlias,
            String filePath,
            long fileSize,
            String status
    ) {

        this.idDocument = idDocument;
        this.filename = filename;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.sensitive = sensitive;
        this.dateUpload = dateUpload;
        this.keyAlias = keyAlias;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.status = status;
    }


    // =========================================================
    // ID
    // =========================================================

    public int getIdDocument() {
        return idDocument;
    }

    public void setIdDocument(int idDocument) {
        this.idDocument = idDocument;
    }


    // =========================================================
    // FILENAME
    // =========================================================

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }


    // =========================================================
    // SENDER
    // =========================================================

    public int getSenderId() {
        return senderId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }


    // =========================================================
    // RECIPIENT
    // =========================================================

    public int getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(int recipientId) {
        this.recipientId = recipientId;
    }


    // =========================================================
    // SENSITIVE
    // =========================================================

    public boolean isSensitive() {
        return sensitive;
    }

    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }


    // =========================================================
    // DATE
    // =========================================================

    public LocalDateTime getDateUpload() {
        return dateUpload;
    }

    public void setDateUpload(LocalDateTime dateUpload) {
        this.dateUpload = dateUpload;
    }


    // =========================================================
    // KEY ALIAS
    // =========================================================

    public String getKeyAlias() {
        return keyAlias;
    }

    public void setKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }


    // =========================================================
    // FILE PATH
    // =========================================================

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }


    // =========================================================
    // FILE SIZE
    // =========================================================

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }


    // =========================================================
    // STATUS
    // =========================================================

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }


    @Override
    public String toString() {

        return "Document{" +
                "idDocument=" + idDocument +
                ", filename='" + filename + '\'' +
                ", senderId=" + senderId +
                ", recipientId=" + recipientId +
                ", sensitive=" + sensitive +
                ", dateUpload=" + dateUpload +
                ", keyAlias='" + keyAlias + '\'' +
                ", filePath='" + filePath + '\'' +
                ", fileSize=" + fileSize +
                ", status='" + status + '\'' +
                '}';
    }
}