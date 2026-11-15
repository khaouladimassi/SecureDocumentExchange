package com.securedocumentexchange.dao;

import com.securedocumentexchange.database.DatabaseConnection;
import com.securedocumentexchange.model.ChatConversation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ================================================================
 * CHAT CONVERSATION DAO
 * ================================================================
 *
 * Gestion des conversations de chat dans PostgreSQL.
 *
 * Responsabilités :
 *
 * - création des conversations privées ;
 * - création des groupes ;
 * - recherche des conversations ;
 * - récupération des conversations d'un utilisateur ;
 * - gestion des membres ;
 * - vérification des membres ;
 * - recherche du premier administrateur.
 */
public class ChatConversationDAO {

    // =========================================================
    // DEPENDENCIES
    // =========================================================

    private final ChatMemberDAO memberDAO =
            new ChatMemberDAO();

    // =========================================================
    // CREATE - COMPATIBILITÉ CHAT SERVICE
    // =========================================================

    /**
     * Crée une conversation à partir d'un objet
     * ChatConversation.
     *
     * Cette méthode est utilisée par ChatService.
     */
    public int create(
            ChatConversation conversation
    ) throws SQLException {

        if (conversation == null) {

            throw new IllegalArgumentException(
                    "La conversation ne peut pas être null."
            );
        }

        String type =
                conversation.getType();

        if (type == null
                || (
                !type.equalsIgnoreCase("PRIVATE")
                        &&
                        !type.equalsIgnoreCase("GROUP")
        )) {

            throw new SQLException(
                    "Type de conversation invalide."
            );
        }

        int createdBy =
                conversation.getCreatedBy();

        if (createdBy <= 0) {

            throw new SQLException(
                    "Créateur de conversation invalide."
            );
        }

        String sql =
                "INSERT INTO chat_conversations "
                        + "(name, type, created_by) "
                        + "VALUES (?, ?, ?) "
                        + "RETURNING id_conversation";

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(
                    1,
                    conversation.getName()
            );

            statement.setString(
                    2,
                    type.toUpperCase()
            );

            statement.setInt(
                    3,
                    createdBy
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (resultSet.next()) {

                    int id =
                            resultSet.getInt(
                                    "id_conversation"
                            );

                    /*
                     * Mettre à jour l'ID de l'objet
                     * si le modèle possède un setter.
                     */
                    try {

                        conversation.setIdConversation(
                                id
                        );

                    } catch (Exception ignored) {
                        // Compatibilité avec différents modèles.
                    }

                    return id;
                }
            }
        }

