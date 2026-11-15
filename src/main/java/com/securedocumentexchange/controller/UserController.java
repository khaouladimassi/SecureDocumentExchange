package com.securedocumentexchange.controller;

import com.securedocumentexchange.dao.ChatConversationDAO;
import com.securedocumentexchange.dao.ChatMessageDAO;
import com.securedocumentexchange.dao.UserDAO;
import com.securedocumentexchange.model.ChatMessage;
import com.securedocumentexchange.model.User;
import com.securedocumentexchange.network.SecureClient;
import com.securedocumentexchange.network.ServerDiscovery;
import com.securedocumentexchange.network.ServerInfo;
import com.securedocumentexchange.service.SessionManager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * ================================================================
 * USER CONTROLLER
 * ================================================================
 *
 * Contrôleur principal de l'interface utilisateur.
 *
 * Responsabilités :
 *
 * 1. Gestion de la session utilisateur
 * 2. Découverte des serveurs UDP
 * 3. Connexion TLS
 * 4. Gestion des documents
 * 5. Envoi de fichiers
 * 6. Réception de fichiers
 * 7. Chat privé
 * 8. Historique des conversations
 * 9. Persistance PostgreSQL
 *
 * ================================================================
 */
public class UserController implements SecureClient.Listener {

    // =========================================================
    // FXML - CONNEXION
    // =========================================================

    @FXML
    private TextField usernameField;

    @FXML
    private ListView<Object> serverList;

    @FXML
    private Label connectionStatus;

    @FXML
    private ProgressIndicator searchProgress;

    @FXML
    private Button connectButton;

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
    // NETWORK
    // =========================================================

    private final SecureClient secureClient =
            new SecureClient(this);

    // =========================================================
    // DAO
    // =========================================================

    private final ChatConversationDAO conversationDAO =
            new ChatConversationDAO();

    private final ChatMessageDAO messageDAO =
            new ChatMessageDAO();

    private final UserDAO userDAO =
            new UserDAO();

    // =========================================================
    // SESSION / CHAT STATE
    // =========================================================

    private volatile int currentConversationId = 0;

    private volatile int currentUserId = 0;

    private volatile String currentUsername;

    private volatile File selectedFile;

    // =========================================================
    // FILES RECEIVED
    // =========================================================

    /**
     * Liste des fichiers réellement reçus sur le PC.
     *
     * Elle permet ensuite de savoir quel fichier ouvrir
     * lorsque l'utilisateur sélectionne une ligne dans
     * documentList.
     */
    private final List<File> receivedFiles =
            new ArrayList<>();

    // =========================================================
    // INITIALIZATION
    // =========================================================

    @FXML
    public void initialize() {

        // -----------------------------------------------------
        // SESSION UTILISATEUR
        // -----------------------------------------------------

        if (SessionManager.isLoggedIn()) {

            User user =
                    SessionManager.getCurrentUser();

            if (user != null) {

                currentUserId =
                        user.getIdUser();

                currentUsername =
                        user.getUsername();

                if (usernameField != null) {

                    usernameField.setText(
                            currentUsername == null
                                    ? ""
                                    : currentUsername
                    );

                    usernameField.setDisable(true);
                }
            }
        }

        // -----------------------------------------------------
        // CHAT AREA
        // -----------------------------------------------------

        if (chatArea != null) {

            chatArea.setEditable(false);
            chatArea.setWrapText(true);
        }

        // -----------------------------------------------------
        // CHAT INPUT
        // -----------------------------------------------------

        if (chatMessageField != null) {

            chatMessageField.setDisable(true);

            chatMessageField.setOnAction(
                    event -> sendChatMessage()
            );
        }

        // -----------------------------------------------------
        // SEARCH PROGRESS
        // -----------------------------------------------------

        if (searchProgress != null) {

            searchProgress.setVisible(false);
        }

        // -----------------------------------------------------
        // INITIAL STATUS
        // -----------------------------------------------------

        setStatus(
                "🔴 Non connecté"
        );
    }

    // =========================================================
    // UTILISATEUR AUTHENTIFIÉ
    // =========================================================

    public void setAuthenticatedUser(User user) {

        if (user == null) {

            return;
        }

        currentUserId =
                user.getIdUser();

        currentUsername =
                user.getUsername();

        if (usernameField != null) {

            usernameField.setText(
                    currentUsername == null
                            ? ""
                            : currentUsername
            );

            usernameField.setDisable(true);
        }

        System.out.println(
                "========================================"
        );

        System.out.println(
                "✓ UserController initialisé"
        );

        System.out.println(
                "Username : "
                        + currentUsername
        );

        System.out.println(
                "User ID  : "
                        + currentUserId
        );

        System.out.println(
                "========================================"
        );
    }

