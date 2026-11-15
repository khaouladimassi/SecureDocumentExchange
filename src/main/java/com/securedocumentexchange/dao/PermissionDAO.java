package com.securedocumentexchange.dao;

import com.securedocumentexchange.database.DatabaseConnection;
import com.securedocumentexchange.model.Permission;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PermissionDAO {

    // =========================================================
    // INSERT
    // =========================================================

    public int insert(
            Permission permission
    ) throws SQLException {

        validatePermission(permission);

        String sql =
                """
                INSERT INTO permissions
                (
                    user_id,
                    document_id,
                    can_view,
                    can_download,
                    granted_at
                )
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                RETURNING id_permission
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(
                    1,
                    permission.getUserId()
            );

            statement.setInt(
                    2,
                    permission.getDocumentId()
            );

            statement.setBoolean(
                    3,
                    permission.isCanView()
            );

            statement.setBoolean(
                    4,
                    permission.isCanDownload()
            );

            try (
                    ResultSet rs =
                            statement.executeQuery()
            ) {

                if (rs.next()) {

                    int id =
                            rs.getInt("id_permission");

                    permission.setIdPermission(id);

                    return id;
                }
            }
        }

        throw new SQLException(
                "Impossible de créer la permission."
        );
    }


    // =========================================================
    // FIND BY DOCUMENT + USER
    // =========================================================

    public Permission findByDocumentAndUser(
            int documentId,
            int userId
    ) throws SQLException {

        String sql =
                """
                SELECT
                    id_permission,
                    user_id,
                    document_id,
                    can_view,
                    can_download,
                    granted_at
                FROM permissions
                WHERE document_id = ?
                  AND user_id = ?
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(
                    1,
                    documentId
            );

            statement.setInt(
                    2,
                    userId
            );

            try (
                    ResultSet rs =
                            statement.executeQuery()
            ) {

                if (rs.next()) {

                    return mapPermission(rs);
                }
            }
        }

        return null;
    }


    // =========================================================
    // FIND BY DOCUMENT
    // =========================================================

    public List<Permission> findByDocumentId(
            int documentId
    ) throws SQLException {

        List<Permission> permissions =
                new ArrayList<>();

        String sql =
                """
                SELECT
                    id_permission,
                    user_id,
                    document_id,
                    can_view,
                    can_download,
                    granted_at
                FROM permissions
                WHERE document_id = ?
                ORDER BY id_permission
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(
                    1,
                    documentId
            );

            try (
                    ResultSet rs =
                            statement.executeQuery()
            ) {

                while (rs.next()) {

                    permissions.add(
                            mapPermission(rs)
                    );
                }
            }
        }

        return permissions;
    }


    // =========================================================
    // FIND BY USER
    // =========================================================

    public List<Permission> findByUserId(
            int userId
    ) throws SQLException {

        List<Permission> permissions =
                new ArrayList<>();

        String sql =
                """
                SELECT
                    id_permission,
                    user_id,
                    document_id,
                    can_view,
                    can_download,
                    granted_at
                FROM permissions
                WHERE user_id = ?
                ORDER BY id_permission
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(
                    1,
                    userId
            );

            try (
                    ResultSet rs =
                            statement.executeQuery()
            ) {

                while (rs.next()) {

                    permissions.add(
                            mapPermission(rs)
                    );
                }
            }
        }

        return permissions;
    }


    // =========================================================
    // UPDATE
    // =========================================================

    public boolean update(
            Permission permission
    ) throws SQLException {

        if (permission == null) {

            throw new IllegalArgumentException(
                    "Permission null."
            );
        }

        if (permission.getIdPermission() <= 0) {

            throw new SQLException(
                    "id_permission invalide : "
                            + permission.getIdPermission()
            );
        }

        if (permission.getUserId() <= 0) {

            throw new SQLException(
                    "userId invalide : "
                            + permission.getUserId()
            );
        }

        if (permission.getDocumentId() <= 0) {

            throw new SQLException(
                    "documentId invalide : "
                            + permission.getDocumentId()
            );
        }

        String sql =
                """
                UPDATE permissions
                SET
                    user_id = ?,
                    document_id = ?,
                    can_view = ?,
                    can_download = ?
                WHERE id_permission = ?
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(
                    1,
                    permission.getUserId()
            );

            statement.setInt(
                    2,
                    permission.getDocumentId()
            );

            statement.setBoolean(
                    3,
                    permission.isCanView()
            );

            statement.setBoolean(
                    4,
                    permission.isCanDownload()
            );

            statement.setInt(
                    5,
                    permission.getIdPermission()
            );

            return statement.executeUpdate() > 0;
        }
    }


    // =========================================================
    // DELETE
    // =========================================================

    public boolean delete(
            int permissionId
    ) throws SQLException {

        if (permissionId <= 0) {

            throw new IllegalArgumentException(
                    "id_permission invalide : "
                            + permissionId
            );
        }

        String sql =
                """
                DELETE FROM permissions
                WHERE id_permission = ?
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(
                    1,
                    permissionId
            );

            return statement.executeUpdate() > 0;
        }
    }


    // =========================================================
    // MAPPING
    // =========================================================

    private Permission mapPermission(
            ResultSet rs
    ) throws SQLException {

        Permission permission =
                new Permission();

        permission.setIdPermission(
                rs.getInt("id_permission")
        );

        permission.setUserId(
                rs.getInt("user_id")
        );

        permission.setDocumentId(
                rs.getInt("document_id")
        );

        permission.setCanView(
                rs.getBoolean("can_view")
        );

        permission.setCanDownload(
                rs.getBoolean("can_download")
        );

        return permission;
    }


    // =========================================================
    // VALIDATION
    // =========================================================

    private void validatePermission(
            Permission permission
    ) throws SQLException {

        if (permission == null) {

            throw new IllegalArgumentException(
                    "Permission null."
            );
        }

        if (permission.getUserId() <= 0) {

            throw new SQLException(
                    "userId invalide : "
                            + permission.getUserId()
            );
        }

        if (permission.getDocumentId() <= 0) {

            throw new SQLException(
                    "documentId invalide : "
                            + permission.getDocumentId()
            );
        }
    }
}