        throw new SQLException(
                "Impossible de créer la conversation."
        );
    }

    // =========================================================
    // CREATE CONVERSATION
    // =========================================================

    /**
     * Crée une nouvelle conversation.
     *
     * @param name      nom de la conversation
     * @param type      PRIVATE ou GROUP
     * @param createdBy identifiant du créateur
     * @return identifiant de la conversation
     */
    public int createConversation(
            String name,
            String type,
            int createdBy
    ) throws SQLException {

        if (type == null
                || (
                !type.equalsIgnoreCase("PRIVATE")
                        &&
                        !type.equalsIgnoreCase("GROUP")
        )) {

            throw new SQLException(
                    "Type de conversation invalide."
            );
        }

        if (createdBy <= 0) {

            throw new SQLException(
                    "Identifiant du créateur invalide."
            );
        }

        String sql =
                "INSERT INTO chat_conversations "
                        + "(name, type, created_by) "
                        + "VALUES (?, ?, ?) "
                        + "RETURNING id_conversation";

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(
                    1,
                    name
            );

            statement.setString(
                    2,
                    type.toUpperCase()
            );

            statement.setInt(
                    3,
                    createdBy
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (resultSet.next()) {

                    return resultSet.getInt(
                            "id_conversation"
                    );
                }
            }
        }

        throw new SQLException(
                "Création de conversation impossible."
        );
    }

    // =========================================================
    // FIND PRIVATE CONVERSATION
    // =========================================================

    /**
     * Recherche une conversation privée existante
     * entre deux utilisateurs.
     *
     * IMPORTANT :
     *
     * Une conversation privée doit contenir exactement
     * les deux utilisateurs.
     */
    public ChatConversation findPrivateConversation(
            int userA,
            int userB
    ) throws SQLException {

        if (userA <= 0
                || userB <= 0
                || userA == userB) {

            return null;
        }

        String sql = """
                SELECT
                    c.id_conversation,
                    c.name,
                    c.type,
                    c.created_by,
                    c.created_at
                FROM chat_conversations c
                JOIN chat_members m1
                    ON m1.id_conversation = c.id_conversation
                   AND m1.id_user = ?
                JOIN chat_members m2
                    ON m2.id_conversation = c.id_conversation
                   AND m2.id_user = ?
                WHERE UPPER(c.type) = 'PRIVATE'
                  AND (
                      SELECT COUNT(*)
                      FROM chat_members x
                      WHERE x.id_conversation = c.id_conversation
                  ) = 2
                LIMIT 1
                """;

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(
                    1,
                    userA
            );

            statement.setInt(
                    2,
                    userB
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (resultSet.next()) {

                    return map(resultSet);
                }
            }
        }

        return null;
    }

    // =========================================================
    // GET OR CREATE PRIVATE CONVERSATION
    // =========================================================

    /**
     * Récupère une conversation privée existante
     * ou la crée si elle n'existe pas.
     *
     * @return ID de la conversation
     */
    public int getOrCreatePrivateConversation(
            int userA,
            int userB,
            String displayName
    ) throws SQLException {

        if (userA <= 0
                || userB <= 0
                || userA == userB) {

            throw new SQLException(
                    "Participants privés invalides."
            );
        }

        // -----------------------------------------------------
        // RECHERCHE
        // -----------------------------------------------------

        ChatConversation existing =
                findPrivateConversation(
                        userA,
                        userB
                );

        if (existing != null) {

            return existing.getIdConversation();
        }

        // -----------------------------------------------------
        // CRÉATION
        // -----------------------------------------------------

        synchronized (this) {

            // Double vérification pour éviter
            // les doublons en cas de concurrence.

            existing =
                    findPrivateConversation(
                            userA,
                            userB
                    );

            if (existing != null) {

                return existing.getIdConversation();
            }

            String sql =
                    "INSERT INTO chat_conversations "
                            + "(name, type, created_by) "
                            + "VALUES (?, 'PRIVATE', ?) "
                            + "RETURNING id_conversation";

            try (
                    Connection connection =
                            DatabaseConnection.getConnection();

                    PreparedStatement statement =
                            connection.prepareStatement(sql)
            ) {

                statement.setString(
                        1,
                        displayName == null
                                || displayName.isBlank()
                                ? "Conversation privée"
                                : displayName
                );

                statement.setInt(
                        2,
                        userA
                );

                int conversationId;

                try (
                        ResultSet resultSet =
                                statement.executeQuery()
                ) {

                    if (!resultSet.next()) {

                        throw new SQLException(
                                "Création conversation impossible."
                        );
                    }

                    conversationId =
                            resultSet.getInt(
                                    "id_conversation"
                            );
                }

                // -------------------------------------------------
                // AJOUT DES MEMBRES
                // -------------------------------------------------

                memberDAO.addMember(
                        conversationId,
                        userA
                );

                memberDAO.addMember(
                        conversationId,
                        userB
                );

                return conversationId;
            }
        }
    }

    // =========================================================
    // FIND FIRST ADMIN
    // =========================================================

    /**
     * Recherche le premier administrateur enregistré.
     *
     * @return ID de l'administrateur ou 0
     */
    public int findFirstAdminId()
            throws SQLException {

        String sql =
                "SELECT id_user "
                        + "FROM users "
                        + "WHERE UPPER(role) = 'ADMIN' "
                        + "ORDER BY id_user ASC "
                        + "LIMIT 1";

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet resultSet =
                        statement.executeQuery()
        ) {

            if (resultSet.next()) {

                return resultSet.getInt(
                        "id_user"
                );
            }
        }

        return 0;
    }

    // =========================================================
    // PRIVATE CONVERSATION WITH FIRST ADMIN
    // =========================================================

    /**
     * Récupère ou crée une conversation privée
     * entre l'utilisateur et le premier administrateur.
     */
    public int getOrCreatePrivateConversationWithFirstAdmin(
            int userId,
            String userDisplayName
    ) throws SQLException {

        if (userId <= 0) {

            throw new SQLException(
                    "Identifiant utilisateur invalide."
            );
        }

        int adminId =
                findFirstAdminId();

        if (adminId <= 0) {

            throw new SQLException(
                    "Aucun administrateur trouvé."
            );
        }

        if (adminId == userId) {

            throw new SQLException(
                    "L'utilisateur connecté est lui-même administrateur."
            );
        }

        return getOrCreatePrivateConversation(
                adminId,
                userId,
                userDisplayName
        );
    }

    // =========================================================
    // CREATE GROUP
    // =========================================================

    /**
     * Crée une conversation de groupe.
     */
    public int createGroup(
            String name,
            int createdBy,
            List<Integer> userIds
    ) throws SQLException {

        if (name == null
                || name.isBlank()) {

            throw new SQLException(
                    "Nom du groupe invalide."
            );
        }

        if (createdBy <= 0) {

            throw new SQLException(
                    "Créateur invalide."
            );
        }

        int id =
                createConversation(
                        name.trim(),
                        "GROUP",
                        createdBy
                );

        // -----------------------------------------------------
        // AJOUT CREATEUR
        // -----------------------------------------------------

        memberDAO.addMember(
                id,
                createdBy
        );

        // -----------------------------------------------------
        // AJOUT DES AUTRES MEMBRES
        // -----------------------------------------------------

        if (userIds != null) {

            for (Integer userId : userIds) {

                if (userId == null) {
                    continue;
                }

                if (userId <= 0) {
                    continue;
                }

                if (userId == createdBy) {
                    continue;
                }

                memberDAO.addMember(
                        id,
                        userId
                );
            }
        }

        return id;
    }

    // =========================================================
    // FIND BY ID
    // =========================================================

    /**
     * Recherche une conversation par son ID.
     */
    public ChatConversation findById(
            int id
    ) throws SQLException {

        if (id <= 0) {

            return null;
        }

        String sql =
                "SELECT "
                        + "id_conversation, "
                        + "name, "
                        + "type, "
                        + "created_by, "
                        + "created_at "
                        + "FROM chat_conversations "
                        + "WHERE id_conversation = ?";

        try (
                Connection connection =
                        DatabaseConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(
                    1,
                    id
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (resultSet.next()) {

                    return map(
                            resultSet
                    );
                }
            }
        }

        return null;
    }

    // =========================================================
    // FIND BY USER ID
    // =========================================================

    /**
     * Alias utilisé par ChatService.
     *
     * Permet de récupérer les conversations
     * d'un utilisateur.
     */
    public List<ChatConversation> findByUserId(
            int userId
    ) throws SQLException {

        return getForUser(
                userId
        );
    }

    // =========================================================
    // GET FOR USER
    // =========================================================

    /**
     * Récupère toutes les conversations auxquelles
     * appartient l'utilisateur.
     */
    public List<ChatConversation> getForUser(
            int userId
    ) throws SQLException {

        if (userId <= 0) {

            throw new IllegalArgumentException(
                    "Identifiant utilisateur invalide."
            );
        }

        List<ChatConversation> result =
                new ArrayList<>();

        String sql =
                "SELECT "
                        + "c.id_conversation, "
                        + "c.name, "
                        + "c.type, "
                        + "c.created_by, "
                        + "c.created_at "
                        + "FROM chat_conversations c "
                        + "JOIN chat_members m "
                        + "ON m.id_conversation = c.id_conversation "
                        + "WHERE m.id_user = ? "
                        + "ORDER BY c.created_at DESC";

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
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                while (resultSet.next()) {

                    result.add(
                            map(
                                    resultSet
                            )
                    );
                }
            }
        }

        return result;
    }

    // =========================================================
    // ADD MEMBER
    // =========================================================

    public void addMember(
            int conversationId,
            int userId
    ) throws SQLException {

        if (conversationId <= 0
                || userId <= 0) {

            throw new IllegalArgumentException(
                    "Conversation ou utilisateur invalide."
            );
        }

        memberDAO.addMember(
                conversationId,
                userId
        );
    }

    // =========================================================
    // IS MEMBER
    // =========================================================

    public boolean isMember(
            int conversationId,
            int userId
    ) throws SQLException {

        if (conversationId <= 0
                || userId <= 0) {

            return false;
        }

        return memberDAO.isMember(
                conversationId,
                userId
        );
    }

    // =========================================================
    // GET MEMBER IDS
    // =========================================================

    public List<Integer> getMemberIds(
            int conversationId
    ) throws SQLException {

        return memberDAO.getUserIds(
                conversationId
        );
    }

    // =========================================================
    // GET MEMBERS
    // =========================================================

    public List<com.securedocumentexchange.model.ChatMember> getMembers(
            int conversationId
    ) throws SQLException {

        return memberDAO.getMembers(
                conversationId
        );
    }

    // =========================================================
    // MAP RESULT SET
    // =========================================================

    /**
     * Transforme une ligne SQL en ChatConversation.
     */
    private ChatConversation map(
            ResultSet resultSet
    ) throws SQLException {

        Timestamp timestamp =
                resultSet.getTimestamp(
                        "created_at"
                );

        LocalDateTime createdAt =
                timestamp == null
                        ? null
                        : timestamp.toLocalDateTime();

        return new ChatConversation(
                resultSet.getInt(
                        "id_conversation"
                ),
                resultSet.getString(
                        "name"
                ),
                resultSet.getString(
                        "type"
                ),
                resultSet.getInt(
                        "created_by"
                ),
                createdAt
        );
    }
}