    // =========================================================
    // GETTER - UTILISATEUR
    // =========================================================

    public int getCurrentUserId() {

        return currentUserId;
    }

    public String getCurrentUsername() {

        return currentUsername;
    }

    // =========================================================
    // GETTER - SECURE CLIENT
    // =========================================================

    public SecureClient getSecureClient() {

        return secureClient;
    }

    // =========================================================
    // DISCOVERY SERVERS
    // =========================================================

    /**
     * Recherche les serveurs SecureDocumentExchange
     * sur le réseau local grâce à UDP.
     *
     * Cette méthode est appelée par :
     *
     * onAction="#discoverServers"
     *
     * dans UserView.fxml.
     */
    @FXML
    private void discoverServers() {

        if (searchProgress != null) {

            searchProgress.setVisible(true);
        }

        setStatus(
                "🟡 Recherche des serveurs..."
        );

        if (serverList != null) {

            serverList.getItems().clear();
        }

        // -----------------------------------------------------
        // RECHERCHE EN ARRIÈRE-PLAN
        // -----------------------------------------------------

        Thread discoveryThread =
                new Thread(
                        () -> {

                            try {

                                System.out.println();
                                System.out.println(
                                        "========================================"
                                );
                                System.out.println(
                                        "      RECHERCHE SERVEURS"
                                );
                                System.out.println(
                                        "========================================"
                                );

                                List<ServerInfo> servers =
                                        ServerDiscovery.discover(
                                                3000
                                        );

                                Platform.runLater(
                                        () -> {

                                            if (serverList != null) {

                                                serverList
                                                        .getItems()
                                                        .clear();

                                                for (
                                                        ServerInfo server :
                                                        servers
                                                ) {

                                                    if (server != null) {

                                                        serverList
                                                                .getItems()
                                                                .add(server);
                                                    }
                                                }
                                            }

                                            if (servers.isEmpty()) {

                                                setStatus(
                                                        "🟠 Aucun serveur trouvé"
                                                );

                                                showAlert(
                                                        Alert.AlertType.INFORMATION,
                                                        "Recherche",
                                                        "Aucun serveur SecureDocumentExchange n'a été trouvé sur le réseau."
                                                );

                                            } else {

                                                setStatus(
                                                        "🟢 "
                                                                + servers.size()
                                                                + " serveur(s) trouvé(s)"
                                                );
                                            }

                                            if (searchProgress != null) {

                                                searchProgress
                                                        .setVisible(false);
                                            }
                                        }
                                );

                            } catch (Exception e) {

                                Platform.runLater(
                                        () -> {

                                            if (searchProgress != null) {

                                                searchProgress
                                                        .setVisible(false);
                                            }

                                            setStatus(
                                                    "⚠ Erreur recherche"
                                            );

                                            showAlert(
                                                    Alert.AlertType.ERROR,
                                                    "Recherche des serveurs",
                                                    safeMessage(e)
                                            );
                                        }
                                );
                            }
                        },
                        "User-Server-Discovery"
                );

        discoveryThread.setDaemon(true);

        discoveryThread.start();
    }

    // =========================================================
    // CONNECTION
    // =========================================================

    @FXML
    private void connectToServer() {

        if (serverList == null) {

            return;
        }

        Object selected =
                serverList
                        .getSelectionModel()
                        .getSelectedItem();

        if (selected == null) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Connexion",
                    "Sélectionnez un serveur."
            );

