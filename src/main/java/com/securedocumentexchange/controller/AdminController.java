package com.securedocumentexchange.controller;

import com.securedocumentexchange.dao.ChatConversationDAO;
import com.securedocumentexchange.dao.ChatMessageDAO;
import com.securedocumentexchange.dao.DocumentDAO;
import com.securedocumentexchange.dao.LogDAO;
import com.securedocumentexchange.dao.PermissionDAO;
import com.securedocumentexchange.dao.UserDAO;

import com.securedocumentexchange.model.ChatMessage;
import com.securedocumentexchange.model.Document;
import com.securedocumentexchange.model.Log;
import com.securedocumentexchange.model.Permission;
import com.securedocumentexchange.model.User;

import com.securedocumentexchange.network.ClientHandler;
import com.securedocumentexchange.network.ConnectionRequest;
import com.securedocumentexchange.network.SecureServer;

import com.securedocumentexchange.service.SessionManager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ================================================================
 * ADMIN CONTROLLER
 * ================================================================
 *
 * Contrôleur principal de l'interface administrateur.
 *
 * Responsabilités :
 *
 * - Démarrage et arrêt du serveur
 * - Gestion des demandes de connexion
 * - Acceptation / refus des utilisateurs
 * - Gestion des utilisateurs connectés
 * - Envoi et réception de fichiers
 * - Gestion des documents
 * - Gestion des permissions
 * - Gestion des logs
 * - Chat privé
 * - Chat de groupe
 * - Historique des conversations
 *
 * Le contrôleur implémente :
 *
 * ClientHandler.Listener
 */
public class AdminController implements ClientHandler.Listener {

    // =========================================================
    // FXML - SERVER
    // =========================================================

    @FXML
    private Label serverStatusLabel;

    @FXML
    private Label serverInfoLabel;

    @FXML
    private Button startServerButton;


    // =========================================================
    // FXML - USERS
    // =========================================================

    @FXML
    private ListView<String> connectionRequestsList;

    @FXML
    private ListView<String> connectedUsersList;


    // =========================================================
    // FXML - DOCUMENTS
    // =========================================================

    @FXML
    private ListView<String> documentList;

    @FXML
    private Label selectedFileLabel;

    @FXML
    private CheckBox sensitiveCheckBox;


    // =========================================================
    // FXML - CHAT
    // =========================================================

    @FXML
    private TextArea chatArea;

    @FXML
    private TextField chatMessageField;


    // =========================================================
    // SERVER
    // =========================================================

    private SecureServer secureServer;


    // =========================================================
    // DAO
    // =========================================================

    private final DocumentDAO documentDAO =
            new DocumentDAO();

    private final PermissionDAO permissionDAO =
            new PermissionDAO();

    private final LogDAO logDAO =
            new LogDAO();

    private final UserDAO userDAO =
            new UserDAO();

    private final ChatConversationDAO conversationDAO =
            new ChatConversationDAO();

    private final ChatMessageDAO messageDAO =
            new ChatMessageDAO();


    // =========================================================
    // ADMIN SESSION
    // =========================================================

    private volatile int adminUserId = 0;


    // =========================================================
    // CONNECTION MANAGEMENT
    // =========================================================

    private final Map<String, ConnectionRequest> pendingRequests =
            new HashMap<>();

    private final Map<String, ClientHandler> connectedUsers =
            new HashMap<>();


    // =========================================================
    // FILE MANAGEMENT
    // =========================================================

    private File selectedFile;


    // =========================================================
    // CHAT STATE
    // =========================================================

    private volatile String selectedChatUser;

    private volatile String selectedChatType;

    private volatile int selectedConversationId = 0;

    private volatile String selectedGroupName;


    // =========================================================
    // INITIALIZATION
    // =========================================================

    @FXML
    public void initialize() {

        refreshAdminSession();

        configureLists();

        configureChat();

        updateServerUI(false);
    }


    // =========================================================
    // LIST CONFIGURATION
    // =========================================================

    private void configureLists() {

        if (connectionRequestsList != null) {

            connectionRequestsList
                    .getSelectionModel()
                    .setSelectionMode(
                            SelectionMode.SINGLE
                    );
        }

        if (connectedUsersList != null) {

            connectedUsersList
                    .getSelectionModel()
                    .setSelectionMode(
                            SelectionMode.SINGLE
                    );

            connectedUsersList
                    .getSelectionModel()
                    .selectedItemProperty()
                    .addListener(
                            (observable, oldValue, newValue) -> {

                                if (newValue != null) {

                                    selectPrivateChat(
                                            extractUsername(newValue)
                                    );
                                }
                            }
                    );
        }

        if (documentList != null) {

            documentList
                    .getSelectionModel()
                    .setSelectionMode(
                            SelectionMode.SINGLE
                    );
        }
    }


