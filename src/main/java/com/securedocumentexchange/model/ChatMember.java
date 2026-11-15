package com.securedocumentexchange.model;

import java.time.LocalDateTime;

/**
 * ================================================================
 * CHAT MEMBER
 * ================================================================
 *
 * Représente un utilisateur membre d'une conversation.
 *
 * Un membre est identifié par :
 * - l'identifiant de la conversation ;
 * - l'identifiant de l'utilisateur.
 *
 * Les informations supplémentaires (username, joinedAt)
 * peuvent être utilisées lors de l'affichage ou de la récupération
 * des données depuis la base de données.
 */
public class ChatMember {

    // =========================================================
    // ATTRIBUTES
    // =========================================================

    private int idConversation;

    private int userId;

    private String username;

    private LocalDateTime joinedAt;

    // =========================================================
    // CONSTRUCTORS
    // =========================================================

    /**
     * Constructeur vide.
     */
    public ChatMember() {
    }

    /**
     * Constructeur principal avec les identifiants.
     *
     * @param idConversation identifiant de la conversation
     * @param userId         identifiant de l'utilisateur
     */
    public ChatMember(
            int idConversation,
            int userId
    ) {

        this.idConversation = idConversation;
        this.userId = userId;
    }

    /**
     * Constructeur complet.
     *
     * @param idConversation identifiant de la conversation
     * @param userId         identifiant de l'utilisateur
     * @param username       nom d'utilisateur
     * @param joinedAt       date d'adhésion à la conversation
     */
    public ChatMember(
            int idConversation,
            int userId,
            String username,
            LocalDateTime joinedAt
    ) {

        this.idConversation = idConversation;
        this.userId = userId;
        this.username = username;
        this.joinedAt = joinedAt;
    }

    // =========================================================
    // GETTERS / SETTERS
    // =========================================================

    public int getIdConversation() {

        return idConversation;
    }

    public void setIdConversation(
            int idConversation
    ) {

        this.idConversation = idConversation;
    }

    public int getUserId() {

        return userId;
    }

    public void setUserId(
            int userId
    ) {

        this.userId = userId;
    }

    public String getUsername() {

        return username;
    }

    public void setUsername(
            String username
    ) {

        this.username = username;
    }

    public LocalDateTime getJoinedAt() {

        return joinedAt;
    }

    public void setJoinedAt(
            LocalDateTime joinedAt
    ) {

        this.joinedAt = joinedAt;
    }

    // =========================================================
    // TO STRING
    // =========================================================

    @Override
    public String toString() {

        return username != null
                ? username
                : "User #" + userId;
    }
}