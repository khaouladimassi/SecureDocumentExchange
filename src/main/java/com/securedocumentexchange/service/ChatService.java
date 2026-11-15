package com.securedocumentexchange.service;

import com.securedocumentexchange.dao.ChatConversationDAO;
import com.securedocumentexchange.dao.ChatMemberDAO;
import com.securedocumentexchange.dao.ChatMessageDAO;
import com.securedocumentexchange.model.ChatConversation;
import com.securedocumentexchange.model.ChatMember;
import com.securedocumentexchange.model.ChatMessage;

import java.sql.SQLException;
import java.util.List;

/**
 * ================================================================
 * CHAT SERVICE
 * ================================================================
 *
 * Service métier du module Chat.
 *
 * Responsabilités :
 *
 * - créer des conversations privées
 * - créer des groupes
 * - ajouter / retirer des membres
 * - vérifier les droits d'accès
 * - envoyer des messages
 * - envoyer des fichiers
 * - récupérer l'historique
 * - modifier / supprimer des messages
 */
public class ChatService {

    private final ChatConversationDAO conversationDAO;

    private final ChatMemberDAO memberDAO;

    private final ChatMessageDAO messageDAO;

    // =========================================================
    // CONSTRUCTEUR
    // =========================================================

    public ChatService() {

        this.conversationDAO =
                new ChatConversationDAO();

        this.memberDAO =
                new ChatMemberDAO();

        this.messageDAO =
                new ChatMessageDAO();
    }

    // =========================================================
    // CONVERSATION PRIVÉE
    // =========================================================

    /**
     * Crée ou récupère une conversation privée
     * entre deux utilisateurs.
     */
    public ChatConversation createPrivateConversation(
            int user1Id,
            int user2Id
    ) throws SQLException {

        validateUserIds(
                user1Id,
                user2Id
        );

        if (user1Id == user2Id) {

            throw new IllegalArgumentException(
                    "Un utilisateur ne peut pas créer " +
                            "une conversation privée avec lui-même."
            );
        }

        /*
         * Le DAO possède déjà la logique :
         *
         * getOrCreatePrivateConversation(...)
         *
         * Il recherche la conversation existante
         * puis la crée si nécessaire.
         */
        int conversationId =
                conversationDAO.getOrCreatePrivateConversation(
                        user1Id,
                        user2Id,
                        "Conversation privée"
                );

        ChatConversation conversation =
                conversationDAO.findById(
                        conversationId
                );

        if (conversation == null) {

            throw new SQLException(
                    "La conversation privée n'a pas pu être récupérée."
            );
        }

        return conversation;
    }

    // =========================================================
    // CONVERSATION AVEC ADMIN
    // =========================================================

    /**
     * Crée ou récupère une conversation privée
     * entre un utilisateur et le premier administrateur.
     */
    public ChatConversation createPrivateConversationWithAdmin(
            int userId,
            String userDisplayName
    ) throws SQLException {

        if (userId <= 0) {

            throw new IllegalArgumentException(
                    "Identifiant utilisateur invalide."
            );
        }

        int conversationId =
                conversationDAO
                        .getOrCreatePrivateConversationWithFirstAdmin(
                                userId,
                                userDisplayName
                        );

        ChatConversation conversation =
                conversationDAO.findById(
                        conversationId
                );

        if (conversation == null) {

            throw new SQLException(
                    "Conversation avec l'administrateur introuvable."
            );
        }

        return conversation;
    }

    // =========================================================
    // CRÉATION GROUPE
    // =========================================================

    /**
     * Crée un groupe.
     *
     * Le créateur est automatiquement ajouté
     * comme membre.
     */
    public ChatConversation createGroup(
            String groupName,
            int creatorId,
            List<Integer> userIds
    ) throws SQLException {

        if (groupName == null ||
                groupName.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Le nom du groupe est obligatoire."
            );
        }

        groupName =
                groupName.trim();

        if (groupName.length() > 150) {

            throw new IllegalArgumentException(
                    "Le nom du groupe ne doit pas dépasser 150 caractères."
            );
        }

        if (creatorId <= 0) {

            throw new IllegalArgumentException(
                    "Identifiant du créateur invalide."
            );
        }

        int conversationId =
                conversationDAO.createGroup(
                        groupName,
                        creatorId,
                        userIds
                );

        ChatConversation conversation =
                conversationDAO.findById(
                        conversationId
                );

        if (conversation == null) {

            throw new SQLException(
                    "Le groupe n'a pas pu être récupéré."
            );
        }

