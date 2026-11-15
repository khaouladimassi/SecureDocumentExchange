package com.securedocumentexchange.network;

/**
 * Représente une demande de connexion
 * envoyée par un utilisateur au serveur.
 */
public class ConnectionRequest {

    private final String username;
    private final String clientIp;
    private final ClientHandler clientHandler;

    public ConnectionRequest(
            String username,
            String clientIp,
            ClientHandler clientHandler
    ) {
        this.username = username;
        this.clientIp = clientIp;
        this.clientHandler = clientHandler;
    }

    public String getUsername() {
        return username;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getIpAddress() {
        return clientIp;
    }

    public ClientHandler getClientHandler() {
        return clientHandler;
    }

    @Override
    public String toString() {
        return username + " (" + clientIp + ")";
    }
}