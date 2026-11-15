package com.securedocumentexchange.network;

public class ServerInfo {

    private final String name;
    private final String host;
    private final int port;

    public ServerInfo(
            String name,
            String host,
            int port
    ) {
        this.name = name;
        this.host = host;
        this.port = port;
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return name
                + " : "
                + host
                + ":"
                + port;
    }
}