            return;
        }

        String username =
                usernameField == null
                        ? currentUsername
                        : usernameField.getText();

        if (username == null
                || username.isBlank()) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Connexion",
                    "Nom utilisateur requis."
            );

            return;
        }

        try {

            String host =
                    property(
                            selected,
                            "getHost",
                            "getIp",
                            "getAddress",
                            "getServerIp"
                    );

            int port =
                    intProperty(
                            selected,
                            "getPort",
                            "getServerPort"
                    );

            if (host == null
                    || host.isBlank()
                    || port <= 0) {

                throw new IllegalArgumentException(
                        "Informations serveur invalides."
                );
            }

            if (connectButton != null) {

                connectButton.setDisable(true);
            }

            setStatus(
                    "🟡 Connexion TLS à "
                            + host
                            + ":"
                            + port
                            + "..."
            );

            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "CONNEXION SERVEUR"
            );

            System.out.println(
                    "Utilisateur : "
                            + username.trim()
            );

            System.out.println(
                    "Serveur : "
                            + host
            );

            System.out.println(
                    "Port TLS : "
                            + port
            );

            System.out.println(
                    "========================================"
            );

            secureClient.connect(
                    host,
                    port,
                    username.trim()
            );

        } catch (Exception e) {

            if (connectButton != null) {

                connectButton.setDisable(false);
            }

            showAlert(
                    Alert.AlertType.ERROR,
                    "Connexion",
                    safeMessage(e)
            );
        }
    }

    // =========================================================
    // CONNECTION - COMPATIBILITY
    // =========================================================

    @FXML
    private void connect() {

        connectToServer();
    }

    // =========================================================
    // DISCONNECT
    // =========================================================

    @FXML
    private void disconnect() {

        try {

            secureClient.close();

        } catch (Exception e) {

            System.err.println(
                    "Erreur fermeture SecureClient : "
                            + safeMessage(e)
            );
        }

        Platform.runLater(
                () -> {

                    setStatus(
                            "🔴 Déconnecté"
                    );

                    if (connectButton != null) {

                        connectButton.setDisable(false);
                    }
                }
        );
    }

    // =========================================================
    // FILE SELECTION
    // =========================================================

    @FXML
    private void selectFile() {

        if (selectedFileLabel == null
                || selectedFileLabel.getScene() == null) {

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

        if (file != null
                && file.isFile()
                && file.canRead()) {

            selectedFile = file;

            selectedFileLabel.setText(
                    "📄 " + file.getName()
            );
        }
    }

    // =========================================================
    // SEND FILE
    // =========================================================

    @FXML
    private void sendFile() {

        // -----------------------------------------------------
        // SI AUCUN FICHIER N'EST SÉLECTIONNÉ
        // -----------------------------------------------------

        if (selectedFile == null) {

            selectFile();

            return;
        }

        // -----------------------------------------------------
        // VÉRIFICATION CONNEXION
        // -----------------------------------------------------

        if (!secureClient.isReady()) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Document",
                    "Vous devez être connecté au serveur."
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

                        secureClient.sendFile(
                                file,
                                sensitive
                        );

                        Platform.runLater(
                                () -> {

                                    if (documentList != null) {

                                        documentList
                                                .getItems()
                                                .add(
                                                        "Moi → "
                                                                + file.getName()
                                                                + (
                                                                sensitive
                                                                        ? " 🔒"
                                                                        : " 📄"
                                                        )
                                                );
                                    }

                                    selectedFile = null;

                                    if (selectedFileLabel != null) {

                                        selectedFileLabel.setText(
                                                "Aucun fichier sélectionné"
                                        );
                                    }

                                    if (sensitiveCheckBox != null) {

                                        sensitiveCheckBox.setSelected(
                                                false
                                        );
                                    }
                                }
                        );

                    } catch (Exception e) {

                        Platform.runLater(
                                () -> showAlert(
                                        Alert.AlertType.ERROR,
                                        "Document",
                                        safeMessage(e)
                                )
                        );
                    }

                },
                "User-FileTransfer"
        ).start();
    }

    // =========================================================
    // DOWNLOAD FILE
    // =========================================================

    /**
     * Téléchargement d'un fichier déjà reçu.
     *
     * Le fichier reçu est déjà présent localement.
     * Cette méthode permet de choisir où en sauvegarder
     * une copie.
     */
    @FXML
    private void downloadFile() {

        int index =
                getSelectedDocumentIndex();

        if (index < 0
                || index >= receivedFiles.size()) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Téléchargement",
                    "Sélectionnez d'abord un document reçu."
            );

            return;
        }

        File source =
                receivedFiles.get(index);

        if (source == null
                || !source.exists()
                || !source.isFile()) {

            showAlert(
                    Alert.AlertType.ERROR,
                    "Téléchargement",
                    "Le fichier source n'existe plus."
            );

            return;
        }

        FileChooser chooser =
                new FileChooser();

        chooser.setTitle(
                "Enregistrer le document"
        );

        chooser.setInitialFileName(
                source.getName()
        );

        File destination =
                chooser.showSaveDialog(
                        documentList
                                .getScene()
                                .getWindow()
                );

        if (destination == null) {

            return;
        }

        try {

            java.nio.file.Files.copy(
                    source.toPath(),
                    destination.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Téléchargement",
                    "Document enregistré avec succès."
            );

        } catch (Exception e) {

            showAlert(
                    Alert.AlertType.ERROR,
                    "Téléchargement",
                    safeMessage(e)
            );
        }
    }

    // =========================================================
    // OPEN FILE
    // =========================================================

    @FXML
    private void openFile() {

        int index =
                getSelectedDocumentIndex();

        if (index < 0
                || index >= receivedFiles.size()) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Ouverture",
                    "Sélectionnez d'abord un document reçu."
            );

            return;
        }

        File file =
                receivedFiles.get(index);

        if (file == null
                || !file.exists()
                || !file.isFile()) {

            showAlert(
                    Alert.AlertType.ERROR,
                    "Ouverture",
                    "Le fichier n'existe plus."
            );

            return;
        }

        try {

            if (!Desktop.isDesktopSupported()) {

                throw new UnsupportedOperationException(
                        "L'ouverture automatique des fichiers n'est pas supportée sur ce système."
                );
            }

            Desktop.getDesktop().open(file);

        } catch (Exception e) {

            showAlert(
                    Alert.AlertType.ERROR,
                    "Ouverture",
                    safeMessage(e)
            );
        }
    }

    // =========================================================
    // SELECTED DOCUMENT INDEX
    // =========================================================

    private int getSelectedDocumentIndex() {

        if (documentList == null) {

            return -1;
        }

        int index =
                documentList
                        .getSelectionModel()
                        .getSelectedIndex();

        return index;
    }

    // =========================================================
    // SEND CHAT MESSAGE
    // =========================================================

    @FXML
    private void sendChatMessage() {

        if (chatMessageField == null) {

            return;
        }

        String text =
                chatMessageField.getText();

        if (text == null
                || text.isBlank()) {

            return;
        }

        if (currentConversationId <= 0) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Chat",
                    "Aucune conversation sélectionnée."
            );

            return;
        }

        if (!secureClient.isReady()) {

            showAlert(
                    Alert.AlertType.WARNING,
                    "Chat",
                    "Vous devez être connecté au serveur."
            );

            return;
        }

        text =
                text.trim();

        try {

            secureClient.sendChatMessage(
                    currentConversationId,
                    currentUsername,
                    text
            );

            persistLocalText(
                    text
            );

            appendChat(
                    "Moi",
                    text
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
    // SELECT CONVERSATION
    // =========================================================

    public void selectConversation(
            int conversationId,
            String title
    ) {

        if (conversationId <= 0) {

            return;
        }

        currentConversationId =
                conversationId;

        if (chatArea != null) {

            chatArea.clear();

            if (title != null
                    && !title.isBlank()) {

                chatArea.appendText(
                        title
                                + "\n────────────────────────\n"
                );
            }
        }

        loadHistory();

        updateChatState();
    }

    // =========================================================
    // SELECT GROUP CHAT
    // =========================================================

    public void selectGroupChat(
            int conversationId,
            String groupName
    ) {

        selectConversation(
                conversationId,
                "👥 Groupe : "
                        + (
                        groupName == null
                                || groupName.isBlank()
                                ? "Groupe"
                                : groupName
                )
        );
    }

    // =========================================================
    // GET CURRENT CONVERSATION
    // =========================================================

    public int getCurrentConversationId() {

        return currentConversationId;
    }

    // =========================================================
    // PRIVATE CONVERSATION
    // =========================================================

    private void ensurePrivateConversation() {

        if (currentUserId <= 0) {

            System.err.println(
                    "Impossible de créer la conversation : "
                            + "ID utilisateur invalide."
            );

            return;
        }

        try {

            currentConversationId =
                    conversationDAO
                            .getOrCreatePrivateConversationWithFirstAdmin(
                                    currentUserId,
                                    "Conversation"
                            );

            System.out.println(
                    "✓ Conversation privée : ID="
                            + currentConversationId
            );

            loadHistory();

        } catch (Exception e) {

            System.err.println(
                    "Conversation privée : "
                            + safeMessage(e)
            );
        }

        updateChatState();
    }

    // =========================================================
    // LOAD HISTORY
    // =========================================================

    private void loadHistory() {

        if (currentConversationId <= 0) {

            return;
        }

        try {

            var list =
                    messageDAO.getMessages(
                            currentConversationId,
                            500
                    );

            Platform.runLater(
                    () -> {

                        if (chatArea == null) {

                            return;
                        }

                        chatArea.clear();

                        for (ChatMessage message : list) {

                            String content =
                                    "FILE".equalsIgnoreCase(
                                            message.getType()
                                    )
                                            ? "📎 "
                                            + message.getContent()
                                            : message.getContent();

                            appendChat(
                                    message.getSenderUsername(),
                                    content
                            );
                        }
                    }
            );

        } catch (Exception e) {

            System.err.println(
                    "Historique : "
                            + safeMessage(e)
            );
        }
    }

    // =========================================================
    // PERSIST LOCAL MESSAGE
    // =========================================================

    private void persistLocalText(
            String text
    ) {

        if (currentConversationId <= 0
                || currentUserId <= 0
                || text == null
                || text.isBlank()) {

            return;
        }

        try {

            ChatMessage message =
                    new ChatMessage(
                            currentConversationId,
                            currentUserId,
                            "TEXT",
                            text,
                            0
                    );

            messageDAO.insert(
                    message
            );

        } catch (Exception ignored) {

            /*
             * Erreur volontairement ignorée.
             * Aucun affichage dans la console.
             */
        }
    }

    // =========================================================
    // SECURE CLIENT LISTENER
    // =========================================================

    @Override
    public void onAccessGranted() {

        Platform.runLater(
                () -> {

                    setStatus(
                            "🟢 Connecté et autorisé"
                    );

                    if (connectButton != null) {

                        connectButton.setDisable(true);
                    }
                }
        );

        ensurePrivateConversation();

        try {

            if (currentConversationId > 0) {

                secureClient.requestChatHistory(
                        currentConversationId
                );
            }

        } catch (Exception ignored) {

            /*
             * Erreur volontairement ignorée.
             * Aucun affichage dans la console.
             */
        }
    }

    // =========================================================
    // ACCESS DENIED
    // =========================================================

    @Override
    public void onAccessDenied() {

        Platform.runLater(
                () -> {

                    setStatus(
                            "🔴 Accès refusé"
                    );

                    if (connectButton != null) {

                        connectButton.setDisable(false);
                    }

                    if (chatMessageField != null) {

                        chatMessageField.setDisable(true);
                    }
                }
        );
    }

    // =========================================================
    // DISCONNECTED
    // =========================================================

    @Override
    public void onDisconnected() {

        Platform.runLater(
                () -> {

                    setStatus(
                            "🔴 Déconnecté"
                    );

                    if (connectButton != null) {

                        connectButton.setDisable(false);
                    }

                    updateChatState();
                }
        );
    }

    // =========================================================
    // ERROR
    // =========================================================

    @Override
    public void onError(
            Exception e
    ) {

        Platform.runLater(
                () -> setStatus(
                        "⚠ "
                                + safeMessage(e)
                )
        );
    }

    // =========================================================
    // FILE RECEIVED
    // =========================================================

    @Override
    public void onFileReceived(
            String filename,
            boolean sensitive,
            File file
    ) {

        Platform.runLater(
                () -> {

                    if (file != null) {

                        receivedFiles.add(file);
                    }

                    if (documentList != null) {

                        documentList
                                .getItems()
                                .add(
                                        "Admin → "
                                                + filename
                                                + (
                                                sensitive
                                                        ? " 🔒"
                                                        : " 📄"
                                        )
                                );
                    }
                }
        );
    }

    // =========================================================
    // CHAT MESSAGE RECEIVED
    // =========================================================

    @Override
    public void onChatMessage(
            int conversationId,
            String sender,
            String message
    ) {

        if (conversationId
                != currentConversationId) {

            return;
        }

        try {

            if (currentUserId > 0) {

                ChatMessage chatMessage =
                        new ChatMessage(
                                conversationId,
                                findSenderId(sender),
                                "TEXT",
                                message,
                                0
                        );

                messageDAO.insert(
                        chatMessage
                );
            }

        } catch (Exception ignored) {

            /*
             * Erreur volontairement ignorée.
             * Aucun affichage dans la console.
             */
        }

        Platform.runLater(
                () -> appendChat(
                        sender,
                        message
                )
        );
    }

    // =========================================================
    // CHAT FILE RECEIVED
    // =========================================================

    @Override
    public void onChatFileReceived(
            int conversationId,
            String sender,
            String filename,
            boolean sensitive,
            File file
    ) {

        if (conversationId
                != currentConversationId) {

            return;
        }

        Platform.runLater(
                () -> appendChat(
                        sender,
                        "📎 "
                                + filename
                                + (
                                sensitive
                                        ? " 🔒"
                                        : ""
                        )
                )
        );
    }

    // =========================================================
    // CHAT HISTORY START
    // =========================================================

    @Override
    public void onChatHistoryStart(
            int conversationId,
            int messageCount
    ) {

        if (conversationId
                != currentConversationId) {

            return;
        }

        Platform.runLater(
                () -> {

                    if (chatArea != null) {

                        chatArea.clear();
                    }
                }
        );
    }

    // =========================================================
    // CHAT HISTORY MESSAGE
    // =========================================================

    @Override
    public void onChatHistoryMessage(
            int conversationId,
            long messageId,
            int senderId,
            String sender,
            String type,
            String content,
            int documentId
    ) {

        if (conversationId
                != currentConversationId) {

            return;
        }

        Platform.runLater(
                () -> appendChat(
                        sender,
                        "FILE".equalsIgnoreCase(type)
                                ? "📎 " + content
                                : content
                )
        );
    }

    // =========================================================
    // CHAT HISTORY END
    // =========================================================

    @Override
    public void onChatHistoryEnd(
            int conversationId
    ) {

        if (conversationId
                == currentConversationId) {

            Platform.runLater(
                    this::updateChatState
            );
        }
    }

    // =========================================================
    // DISCOVERED SERVERS
    // =========================================================

    public void setDiscoveredServers(
            Collection<?> servers
    ) {

        if (serverList == null) {

            return;
        }

        Platform.runLater(
                () -> {

                    serverList
                            .getItems()
                            .clear();

                    if (servers != null) {

                        for (Object server : servers) {

                            if (server != null) {

                                serverList
                                        .getItems()
                                        .add(server);
                            }
                        }
                    }
                }
        );
    }

    // =========================================================
    // APPEND CHAT
    // =========================================================

    private void appendChat(
            String sender,
            String message
    ) {

        if (chatArea == null
                || message == null
                || message.isBlank()) {

            return;
        }

        chatArea.appendText(
                (
                        sender == null
                                || sender.isBlank()
                                ? "SYSTEM"
                                : sender
                )
                        + " : "
                        + message
                        + "\n"
        );

        chatArea.setScrollTop(
                Double.MAX_VALUE
        );
    }

    // =========================================================
    // UPDATE CHAT STATE
    // =========================================================

    private void updateChatState() {

        if (chatMessageField != null) {

            boolean ready =
                    secureClient.isReady()
                            && currentConversationId > 0;

            chatMessageField.setDisable(
                    !ready
            );
        }
    }

    // =========================================================
    // STATUS
    // =========================================================

    private void setStatus(
            String status
    ) {

        if (connectionStatus != null) {

            connectionStatus.setText(
                    status == null
                            ? ""
                            : status
            );
        }

        updateChatState();
    }

    // =========================================================
    // FIND USER ID
    // =========================================================

    private int findSenderId(
            String sender
    ) throws Exception {

        if (sender == null
                || sender.isBlank()) {

            return 0;
        }

        User user =
                userDAO.findByUsername(
                        sender
                );

        return user == null
                ? 0
                : user.getIdUser();
    }

    // =========================================================
    // REFLECTION - PROPERTY
    // =========================================================

    private String property(
            Object object,
            String... methods
    ) {

        if (object == null) {

            return null;
        }

        for (String methodName : methods) {

            try {

                Method method =
                        object.getClass()
                                .getMethod(
                                        methodName
                                );

                Object value =
                        method.invoke(
                                object
                        );

                if (value != null) {

                    String result =
                            String.valueOf(value);

                    if (!result.isBlank()) {

                        return result;
                    }
                }

            } catch (Exception ignored) {
            }
        }

        return null;
    }

    // =========================================================
    // REFLECTION - INTEGER PROPERTY
    // =========================================================

    private int intProperty(
            Object object,
            String... methods
    ) {

        String value =
                property(
                        object,
                        methods
                );

        if (value == null) {

            return 0;
        }

        try {

            return Integer.parseInt(
                    value
            );

        } catch (Exception e) {

            return 0;
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

        Platform.runLater(
                () -> {

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
                }
        );
    }

    // =========================================================
    // SAFE ERROR MESSAGE
    // =========================================================

    private String safeMessage(
            Exception e
    ) {

        if (e == null) {

            return "Erreur inconnue.";
        }

        if (e.getMessage() == null
                || e.getMessage().isBlank()) {

            return e.getClass()
                    .getSimpleName();
        }

        return e.getMessage();
    }
}