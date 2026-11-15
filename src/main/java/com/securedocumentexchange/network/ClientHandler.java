package com.securedocumentexchange.network;

import com.securedocumentexchange.security.FileTransferCrypto;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
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
 * CLIENT HANDLER
 * ================================================================
 *
 * Gère une connexion TLS avec un utilisateur.
 *
 * Responsabilités :
 *
 * - réception d'une demande de connexion ;
 * - autorisation / refus ;
 * - gestion de l'état READY ;
 * - transfert de fichiers ;
 * - réception de messages de chat ;
 * - envoi de messages de chat ;
 * - réception / envoi de fichiers de chat ;
 * - demande d'historique ;
 * - fermeture propre de la connexion.
 *
 * ================================================================
 */
public class ClientHandler implements Runnable {

    // ============================================================
    // LISTENER
    // ============================================================

    public interface Listener {

        void onConnectionRequest(
                ConnectionRequest request
        );

        void onFileReceivedFromUser(
                ClientHandler handler,
                File file,
                String originalFilename,
                boolean sensitive
        );

        default void onChatMessage(
                ClientHandler handler,
                String message
        ) {
        }

        default void onChatMessage(
                ClientHandler handler,
                int conversationId,
                String message
        ) {
            onChatMessage(
                    handler,
                    message
            );
        }

        default void onChatFileReceived(
                ClientHandler handler,
                int conversationId,
                File file,
                String originalFilename,
                boolean sensitive
        ) {
        }

        default void onChatHistoryRequest(
                ClientHandler handler,
                int conversationId
        ) {
        }

        void onClientDisconnected(
                ClientHandler handler
        );
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
    // SOCKET
    // ============================================================

    private final SSLSocket socket;

    private DataInputStream input;

    private DataOutputStream output;

    private final Listener listener;

    /**
     * Un seul thread doit écrire à la fois
     * sur la connexion TLS.
     */
    private final Object outputLock =
            new Object();

    // ============================================================
    // ETAT
    // ============================================================

    private volatile boolean running = false;

    /**
     * TRUE lorsque l'administrateur a accepté
     * l'utilisateur.
     */
    private volatile boolean accessGranted = false;

    /**
     * TRUE lorsque le client a répondu READY.
     */
    private volatile boolean ready = false;

    /**
     * Nom de l'utilisateur associé à cette connexion.
     */
    private volatile String username;

    // ============================================================
    // CONSTRUCTEUR
    // ============================================================

    public ClientHandler(
            SSLSocket socket,
            Listener listener
    ) {

        if (socket == null) {

            throw new IllegalArgumentException(
                    "Socket TLS null."
            );
        }

        this.socket = socket;
        this.listener = listener;
    }

    // ============================================================
    // RUN
    // ============================================================

    @Override
    public void run() {

        try {

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

            /*
             * Le client attend cette commande avant
             * d'envoyer REQUEST_CONNECTION.
             */
            send(
                    "SERVER_READY"
            );

            while (
                    running
                            && !socket.isClosed()
            ) {

                String command;

                try {

                    command =
                            input.readUTF();

                } catch (EOFException e) {

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
                    // DEMANDE DE CONNEXION
                    // =================================================

                    case "REQUEST_CONNECTION":

                        handleConnectionRequest();

                        break;

                    // =================================================
                    // CLIENT PRET
                    // =================================================

                    case "READY":

                        handleReady();

                        break;

                    // =================================================
                    // FICHIER
                    // =================================================

                    case "FILE_TRANSFER":

                        handleIncomingFile();

                        break;

                    /*
                     * Ancien protocole conservé pour compatibilité.
                     */
                    case "SEND_FILE":

                        handleIncomingFileLegacy();

                        break;

                    // =================================================
                    // MESSAGE CHAT
                    // =================================================

                    case "CHAT_MESSAGE":

                        handleIncomingChatMessage();

                        break;

                    // =================================================
                    // FICHIER CHAT
                    // =================================================

                    case "CHAT_FILE":

                        handleIncomingChatFile();

                        break;

                    // =================================================
                    // HISTORIQUE
                    // =================================================

                    case "CHAT_HISTORY_REQUEST":

                        handleChatHistoryRequest();

                        break;

                    // =================================================
                    // PING
                    // =================================================

                    case "PING":

                        send("PONG");

                        break;

                    // =================================================
                    // DECONNEXION
                    // =================================================

                    case "DISCONNECT":

                    case "SERVER_CLOSING":

                        return;

                    // =================================================
                    // COMMANDE INCONNUE
                    // =================================================

                    default:

                        sendError(
                                "Commande inconnue : "
                                        + command
                        );

                        break;
                }
            }

        } catch (Exception e) {

            /*
             * Pas d'affichage console.
             *
             * Les erreurs sont volontairement silencieuses
             * afin de garder la console propre.
             */

        } finally {

            closeInternal();
        }
    }

