package com.securedocumentexchange.dao;

import com.securedocumentexchange.database.DatabaseConnection;
import com.securedocumentexchange.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    // =========================================================
    // VERIFIER SI USERNAME EXISTE
    // =========================================================

    public boolean usernameExists(String username)
            throws SQLException {

        String sql =
                "SELECT id_user FROM users WHERE username = ?";

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(1, username);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                return resultSet.next();
            }
        }
    }


    // =========================================================
    // CREER UTILISATEUR
    // =========================================================

    public boolean createUser(User user)
            throws SQLException {

        if (user == null) {
            throw new IllegalArgumentException(
                    "Utilisateur null."
            );
        }

        String sql =
                """
                INSERT INTO users
                (username, password, role, status)
                VALUES (?, ?, ?, ?)
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(
                    1,
                    user.getUsername()
            );

            statement.setString(
                    2,
                    user.getPassword()
            );

            statement.setString(
                    3,
                    user.getRole()
            );

            statement.setString(
                    4,
                    user.getStatus()
            );

            return statement.executeUpdate() == 1;
        }
    }


    // =========================================================
    // RECHERCHER USER PAR ID
    // =========================================================

// =========================================================
// RECHERCHER USER PAR ID
// =========================================================

    public User findById(int userId) throws SQLException {

        if (userId <= 0) {
            return null;
        }

        String sql =
                """
                SELECT
                    id_user,
                    username,
                    password,
                    role,
                    status
                FROM users
                WHERE id_user = ?
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(1, userId);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (resultSet.next()) {
                    return mapUser(resultSet);
                }
            }
        }

        return null;
    }


    // =========================================================
    // RECHERCHER USER PAR USERNAME
    // =========================================================

    public User findByUsername(
            String username
    ) throws SQLException {

        if (username == null
                || username.isBlank()) {

            return null;
        }

        String sql =
                """
                SELECT
                    id_user,
                    username,
                    password,
                    role,
                    status
                FROM users
                WHERE username = ?
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(
                    1,
                    username.trim()
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (resultSet.next()) {

                    return mapUser(
                            resultSet
                    );
                }
            }
        }

        return null;
    }


    // =========================================================
    // RECHERCHE POUR AUTHENTIFICATION
    // =========================================================

    public User findForLogin(
            String username
    ) throws SQLException {

        return findByUsername(
                username
        );
    }


    // =========================================================
    // MODIFIER LE STATUT
    // =========================================================

    public boolean updateStatus(
            int userId,
            String status
    ) throws SQLException {

        String sql =
                """
                UPDATE users
                SET status = ?
                WHERE id_user = ?
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(
                    1,
                    status
            );

            statement.setInt(
                    2,
                    userId
            );

            return statement.executeUpdate() == 1;
        }
    }


    // =========================================================
    // UTILISATEURS EN ATTENTE
    // =========================================================

    public List<User> findPendingUsers()
            throws SQLException {

        List<User> users =
                new ArrayList<>();

        String sql =
                """
                SELECT
                    id_user,
                    username,
                    password,
                    role,
                    status
                FROM users
                WHERE status = 'PENDING'
                ORDER BY id_user
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet resultSet =
                        statement.executeQuery()
        ) {

            while (resultSet.next()) {

                users.add(
                        mapUser(resultSet)
                );
            }
        }

        return users;
    }


    // =========================================================
    // TOUS LES UTILISATEURS
    // =========================================================

    public List<User> findAll()
            throws SQLException {

        List<User> users =
                new ArrayList<>();

        String sql =
                """
                SELECT
                    id_user,
                    username,
                    password,
                    role,
                    status
                FROM users
                ORDER BY id_user
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet resultSet =
                        statement.executeQuery()
        ) {

            while (resultSet.next()) {

                users.add(
                        mapUser(resultSet)
                );
            }
        }

        return users;
    }


    // =========================================================
    // SUPPRIMER UTILISATEUR
    // =========================================================

    public boolean delete(
            int userId
    ) throws SQLException {

        String sql =
                """
                DELETE FROM users
                WHERE id_user = ?
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

            return statement.executeUpdate() == 1;
        }
    }


    // =========================================================
    // CONVERSION RESULTSET -> USER
    // =========================================================

    private User mapUser(
            ResultSet resultSet
    ) throws SQLException {

        return new User(
                resultSet.getInt("id_user"),
                resultSet.getString("username"),
                resultSet.getString("password"),
                resultSet.getString("role"),
                resultSet.getString("status")
        );
    }


}