package com.securedocumentexchange.network;

import com.securedocumentexchange.security.TLSManager;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ================================================================
 * SECURE SERVER
 * ================================================================
 *
 * Serveur TLS principal de l'application.
 *
 * Responsabilités :
 *
 * - démarrage du serveur TLS
 * - découverte UDP du serveur
 * - acceptation des connexions clientes
 * - gestion des ClientHandler
 * - transfert de fichiers
 * - chat
 * - historique du chat
 * - arrêt propre du serveur
 *
 * IMPORTANT :
 * Un ClientHandler est créé dès qu'une connexion TCP/TLS arrive,
 * mais il n'est considéré comme "client connecté" qu'après
 * acceptation de la demande par l'administrateur.
 */
public class SecureServer {

    // =========================================================
    // SERVER INFORMATION
    // =========================================================

    private final String serverName;
    private final int port;
    private final ClientHandler.Listener listener;

    // =========================================================
    // SERVER SOCKET
    // =========================================================

    private SSLServerSocket serverSocket;
    private ServerDiscovery discovery;

    private volatile boolean running = false;

    // =========================================================
    // CLIENTS
    // =========================================================

    /**
     * Clients actuellement acceptés/actifs.
     */
    private final List<ClientHandler> clients =
            new CopyOnWriteArrayList<>();

    /**
     * Tous les handlers TLS actuellement ouverts.
     *
     * Cette liste permet notamment de fermer proprement les
     * connexions encore en attente lors de l'arrêt du serveur.
     */
    private final List<ClientHandler> handlers =
            new CopyOnWriteArrayList<>();

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public SecureServer(
            String serverName,
            int port,
            ClientHandler.Listener listener
    ) {

        if (serverName == null || serverName.isBlank()) {
            throw new IllegalArgumentException(
                    "Nom serveur invalide."
            );
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "Port invalide."
            );
        }