    // ============================================================
    // CONNECTION REQUEST
    // ============================================================

    private void handleConnectionRequest()
            throws Exception {

        if (
                username != null
                        && !username.isBlank()
        ) {

            sendError(
                    "Demande de connexion déjà effectuée."
            );

            return;
        }

        String receivedUsername =
                input.readUTF();

        if (
                receivedUsername == null
                        || receivedUsername.isBlank()
                        || receivedUsername.length()
                        > MAX_USERNAME_LENGTH
                        || containsControlCharacters(
                        receivedUsername
                )
        ) {

            sendError(
                    "Nom utilisateur invalide."
            );

            return;
        }

        receivedUsername =
                receivedUsername.trim();

        username =
                receivedUsername;

        if (listener != null) {

            listener.onConnectionRequest(
                    new ConnectionRequest(
                            username,
                            getClientIp(),
                            this
                    )
            );
        }
    }

    // ============================================================
    // READY
    // ============================================================

    private void handleReady() {

        if (!accessGranted) {

            sendError(
                    "Accès non autorisé."
            );

            return;
        }

        if (
                username == null
                        || username.isBlank()
        ) {

            sendError(
                    "Utilisateur non identifié."
            );

            return;
        }

        ready = true;

        try {

            send(
                    "READY_ACK"
            );

        } catch (IOException ignored) {
        }
    }

    // ============================================================
    // ACCESS GRANTED
    // ============================================================

    public synchronized void acceptUser() {

        grantAccess();
    }

    public synchronized void grantAccess() {

        if (
                !running
                        || username == null
                        || username.isBlank()
                        || accessGranted
        ) {

            return;
        }

        try {

            send(
                    "ACCESS_GRANTED"
            );

            accessGranted = true;
            ready = false;

        } catch (IOException e) {

            accessGranted = false;
            ready = false;
        }
    }

    // ============================================================
    // ACCESS DENIED
    // ============================================================

    public synchronized void refuseUser() {

        denyAccess();
    }

    public synchronized void denyAccess() {

        if (!running) {

            return;
        }

        accessGranted = false;
        ready = false;

        try {

            send(
                    "ACCESS_DENIED"
            );

        } catch (Exception ignored) {

        } finally {

            close();
        }
    }

    // ============================================================
    // INCOMING FILE
    // ============================================================

    private void handleIncomingFile()
            throws Exception {

        checkReadyForReceive();

        String filename =
                sanitizeFilename(
                        input.readUTF()
                );

        boolean sensitive =
                input.readBoolean();

        long originalSize =
                input.readLong();

        if (
                filename.isBlank()
                        || originalSize <= 0
                        || originalSize > MAX_FILE_SIZE
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
                        || transmitted > MAX_ENCRYPTED_FILE_SIZE
        ) {

            throw new IOException(
                    "Taille transmise invalide."
            );
        }

        Path dir =
                Paths.get(
                        System.getProperty(
                                "user.home"
                        ),
                        "SecureDocumentExchange",
                        "ServerReceived"
                );

        Files.createDirectories(dir);

        File temp =
                File.createTempFile(
                        "received_",
                        ".tmp",
                        dir.toFile()
                );

        try {

            receiveBytes(
                    temp,
                    transmitted
            );

            Path destination =
                    createUniquePath(
                            dir,
                            filename
                    );

            File finalFile;

            if (sensitive) {

                finalFile =
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

                finalFile =
                        destination.toFile();
            }

            if (
                    finalFile == null
                            || !finalFile.exists()
                            || finalFile.length()
                            != originalSize
            ) {

                deleteFile(finalFile);

                throw new IOException(
                        "Taille après traitement incorrecte."
                );
            }

            if (listener != null) {

                listener.onFileReceivedFromUser(
                        this,
                        finalFile,
                        filename,
                        sensitive
                );
            }

            temp = null;

        } finally {

            deleteFile(temp);
        }
    }

    // ============================================================
    // LEGACY FILE
    // ============================================================

