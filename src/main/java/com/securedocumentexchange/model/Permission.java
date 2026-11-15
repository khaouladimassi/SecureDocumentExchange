package com.securedocumentexchange.model;

import java.time.LocalDateTime;

public class Permission {

    private int idPermission;

    private int documentId;

    private int userId;

    private boolean canView;

    private boolean canDownload;

    private LocalDateTime grantedAt;


    // =========================================================
    // CONSTRUCTEUR VIDE
    // =========================================================

    public Permission() {
    }


    // =========================================================
    // CONSTRUCTEUR SIMPLE
    // =========================================================

    public Permission(
            int documentId,
            int userId,
            boolean canView,
            boolean canDownload
    ) {

        this.documentId = documentId;
        this.userId = userId;
        this.canView = canView;
        this.canDownload = canDownload;
    }


    // =========================================================
    // CONSTRUCTEUR COMPLET
    // =========================================================

    public Permission(
            int idPermission,
            int documentId,
            int userId,
            boolean canView,
            boolean canDownload,
            LocalDateTime grantedAt
    ) {

        this.idPermission = idPermission;
        this.documentId = documentId;
        this.userId = userId;
        this.canView = canView;
        this.canDownload = canDownload;
        this.grantedAt = grantedAt;
    }


    // =========================================================
    // ID PERMISSION
    // =========================================================

    public int getIdPermission() {
        return idPermission;
    }

    public void setIdPermission(int idPermission) {
        this.idPermission = idPermission;
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
    // USER
    // =========================================================

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }


    // =========================================================
    // VIEW
    // =========================================================

    public boolean isCanView() {
        return canView;
    }

    public void setCanView(boolean canView) {
        this.canView = canView;
    }


    // =========================================================
    // DOWNLOAD
    // =========================================================

    public boolean isCanDownload() {
        return canDownload;
    }

    public void setCanDownload(boolean canDownload) {
        this.canDownload = canDownload;
    }


    // =========================================================
    // DATE
    // =========================================================

    public LocalDateTime getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(LocalDateTime grantedAt) {
        this.grantedAt = grantedAt;
    }


    @Override
    public String toString() {

        return "Permission{" +
                "idPermission=" + idPermission +
                ", documentId=" + documentId +
                ", userId=" + userId +
                ", canView=" + canView +
                ", canDownload=" + canDownload +
                ", grantedAt=" + grantedAt +
                '}';
    }
}