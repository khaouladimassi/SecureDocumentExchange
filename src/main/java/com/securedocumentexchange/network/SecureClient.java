package com.securedocumentexchange.network;

import com.securedocumentexchange.security.FileTransferCrypto;
import com.securedocumentexchange.security.TLSManager;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.util.Base64;

/**
 * ================================================================
 * SECURE CLIENT
 * ================================================================
 *
 * Client TLS sécurisé.
 *
 * Responsabilités :
 *
 * - connexion TLS ;
 * - demande de connexion ;
 * - réception de l'autorisation ;
 * - état READY ;
 * - envoi / réception de fichiers ;
 * - chiffrement AES-256-GCM ;
 * - chat privé / groupe ;
 * - réception de l'historique ;
 * - fermeture propre.
 *
 * ================================================================
 */
public class SecureClient {

    // ============================================================
    // LISTENER
    // ============================================================

    public interface Listener {

        void onAccessGranted();

        void onAccessDenied();

        void onDisconnected();

        void onError(Exception e);

        void onFileReceived(
                String filename,
                boolean sensitive,
                File file
        );

        default void onChatMessage(
                int conversationId,
                String sender,
                String message
        ) {
        }

        default void onChatFileReceived(
                int conversationId,
                String sender,
                String filename,
                boolean sensitive,
                File file
        ) {
        }

        default void onChatHistoryStart(
                int conversationId,
                int messageCount
        ) {
        }

        default void onChatHistoryMessage(
                int conversationId,
                long messageId,
                int senderId,
                String sender,
                String type,
                String content,
                int documentId
        ) {
        }

        default void onChatHistoryEnd(
                int conversationId
        ) {
        }
    }

    // ============================================================
    // CONSTANTES
    // ============================================================

    private static final int BUFFER_SIZE = 8192;

    private static final long MAX_FILE_SIZE =
            500L * 1024L * 1024L;

    private static final long MAX_ENCRYPTED_FILE_SIZE =
            MAX_FILE_SIZE + 2L * 1024L * 1024L;

    private static final int MAX_CHAT_MESSAGE_LENGTH = 5000;

    private static final int MAX_USERNAME_LENGTH = 50;

    // ============================================================
    // CONNEXION
    // ============================================================

    private SSLSocket socket;

    private DataInputStream input;

    private DataOutputStream output;

    private final Listener listener;

    /**
     * Protection contre plusieurs écritures
     * simultanées sur le socket TLS.
     */
    private final Object outputLock =
            new Object();

    // ============================================================
    // ETAT
    // ============================================================

    private volatile boolean accessGranted = false;

    private volatile boolean running = false;

    private volatile boolean ready = false;

    private volatile String username;

    // ============================================================
    // CONSTRUCTEUR
    // ============================================================

    public SecureClient(
            Listener listener
    ) {

        this.listener = listener;
    }

    // ============================================================
    // CONNEXION
    // ============================================================

    public synchronized void connect(
            String host,
            int port,
            String username
    ) throws Exception {

        if (isConnected()) {

            throw new IOException(
                    "Le client est déjà connecté."
            );
        }

        if (
                host == null
                        || host.isBlank()
                        || port < 1
                        || port > 65535
                        || username == null
                        || username.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Paramètres de connexion invalides."
            );
        }

        String cleanUsername =
                username.trim();

        if (
                cleanUsername.length()
                        > MAX_USERNAME_LENGTH
        ) {

            throw new IllegalArgumentException(
                    "Nom utilisateur trop long."
            );
        }

        if (
                containsControlCharacters(
                        cleanUsername
                )
        ) {

            throw new IllegalArgumentException(
                    "Nom utilisateur invalide."
            );
        }

        this.username =
                cleanUsername;

        SSLContext context =
                TLSManager.createClientSSLContext();

        socket =
                (SSLSocket)
                        context.getSocketFactory()
                                .createSocket(
                                        host,
                                        port
                                );

        socket.setEnabledProtocols(
                TLSManager.getEnabledProtocols()
        );

        socket.startHandshake();

