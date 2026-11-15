package com.securedocumentexchange.model;

import java.time.LocalDateTime;

/**
 * ================================================================
 * CHAT MESSAGE
 * ================================================================
 *
 * Représente un message envoyé dans une conversation.
 *
 * Types supportés :
 *
 * - TEXT   : message texte normal
 * - FILE   : message contenant/référençant un document
 * - SYSTEM : message système
 */
public class ChatMessage {

    // =========================================================
    // CONSTANTES
    // =========================================================

    public static final String TYPE_TEXT = "TEXT";

    public static final String TYPE_FILE = "FILE";

    public static final String TYPE_SYSTEM = "SYSTEM";


    // =========================================================
    // ATTRIBUTES
    // =========================================================

    private long idMessage;

    private int idConversation;

    private int senderId;

    private String senderUsername;

    private String type;

    private String content;

    private int documentId;

    private LocalDateTime sentAt;


    // =========================================================
    // CONSTRUCTEUR VIDE
    // =========================================================

    public ChatMessage() {

        this.idMessage = 0;

        this.idConversation = 0;

        this.senderId = 0;

        this.senderUsername = null;

        this.type = TYPE_TEXT;

        this.content = "";

        this.documentId = 0;

        this.sentAt = null;
    }


    // =========================================================
    // CONSTRUCTEUR MESSAGE
    // =========================================================

    /**
     * Constructeur général utilisé pour un message.
     *
     * Ordre des paramètres :
     *
     * conversationId
     * senderId
     * content
     * type
     */
    public ChatMessage(
            int idConversation,
            int senderId,
            String content,
            String type
    ) {

        this.idConversation = idConversation;

        this.senderId = senderId;

        this.content = content;

        this.type = normalizeType(type);

        this.documentId = 0;
    }


    // =========================================================
    // CONSTRUCTEUR MESSAGE FICHIER
    // =========================================================

    /**
     * Constructeur utilisé pour un message contenant
     * une référence vers un document.
     *
     * Ordre des paramètres :
     *
     * conversationId
     * senderId
     * content
     * type
     * documentId
     */
    public ChatMessage(
            int idConversation,
            int senderId,
            String content,
            String type,
            int documentId
    ) {

        this.idConversation = idConversation;

        this.senderId = senderId;

        this.content = content;

        this.type = normalizeType(type);

        this.documentId = documentId;
    }


    // =========================================================
    // GETTERS / SETTERS
    // =========================================================

    public long getIdMessage() {

        return idMessage;
    }


    public void setIdMessage(
            long idMessage
    ) {

        this.idMessage = idMessage;
    }


    public int getIdConversation() {

        return idConversation;
    }


    public void setIdConversation(
            int idConversation
    ) {

        this.idConversation = idConversation;
    }


    public int getSenderId() {

        return senderId;
    }


    public void setSenderId(
            int senderId
    ) {

        this.senderId = senderId;
    }


    public String getSenderUsername() {

        return senderUsername;
    }


    public void setSenderUsername(
            String senderUsername
    ) {

        this.senderUsername = senderUsername;
    }


    public String getType() {

        return type;
    }


    public void setType(
            String type
    ) {

        this.type = normalizeType(type);
    }


    public String getContent() {

        return content;
    }


    public void setContent(
            String content
    ) {

        this.content = content;
    }


    public int getDocumentId() {

        return documentId;
    }


    public void setDocumentId(
            int documentId
    ) {

        this.documentId = documentId;
    }


    public LocalDateTime getSentAt() {

        return sentAt;
    }


    public void setSentAt(
            LocalDateTime sentAt
    ) {

        this.sentAt = sentAt;
    }


    // =========================================================
    // TYPE CHECK
    // =========================================================

    /**
     * Vérifie si le message est un message texte.
     */
    public boolean isText() {

        return TYPE_TEXT.equalsIgnoreCase(type);
    }


    /**
     * Vérifie si le message est un message fichier.
     */
    public boolean isFile() {

        return TYPE_FILE.equalsIgnoreCase(type);
    }


    /**
     * Vérifie si le message est un message système.
     */
    public boolean isSystem() {

        return TYPE_SYSTEM.equalsIgnoreCase(type);
    }


    // =========================================================
    // TYPE NORMALIZATION
    // =========================================================

    /**
     * Normalise le type du message.
     *
     * Valeurs supportées :
     *
     * TEXT
     * FILE
     * SYSTEM
     *
     * Si le type est null ou vide, TEXT est utilisé.
     */
    private String normalizeType(
            String type
    ) {

        if (
                type == null
                        || type.isBlank()
        ) {

            return TYPE_TEXT;
        }

        return type
                .trim()
                .toUpperCase();
    }


    // =========================================================
    // VALIDATION
    // =========================================================

    /**
     * Vérifie que le type du message est valide.
     */
    public boolean hasValidType() {

        return TYPE_TEXT.equalsIgnoreCase(type)
                || TYPE_FILE.equalsIgnoreCase(type)
                || TYPE_SYSTEM.equalsIgnoreCase(type);
    }


    /**
     * Vérifie que l'expéditeur possède un identifiant valide.
     */
    public boolean hasValidSender() {

        return senderId > 0;
    }


    /**
     * Vérifie que la conversation possède
     * un identifiant valide.
     */
    public boolean hasValidConversation() {

        return idConversation > 0;
    }


    /**
     * Vérifie que le contenu est valide.
     *
     * Pour un message système ou texte, le contenu
     * doit normalement être présent.
     *
     * Pour un message fichier, le contenu peut
     * représenter le nom du fichier.
     */
    public boolean hasValidContent() {

        return content != null
                && !content.isBlank();
    }


    // =========================================================
    // TO STRING
    // =========================================================

    @Override
    public String toString() {

        String sender =
                senderUsername == null
                        || senderUsername.isBlank()
                        ? String.valueOf(senderId)
                        : senderUsername;

        String messageContent =
                content == null
                        ? ""
                        : content;

        return sender
                + " : "
                + messageContent;
    }
}