package com.securedocumentexchange.dao;

import com.securedocumentexchange.database.DatabaseConnection;
import com.securedocumentexchange.model.ChatMember;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.util.ArrayList;
import java.util.List;

/**
 * ================================================================
 * CHAT MEMBER DAO
 * ================================================================
 *
 * Gestion des membres des conversations.
 *
 * Responsabilités :
 * - ajouter un membre ;
 * - supprimer un membre ;
 * - vérifier l'appartenance à une conversation ;
 * - récupérer les membres ;
 * - récupérer les identifiants des utilisateurs ;
 * - compter les membres d'une conversation.
 */
public class ChatMemberDAO {

    // =========================================================
    // ADD MEMBER
    // =========================================================

    /**
     * Ajoute un utilisateur à une conversation.
     *
     * Si l'utilisateur est déjà membre, aucune nouvelle ligne
     * n'est créée.
     *
     * @param conversationId identifiant de la conversation
     * @param userId         identifiant de l'utilisateur
     */
    public void addMember(
            int conversationId,
            int userId
    ) throws SQLException {

        String sql =
                "INSERT INTO chat_members "
                        + "(id_conversation,id_user) "
                        + "VALUES (?,?) "
                        + "ON CONFLICT "
                        + "(id_conversation,id_user) "
                        + "DO NOTHING";

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
                    userId
            );

            statement.executeUpdate();
        }
    }

    // =========================================================
    // REMOVE MEMBER
    // =========================================================

    /**
     * Supprime un utilisateur d'une conversation.
     *
     * @param conversationId identifiant de la conversation
     * @param userId         identifiant de l'utilisateur
     * @return true si un membre a été supprimé
     */
    public boolean removeMember(
            int conversationId,
            int userId
    ) throws SQLException {

        String sql =
                "DELETE FROM chat_members "
                        + "WHERE id_conversation=? "
                        + "AND id_user=?";

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
                    userId
            );

            return statement.executeUpdate() > 0;
        }
    }

    // =========================================================
    // CHECK MEMBERSHIP
    // =========================================================

    /**
     * Vérifie si un utilisateur appartient à une conversation.
     *
     * @param conversationId identifiant de la conversation
     * @param userId         identifiant de l'utilisateur
     * @return true si l'utilisateur est membre
     */
    public boolean isMember(
            int conversationId,
            int userId
    ) throws SQLException {

        String sql =
                "SELECT 1 "
                        + "FROM chat_members "
                        + "WHERE id_conversation=? "
                        + "AND id_user=? "
                        + "LIMIT 1";

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
                    userId
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                return resultSet.next();
            }
        }
    }

    // =========================================================
    // GET MEMBERS
    // =========================================================

    /**
     * Récupère les membres d'une conversation.
     *
     * Les membres sont retournés dans l'ordre de leur
     * date d'adhésion.
     *
     * @param conversationId identifiant de la conversation
     * @return liste des membres
     */
    public List<ChatMember> getMembers(
            int conversationId
    ) throws SQLException {

        List<ChatMember> result =
                new ArrayList<>();

        String sql =
                "SELECT "
                        + "m.id_conversation,"
                        + "m.id_user,"
                        + "u.username,"
                        + "m.joined_at "
                        + "FROM chat_members m "
                        + "JOIN users u "
                        + "ON u.id_user=m.id_user "
                        + "WHERE m.id_conversation=? "
                        + "ORDER BY m.joined_at ASC";

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

                while (resultSet.next()) {

                    Timestamp timestamp =
                            resultSet.getTimestamp(
                                    "joined_at"
                            );

                    ChatMember member =
                            new ChatMember(
                                    resultSet.getInt(
                                            "id_conversation"
                                    ),
                                    resultSet.getInt(
                                            "id_user"
                                    ),
                                    resultSet.getString(
                                            "username"
                                    ),
                                    timestamp == null
                                            ? null
                                            : timestamp.toLocalDateTime()
                            );

                    result.add(member);
                }
            }
        }

        return result;
    }

    // =========================================================
    // GET USER IDS
    // =========================================================

    /**
     * Récupère les identifiants des utilisateurs
     * appartenant à une conversation.
     *
     * @param conversationId identifiant de la conversation
     * @return liste des identifiants utilisateurs
     */
    public List<Integer> getUserIds(
            int conversationId
    ) throws SQLException {

        List<Integer> result =
                new ArrayList<>();

        String sql =
                "SELECT id_user "
                        + "FROM chat_members "
                        + "WHERE id_conversation=? "
                        + "ORDER BY joined_at ASC";

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

                while (resultSet.next()) {

                    result.add(
                            resultSet.getInt(1)
                    );
                }
            }
        }

        return result;
    }

    // =========================================================
    // COUNT MEMBERS
    // =========================================================

    /**
     * Compte le nombre de membres d'une conversation.
     *
     * @param conversationId identifiant de la conversation
     * @return nombre de membres
     */
    public int countMembers(
            int conversationId
    ) throws SQLException {

        String sql =
                "SELECT COUNT(*) "
                        + "FROM chat_members "
                        + "WHERE id_conversation=?";

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

                return resultSet.next()
                        ? resultSet.getInt(1)
                        : 0;
            }
        }
    }
}