        input =
                new DataInputStream(
                        new BufferedInputStream(
                                socket.getInputStream()
                        )
                );

        output =
                new DataOutputStream(
                        new BufferedOutputStream(
                                socket.getOutputStream()
                        )
                );

        running = true;
        accessGranted = false;
        ready = false;

        /*
         * ========================================================
         * SERVER_READY
         * ========================================================
         */

        String serverReady =
                input.readUTF();

        if (
                !"SERVER_READY".equals(
                        serverReady
                )
        ) {

            throw new IOException(
                    "Réponse serveur inattendue : "
                            + serverReady
            );
        }

        /*
         * ========================================================
         * REQUEST_CONNECTION
         * ========================================================
         *
         * USER -> SERVER
         *
         * REQUEST_CONNECTION
         * username
         */

        synchronized (outputLock) {

            output.writeUTF(
                    "REQUEST_CONNECTION"
            );

            output.writeUTF(
                    this.username
            );

            output.flush();
        }

        /*
         * ========================================================
         * THREAD D'ECOUTE
         * ========================================================
         */

        Thread listenerThread =
                new Thread(
                        this::listen,
                        "SecureClient-Listener"
                );

        listenerThread.setDaemon(true);

        listenerThread.start();

        System.out.println(
                "🔐 Demande de connexion envoyée pour : "
                        + this.username
        );
    }

    // ============================================================
    // ECOUTE SERVEUR
    // ============================================================

    private void listen() {

        boolean notifyDisconnected =
                false;

        try {

            while (
                    running
                            && socket != null
                            && !socket.isClosed()
            ) {

                String command;

                try {

                    command =
                            input.readUTF();

                } catch (EOFException e) {

                    notifyDisconnected = true;

                    break;
                }

                if (
                        command == null
                                || command.isBlank()
                ) {

                    continue;
                }

                command =
                        command.trim();

                switch (command) {

                    // =================================================
                    // ACCES ACCORDE
                    // =================================================

                    case "ACCESS_GRANTED":

                        accessGranted = true;
                        ready = false;

                        System.out.println(
                                "✅ Accès accordé par le serveur."
                        );

                        /*
                         * Le client confirme immédiatement
                         * qu'il est prêt.
                         */
                        send(
                                "READY"
                        );

                        break;

                    // =================================================
                    // ACCES REFUSE
                    // =================================================

                    case "ACCESS_DENIED":

                        accessGranted = false;
                        ready = false;

                        if (listener != null) {

                            listener.onAccessDenied();
                        }

                        close();

                        return;

                    // =================================================
                    // READY ACK
                    // =================================================

                    case "READY_ACK":

                        ready = true;

                        System.out.println(
                                "🟢 Client prêt : "
                                        + username
                        );

                        if (listener != null) {

                            listener.onAccessGranted();
                        }

                        break;

                    // =================================================
                    // FICHIER
                    // =================================================

                    case "FILE_TRANSFER":

                        receiveFile();

                        break;

                    // =================================================
                    // CHAT MESSAGE
                    // =================================================

                    case "CHAT_MESSAGE":

                        receiveChatMessage();

                        break;

                    // =================================================
                    // CHAT FILE
                    // =================================================

                    case "CHAT_FILE":

                        receiveChatFile();

                        break;

                    // =================================================
                    // HISTORIQUE
                    // =================================================

                    case "CHAT_HISTORY_START":

                        receiveHistoryStart();

                        break;

                    case "CHAT_HISTORY_MESSAGE":

                        receiveHistoryMessage();

                        break;

                    case "CHAT_HISTORY_END":

                        receiveHistoryEnd();

                        break;

                    // =================================================
                    // SERVER CLOSING
                    // =================================================

                    case "SERVER_CLOSING":

                        close();

                        return;

                    // =================================================
                    // ERROR
                    // =================================================

                    case "ERROR":

                        String message =
                                input.readUTF();

                        if (listener != null) {

                            listener.onError(
                                    new IOException(
                                            message
                                    )
                            );
                        }

                        break;

                    // =================================================
                    // PONG
                    // =================================================

                    case "PONG":

                        break;

                    // =================================================
                    // INCONNU
                    // =================================================

                    default:

                        System.err.println(
                                "⚠ Message serveur inconnu : "
                                        + command
                        );

                        break;
                }
            }

        } catch (Exception e) {

            if (
                    running
                            && listener != null
            ) {

                listener.onError(e);
            }

        } finally {

            if (
                    running
                            && listener != null
                            && notifyDisconnected
            ) {

                try {

                    listener.onDisconnected();

                } catch (Exception ignored) {
                }
            }

            running = false;
            ready = false;
            accessGranted = false;
        }
    }

    // ============================================================
    // ENVOI FICHIER
    // ============================================================

    public synchronized void sendFile(
            File file,
            boolean sensitive
    ) throws Exception {

        checkReady();

        if (
                file == null
                        || !file.isFile()
                        || !file.canRead()
        ) {

            throw new IOException(
                    "Fichier invalide."
            );
        }

        String filename =
                sanitizeFilename(
                        file.getName()
                );

        long original =
                file.length();

        if (
                filename.isBlank()
                        || original <= 0
                        || original > MAX_FILE_SIZE
        ) {

            throw new IOException(
                    "Fichier invalide."
            );
        }

        File toSend =
                file;

        File encryptedFile =
                null;

        SecretKey key =
                null;

        try {

            if (sensitive) {

                key =
                        FileTransferCrypto.generateKey();

                Path directory =
                        Paths.get(
                                System.getProperty(
                                        "java.io.tmpdir"
                                ),
                                "SecureDocumentExchange"
                        );

                Files.createDirectories(
                        directory
                );

                encryptedFile =
                        File.createTempFile(
                                "client_encrypted_",
                                ".enc",
                                directory.toFile()
                        );

                FileTransferCrypto.encryptFile(
                        file,
                        encryptedFile,
                        key
                );

                toSend =
                        encryptedFile;
            }

            long transmittedSize =
                    toSend.length();

            if (
                    transmittedSize <= 0
                            || transmittedSize
                            > MAX_ENCRYPTED_FILE_SIZE
            ) {

                throw new IOException(
                        "Taille fichier chiffré invalide."
                );
            }

            synchronized (outputLock) {

                output.writeUTF(
                        "FILE_TRANSFER"
                );

                output.writeUTF(
                        filename
                );

                output.writeBoolean(
                        sensitive
                );

                output.writeLong(
                        original
                );

                if (sensitive) {

                    output.writeUTF(
                            Base64.getEncoder()
                                    .encodeToString(
                                            key.getEncoded()
                                    )
                    );
                }

                output.writeLong(
                        transmittedSize
                );

                output.flush();

                sendFileBytes(
                        toSend
                );
            }

        } finally {

            deleteFile(
                    encryptedFile
            );
        }
    }

    // ============================================================
    // CHAT MESSAGE - SEND
    // ============================================================

    /**
     * ============================================================
     * USER -> SERVER
     * ============================================================
     *
     * CHAT_MESSAGE
     * int conversationId
     * UTF message
     *
     * IMPORTANT :
     *
     * sender n'est PAS envoyé.
     *
     * Le serveur connaît déjà :
     *
     * username
     *
     * via ClientHandler.
     */
    public void sendChatMessage(
            int conversationId,
            String sender,
            String message
    ) throws IOException {

        checkReady();

        if (conversationId <= 0) {

            throw new IOException(
                    "Conversation invalide."
            );
        }

        if (
                message == null
                        || message.isBlank()
        ) {

            return;
        }

        message =
                message.trim();

        if (
                message.length()
                        > MAX_CHAT_MESSAGE_LENGTH
        ) {

            throw new IOException(
                    "Message trop long."
            );
        }

        if (
                containsControlCharacters(
                        message
                )
        ) {

            throw new IOException(
                    "Message invalide."
            );
        }

        synchronized (outputLock) {

            if (
                    output == null
                            || !running
            ) {

                throw new IOException(
                        "Connexion indisponible."
                );
            }

            output.writeUTF(
                    "CHAT_MESSAGE"
            );

            output.writeInt(
                    conversationId
            );

            /*
             * NE PAS envoyer sender.
             *
             * Le serveur utilise :
             *
             * ClientHandler.getUsername()
             */
            output.writeUTF(
                    message
            );

            output.flush();
        }

        System.out.println(
                "📤 USER -> SERVER"
                        + " | utilisateur="
                        + username
                        + " | conversation="
                        + conversationId
                        + " | message="
                        + message
        );
    }

    /**
     * Version recommandée.
     */
    public void sendChatMessage(
            int conversationId,
            String message
    ) throws IOException {

        sendChatMessage(
                conversationId,
                username,
                message
        );
    }

    /**
     * Compatibilité ancienne version.
     */
    public void sendChatMessage(
            String message
    ) throws IOException {

        sendChatMessage(
                1,
                username,
                message
        );
    }

    // ============================================================
    // CHAT HISTORY REQUEST
    // ============================================================

    /**
     * USER -> SERVER
     *
     * CHAT_HISTORY_REQUEST
     * int conversationId
     */
    public void requestChatHistory(
            int conversationId
    ) throws IOException {

        checkReady();

        if (conversationId <= 0) {

            throw new IOException(
                    "Conversation invalide."
            );
        }

        synchronized (outputLock) {

            output.writeUTF(
                    "CHAT_HISTORY_REQUEST"
            );

            output.writeInt(
                    conversationId
            );

            output.flush();
        }
    }

    // ============================================================
    // CLOSE
    // ============================================================

    public synchronized void close() {

        boolean wasRunning =
                running;

        running = false;
        ready = false;
        accessGranted = false;

        /*
         * Informer le serveur avant fermeture.
         */
        try {

            if (
                    output != null
                            && socket != null
                            && !socket.isClosed()
            ) {

                synchronized (outputLock) {

                    output.writeUTF(
                            "DISCONNECT"
                    );

                    output.flush();
                }
            }

        } catch (Exception ignored) {
        }

        /*
         * Fermer le socket.
         *
         * Cela débloque également le thread
         * d'écoute si celui-ci attend readUTF().
         */
        try {

            if (socket != null) {

                socket.close();
            }

        } catch (Exception ignored) {
        }

        input = null;
        output = null;

        if (wasRunning) {

            System.out.println(
                    "🔌 Connexion client fermée."
            );
        }
    }

    // ============================================================
    // RECEIVE CHAT MESSAGE
    // ============================================================

    /**
     * ============================================================
     * SERVER -> USER
     * ============================================================
     *
     * CHAT_MESSAGE
     * int conversationId
     * UTF sender
     * UTF message
     */
    private void receiveChatMessage()
            throws IOException {

        int conversationId =
                input.readInt();

        String sender =
                input.readUTF();

        String message =
                input.readUTF();

        if (conversationId <= 0) {

            throw new IOException(
                    "Conversation invalide."
            );
        }

        if (
                sender == null
                        || sender.isBlank()
        ) {

            throw new IOException(
                    "Expéditeur invalide."
            );
        }

        if (
                message == null
                        || message.isBlank()
        ) {

            throw new IOException(
                    "Message chat invalide."
            );
        }

        sender =
                sender.trim();

        message =
                message.trim();

        if (
                sender.length()
                        > MAX_USERNAME_LENGTH
        ) {

            throw new IOException(
                    "Expéditeur trop long."
            );
        }

        if (
                message.length()
                        > MAX_CHAT_MESSAGE_LENGTH
        ) {

            throw new IOException(
                    "Message chat trop long."
            );
        }

        if (
                containsControlCharacters(
                        sender
                )
                        || containsControlCharacters(
                        message
                )
        ) {

            throw new IOException(
                    "Message chat invalide."
            );
        }

        System.out.println(
                "💬 Message reçu"
                        + " | conversation="
                        + conversationId
                        + " | sender="
                        + sender
                        + " | message="
                        + message
        );

        if (listener != null) {

            listener.onChatMessage(
                    conversationId,
                    sender,
                    message
            );
        }
    }

    // ============================================================
    // RECEIVE CHAT FILE
    // ============================================================

    private void receiveChatFile()
            throws Exception {

        int conversationId =
                input.readInt();

        int documentId =
                input.readInt();

        String filename =
                sanitizeFilename(
                        input.readUTF()
                );

        boolean sensitive =
                input.readBoolean();

        long original =
                input.readLong();

        if (
                conversationId <= 0
                        || documentId <= 0
                        || filename.isBlank()
                        || original <= 0
                        || original > MAX_FILE_SIZE
        ) {

            throw new IOException(
                    "Fichier chat invalide."
            );
        }

        byte[] keyBytes = null;

        if (sensitive) {

            String encodedKey =
                    input.readUTF();

            try {

                keyBytes =
                        Base64.getDecoder()
                                .decode(
                                        encodedKey
                                );

            } catch (IllegalArgumentException e) {

                throw new IOException(
                        "Clé AES invalide.",
                        e
                );
            }

            if (keyBytes.length != 32) {

                throw new IOException(
                        "Clé AES-256 invalide."
                );
            }
        }

        long transmitted =
                input.readLong();

        if (
                transmitted <= 0
                        || transmitted
                        > MAX_ENCRYPTED_FILE_SIZE
        ) {

            throw new IOException(
                    "Taille transmise invalide."
            );
        }

        Path directory =
                Paths.get(
                        System.getProperty(
                                "user.home"
                        ),
                        "SecureDocumentExchange",
                        "ChatReceived"
                );

        Files.createDirectories(
                directory
        );

        File temp =
                File.createTempFile(
                        "chat_client_",
                        ".tmp",
                        directory.toFile()
                );

        try {

            receiveBytes(
                    temp,
                    transmitted
            );

            Path destination =
                    createUniquePath(
                            directory,
                            filename
                    );

            File readyFile;

            if (sensitive) {

                readyFile =
                        FileTransferCrypto.decryptFile(
                                temp,
                                new SecretKeySpec(
                                        keyBytes,
                                        "AES"
                                ),
                                destination.toFile()
                        );

                deleteFile(temp);

            } else {

                Files.move(
                        temp.toPath(),
                        destination,
                        StandardCopyOption.REPLACE_EXISTING
                );

                readyFile =
                        destination.toFile();
            }

            if (
                    readyFile == null
                            || !readyFile.exists()
                            || readyFile.length()
                            != original
            ) {

                deleteFile(
                        readyFile
                );

                throw new IOException(
                        "Taille fichier chat incorrecte."
                );
            }

            if (listener != null) {

                listener.onChatFileReceived(
                        conversationId,
                        username,
                        filename,
                        sensitive,
                        readyFile
                );
            }

            temp = null;

        } finally {

            deleteFile(temp);
        }
    }

    // ============================================================
    // RECEIVE GENERAL FILE
    // ============================================================

    private void receiveFile()
            throws Exception {

        String filename =
                sanitizeFilename(
                        input.readUTF()
                );

        boolean sensitive =
                input.readBoolean();

        long original =
                input.readLong();

        if (
                filename.isBlank()
                        || original <= 0
                        || original > MAX_FILE_SIZE
        ) {

            throw new IOException(
                    "Fichier invalide."
            );
        }

        byte[] keyBytes = null;

        if (sensitive) {

            String encodedKey =
                    input.readUTF();

            try {

                keyBytes =
                        Base64.getDecoder()
                                .decode(
                                        encodedKey
                                );

            } catch (IllegalArgumentException e) {

                throw new IOException(
                        "Clé AES invalide.",
                        e
                );
            }

            if (keyBytes.length != 32) {

                throw new IOException(
                        "Clé AES-256 invalide."
                );
            }
        }

        long transmitted =
                input.readLong();

        if (
                transmitted <= 0
                        || transmitted
                        > MAX_ENCRYPTED_FILE_SIZE
        ) {

            throw new IOException(
                    "Taille fichier invalide."
            );
        }

        Path directory =
                Paths.get(
                        System.getProperty(
                                "user.home"
                        ),
                        "SecureDocumentExchange",
                        "Received"
                );

        Files.createDirectories(
                directory
        );

        File temp =
                File.createTempFile(
                        "received_",
                        ".tmp",
                        directory.toFile()
                );

        try {

            receiveBytes(
                    temp,
                    transmitted
            );

            Path destination =
                    createUniquePath(
                            directory,
                            filename
                    );

            File readyFile;

            if (sensitive) {

                readyFile =
                        FileTransferCrypto.decryptFile(
                                temp,
                                new SecretKeySpec(
                                        keyBytes,
                                        "AES"
                                ),
                                destination.toFile()
                        );

                deleteFile(temp);

            } else {

                Files.move(
                        temp.toPath(),
                        destination,
                        StandardCopyOption.REPLACE_EXISTING
                );

                readyFile =
                        destination.toFile();
            }

            if (
                    readyFile == null
                            || !readyFile.exists()
                            || readyFile.length()
                            != original
            ) {

                deleteFile(
                        readyFile
                );

                throw new IOException(
                        "Taille fichier incorrecte."
                );
            }

            if (listener != null) {

                listener.onFileReceived(
                        filename,
                        sensitive,
                        readyFile
                );
            }

            temp = null;

        } finally {

            deleteFile(temp);
        }
    }

    // ============================================================
    // HISTORY START
    // ============================================================

    private void receiveHistoryStart()
            throws IOException {

        int conversationId =
                input.readInt();

        int count =
                input.readInt();

        if (conversationId <= 0) {

            throw new IOException(
                    "Conversation historique invalide."
            );
        }

        if (count < 0) {

            throw new IOException(
                    "Nombre de messages invalide."
            );
        }

        if (listener != null) {

            listener.onChatHistoryStart(
                    conversationId,
                    count
            );
        }
    }

    // ============================================================
    // HISTORY MESSAGE
    // ============================================================

    private void receiveHistoryMessage()
            throws IOException {

        int conversationId =
                input.readInt();

        long messageId =
                input.readLong();

        int senderId =
                input.readInt();

        String sender =
                input.readUTF();

        String type =
                input.readUTF();

        String content =
                input.readUTF();

        int documentId =
                input.readInt();

        if (conversationId <= 0) {

            throw new IOException(
                    "Conversation historique invalide."
            );
        }

        if (messageId < 0) {

            throw new IOException(
                    "ID message invalide."
            );
        }

        if (sender == null) {

            sender = "";
        }

        if (type == null) {

            type = "TEXT";
        }

        if (content == null) {

            content = "";
        }

        if (listener != null) {

            listener.onChatHistoryMessage(
                    conversationId,
                    messageId,
                    senderId,
                    sender,
                    type,
                    content,
                    documentId
            );
        }
    }

    // ============================================================
    // HISTORY END
    // ============================================================

    private void receiveHistoryEnd()
            throws IOException {

        int conversationId =
                input.readInt();

        if (conversationId <= 0) {

            throw new IOException(
                    "Conversation historique invalide."
            );
        }

        if (listener != null) {

            listener.onChatHistoryEnd(
                    conversationId
            );
        }
    }

    // ============================================================
    // SEND FILE BYTES
    // ============================================================

    private void sendFileBytes(
            File file
    ) throws IOException {

        try (
                InputStream in =
                        new BufferedInputStream(
                                new FileInputStream(
                                        file
                                )
                        )
        ) {

            byte[] buffer =
                    new byte[BUFFER_SIZE];

            int bytesRead;

            while (
                    (bytesRead =
                            in.read(buffer))
                            != -1
            ) {

                output.write(
                        buffer,
                        0,
                        bytesRead
                );
            }

            output.flush();
        }
    }

    // ============================================================
    // RECEIVE BYTES
    // ============================================================

    private void receiveBytes(
            File file,
            long size
    ) throws IOException {

        try (
                OutputStream out =
                        new BufferedOutputStream(
                                new FileOutputStream(
                                        file
                                )
                        )
        ) {

            byte[] buffer =
                    new byte[BUFFER_SIZE];

            long remaining =
                    size;

            while (remaining > 0) {

                int bytesRead =
                        input.read(
                                buffer,
                                0,
                                (int) Math.min(
                                        BUFFER_SIZE,
                                        remaining
                                )
                        );

                if (bytesRead < 0) {

                    throw new EOFException(
                            "Connexion interrompue pendant le transfert."
                    );
                }

                if (bytesRead == 0) {

                    continue;
                }

                out.write(
                        buffer,
                        0,
                        bytesRead
                );

                remaining -= bytesRead;
            }

            out.flush();
        }
    }

    // ============================================================
    // SEND COMMAND
    // ============================================================

    private void send(
            String message
    ) throws IOException {

        synchronized (outputLock) {

            if (
                    output == null
                            || !running
            ) {

                throw new IOException(
                        "Connexion indisponible."
                );
            }

            output.writeUTF(
                    message == null
                            ? ""
                            : message
            );

            output.flush();
        }
    }

    // ============================================================
    // CHECK READY
    // ============================================================

    private void checkReady()
            throws IOException {

        if (
                !running
                        || socket == null
                        || socket.isClosed()
                        || output == null
        ) {

            throw new IOException(
                    "Client non connecté."
            );
        }

        if (!accessGranted) {

            throw new IOException(
                    "Accès non autorisé."
            );
        }

        if (!ready) {

            throw new IOException(
                    "Client non prêt."
            );
        }

        if (
                username == null
                        || username.isBlank()
        ) {

            throw new IOException(
                    "Utilisateur non identifié."
            );
        }
    }

    // ============================================================
    // FILENAME SECURITY
    // ============================================================

    private String sanitizeFilename(
            String filename
    ) {

        if (filename == null) {

            return "";
        }

        String clean =
                new File(filename)
                        .getName()
                        .trim();

        if (
                clean.isBlank()
                        || clean.equals(".")
                        || clean.equals("..")
                        || clean.length() > 255
                        || containsControlCharacters(
                        clean
                )
        ) {

            return "";
        }

        return clean;
    }

    // ============================================================
    // UNIQUE PATH
    // ============================================================

    private Path createUniquePath(
            Path directory,
            String filename
    ) {

        String safeFilename =
                sanitizeFilename(
                        filename
                );

        Path path =
                directory.resolve(
                        safeFilename
                );

        if (!Files.exists(path)) {

            return path;
        }

        int dot =
                safeFilename.lastIndexOf('.');

        String name =
                dot > 0
                        ? safeFilename.substring(
                        0,
                        dot
                )
                        : safeFilename;

        String extension =
                dot > 0
                        ? safeFilename.substring(dot)
                        : "";

        int index = 1;

        do {

            path =
                    directory.resolve(
                            name
                                    + "_"
                                    + index
                                    + extension
                    );

            index++;

        } while (Files.exists(path));

        return path;
    }

    // ============================================================
    // DELETE FILE
    // ============================================================

    private void deleteFile(
            File file
    ) {

        if (file == null) {

            return;
        }

        try {

            Files.deleteIfExists(
                    file.toPath()
            );

        } catch (Exception ignored) {
        }
    }

    // ============================================================
    // CONTROL CHARACTERS
    // ============================================================

    private boolean containsControlCharacters(
            String value
    ) {

        if (value == null) {

            return true;
        }

        for (
                int i = 0;
                i < value.length();
                i++
        ) {

            if (
                    Character.isISOControl(
                            value.charAt(i)
                    )
            ) {

                return true;
            }
        }

        return false;
    }

    // ============================================================
    // GETTERS
    // ============================================================

    public boolean isConnected() {

        return running
                && socket != null
                && !socket.isClosed();
    }

    public boolean isAccessGranted() {

        return accessGranted;
    }

    public boolean isReady() {

        return ready;
    }

    public String getUsername() {

        return username;
    }
}