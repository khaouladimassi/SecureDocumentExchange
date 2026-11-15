package com.securedocumentexchange.model;

import java.time.LocalDateTime;

/**
 * ================================================================
 * CHAT CONVERSATION
 * ================================================================
 *
 * Représente une conversation dans le système de chat.
 *
 * Types :
 *
 * - PRIVATE : conversation privée entre deux utilisateurs
 * - GROUP   : conversation de groupe
 */
public class ChatConversation {

    // =========================================================
    // CONSTANTES
    // =========================================================

    public static final String TYPE_PRIVATE = "PRIVATE";

    public static final String TYPE_GROUP = "GROUP";

    // =========================================================
    // ATTRIBUTES
    // =========================================================

    private int idConversation;

    private String name;

    private String type;

    private int createdBy;

    private LocalDateTime createdAt;

    // =========================================================
    // CONSTRUCTEUR VIDE
    // =========================================================

    public ChatConversation() {
    }

    // =========================================================
    // CONSTRUCTEUR COMPLET
    // =========================================================

    public ChatConversation(
            int idConversation,
            String name,
            String type,
            int createdBy,
            LocalDateTime createdAt
    ) {

        this.idConversation = idConversation;

        this.name = name;

        this.type = type;

        this.createdBy = createdBy;

        this.createdAt = createdAt;
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

    public String getName() {

        return name;
    }

    public void setName(
            String name
    ) {

        this.name = name;
    }

    public String getType() {

        return type;
    }

    public void setType(
            String type
    ) {

        this.type = type;
    }

    public int getCreatedBy() {

        return createdBy;
    }

    public void setCreatedBy(
            int createdBy
    ) {

        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {

        return createdAt;
    }

    public void setCreatedAt(
            LocalDateTime createdAt
    ) {

        this.createdAt = createdAt;
    }

    // =========================================================
    // TYPE CHECK
    // =========================================================

    /**
     * Vérifie si la conversation est privée.
     */
    public boolean isPrivate() {

        return TYPE_PRIVATE.equalsIgnoreCase(
                type
        );
    }

    /**
     * Vérifie si la conversation est un groupe.
     */
    public boolean isGroup() {

        return TYPE_GROUP.equalsIgnoreCase(
                type
        );
    }

    // =========================================================
    // TO STRING
    // =========================================================

    @Override
    public String toString() {

        if (name != null &&
                !name.isBlank()) {

            return name;
        }

        if (isPrivate()) {

            return "Conversation privée";
        }

        if (isGroup()) {

            return "Groupe";
        }

        return "Conversation";
    }
}