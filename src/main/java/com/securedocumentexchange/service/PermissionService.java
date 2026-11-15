package com.securedocumentexchange.service;

import com.securedocumentexchange.dao.PermissionDAO;
import com.securedocumentexchange.model.Permission;

import java.sql.SQLException;
import java.util.List;

public class PermissionService {

    private final PermissionDAO permissionDAO;

    public PermissionService() {
        this.permissionDAO = new PermissionDAO();
    }


    // =========================================================
    // AJOUTER UNE PERMISSION
    // =========================================================

    public int grantPermission(
            Permission permission
    ) throws SQLException {

        return permissionDAO.insert(permission);
    }


    // =========================================================
    // TROUVER UNE PERMISSION
    // =========================================================

    public Permission getPermission(
            int documentId,
            int userId
    ) throws SQLException {

        return permissionDAO.findByDocumentAndUser(
                documentId,
                userId
        );
    }


    // =========================================================
    // VÉRIFIER VIEW
    // =========================================================

    public boolean canUserView(
            int documentId,
            int userId
    ) throws SQLException {

        Permission permission =
                permissionDAO.findByDocumentAndUser(
                        documentId,
                        userId
                );

        return permission != null
                && permission.isCanView();
    }


    // =========================================================
    // VÉRIFIER DOWNLOAD
    // =========================================================

    public boolean canUserDownload(
            int documentId,
            int userId
    ) throws SQLException {

        Permission permission =
                permissionDAO.findByDocumentAndUser(
                        documentId,
                        userId
                );

        return permission != null
                && permission.isCanDownload();
    }


    // =========================================================
    // TOUTES LES PERMISSIONS D'UN DOCUMENT
    // =========================================================

    public List<Permission> getDocumentPermissions(
            int documentId
    ) throws SQLException {

        return permissionDAO.findByDocumentId(
                documentId
        );
    }


    // =========================================================
    // TOUTES LES PERMISSIONS D'UN USER
    // =========================================================

    public List<Permission> getUserPermissions(
            int userId
    ) throws SQLException {

        return permissionDAO.findByUserId(
                userId
        );
    }


    // =========================================================
    // MODIFIER
    // =========================================================

    public boolean updatePermission(
            Permission permission
    ) throws SQLException {

        return permissionDAO.update(permission);
    }


    // =========================================================
    // SUPPRIMER
    // =========================================================

    public boolean revokePermission(
            int permissionId
    ) throws SQLException {

        return permissionDAO.delete(permissionId);
    }
}