    // =========================================================
    // CHAT CONFIGURATION
    // =========================================================

    private void configureChat() {

        if (chatArea != null) {

            chatArea.setEditable(false);

            chatArea.setWrapText(true);
        }

        if (chatMessageField != null) {

            chatMessageField.setDisable(true);

            chatMessageField.setOnAction(
                    event -> sendChatMessage()
            );
        }
    }


    // =========================================================
    // ADMIN SESSION
    // =========================================================

    public void setAdminUserId(int id) {

        if (id <= 0) {

            throw new IllegalArgumentException(
                    "ID admin invalide."
            );
        }

        adminUserId = id;
    }


    private void refreshAdminSession() {

        if (!SessionManager.isLoggedIn()) {

            adminUserId = 0;

            return;
        }

        User user =
                SessionManager.getCurrentUser();

        adminUserId =
                (
                        user != null
                                && "ADMIN".equalsIgnoreCase(
                                user.getRole()
                        )
                )
                        ? user.getIdUser()
                        : 0;
    }


    // =========================================================
    // SERVER - START
    // =========================================================

    @FXML
    private void startServer() {

        if (
                secureServer != null
                        && secureServer.isRunning()
        ) {

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Serveur",
                    "Le serveur est déjà démarré."
            );

            return;
        }

        refreshAdminSession();

        if (adminUserId <= 0) {

            showAlert(
                    Alert.AlertType.ERROR,
                    "Session",
                    "Administrateur non identifié."
            );

            return;
        }