    private void handleIncomingFileLegacy()
            throws Exception {

        checkReadyForReceive();

        String filename =
                sanitizeFilename(
                        input.readUTF()
                );

        boolean sensitive =
                input.readBoolean();

        long size =
                input.readLong();

        if (
                filename.isBlank()
                        || size <= 0
                        || size > MAX_FILE_SIZE
        ) {

            throw new IOException(
                    "Fichier invalide."
            );
        }

        Path dir =
                Paths.get(
                        System.getProperty(
                                "user.home"
                        ),
                        "SecureDocumentExchange",
                        "ServerReceived"
                );

        Files.createDirectories(dir);

        Path destination =
                createUniquePath(
                        dir,
                        filename
                );

        File file =
                destination.toFile();

        receiveBytes(
                file,
                size
        );

        if (listener != null) {

            listener.onFileReceivedFromUser(
                    this,
                    file,
                    filename,
                    sensitive
            );
        }
    }

    // ============================================================
    // CHAT MESSAGE - RECEIVE
    // ============================================================

    private void handleIncomingChatMessage()
            throws IOException {

        checkReadyForReceive();

        if (
                username == null
                        || username.isBlank()
        ) {

            throw new IOException(
                    "Utilisateur non authentifié."
            );
        }

        int conversationId =
                input.readInt();

        String message =
                input.readUTF();

        if (conversationId <= 0) {

            throw new IOException(
                    "Conversation invalide."
            );
        }

        if (
                message == null
                        || message.isBlank()
        ) {

            throw new IOException(
                    "Message vide."
            );
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

        /*
         * Aucun affichage console du message.
         */
        if (listener != null) {

            listener.onChatMessage(
                    this,
                    conversationId,
                    message
            );
        }
    }

    // ============================================================
    // CHAT FILE - RECEIVE
    // ============================================================

    private void handleIncomingChatFile()
            throws Exception {

        checkReadyForReceive();

        if (
                username == null
                        || username.isBlank()
        ) {

            throw new IOException(
                    "Utilisateur non authentifié."
            );
        }

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

        long originalSize =
                input.readLong();

        if (
                conversationId <= 0
                        || documentId <= 0
                        || filename.isBlank()
                        || originalSize <= 0
                        || originalSize > MAX_FILE_SIZE
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
                        || transmitted > MAX_ENCRYPTED_FILE_SIZE
        ) {

            throw new IOException(
                    "Taille transmise invalide."
            );
        }

        Path dir =
                Paths.get(
                        System.getProperty(
                                "user.home"
                        ),
                        "SecureDocumentExchange",
                        "ChatReceived"
                );

        Files.createDirectories(dir);

        File temp =
                File.createTempFile(
                        "chat_received_",
                        ".tmp",
                        dir.toFile()
                );

        try {

            receiveBytes(
                    temp,
                    transmitted
            );

            Path destination =
                    createUniquePath(
                            dir,
                            filename
                    );

            File finalFile;

            if (sensitive) {

                finalFile =
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

                finalFile =
                        destination.toFile();
            }

            if (
                    finalFile == null
                            || !finalFile.exists()
                            || finalFile.length()
                            != originalSize
            ) {

                deleteFile(finalFile);

                throw new IOException(
                        "Taille fichier incorrecte."
                );
            }

            if (listener != null) {

                listener.onChatFileReceived(
                        this,
                        conversationId,
                        finalFile,
                        filename,
                        sensitive
                );
            }

            temp = null;

        } finally {

            deleteFile(temp);
        }
    }

    // ============================================================
    // CHAT HISTORY REQUEST
    // ============================================================

    private void handleChatHistoryRequest()
            throws IOException {

        checkReadyForReceive();

        int conversationId =
                input.readInt();

        if (conversationId <= 0) {

            throw new IOException(
                    "Conversation invalide."
            );
        }

        if (listener != null) {

            listener.onChatHistoryRequest(
                    this,
                    conversationId
            );
        }
    }

    // ============================================================
    // SEND FILE
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
                                "server_encrypted_",
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

            long size =
                    toSend.length();

            if (
                    size <= 0
                            || size > MAX_ENCRYPTED_FILE_SIZE
            ) {

                throw new IOException(
                        "Taille fichier invalide."
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
                        size
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
    // SEND CHAT MESSAGE
    // ============================================================

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

        String safeSender =
                sender == null
                        || sender.isBlank()
                        ? "SYSTEM"
                        : sender.trim();

        if (
                safeSender.length()
                        > MAX_USERNAME_LENGTH
        ) {

            throw new IOException(
                    "Expéditeur invalide."
            );
        }

        if (
                containsControlCharacters(
                        safeSender
                )
        ) {

            throw new IOException(
                    "Expéditeur invalide."
            );
        }

        synchronized (outputLock) {

            if (
                    !running
                            || output == null
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

            output.writeUTF(
                    safeSender
            );

            output.writeUTF(
                    message
            );

            output.flush();
        }
    }

    /**
     * Compatibilité ancienne version.
     */
    public void sendChatMessage(
            String sender,
            String message
    ) throws IOException {

        sendChatMessage(
                1,
                sender,
                message
        );
    }

    // ============================================================
    // SEND CHAT FILE
    // ============================================================

    public void sendChatFile(
            int conversationId,
            File file,
            String filename,
            boolean sensitive,
            int documentId
    ) throws Exception {

        checkReady();

        if (
                conversationId <= 0
                        || documentId <= 0
        ) {

            throw new IOException(
                    "Conversation/document invalide."
            );
        }

        if (
                file == null
                        || !file.isFile()
                        || !file.canRead()
        ) {

            throw new IOException(
                    "Fichier invalide."
            );
        }

        String safeFilename =
                sanitizeFilename(
                        filename == null
                                ? file.getName()
                                : filename
                );

        long original =
                file.length();

        if (
                safeFilename.isBlank()
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
                                "chat_server_encrypted_",
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

            long size =
                    toSend.length();

            if (
                    size <= 0
                            || size > MAX_ENCRYPTED_FILE_SIZE
            ) {

                throw new IOException(
                        "Taille fichier invalide."
                );
            }

            synchronized (outputLock) {

                output.writeUTF(
                        "CHAT_FILE"
                );

                output.writeInt(
                        conversationId
                );

                output.writeInt(
                        documentId
                );

                output.writeUTF(
                        safeFilename
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
                        size
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
    // CHAT HISTORY START
    // ============================================================

    public void sendChatHistoryStart(
            int conversationId,
            int count
    ) throws IOException {

        checkReady();

        if (conversationId <= 0) {

            throw new IOException(
                    "Conversation invalide."
            );
        }

        synchronized (outputLock) {

            output.writeUTF(
                    "CHAT_HISTORY_START"
            );

            output.writeInt(
                    conversationId
            );

            output.writeInt(
                    Math.max(
                            0,
                            count
                    )
            );

            output.flush();
        }
    }

    // ============================================================
    // CHAT HISTORY MESSAGE
    // ============================================================

    public void sendChatHistoryMessage(
            int conversationId,
            long messageId,
            int senderId,
            String sender,
            String type,
            String content,
            int documentId
    ) throws IOException {

        checkReady();

        if (conversationId <= 0) {

            throw new IOException(
                    "Conversation invalide."
            );
        }

        String normalizedType =
                type == null
                        ? "TEXT"
                        : type.trim()
                        .toUpperCase();

        if (
                !"TEXT".equals(
                        normalizedType
                )
                        && !"FILE".equals(
                        normalizedType
                )
        ) {

            throw new IOException(
                    "Type de message invalide : "
                            + type
            );
        }

        String safeSender =
                sender == null
                        ? ""
                        : sender;

        String safeContent =
                content == null
                        ? ""
                        : content;

        if (
                safeSender.length()
                        > MAX_USERNAME_LENGTH
        ) {

            throw new IOException(
                    "Expéditeur trop long."
            );
        }

        if (
                safeContent.length()
                        > MAX_CHAT_MESSAGE_LENGTH
        ) {

            throw new IOException(
                    "Contenu trop long."
            );
        }

        synchronized (outputLock) {

            output.writeUTF(
                    "CHAT_HISTORY_MESSAGE"
            );

            output.writeInt(
                    conversationId
            );

            output.writeLong(
                    messageId
            );

            output.writeInt(
                    senderId
            );

            output.writeUTF(
                    safeSender
            );

            output.writeUTF(
                    normalizedType
            );

            output.writeUTF(
                    safeContent
            );

            output.writeInt(
                    Math.max(
                            0,
                            documentId
                    )
            );

            output.flush();
        }
    }

    // ============================================================
    // CHAT HISTORY END
    // ============================================================

    public void sendChatHistoryEnd(
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
                    "CHAT_HISTORY_END"
            );

            output.writeInt(
                    conversationId
            );

            output.flush();
        }
    }

    // ============================================================
    // SEND GENERAL MESSAGE
    // ============================================================

    public void send(
            String message
    ) throws IOException {

        synchronized (outputLock) {

            if (
                    !running
                            || output == null
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
    // SEND ERROR
    // ============================================================

    private void sendError(
            String message
    ) {

        try {

            synchronized (outputLock) {

                if (
                        !running
                                || output == null
                ) {

                    return;
                }

                output.writeUTF(
                        "ERROR"
                );

                output.writeUTF(
                        message == null
                                ? "Erreur inconnue."
                                : message
                );

                output.flush();
            }

        } catch (Exception ignored) {
        }
    }

    // ============================================================
    // RECEIVE BYTES
    // ============================================================

    private void receiveBytes(
            File destination,
            long size
    ) throws IOException {

        try (
                OutputStream outputStream =
                        new BufferedOutputStream(
                                new FileOutputStream(
                                        destination
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
                            "Connexion interrompue."
                    );
                }

                if (bytesRead == 0) {

                    continue;
                }

                outputStream.write(
                        buffer,
                        0,
                        bytesRead
                );

                remaining -= bytesRead;
            }

            outputStream.flush();
        }
    }

    // ============================================================
    // SEND FILE BYTES
    // ============================================================

    private void sendFileBytes(
            File file
    ) throws IOException {

        try (
                InputStream inputStream =
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
                            inputStream.read(
                                    buffer
                            ))
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
    // STATE CHECK
    // ============================================================

    private void checkReady()
            throws IOException {

        if (
                !running
                        || output == null
        ) {

            throw new IOException(
                    "Connexion indisponible."
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

        if (!accessGranted) {

            throw new IOException(
                    "Utilisateur non autorisé."
            );
        }

        if (!ready) {

            throw new IOException(
                    "Utilisateur non prêt."
            );
        }
    }

    private void checkReadyForReceive()
            throws IOException {

        if (
                !running
                        || !accessGranted
                        || !ready
        ) {

            sendError(
                    "Utilisateur non autorisé ou non prêt."
            );

            throw new IOException(
                    "Utilisateur non autorisé ou non prêt."
            );
        }

        if (
                username == null
                        || username.isBlank()
        ) {

            sendError(
                    "Utilisateur non identifié."
            );

            throw new IOException(
                    "Utilisateur non identifié."
            );
        }
    }

    // ============================================================
    // CLOSE
    // ============================================================

    public synchronized void close() {

        closeInternal();
    }

    private void closeInternal() {

        boolean wasRunning =
                running;

        running = false;

        ready = false;

        accessGranted = false;

        try {

            socket.close();

        } catch (Exception ignored) {
        }

        input = null;

        output = null;

        if (
                wasRunning
                        && listener != null
        ) {

            try {

                listener.onClientDisconnected(
                        this
                );

            } catch (Exception ignored) {
            }
        }
    }

    // ============================================================
    // GETTERS
    // ============================================================

    public String getUsername() {

        return username;
    }

    public boolean isAccessGranted() {

        return accessGranted;
    }

    public boolean isAccepted() {

        return accessGranted;
    }

    public boolean isReady() {

        return ready;
    }

    public boolean isRunning() {

        return running;
    }

    public String getIp() {

        return getClientIp();
    }

    public String getClientIp() {

        try {

            if (socket.getInetAddress() == null) {

                return "UNKNOWN";
            }

            return socket.getInetAddress()
                    .getHostAddress();

        } catch (Exception e) {

            return "UNKNOWN";
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

        String cleanFilename =
                new File(filename)
                        .getName()
                        .trim();

        return cleanFilename.isBlank()
                || cleanFilename.equals(".")
                || cleanFilename.equals("..")
                || cleanFilename.length() > 255
                || containsControlCharacters(
                cleanFilename
        )
                ? ""
                : cleanFilename;
    }

    // ============================================================
    // UNIQUE FILE PATH
    // ============================================================

    private Path createUniquePath(
            Path directory,
            String filename
    ) {

        Path path =
                directory.resolve(
                        filename
                );

        if (!Files.exists(path)) {

            return path;
        }

        int dot =
                filename.lastIndexOf('.');

        String name =
                dot > 0
                        ? filename.substring(
                        0,
                        dot
                )
                        : filename;

        String extension =
                dot > 0
                        ? filename.substring(dot)
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
    // ERROR MESSAGE
    // ============================================================

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