        this.serverName = serverName.trim();
        this.port = port;
        this.listener = listener;
    }

    // =========================================================
    // START
    // =========================================================

    public synchronized void start()
            throws Exception {

        if (running) {
            return;
        }

        String localIp = getLocalIp();

        if (localIp == null
                || localIp.isBlank()
                || "127.0.0.1".equals(localIp)) {

            throw new IllegalStateException(
                    "Aucune IP LAN valide trouvée."
            );
        }

        // =====================================================
        // TLS CONTEXT
        // =====================================================

        SSLContext context =
                TLSManager.createServerSSLContext();

        if (context == null) {
            throw new IllegalStateException(
                    "SSLContext serveur null."
            );
        }

        // =====================================================
        // TLS SERVER SOCKET
        // =====================================================

        SSLServerSocketFactory factory =
                context.getServerSocketFactory();

        serverSocket =
                (SSLServerSocket)
                        factory.createServerSocket(port);

        String[] enabledProtocols =
                TLSManager.getEnabledProtocols();

        if (enabledProtocols != null
                && enabledProtocols.length > 0) {

            serverSocket.setEnabledProtocols(
                    enabledProtocols
            );
        }

        /*
         * Authentification mutuelle désactivée.
         *
         * Le serveur authentifie l'utilisateur au niveau
         * applicatif après établissement du tunnel TLS.
         */
        serverSocket.setNeedClientAuth(false);

        running = true;

        System.out.println();
        System.out.println(
                "================================="
        );
        System.out.println(
                "      SECURE DOCUMENT SERVER"
        );
        System.out.println(
                "================================="
        );
        System.out.println(
                "✓ Serveur TLS démarré"
        );
        System.out.println(
                "Nom : " + serverName
        );
        System.out.println(
                "IP : " + localIp
        );
        System.out.println(
                "PORT : " + port
        );
        System.out.println(
                "================================="
        );

        // =====================================================
        // UDP DISCOVERY
        // =====================================================

        try {

            discovery = new ServerDiscovery();

            discovery.startServerDiscovery(
                    serverName,
                    port
            );

            System.out.println(
                    "✓ Discovery UDP démarrée"
            );

        } catch (Exception e) {

            discovery = null;

            System.err.println(
                    "⚠ Discovery UDP impossible : "
                            + e.getMessage()
            );
        }

        // =====================================================
        // ACCEPT THREAD
        // =====================================================

        Thread acceptThread =
                new Thread(
                        this::acceptClients,
                        "SecureServer-Accept"
                );

        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    // =========================================================
    // ACCEPT CLIENTS
    // =========================================================

    private void acceptClients() {

        while (running) {

            try {

                SSLSocket socket =
                        (SSLSocket)
                                serverSocket.accept();

                if (!running) {

                    closeSocket(socket);
                    break;
                }

                socket.setKeepAlive(true);
                socket.setTcpNoDelay(true);

                String ip =
                        socket.getInetAddress()
                                .getHostAddress();

                System.out.println(
                        "✓ Nouvelle connexion TLS : "
                                + ip
                );

                ClientHandler handler =
                        createClientHandler(socket);

                handlers.add(handler);

                Thread clientThread =
                        new Thread(
                                handler,
                                "ClientHandler-" + ip
                        );

                clientThread.setDaemon(true);
                clientThread.start();

            } catch (Exception e) {

                if (running) {

                    System.err.println(
                            "❌ Erreur acceptation : "
                                    + e.getMessage()
                    );
                }
            }
        }
    }

    // =========================================================
    // CREATE CLIENT HANDLER
    // =========================================================

    private ClientHandler createClientHandler(
            SSLSocket socket
    ) {

        return new ClientHandler(
                socket,
                new ClientHandler.Listener() {

                    // =========================================
                    // CONNECTION REQUEST
                    // =========================================

                    @Override
                    public void onConnectionRequest(
                            ConnectionRequest request
                    ) {

                        if (request == null) {
                            return;
                        }

                        ClientHandler client =
                                request.getClientHandler();

                        if (client == null) {
                            return;
                        }

                        /*
                         * IMPORTANT :
                         *
                         * On NE met PAS encore le client dans
                         * "clients".
                         *
                         * Il est encore PENDING.
                         *
                         * L'AdminController décidera ensuite :
                         *
                         *     client.grantAccess()
                         *
                         * ou
                         *
                         *     client.denyAccess()
                         */
                        System.out.println(
                                "📥 Demande de connexion : "
                                        + request.getUsername()
                                        + " / "
                                        + request.getClientIp()
                        );

                        if (listener != null) {

                            listener.onConnectionRequest(
                                    request
                            );
                        }
                    }

                    // =========================================
                    // USER -> SERVER FILE
                    // =========================================

                    @Override
                    public void onFileReceivedFromUser(
                            ClientHandler handler,
                            File file,
                            String originalFilename,
                            boolean sensitive
                    ) {

                        if (handler == null
                                || file == null) {
                            return;
                        }

                        if (listener != null) {

                            listener.onFileReceivedFromUser(
                                    handler,
                                    file,
                                    originalFilename,
                                    sensitive
                            );
                        }
                    }

                    // =========================================
                    // CHAT MESSAGE COMPATIBILITY
                    // =========================================

                    @Override
                    public void onChatMessage(
                            ClientHandler handler,
                            String message
                    ) {

                        if (handler == null
                                || message == null
                                || message.isBlank()) {
                            return;
                        }

                        if (listener != null) {

                            listener.onChatMessage(
                                    handler,
                                    message
                            );
                        }
                    }

                    // =========================================
                    // CHAT MESSAGE
                    // =========================================

                    @Override
                    public void onChatMessage(
                            ClientHandler handler,
                            int conversationId,
                            String message
                    ) {

                        if (handler == null
                                || conversationId <= 0
                                || message == null
                                || message.isBlank()) {
                            return;
                        }

                        if (listener != null) {

                            listener.onChatMessage(
                                    handler,
                                    conversationId,
                                    message
                            );
                        }
                    }

                    // =========================================
                    // CHAT FILE
                    // =========================================

                    @Override
                    public void onChatFileReceived(
                            ClientHandler handler,
                            int conversationId,
                            File file,
                            String originalFilename,
                            boolean sensitive
                    ) {

                        if (handler == null
                                || conversationId <= 0
                                || file == null) {
                            return;
                        }

                        if (listener != null) {

                            listener.onChatFileReceived(
                                    handler,
                                    conversationId,
                                    file,
                                    originalFilename,
                                    sensitive
                            );
                        }
                    }

                    // =========================================
                    // CHAT HISTORY
                    // =========================================

                    @Override
                    public void onChatHistoryRequest(
                            ClientHandler handler,
                            int conversationId
                    ) {

                        if (handler == null
                                || conversationId <= 0) {
                            return;
                        }

                        if (listener != null) {

                            listener.onChatHistoryRequest(
                                    handler,
                                    conversationId
                            );
                        }
                    }

                    // =========================================
                    // CLIENT DISCONNECTED
                    // =========================================

                    @Override
                    public void onClientDisconnected(
                            ClientHandler handler
                    ) {

                        removeClient(handler);
                        removeHandler(handler);

                        if (listener != null) {

                            listener.onClientDisconnected(
                                    handler
                            );
                        }
                    }
                }
        );
    }

    // =========================================================
    // ADD CLIENT
    // =========================================================

    /**
     * Ajoute un client uniquement lorsqu'il est accepté.
     */
    public synchronized boolean addClient(
            ClientHandler handler
    ) {

        if (handler == null) {
            return false;
        }

        if (!handler.isRunning()) {
            return false;
        }

        String username =
                handler.getUsername();

        if (username == null
                || username.isBlank()) {

            return false;
        }

        ClientHandler existing =
                getClientByUsername(username);

        if (existing != null
                && existing != handler) {

            System.out.println(
                    "⚠ Username déjà connecté : "
                            + username
            );

            return false;
        }

        if (!clients.contains(handler)) {

            clients.add(handler);

            System.out.println(
                    "✓ Client accepté et ajouté : "
                            + username
            );
        }

        return true;
    }

    // =========================================================
    // REMOVE CLIENT
    // =========================================================

    private void removeClient(
            ClientHandler handler
    ) {

        if (handler == null) {
            return;
        }

        if (clients.remove(handler)) {

            System.out.println(
                    "✓ Client supprimé : "
                            + handler.getUsername()
            );
        }
    }

    // =========================================================
    // REMOVE HANDLER
    // =========================================================

    private void removeHandler(
            ClientHandler handler
    ) {

        if (handler != null) {
            handlers.remove(handler);
        }
    }

    // =========================================================
    // GET CLIENTS
    // =========================================================

    /**
     * Retourne les clients acceptés.
     */
    public List<ClientHandler> getClients() {

        return new ArrayList<>(clients);
    }

    // =========================================================
    // GET ALL HANDLERS
    // =========================================================

    public List<ClientHandler> getHandlers() {

        return new ArrayList<>(handlers);
    }

    // =========================================================
    // GET CONNECTED CLIENTS
    // =========================================================

    public List<ClientHandler> getConnectedClients() {

        List<ClientHandler> result =
                new ArrayList<>();

        for (ClientHandler client : clients) {

            if (client != null
                    && client.isRunning()
                    && client.isAccessGranted()
                    && client.isReady()) {

                result.add(client);
            }
        }

        return result;
    }

    // =========================================================
    // GET CLIENT BY USERNAME
    // =========================================================

    public ClientHandler getClientByUsername(
            String username
    ) {

        if (username == null
                || username.isBlank()) {

            return null;
        }

        String target =
                username.trim();

        for (ClientHandler client : clients) {

            if (client == null
                    || !client.isRunning()) {

                continue;
            }

            String current =
                    client.getUsername();

            if (current != null
                    && target.equalsIgnoreCase(current)) {

                return client;
            }
        }

        return null;
    }

    // =========================================================
    // USER ONLINE
    // =========================================================

    public boolean isUserOnline(
            String username
    ) {

        ClientHandler client =
                getClientByUsername(username);

        return client != null
                && client.isRunning()
                && client.isAccessGranted()
                && client.isReady();
    }

    // =========================================================
    // CONNECTED USERNAMES
    // =========================================================

    public List<String> getConnectedUsernames() {

        List<String> result =
                new ArrayList<>();

        for (ClientHandler client :
                getConnectedClients()) {

            if (client.getUsername() != null) {

                result.add(
                        client.getUsername()
                );
            }
        }

        Collections.sort(
                result,
                String.CASE_INSENSITIVE_ORDER
        );

        return result;
    }

    // =========================================================
    // SEND FILE TO USER
    // =========================================================

    public boolean sendFileToUser(
            String username,
            File file,
            boolean sensitive
    ) {

        if (!running
                || username == null
                || username.isBlank()
                || file == null) {

            return false;
        }

        ClientHandler client =
                getClientByUsername(username);

        if (client == null
                || !client.isAccessGranted()
                || !client.isReady()) {

            return false;
        }

        try {

            client.sendFile(
                    file,
                    sensitive
            );

            return true;

        } catch (Exception e) {

            System.err.println(
                    "❌ Envoi impossible vers "
                            + username
                            + " : "
                            + e.getMessage()
            );

            return false;
        }
    }

    // =========================================================
    // SEND FILE TO MULTIPLE USERS
    // =========================================================

    public int sendFileToUsers(
            Collection<String> usernames,
            File file,
            boolean sensitive
    ) {

        if (usernames == null
                || file == null) {

            return 0;
        }

        int count = 0;

        for (String username : usernames) {

            if (sendFileToUser(
                    username,
                    file,
                    sensitive
            )) {

                count++;
            }
        }

        return count;
    }

    // =========================================================
    // BROADCAST FILE
    // =========================================================

    public int broadcastFile(
            File file,
            boolean sensitive,
            ClientHandler excludedClient
    ) {

        if (file == null) {
            return 0;
        }

        int count = 0;

        for (ClientHandler client :
                getConnectedClients()) {

            if (client == null
                    || client == excludedClient) {
                continue;
            }

            try {

                client.sendFile(
                        file,
                        sensitive
                );

                count++;

            } catch (Exception e) {

                System.err.println(
                        "⚠ Broadcast impossible vers "
                                + client.getUsername()
                );
            }
        }

        return count;
    }

    // =========================================================
    // SEND CHAT MESSAGE TO USER
    // =========================================================

    public boolean sendChatMessageToUser(
            String username,
            int conversationId,
            String sender,
            String message
    ) {

        if (!running
                || username == null
                || username.isBlank()
                || conversationId <= 0
                || message == null
                || message.isBlank()) {

            return false;
        }

        ClientHandler client =
                getClientByUsername(username);

        if (client == null
                || !client.isAccessGranted()
                || !client.isReady()) {

            return false;
        }

        try {

            client.sendChatMessage(
                    conversationId,
                    sender,
                    message
            );

            return true;

        } catch (Exception e) {

            System.err.println(
                    "❌ Chat impossible vers "
                            + username
                            + " : "
                            + e.getMessage()
            );

            return false;
        }
    }

    // =========================================================
    // CHAT COMPATIBILITY
    // =========================================================

    public boolean sendChatMessageToUser(
            String username,
            String sender,
            String message
    ) {

        return sendChatMessageToUser(
                username,
                1,
                sender,
                message
        );
    }

    // =========================================================
    // BROADCAST CHAT
    // =========================================================

    public int broadcastChatMessage(
            int conversationId,
            String sender,
            String message,
            ClientHandler excludedClient
    ) {

        if (conversationId <= 0
                || message == null
                || message.isBlank()) {

            return 0;
        }

        int count = 0;

        for (ClientHandler client :
                getConnectedClients()) {

            if (client == null
                    || client == excludedClient) {
                continue;
            }

            try {

                client.sendChatMessage(
                        conversationId,
                        sender,
                        message
                );

                count++;

            } catch (Exception e) {

                System.err.println(
                        "⚠ Broadcast chat impossible vers "
                                + client.getUsername()
                );
            }
        }

        return count;
    }

    // =========================================================
    // CHAT COMPATIBILITY
    // =========================================================

    public int broadcastChatMessage(
            String sender,
            String message,
            ClientHandler excludedClient
    ) {

        return broadcastChatMessage(
                1,
                sender,
                message,
                excludedClient
        );
    }

    // =========================================================
    // BROADCAST COMMAND
    // =========================================================

    public int broadcastMessage(
            String message,
            ClientHandler excludedClient
    ) {

        if (message == null
                || message.isBlank()) {

            return 0;
        }

        int count = 0;

        for (ClientHandler client :
                getConnectedClients()) {

            if (client == null
                    || client == excludedClient) {
                continue;
            }

            try {

                client.send(message);

                count++;

            } catch (Exception e) {

                System.err.println(
                        "⚠ Broadcast impossible vers "
                                + client.getUsername()
                );
            }
        }

        return count;
    }

    // =========================================================
    // COUNTS
    // =========================================================

    public int getClientCount() {

        return clients.size();
    }

    public int getConnectedClientCount() {

        return getConnectedClients().size();
    }

    public int getHandlerCount() {

        return handlers.size();
    }

    // =========================================================
    // STOP
    // =========================================================

    public synchronized void stop() {

        if (!running) {
            return;
        }

        System.out.println(
                "⏹ Arrêt du serveur..."
        );

        running = false;

        // =====================================================
        // INFORM ALL HANDLERS
        // =====================================================

        for (ClientHandler handler : handlers) {

            try {

                handler.send(
                        "SERVER_CLOSING"
                );

            } catch (Exception ignored) {
            }
        }

        // =====================================================
        // CLOSE ALL HANDLERS
        // =====================================================

        for (ClientHandler handler : handlers) {

            try {

                handler.close();

            } catch (Exception ignored) {
            }
        }

        handlers.clear();
        clients.clear();

        // =====================================================
        // STOP DISCOVERY
        // =====================================================

        if (discovery != null) {

            try {

                discovery.stop();

            } catch (Exception ignored) {
            }

            discovery = null;
        }

        // =====================================================
        // CLOSE SERVER SOCKET
        // =====================================================

        if (serverSocket != null) {

            try {

                serverSocket.close();

            } catch (Exception ignored) {
            }

            serverSocket = null;
        }

        System.out.println(
                "✓ Serveur arrêté."
        );
    }

    // =========================================================
    // STATUS
    // =========================================================

    public boolean isRunning() {

        return running;
    }

    public int getPort() {

        return port;
    }

    public String getServerName() {

        return serverName;
    }

    // =========================================================
    // LOCAL IP
    // =========================================================

    public static String getLocalIp() {

        try {

            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();

            if (interfaces != null) {

                while (interfaces.hasMoreElements()) {

                    NetworkInterface ni =
                            interfaces.nextElement();

                    if (ni == null) {
                        continue;
                    }

                    if (!ni.isUp()
                            || ni.isLoopback()
                            || ni.isVirtual()) {

                        continue;
                    }

                    String displayName =
                            ni.getDisplayName() == null
                                    ? ""
                                    : ni.getDisplayName()
                                    .toLowerCase();

                    String interfaceName =
                            ni.getName() == null
                                    ? ""
                                    : ni.getName()
                                    .toLowerCase();

                    // =========================================
                    // IGNORE VIRTUAL INTERFACES
                    // =========================================

                    if (displayName.contains("virtualbox")
                            || displayName.contains("vmware")
                            || displayName.contains("hyper-v")
                            || displayName.contains("host-only")
                            || interfaceName.contains("virtualbox")
                            || interfaceName.contains("vmware")
                            || interfaceName.contains("hyper-v")) {

                        continue;
                    }

                    Enumeration<InetAddress> addresses =
                            ni.getInetAddresses();

                    while (addresses.hasMoreElements()) {

                        InetAddress address =
                                addresses.nextElement();

                        if (!(address instanceof Inet4Address)
                                || address.isLoopbackAddress()
                                || address.isLinkLocalAddress()) {

                            continue;
                        }

                        String ip =
                                address.getHostAddress();

                        if (ip == null
                                || ip.isBlank()) {
                            continue;
                        }

                        // =====================================
                        // IGNORE VIRTUALBOX / NAT
                        // =====================================

                        if (ip.startsWith("192.168.56.")
                                || ip.startsWith("192.168.57.")
                                || ip.startsWith("10.0.2.")) {

                            continue;
                        }

                        // =====================================
                        // PRIVATE LAN
                        // =====================================

                        if (ip.startsWith("192.168.")
                                || ip.startsWith("10.")
                                || isPrivate172(ip)) {

                            return ip;
                        }
                    }
                }
            }

            // =================================================
            // FALLBACK
            // =================================================

            InetAddress localHost =
                    InetAddress.getLocalHost();

            if (localHost != null) {

                String fallback =
                        localHost.getHostAddress();

                if (isValidLanIp(fallback)) {

                    return fallback;
                }
            }

        } catch (Exception ignored) {
        }

        return "127.0.0.1";
    }

    // =========================================================
    // VALID LAN IP
    // =========================================================

    private static boolean isValidLanIp(
            String ip
    ) {

        if (ip == null
                || ip.isBlank()
                || "127.0.0.1".equals(ip)) {

            return false;
        }

        if (ip.startsWith("192.168.56.")
                || ip.startsWith("192.168.57.")
                || ip.startsWith("10.0.2.")) {

            return false;
        }

        return ip.startsWith("192.168.")
                || ip.startsWith("10.")
                || isPrivate172(ip);
    }

    // =========================================================
    // PRIVATE 172.16.0.0/12
    // =========================================================

    private static boolean isPrivate172(
            String ip
    ) {

        try {

            String[] parts =
                    ip.split("\\.");

            if (parts.length != 4) {
                return false;
            }

            int first =
                    Integer.parseInt(parts[0]);

            int second =
                    Integer.parseInt(parts[1]);

            return first == 172
                    && second >= 16
                    && second <= 31;

        } catch (Exception e) {

            return false;
        }
    }

    // =========================================================
    // CLOSE SOCKET
    // =========================================================

    private void closeSocket(
            SSLSocket socket
    ) {

        if (socket == null) {
            return;
        }

        try {

            socket.close();

        } catch (Exception ignored) {
        }
    }
}