        try {

            secureServer =
                    new SecureServer(
                            "SWConsulting-Server",
                            5000,
                            this
                    );

            secureServer.start();

            updateServerUI(true);

            insertLogSafely(
                    createLog(
                            adminUserId,
                            0,
                            "SERVER_STARTED",
                            "INFO",
                            "Serveur TLS démarré sur le port 5000.",
                            SecureServer.getLocalIp()
                    )
            );

        } catch (Exception e) {

            secureServer = null;

            updateServerUI(false);

            showAlert(
                    Alert.AlertType.ERROR,
                    "Erreur serveur",
                    safeMessage(e)
            );
        }
    }


    // =========================================================
    // SERVER - UI
    // =========================================================

    private void updateServerUI(
            boolean running
    ) {

        if (serverStatusLabel != null) {

            serverStatusLabel.setText(
                    running
                            ? "🟢 Serveur démarré"
                            : "🔴 Serveur arrêté"
            );
        }

        if (serverInfoLabel != null) {

            serverInfoLabel.setText(
                    running
                            ? "IP : "
                            + SecureServer.getLocalIp()
                            + " | TLS : 5000"
                            : "Serveur non démarré"
            );
        }

        if (startServerButton != null) {

            startServerButton.setDisable(running);

            startServerButton.setText(
                    running
                            ? "🟢 Serveur actif"
                            : "▶ Démarrer serveur"
            );
        }

        updateChatInputState();
    }


    private void updateChatInputState() {

        if (chatMessageField == null) {
            return;
        }

        boolean enabled =
                secureServer != null
                        && secureServer.isRunning()
                        && selectedConversationId > 0;

        if ("USER".equals(selectedChatType)
                && selectedChatUser != null) {

            ClientHandler handler =
                    connectedUsers.get(
                            selectedChatUser
                    );

            enabled =
                    enabled
                            && handler != null
                            && handler.isRunning()
                            && handler.isAccessGranted()
                            && handler.isReady();

        } else if (!"GROUP".equals(selectedChatType)) {

            enabled = false;
        }

        chatMessageField.setDisable(!enabled);
    }


    // =========================================================
    // CONNECTION REQUEST
    // =========================================================

    @Override
    public void onConnectionRequest(
            ConnectionRequest request
    ) {

        if (request == null) {
            return;
        }

        Platform.runLater(() -> {

            String username =
                    request.getUsername();

            if (
                    username == null
                            || username.isBlank()
            ) {
                return;
            }

            username =
                    username.trim();

            if (
                    pendingRequests.containsKey(username)
                            || connectedUsers.containsKey(username)
            ) {

                return;
            }

            pendingRequests.put(
                    username,
                    request
            );

            if (connectionRequestsList != null) {

                connectionRequestsList
                        .getItems()
                        .add(
                                formatUser(
                                        username,
                                        request.getClientIp()
                                )
                        );
            }
        });
    }


    // =========================================================
    // FILE RECEIVED FROM USER
    // =========================================================

    @Override
    public void onFileReceivedFromUser(
            ClientHandler handler,
            File file,
            String originalFilename,
            boolean sensitive
    ) {

        if (
                handler == null
                        || file == null
        ) {

            return;
        }

        String username =
                handler.getUsername();

        Platform.runLater(() -> {

            if (documentList != null) {

                documentList
                        .getItems()
                        .add(
                                originalFilename
                                        + " ← "
                                        + username
                                        + (
                                        sensitive
                                                ? " 🔒"
                                                : " 📄"
                                )
                        );
            }
        });

        insertLogSafely(
                createLog(
                        findUserIdSafely(username),
                        0,
                        "FILE_RECEIVED",
                        "INFO",
                        sensitive
                                ? "Document sensible reçu avec AES-256-GCM + TLS"
                                : "Document reçu avec TLS.",
                        handler.getClientIp()
                )
        );
    }


    // =========================================================
    // CHAT - MESSAGE RECEIVED
    // =========================================================

    @Override
    public void onChatMessage(
            ClientHandler handler,
            int conversationId,
            String message
    ) {

        if (
                handler == null
                        || conversationId <= 0
                        || message == null
                        || message.isBlank()
        ) {

            return;
        }

        try {

            int senderId =
                    findUserId(
                            handler.getUsername()
                    );

            if (senderId <= 0) {
                return;
            }

            /*
             * Vérification de sécurité :
             * l'utilisateur doit appartenir
             * à la conversation.
             */
            if (
                    !conversationDAO.isMember(
                            conversationId,
                            senderId
                    )
            ) {

                return;
            }

            final String receivedMessage = message.trim();

            /*
             * IMPORTANT :
             *
             * Constructeur :
             *
             * (conversationId,
             *  senderId,
             *  content,
             *  type,
             *  documentId)
             *
             * Donc :
             *
             * message -> content
             * TEXT    -> type
             */
            ChatMessage chatMessage =
                    new ChatMessage(
                            conversationId,
                            senderId,
                            receivedMessage,
                            ChatMessage.TYPE_TEXT,
                            0
                    );

            messageDAO.insert(
                    chatMessage
            );

            /*
             * Envoi en temps réel
             * aux autres membres.
             */
            broadcastChat(
                    conversationId,
                    senderId,
                    handler.getUsername(),
                    receivedMessage
            );

            /*
             * Si l'admin regarde actuellement
             * cette conversation, afficher immédiatement.
             */
            if (
                    conversationId
                            == selectedConversationId
            ) {

                Platform.runLater(
                        () -> appendChatMessage(
                                handler.getUsername(),
                                receivedMessage
                        )
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "Chat réception : "
                            + safeMessage(e)
            );
        }
    }


    // =========================================================
    // CHAT - FILE RECEIVED
    // =========================================================

    @Override
    public void onChatFileReceived(
            ClientHandler handler,
            int conversationId,
            File file,
            String originalFilename,
            boolean sensitive
    ) {

        if (
                handler == null
                        || conversationId <= 0
                        || file == null
        ) {

            return;
        }

        try {

            int senderId =
                    findUserId(
                            handler.getUsername()
                    );

            if (senderId <= 0) {
                return;
            }

            if (
                    !conversationDAO.isMember(
                            conversationId,
                            senderId
                    )
            ) {

                return;
            }

            /*
             * IMPORTANT :
             *
             * content = originalFilename
             * type    = FILE
             */
            ChatMessage chatMessage =
                    new ChatMessage(
                            conversationId,
                            senderId,
                            originalFilename,
                            ChatMessage.TYPE_FILE,
                            0
                    );

            messageDAO.insert(
                    chatMessage
            );

            /*
             * Pour le moment le fichier est
             * représenté par le message FILE.
             */
            broadcastChatFile(
                    conversationId,
                    senderId,
                    handler.getUsername(),
                    originalFilename,
                    sensitive
            );

            if (
                    conversationId
                            == selectedConversationId
            ) {

                Platform.runLater(
                        () -> appendChatMessage(
                                handler.getUsername(),
                                "📎 "
                                        + originalFilename
                                        + (
                                        sensitive
                                                ? " 🔒"
                                                : ""
                                )
                        )
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "Chat fichier : "
                            + safeMessage(e)
            );
        }
    }


    // =========================================================
    // CHAT - HISTORY REQUEST
    // =========================================================

    @Override
    public void onChatHistoryRequest(
            ClientHandler handler,
            int conversationId
    ) {

        if (
                handler == null
                        || conversationId <= 0
        ) {

            return;
        }

        try {

            int userId =
                    findUserId(
                            handler.getUsername()
                    );

            if (userId <= 0) {
                return;
            }

            if (
                    !conversationDAO.isMember(
                            conversationId,
                            userId
                    )
            ) {

                return;
            }

            List<ChatMessage> messages =
                    messageDAO.getMessages(
                            conversationId,
                            500
                    );

            handler.sendChatHistoryStart(
                    conversationId,
                    messages.size()
            );

            for (ChatMessage message : messages) {

                String type =
                        normalizeChatType(
                                message.getType()
                        );

                handler.sendChatHistoryMessage(
                        conversationId,
                        message.getIdMessage(),
                        message.getSenderId(),
                        message.getSenderUsername(),
                        type,
                        message.getContent(),
                        message.getDocumentId()
                );
            }

            handler.sendChatHistoryEnd(
                    conversationId
            );

        } catch (Exception e) {

            System.err.println(
                    "Historique : "
                            + safeMessage(e)
            );
        }
    }


    // =========================================================
    // CHAT - BROADCAST MESSAGE
    // =========================================================

    private void broadcastChat(
            int conversationId,
            int senderId,
            String sender,
            String message
    ) throws Exception {

        List<Integer> memberIds =
                conversationDAO.getMemberIds(
                        conversationId
                );

        if (memberIds == null) {
            return;
        }

        for (int userId : memberIds) {

            /*
             * L'admin n'a pas besoin de recevoir
             * son propre message via socket.
             *
             * L'expéditeur n'a pas besoin de recevoir
             * son propre message non plus.
             */
            if (
                    userId == adminUserId
                            || userId == senderId
            ) {

                continue;
            }

            ClientHandler handler =
                    handlerForUserId(userId);

            if (
                    handler != null
                            && handler.isRunning()
                            && handler.isAccessGranted()
                            && handler.isReady()
            ) {

                try {

                    handler.sendChatMessage(
                            conversationId,
                            sender,
                            message
                    );

                } catch (Exception e) {

                    System.err.println(
                            "Erreur broadcast chat vers "
                                    + userId
                                    + " : "
                                    + safeMessage(e)
                    );
                }
            }
        }
    }


    // =========================================================
    // CHAT - BROADCAST FILE
    // =========================================================

    private void broadcastChatFile(
            int conversationId,
            int senderId,
            String sender,
            String filename,
            boolean sensitive
    ) {

        /*
         * Le transfert physique du fichier de chat
         * doit être effectué par sendChatFile().
         *
         * Ici le message FILE est déjà persisté.
         *
         * On ne crée volontairement pas de faux
         * transfert de fichier avec uniquement
         * le nom.
         */
    }


    // =========================================================
    // FIND HANDLER BY USER ID
    // =========================================================

    private ClientHandler handlerForUserId(
            int userId
    ) {

        try {

            List<Map.Entry<String, ClientHandler>> entries =
                    new ArrayList<>(
                            connectedUsers.entrySet()
                    );

            for (
                    Map.Entry<String, ClientHandler> entry :
                    entries
            ) {

                String username =
                        entry.getKey();

                int currentId =
                        findUserId(username);

                if (currentId == userId) {

                    return entry.getValue();
                }
            }

        } catch (Exception ignored) {
        }

        return null;
    }


    // =========================================================
    // PRIVATE CHAT
    // =========================================================

    private void selectPrivateChat(
            String username
    ) {

        try {

            if (
                    username == null
                            || username.isBlank()
            ) {

                clearChatSelection();

                return;
            }

            int userId =
                    findUserId(username);

            if (
                    userId <= 0
                            || adminUserId <= 0
            ) {

                clearChatSelection();

                return;
            }

            int conversationId =
                    conversationDAO
                            .getOrCreatePrivateConversation(
                                    adminUserId,
                                    userId,
                                    "Conversation avec "
                                            + username
                            );

            if (conversationId <= 0) {

                throw new IOException(
                        "Impossible de créer la conversation."
                );
            }

            selectedChatUser =
                    username;

            selectedChatType =
                    "USER";

            selectedGroupName =
                    null;

            selectedConversationId =
                    conversationId;

            showConversation(
                    "🔒 Conversation privée avec "
                            + username,
                    conversationId
            );

            requestHistoryForAdmin(
                    conversationId
            );

            updateChatInputState();

        } catch (Exception e) {

            showAlert(
                    Alert.AlertType.ERROR,
                    "Chat",
                    safeMessage(e)
            );
        }
    }


    // =========================================================
    // GROUP CHAT
    // =========================================================

    public void selectGroupChat(
            int conversationId,
            String groupName
    ) {

        if (conversationId <= 0) {
            return;
        }

        selectedConversationId =
                conversationId;

        selectedChatType =
                "GROUP";

        selectedChatUser =
                null;

        selectedGroupName =
                groupName == null
                        || groupName.isBlank()
                        ? "Groupe"
                        : groupName.trim();

        showConversation(
                "👥 Groupe : "
                        + selectedGroupName,
                conversationId
        );

        requestHistoryForAdmin(
                conversationId
        );

        updateChatInputState();
    }


    // =========================================================
    // CHAT HISTORY - ADMIN
    // =========================================================

    private void requestHistoryForAdmin(
            int conversationId
    ) {

        try {

            List<ChatMessage> messages =
                    messageDAO.getMessages(
                            conversationId,
                            500
                    );

            Platform.runLater(() -> {

                if (chatArea == null) {
                    return;
                }

                /*
                 * On efface uniquement avant
                 * de reconstruire l'historique.
                 */
                chatArea.clear();

                for (ChatMessage message : messages) {

                    String type =
                            normalizeChatType(
                                    message.getType()
                            );

                    String content =
                            message.getContent();

                    if (
                            ChatMessage.TYPE_FILE.equals(type)
                    ) {

                        content =
                                "📎 "
                                        + (
                                        content == null
                                                ? ""
                                                : content
                                );
                    }

                    appendChatMessage(
                            message.getSenderUsername(),
                            content
                    );
                }
            });

        } catch (Exception e) {

            System.err.println(
                    "Historique admin : "
                            + safeMessage(e)
            );
        }
    }


    // =========================================================
    // SHOW CONVERSATION
    // =========================================================

    private void showConversation(
            String title,
            int conversationId
    ) {

        if (chatArea == null) {
            return;
        }

        chatArea.clear();

        chatArea.appendText(
                title
                        + "\n────────────────────────\n"
        );
    }


    // =========================================================
    // CLEAR CHAT
    // =========================================================

    private void clearChatSelection() {

        selectedChatUser =
                null;

        selectedChatType =
                null;

        selectedConversationId =
                0;

        selectedGroupName =
                null;

        if (chatMessageField != null) {

            chatMessageField.setDisable(true);
        }

        if (chatArea != null) {

            chatArea.clear();
        }
    }


    // =========================================================
    // APPEND CHAT MESSAGE
    // =========================================================

    private void appendChatMessage(
            String sender,
            String message
    ) {

        if (
                chatArea == null
                        || message == null
                        || message.isBlank()
        ) {

            return;
        }

        String displaySender =
                sender == null
                        || sender.isBlank()
                        ? "SYSTEM"
                        : sender;

        chatArea.appendText(
                displaySender
                        + " : "
                        + message.trim()
                        + "\n"
        );

        chatArea.setScrollTop(
                Double.MAX_VALUE
        );
    }


    // =========================================================
    // SEND CHAT MESSAGE
    // =========================================================

    @FXML
    private void sendChatMessage() {

        if (chatMessageField == null) {
            return;
        }

        String message =
                chatMessageField.getText();

        if (
                message == null
                        || message.isBlank()
                        || selectedConversationId <= 0
        ) {

            return;
        }

        message =
                message.trim();

        if (
                message.length()
                        > 5000
        ) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Chat",
                    "Le message est trop long."
            );

            return;
        }

        try {

            // =================================================
            // PRIVATE CHAT
            // =================================================

            if ("USER".equals(selectedChatType)) {

                if (
                        selectedChatUser == null
                                || selectedChatUser.isBlank()
                ) {

                    throw new IOException(
                            "Aucun utilisateur sélectionné."
                    );
                }

                /*
                 * Toujours récupérer/créer la conversation privée avant
                 * l'envoi. Cela évite d'utiliser un ancien ID de
                 * conversation qui ne possède plus ses membres.
                 */
                int userId = findUserId(selectedChatUser);

                if (userId <= 0 || adminUserId <= 0) {
                    throw new IOException(
                            "Utilisateur ou administrateur introuvable."
                    );
                }

                selectedConversationId =
                        conversationDAO.getOrCreatePrivateConversation(
                                adminUserId,
                                userId,
                                "Conversation avec " + selectedChatUser
                        );

                if (selectedConversationId <= 0) {
                    throw new IOException(
                            "Impossible de récupérer la conversation privée."
                    );
                }

                ClientHandler handler =
                        connectedUsers.get(
                                selectedChatUser
                        );

                if (
                        handler == null
                                || !handler.isRunning()
                                || !handler.isAccessGranted()
                                || !handler.isReady()
                ) {

                    throw new IOException(
                            "Utilisateur non disponible."
                    );
                }

                /*
                 * IMPORTANT :
                 *
                 * content = message
                 * type    = TEXT
                 */
                ChatMessage chatMessage =
                        new ChatMessage(
                                selectedConversationId,
                                adminUserId,
                                message,
                                ChatMessage.TYPE_TEXT,
                                0
                        );

                messageDAO.insert(
                        chatMessage
                );

                /*
                 * Le serveur transmet également le nom de l'expéditeur.
                 * SecureClient doit utiliser le même protocole :
                 * CHAT_MESSAGE + conversationId + sender + message.
                 */
                handler.sendChatMessage(
                        selectedConversationId,
                        "ADMIN",
                        message
                );
            }

            // =================================================
            // GROUP CHAT
            // =================================================

            else if ("GROUP".equals(selectedChatType)) {

                /*
                 * IMPORTANT :
                 *
                 * content = message
                 * type    = TEXT
                 */
                ChatMessage chatMessage =
                        new ChatMessage(
                                selectedConversationId,
                                adminUserId,
                                message,
                                ChatMessage.TYPE_TEXT,
                                0
                        );

                messageDAO.insert(
                        chatMessage
                );

                List<Integer> memberIds =
                        conversationDAO.getMemberIds(
                                selectedConversationId
                        );

                if (memberIds != null) {

                    for (int userId : memberIds) {

                        if (userId == adminUserId) {
                            continue;
                        }

                        ClientHandler handler =
                                handlerForUserId(userId);

                        if (
                                handler != null
                                        && handler.isRunning()
                                        && handler.isAccessGranted()
                                        && handler.isReady()
                        ) {

                            try {

                                handler.sendChatMessage(
                                        selectedConversationId,
                                        "ADMIN",
                                        message
                                );

                            } catch (Exception e) {

                                System.err.println(
                                        "Erreur envoi groupe vers "
                                                + userId
                                                + " : "
                                                + safeMessage(e)
                                );
                            }
                        }
                    }
                }

            } else {

                throw new IOException(
                        "Aucune conversation sélectionnée."
                );
            }

            /*
             * Affichage immédiat chez l'admin.
             */
            appendChatMessage(
                    "Moi",
                    message
            );

            chatMessageField.clear();

        } catch (Exception e) {

            showAlert(
                    Alert.AlertType.ERROR,
                    "Chat",
                    safeMessage(e)
            );
        }
    }


    // =========================================================
    // ACCEPT USER
    // =========================================================

    @FXML
    private void acceptUser() {

        if (connectionRequestsList == null) {
            return;
        }

        String selected =
                connectionRequestsList
                        .getSelectionModel()
                        .getSelectedItem();

        if (selected == null) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Demande",
                    "Sélectionnez une demande."
            );

            return;
        }

        String username =
                extractUsername(selected);

        ConnectionRequest request =
                pendingRequests.remove(username);

        if (request == null) {
            return;
        }

        try {

            ClientHandler handler =
                    request.getClientHandler();

            if (
                    handler == null
                            || !handler.isRunning()
            ) {

                throw new IOException(
                        "Client déconnecté."
                );
            }

            handler.grantAccess();

            connectedUsers.put(
                    username,
                    handler
            );

            connectionRequestsList
                    .getItems()
                    .remove(selected);

            if (connectedUsersList != null) {

                connectedUsersList
                        .getItems()
                        .add(
                                formatUser(
                                        username,
                                        request.getClientIp()
                                )
                        );
            }

            refreshAdminSession();

            insertLogSafely(
                    createLog(
                            adminUserId,
                            0,
                            "USER_ACCEPTED",
                            "INFO",
                            "Utilisateur "
                                    + username
                                    + " accepté.",
                            request.getClientIp()
                    )
            );

        } catch (Exception e) {

            pendingRequests.put(
                    username,
                    request
            );

            showAlert(
                    Alert.AlertType.ERROR,
                    "Erreur",
                    safeMessage(e)
            );
        }
    }


    // =========================================================
    // REFUSE USER
    // =========================================================

    @FXML
    private void refuseUser() {

        if (connectionRequestsList == null) {
            return;
        }

        String selected =
                connectionRequestsList
                        .getSelectionModel()
                        .getSelectedItem();

        if (selected == null) {
            return;
        }

        String username =
                extractUsername(selected);

        ConnectionRequest request =
                pendingRequests.remove(username);

        if (request == null) {
            return;
        }

        if (request.getClientHandler() != null) {

            request.getClientHandler()
                    .denyAccess();
        }

        connectionRequestsList
                .getItems()
                .remove(selected);

        refreshAdminSession();

        insertLogSafely(
                createLog(
                        adminUserId,
                        0,
                        "USER_REFUSED",
                        "INFO",
                        "Utilisateur "
                                + username
                                + " refusé.",
                        request.getClientIp()
                )
        );
    }


    // =========================================================
    // CLIENT DISCONNECTED
    // =========================================================

    @Override
    public void onClientDisconnected(
            ClientHandler handler
    ) {

        if (handler == null) {
            return;
        }

        Platform.runLater(() -> {

            String username =
                    handler.getUsername();

            if (username == null) {
                return;
            }

            connectedUsers.remove(
                    username
            );

            pendingRequests.remove(
                    username
            );

            if (
                    username.equals(
                            selectedChatUser
                    )
            ) {

                clearChatSelection();
            }

            if (connectedUsersList != null) {

                connectedUsersList
                        .getItems()
                        .removeIf(
                                item ->
                                        extractUsername(item)
                                                .equals(username)
                        );
            }
        });
    }


    // =========================================================
    // FILE SELECTION
    // =========================================================

    @FXML
    private void selectFile() {

        if (
                selectedFileLabel == null
                        || selectedFileLabel.getScene() == null
        ) {

            return;
        }

        FileChooser chooser =
                new FileChooser();

        chooser.setTitle(
                "Sélectionner un document"
        );

        File file =
                chooser.showOpenDialog(
                        selectedFileLabel
                                .getScene()
                                .getWindow()
                );

        if (
                file != null
                        && file.isFile()
                        && file.canRead()
        ) {

            selectedFile =
                    file;

            selectedFileLabel.setText(
                    "📄 "
                            + file.getName()
            );
        }
    }


    // =========================================================
    // SEND FILE
    // =========================================================

    @FXML
    private void sendFile() {

        refreshAdminSession();

        if (
                adminUserId <= 0
                        || secureServer == null
                        || !secureServer.isRunning()
                        || selectedFile == null
                        || connectedUsersList == null
        ) {

            return;
        }

        String item =
                connectedUsersList
                        .getSelectionModel()
                        .getSelectedItem();

        if (item == null) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Document",
                    "Sélectionnez un utilisateur."
            );

            return;
        }

        String username =
                extractUsername(item);

        ClientHandler handler =
                connectedUsers.get(username);

        if (
                handler == null
                        || !handler.isReady()
        ) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Document",
                    "Utilisateur non prêt."
            );

            return;
        }

        File file =
                selectedFile;

        boolean sensitive =
                sensitiveCheckBox != null
                        && sensitiveCheckBox.isSelected();

        new Thread(
                () -> {

                    try {

                        int recipientId =
                                findUserId(username);

                        if (recipientId <= 0) {

                            throw new IOException(
                                    "Utilisateur destinataire introuvable."
                            );
                        }

                        handler.sendFile(
                                file,
                                sensitive
                        );

                        Document document =
                                new Document(
                                        file.getName(),
                                        adminUserId,
                                        recipientId,
                                        sensitive
                                );

                        document.setFilePath(
                                file.getAbsolutePath()
                        );

                        document.setFileSize(
                                file.length()
                        );

                        document.setStatus(
                                "AVAILABLE"
                        );

                        int documentId =
                                documentDAO.insert(
                                        document
                                );

                        permissionDAO.insert(
                                new Permission(
                                        documentId,
                                        recipientId,
                                        true,
                                        true
                                )
                        );

                        insertLogSafely(
                                createLog(
                                        adminUserId,
                                        documentId,
                                        "FILE_SENT",
                                        "INFO",
                                        sensitive
                                                ? "Document sensible envoyé avec AES-256-GCM + TLS"
                                                : "Document envoyé avec TLS.",
                                        handler.getClientIp()
                                )
                        );

                        Platform.runLater(() -> {

                            if (documentList != null) {

                                documentList
                                        .getItems()
                                        .add(
                                                file.getName()
                                                        + " → "
                                                        + username
                                                        + (
                                                        sensitive
                                                                ? " 🔒"
                                                                : " 📄"
                                                )
                                        );
                            }

                            selectedFile =
                                    null;

                            if (selectedFileLabel != null) {

                                selectedFileLabel.setText(
                                        "Aucun fichier sélectionné"
                                );
                            }
                        });

                    } catch (Exception e) {

                        Platform.runLater(
                                () ->
                                        showAlert(
                                                Alert.AlertType.ERROR,
                                                "Erreur d'envoi",
                                                safeMessage(e)
                                        )
                        );
                    }

                },
                "Admin-FileTransfer"
        ).start();
    }


    // =========================================================
    // STOP SERVER
    // =========================================================

    public void stopServer() {

        if (secureServer == null) {
            return;
        }

        try {

            secureServer.stop();

        } finally {

            secureServer =
                    null;

            pendingRequests.clear();

            connectedUsers.clear();

            clearChatSelection();

            Platform.runLater(() -> {

                if (connectionRequestsList != null) {

                    connectionRequestsList
                            .getItems()
                            .clear();
                }

                if (connectedUsersList != null) {

                    connectedUsersList
                            .getItems()
                            .clear();
                }

                if (chatArea != null) {

                    chatArea.clear();
                }

                updateServerUI(false);
            });
        }
    }


    // =========================================================
    // GETTERS
    // =========================================================

    public Map<String, ClientHandler> getConnectedUsers() {

        return connectedUsers;
    }


    public String getSelectedChatUser() {

        return selectedChatUser;
    }


    public String getSelectedChatType() {

        return selectedChatType;
    }


    public int getSelectedConversationId() {

        return selectedConversationId;
    }


    public String getSelectedGroupName() {

        return selectedGroupName;
    }


    // =========================================================
    // USER SEARCH
    // =========================================================

    private int findUserId(
            String username
    ) throws Exception {

        User user =
                userDAO.findByUsername(
                        username
                );

        return user == null
                ? -1
                : user.getIdUser();
    }


    private int findUserIdSafely(
            String username
    ) {

        try {

            return findUserId(username);

        } catch (Exception e) {

            return 0;
        }
    }


    // =========================================================
    // USER DISPLAY
    // =========================================================

    private String formatUser(
            String username,
            String ip
    ) {

        return username
                + "  |  IP : "
                + (
                ip == null
                        ? "UNKNOWN"
                        : ip
        );
    }


    private String extractUsername(
            String value
    ) {

        if (value == null) {
            return "";
        }

        int index =
                value.indexOf("  |");

        return (
                index < 0
                        ? value
                        : value.substring(
                        0,
                        index
                )
        ).trim();
    }


    // =========================================================
    // CHAT TYPE NORMALIZATION
    // =========================================================

    private String normalizeChatType(
            String type
    ) {

        if (type == null) {

            return ChatMessage.TYPE_TEXT;
        }

        String normalized =
                type.trim()
                        .toUpperCase();

        if (
                ChatMessage.TYPE_TEXT.equals(
                        normalized
                )
        ) {

            return ChatMessage.TYPE_TEXT;
        }

        if (
                ChatMessage.TYPE_FILE.equals(
                        normalized
                )
        ) {

            return ChatMessage.TYPE_FILE;
        }

        /*
         * La base actuelle autorise uniquement
         * TEXT et FILE.
         */
        return ChatMessage.TYPE_TEXT;
    }


    // =========================================================
    // LOGGING
    // =========================================================

    private Log createLog(
            int userId,
            int documentId,
            String action,
            String level,
            String description,
            String ip
    ) {

        Log log =
                new Log();

        log.setUserId(
                userId
        );

        log.setDocumentId(
                documentId
        );

        log.setAction(
                action
        );

        log.setLevel(
                level
        );

        log.setDescription(
                description
        );

        log.setIpAddress(
                ip
        );

        return log;
    }


    private void insertLogSafely(
            Log log
    ) {

        try {

            if (log != null) {

                logDAO.insert(
                        log
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "Log : "
                            + safeMessage(e)
            );
        }
    }


    // =========================================================
    // ALERT
    // =========================================================

    private void showAlert(
            Alert.AlertType type,
            String title,
            String message
    ) {

        Platform.runLater(() -> {

            Alert alert =
                    new Alert(type);

            alert.setTitle(
                    title
            );

            alert.setHeaderText(
                    null
            );

            alert.setContentText(
                    message == null
                            ? ""
                            : message
            );

            alert.showAndWait();
        });
    }


    // =========================================================
    // ERROR MESSAGE
    // =========================================================

    private String safeMessage(
            Exception e
    ) {

        if (e == null) {

            return "Erreur inconnue.";
        }

        if (
                e.getMessage() == null
                        || e.getMessage().isBlank()
        ) {

            return e.getClass()
                    .getSimpleName();
        }

        return e.getMessage();
    }
}