        return conversation;
    }

    // =========================================================
    // AJOUTER MEMBRE
    // =========================================================

    /**
     * Ajoute un utilisateur dans un groupe.
     *
     * Seul le créateur peut ajouter un membre.
     */
    public void addMember(
            int conversationId,
            int requesterId,
            int userId
    ) throws SQLException {

        ChatConversation conversation =
                getConversationOrThrow(
                        conversationId
                );

        if (!conversation.isGroup()) {

            throw new IllegalArgumentException(
                    "Les membres ne peuvent être ajoutés " +
                            "que dans un groupe."
            );
        }

        verifyCreator(
                conversation,
                requesterId
        );

        if (userId <= 0) {

            throw new IllegalArgumentException(
                    "Utilisateur invalide."
            );
        }

        memberDAO.addMember(
                conversationId,
                userId
        );
    }

    // =========================================================
    // RETIRER MEMBRE
    // =========================================================

    /**
     * Retire un membre d'un groupe.
     *
     * Seul le créateur peut retirer un membre.
     */
    public void removeMember(
            int conversationId,
            int requesterId,
            int userId
    ) throws SQLException {

        ChatConversation conversation =
                getConversationOrThrow(
                        conversationId
                );

        if (!conversation.isGroup()) {

            throw new IllegalArgumentException(
                    "Cette opération concerne uniquement les groupes."
            );
        }

        verifyCreator(
                conversation,
                requesterId
        );

        if (userId ==
                conversation.getCreatedBy()) {

            throw new IllegalArgumentException(
                    "Le créateur du groupe ne peut pas être retiré."
            );
        }

        memberDAO.removeMember(
                conversationId,
                userId
        );
    }

    // =========================================================
    // QUITTER GROUPE
    // =========================================================

    public void leaveGroup(
            int conversationId,
            int userId
    ) throws SQLException {

        ChatConversation conversation =
                getConversationOrThrow(
                        conversationId
                );

        if (!conversation.isGroup()) {

            throw new IllegalArgumentException(
                    "Cette conversation n'est pas un groupe."
            );
        }

        verifyMembership(
                conversationId,
                userId
        );

        if (conversation.getCreatedBy() ==
                userId) {

            throw new IllegalArgumentException(
                    "Le créateur doit supprimer le groupe " +
                            "ou transférer sa gestion avant de partir."
            );
        }

        memberDAO.removeMember(
                conversationId,
                userId
        );
    }

    // =========================================================
    // LISTE DES CONVERSATIONS
    // =========================================================

    public List<ChatConversation> getUserConversations(
            int userId
    ) throws SQLException {

        if (userId <= 0) {

            throw new IllegalArgumentException(
                    "Identifiant utilisateur invalide."
            );
        }

        /*
         * Le DAO utilise getForUser().
         */
        return conversationDAO.getForUser(
                userId
        );
    }

    // =========================================================
    // RÉCUPÉRER UNE CONVERSATION
    // =========================================================

    public ChatConversation getConversation(
            int conversationId,
            int requesterId
    ) throws SQLException {

        verifyMembership(
                conversationId,
                requesterId
        );

        return getConversationOrThrow(
                conversationId
        );
    }

    // =========================================================
    // MEMBRES
    // =========================================================

    public List<ChatMember> getMembers(
            int conversationId,
            int requesterId
    ) throws SQLException {

        verifyMembership(
                conversationId,
                requesterId
        );

        return memberDAO.getMembers(
                conversationId
        );
    }

    // =========================================================
    // ENVOYER MESSAGE TEXTE
    // =========================================================

    public ChatMessage sendMessage(
            int conversationId,
            int senderId,
            String text
    ) throws SQLException {

        verifyMembership(
                conversationId,
                senderId
        );

        if (text == null ||
                text.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Le message ne peut pas être vide."
            );
        }

        text =
                text.trim();

        if (text.length() > 5000) {

            throw new IllegalArgumentException(
                    "Le message ne doit pas dépasser 5000 caractères."
            );
        }

        /*
         * Convention :
         *
         * conversationId
         * senderId
         * content
         * type
         */
        ChatMessage message =
                new ChatMessage(
                        conversationId,
                        senderId,
                        text,
                        ChatMessage.TYPE_TEXT
                );

        messageDAO.insert(
                message
        );

        return message;
    }

    // =========================================================
    // ENVOYER MESSAGE FICHIER
    // =========================================================

    public ChatMessage sendFileMessage(
            int conversationId,
            int senderId,
            int documentId,
            String filename
    ) throws SQLException {

        verifyMembership(
                conversationId,
                senderId
        );

        if (documentId <= 0) {

            throw new IllegalArgumentException(
                    "Document invalide."
            );
        }

        if (filename == null ||
                filename.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Le nom du fichier est obligatoire."
            );
        }

        ChatMessage message =
                new ChatMessage(
                        conversationId,
                        senderId,
                        filename.trim(),
                        ChatMessage.TYPE_FILE,
                        documentId
                );

        messageDAO.insert(
                message
        );

        return message;
    }

    // =========================================================
    // MESSAGE SYSTÈME
    // =========================================================

    public ChatMessage sendSystemMessage(
            int conversationId,
            int senderId,
            String text
    ) throws SQLException {

        verifyMembership(
                conversationId,
                senderId
        );

        if (text == null ||
                text.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Le message système ne peut pas être vide."
            );
        }

        ChatMessage message =
                new ChatMessage(
                        conversationId,
                        senderId,
                        text.trim(),
                        ChatMessage.TYPE_SYSTEM
                );

        messageDAO.insert(
                message
        );

        return message;
    }

    // =========================================================
    // HISTORIQUE
    // =========================================================

    public List<ChatMessage> getHistory(
            int conversationId,
            int requesterId
    ) throws SQLException {

        verifyMembership(
                conversationId,
                requesterId
        );

        return messageDAO.getHistory(
                conversationId
        );
    }

    // =========================================================
    // DERNIERS MESSAGES
    // =========================================================

    public List<ChatMessage> getLastMessages(
            int conversationId,
            int requesterId,
            int limit
    ) throws SQLException {

        verifyMembership(
                conversationId,
                requesterId
        );

        if (limit <= 0) {

            throw new IllegalArgumentException(
                    "La limite doit être supérieure à zéro."
            );
        }

        return messageDAO.getLastMessages(
                conversationId,
                limit
        );
    }

    // =========================================================
    // MODIFIER MESSAGE
    // =========================================================

    public boolean editMessage(
            long messageId,
            int senderId,
            String newText
    ) throws SQLException {

        if (newText == null ||
                newText.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Le nouveau message ne peut pas être vide."
            );
        }

        newText =
                newText.trim();

        if (newText.length() > 5000) {

            throw new IllegalArgumentException(
                    "Le message ne doit pas dépasser 5000 caractères."
            );
        }

        ChatMessage message =
                messageDAO.findById(
                        messageId
                );

        if (message == null) {

            throw new IllegalArgumentException(
                    "Message introuvable."
            );
        }

        verifyMembership(
                message.getIdConversation(),
                senderId
        );

        if (message.getSenderId()
                != senderId) {

            throw new SecurityException(
                    "Vous ne pouvez modifier que vos propres messages."
            );
        }

        if (!message.isText()) {

            throw new IllegalArgumentException(
                    "Seuls les messages texte peuvent être modifiés."
            );
        }

        return messageDAO.updateMessage(
                messageId,
                senderId,
                newText
        );
    }

    // =========================================================
    // SUPPRIMER MESSAGE
    // =========================================================

    public boolean deleteMessage(
            long messageId,
            int senderId
    ) throws SQLException {

        ChatMessage message =
                messageDAO.findById(
                        messageId
                );

        if (message == null) {

            throw new IllegalArgumentException(
                    "Message introuvable."
            );
        }

        verifyMembership(
                message.getIdConversation(),
                senderId
        );

        if (message.getSenderId()
                != senderId) {

            throw new SecurityException(
                    "Vous ne pouvez supprimer que vos propres messages."
            );
        }

        return messageDAO.deleteMessage(
                messageId,
                senderId
        );
    }

    // =========================================================
    // VÉRIFICATION MEMBRE
    // =========================================================

    public void verifyMembership(
            int conversationId,
            int userId
    ) throws SQLException {

        if (conversationId <= 0) {

            throw new IllegalArgumentException(
                    "Conversation invalide."
            );
        }

        if (userId <= 0) {

            throw new IllegalArgumentException(
                    "Utilisateur invalide."
            );
        }

        if (!memberDAO.isMember(
                conversationId,
                userId
        )) {

            throw new SecurityException(
                    "Accès refusé : l'utilisateur " +
                            "n'est pas membre de cette conversation."
            );
        }
    }

    // =========================================================
    // VÉRIFICATION CRÉATEUR
    // =========================================================

    private void verifyCreator(
            ChatConversation conversation,
            int userId
    ) {

        if (conversation.getCreatedBy()
                != userId) {

            throw new SecurityException(
                    "Seul le créateur du groupe " +
                            "peut effectuer cette opération."
            );
        }
    }

    // =========================================================
    // RÉCUPÉRER CONVERSATION
    // =========================================================

    private ChatConversation getConversationOrThrow(
            int conversationId
    ) throws SQLException {

        ChatConversation conversation =
                conversationDAO.findById(
                        conversationId
                );

        if (conversation == null) {

            throw new IllegalArgumentException(
                    "Conversation introuvable."
            );
        }

        return conversation;
    }

    // =========================================================
    // VALIDATION IDS
    // =========================================================

    private void validateUserIds(
            int user1Id,
            int user2Id
    ) {

        if (user1Id <= 0 ||
                user2Id <= 0) {

            throw new IllegalArgumentException(
                    "Identifiant utilisateur invalide."
            );
        }
    }
}