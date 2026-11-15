package com.securedocumentexchange.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * ================================================================
 * SERVER DISCOVERY
 * ================================================================
 *
 * Découverte UDP du serveur SecureDocumentExchange.
 *
 * UDP Discovery Port : 5001
 * TLS Server Port     : 5000
 *
 * Le serveur écoute les demandes :
 *
 *     DISCOVER_SECURE_SERVER
 *
 * et répond :
 *
 *     SECURE_SERVER|serverName|serverIp|tlsPort
 */
public class ServerDiscovery {

    // =========================================================
    // CONFIGURATION
    // =========================================================

    public static final int DISCOVERY_PORT = 5001;

    private static final int BUFFER_SIZE = 1024;

    private static final int DEFAULT_TIMEOUT = 3000;

    // =========================================================
    // SERVER SOCKET
    // =========================================================

    private volatile DatagramSocket socket;

    private volatile boolean running = false;

    // =========================================================
    // ADMIN : START DISCOVERY
    // =========================================================

    public synchronized void startServerDiscovery(
            String serverName,
            int tlsPort
    ) throws IOException {

        if (running) {
            return;
        }

        if (serverName == null
                || serverName.isBlank()) {

            throw new IllegalArgumentException(
                    "Nom serveur invalide."
            );
        }

        if (tlsPort < 1
                || tlsPort > 65535) {

            throw new IllegalArgumentException(
                    "Port TLS invalide."
            );
        }

        String cleanServerName =
                serverName.trim();

        // =====================================================
        // CREATION SOCKET UDP
        // =====================================================

        DatagramSocket discoverySocket =
                new DatagramSocket(
                        DISCOVERY_PORT,
                        InetAddress.getByName("0.0.0.0")
                );

        discoverySocket.setBroadcast(true);

        socket = discoverySocket;

        running = true;

        // =====================================================
        // INFORMATIONS
        // =====================================================

        System.out.println();
        System.out.println(
                "================================="
        );
        System.out.println(
                "       SERVER DISCOVERY"
        );
        System.out.println(
                "================================="
        );
        System.out.println(
                "UDP Port : "
                        + DISCOVERY_PORT
        );
        System.out.println(
                "Server   : "
                        + cleanServerName
        );

        String serverIp =
                SecureServer.getLocalIp();

        System.out.println(
                "IP       : "
                        + serverIp
        );

        System.out.println(
                "================================="
        );

        // =====================================================
        // THREAD DISCOVERY
        // =====================================================

        Thread thread =
                new Thread(
                        () -> runDiscoveryServer(
                                discoverySocket,
                                cleanServerName,
                                tlsPort
                        ),
                        "Server-Discovery"
                );

        thread.setDaemon(true);
        thread.start();
    }

    // =========================================================
    // DISCOVERY SERVER LOOP
    // =========================================================

    private void runDiscoveryServer(
            DatagramSocket discoverySocket,
            String serverName,
            int tlsPort
    ) {

        byte[] buffer =
                new byte[BUFFER_SIZE];

        while (
                running
                        && discoverySocket != null
                        && !discoverySocket.isClosed()
        ) {

            try {

                DatagramPacket packet =
                        new DatagramPacket(
                                buffer,
                                buffer.length
                        );

                discoverySocket.receive(
                        packet
                );

                if (!running) {
                    break;
                }

                // =================================================
                // MESSAGE
                // =================================================

                String message =
                        new String(
                                packet.getData(),
                                packet.getOffset(),
                                packet.getLength(),
                                StandardCharsets.UTF_8
                        ).trim();

                if (!"DISCOVER_SECURE_SERVER"
                        .equals(message)) {

                    continue;
                }

                String clientIp =
                        packet.getAddress()
                                .getHostAddress();

                System.out.println(
                        "🔎 Discovery reçue de : "
                                + clientIp
                );

                // =================================================
                // IP SERVEUR
                // =================================================

                String serverIp =
                        SecureServer.getLocalIp();

                if (!isValidServerIp(serverIp)) {

                    System.err.println(
                            "❌ IP serveur invalide : "
                                    + serverIp
                    );

                    continue;
                }

                // =================================================
                // RESPONSE
                // =================================================

                String response =
                        "SECURE_SERVER|"
                                + sanitizeServerName(
                                serverName
                        )
                                + "|"
                                + serverIp
                                + "|"
                                + tlsPort;

                byte[] data =
                        response.getBytes(
                                StandardCharsets.UTF_8
                        );

                DatagramPacket responsePacket =
                        new DatagramPacket(
                                data,
                                data.length,
                                packet.getAddress(),
                                packet.getPort()
                        );

                discoverySocket.send(
                        responsePacket
                );

                System.out.println(
                        "✓ Réponse envoyée à "
                                + clientIp
                                + " : "
                                + response
                );

            } catch (SocketTimeoutException e) {

                // Aucun problème.
                // Continuer l'écoute.

            } catch (IOException e) {

                if (running) {

                    System.err.println(
                            "❌ Erreur Discovery : "
                                    + safeMessage(e)
                    );
                }
            } catch (Exception e) {

                if (running) {

                    System.err.println(
                            "❌ Erreur Discovery inattendue : "
                                    + safeMessage(e)
                    );
                }
            }
        }

        System.out.println(
                "✓ Thread Discovery terminé."
        );
    }

    // =========================================================
    // STOP
    // =========================================================

    public synchronized void stop() {

        running = false;

        DatagramSocket currentSocket =
                socket;

        socket = null;

        if (currentSocket != null) {

            try {

                currentSocket.close();

            } catch (Exception ignored) {
            }
        }

        System.out.println(
                "✓ Server Discovery arrêté."
        );
    }

    // =========================================================
    // STATUS
    // =========================================================

    public boolean isRunning() {

        return running;
    }

    // =========================================================
    // USER : DISCOVER SERVERS
    // =========================================================

    public static List<ServerInfo> discover(
            int timeout
    ) {

        List<ServerInfo> servers =
                new ArrayList<>();

        if (timeout <= 0) {

            timeout =
                    DEFAULT_TIMEOUT;
        }

        try (
                DatagramSocket clientSocket =
                        new DatagramSocket()
        ) {

            clientSocket.setBroadcast(true);

            // =================================================
            // DISCOVERY REQUEST
            // =================================================

            String message =
                    "DISCOVER_SECURE_SERVER";

            byte[] data =
                    message.getBytes(
                            StandardCharsets.UTF_8
                    );

            // =================================================
            // NETWORK INTERFACES
            // =================================================

            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface
                            .getNetworkInterfaces();

            if (interfaces == null) {

                return servers;
            }

            boolean broadcastSent =
                    false;

            while (
                    interfaces.hasMoreElements()
            ) {

                NetworkInterface networkInterface =
                        interfaces.nextElement();

                if (!isValidInterface(
                        networkInterface
                )) {

                    continue;
                }

                String displayName =
                        networkInterface
                                .getDisplayName();

                System.out.println(
                        "✓ Interface utilisée : "
                                + displayName
                );

                // =================================================
                // INTERFACE ADDRESSES
                // =================================================

                Enumeration<InetAddress> addresses =
                        networkInterface
                                .getInetAddresses();

                while (
                        addresses.hasMoreElements()
                ) {

                    InetAddress address =
                            addresses.nextElement();

                    if (!(address instanceof Inet4Address)) {

                        continue;
                    }

                    if (address.isLoopbackAddress()) {

                        continue;
                    }

                    // =================================================
                    // BROADCAST ADDRESS
                    // =================================================

                    InetAddress broadcast =
                            findBroadcastAddress(
                                    networkInterface,
                                    address
                            );

                    if (broadcast == null) {

                        continue;
                    }

                    DatagramPacket packet =
                            new DatagramPacket(
                                    data,
                                    data.length,
                                    broadcast,
                                    DISCOVERY_PORT
                            );

                    try {

                        clientSocket.send(
                                packet
                        );

                        broadcastSent = true;

                        System.out.println(
                                "📡 Broadcast envoyé sur "
                                        + displayName
                                        + " : "
                                        + broadcast
                                        .getHostAddress()
                        );

                    } catch (IOException e) {

                        System.err.println(
                                "⚠ Broadcast impossible sur "
                                        + displayName
                                        + " : "
                                        + e.getMessage()
                        );
                    }
                }
            }

            // =================================================
            // FALLBACK BROADCAST
            // =================================================

            if (!broadcastSent) {

                try {

                    InetAddress globalBroadcast =
                            InetAddress.getByName(
                                    "255.255.255.255"
                            );

                    DatagramPacket packet =
                            new DatagramPacket(
                                    data,
                                    data.length,
                                    globalBroadcast,
                                    DISCOVERY_PORT
                            );

                    clientSocket.send(
                            packet
                    );

                    System.out.println(
                            "📡 Broadcast global envoyé : "
                                    + "255.255.255.255"
                    );

                } catch (Exception e) {

                    System.err.println(
                            "⚠ Broadcast global impossible : "
                                    + e.getMessage()
                    );
                }
            }

            // =================================================
            // RECEVOIR LES REPONSES
            // =================================================

            long start =
                    System.currentTimeMillis();

            while (
                    System.currentTimeMillis()
                            - start
                            < timeout
            ) {

                long elapsed =
                        System.currentTimeMillis()
                                - start;

                long remaining =
                        timeout - elapsed;

                if (remaining <= 0) {

                    break;
                }

                try {

                    byte[] buffer =
                            new byte[BUFFER_SIZE];

                    DatagramPacket response =
                            new DatagramPacket(
                                    buffer,
                                    buffer.length
                            );

                    clientSocket.setSoTimeout(
                            (int)
                                    Math.min(
                                            remaining,
                                            1000
                                    )
                    );

                    clientSocket.receive(
                            response
                    );

                    String result =
                            new String(
                                    response.getData(),
                                    response.getOffset(),
                                    response.getLength(),
                                    StandardCharsets.UTF_8
                            ).trim();

                    System.out.println(
                            "📨 Réponse reçue : "
                                    + result
                    );

                    // =================================================
                    // PARSING
                    // =================================================

                    String[] parts =
                            result.split(
                                    "\\|",
                                    -1
                            );

                    if (
                            parts.length != 4
                                    ||
                                    !"SECURE_SERVER"
                                            .equals(parts[0])
                    ) {

                        continue;
                    }

                    String serverName =
                            parts[1].trim();

                    String serverIp =
                            parts[2].trim();

                    String portString =
                            parts[3].trim();

                    // =================================================
                    // VALIDATION SERVER NAME
                    // =================================================

                    if (serverName.isBlank()
                            || serverName.length() > 100) {

                        System.out.println(
                                "⏭ Nom serveur invalide."
                        );

                        continue;
                    }

                    // =================================================
                    // VALIDATION PORT
                    // =================================================

                    int port;

                    try {

                        port =
                                Integer.parseInt(
                                        portString
                                );

                    } catch (
                            NumberFormatException e
                    ) {

                        System.err.println(
                                "❌ Port serveur invalide : "
                                        + portString
                        );

                        continue;
                    }

                    if (port < 1
                            || port > 65535) {

                        System.err.println(
                                "❌ Port serveur hors limites : "
                                        + port
                        );

                        continue;
                    }

                    // =================================================
                    // VALIDATION IP
                    // =================================================

                    if (!isValidServerIp(
                            serverIp
                    )) {

                        System.out.println(
                                "⏭ IP serveur ignorée : "
                                        + serverIp
                        );

                        continue;
                    }

                    // =================================================
                    // SERVER INFO
                    // =================================================

                    ServerInfo server =
                            new ServerInfo(
                                    serverName,
                                    serverIp,
                                    port
                            );

                    // =================================================
                    // EVITER DOUBLONS
                    // =================================================

                    boolean exists =
                            servers.stream()
                                    .anyMatch(
                                            existing ->
                                                    existing != null
                                                            &&
                                                            serverIp.equals(
                                                                    existing.getHost()
                                                            )
                                                            &&
                                                            port
                                                                    == existing.getPort()
                                    );

                    if (!exists) {

                        servers.add(
                                server
                        );

                        System.out.println(
                                "✓ Serveur trouvé : "
                                        + server
                        );
                    }

                } catch (
                        SocketTimeoutException e
                ) {

                    /*
                     * Timeout intermédiaire.
                     * Continuer jusqu'au timeout global.
                     */
                }
            }

        } catch (Exception e) {

            System.err.println(
                    "❌ Erreur découverte : "
                            + safeMessage(e)
            );
        }

        return servers;
    }

    // =========================================================
    // FIND BROADCAST ADDRESS
    // =========================================================

    private static InetAddress findBroadcastAddress(
            NetworkInterface networkInterface,
            InetAddress address
    ) {

        try {

            for (
                    java.net.InterfaceAddress interfaceAddress :
                    networkInterface.getInterfaceAddresses()
            ) {

                InetAddress interfaceIp =
                        interfaceAddress.getAddress();

                if (interfaceIp == null) {

                    continue;
                }

                if (!interfaceIp.equals(address)) {

                    continue;
                }

                InetAddress broadcast =
                        interfaceAddress.getBroadcast();

                if (broadcast != null) {

                    return broadcast;
                }

                // =================================================
                // FALLBACK PREFIX
                // =================================================

                short prefixLength =
                        interfaceAddress
                                .getNetworkPrefixLength();

                if (prefixLength <= 0
                        || prefixLength >= 32) {

                    prefixLength = 24;
                }

                return calculateBroadcast(
                        address,
                        prefixLength
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "⚠ Impossible de calculer le broadcast : "
                            + e.getMessage()
            );
        }

        return null;
    }

    // =========================================================
    // CALCULATE BROADCAST
    // =========================================================

    private static InetAddress calculateBroadcast(
            InetAddress address,
            short prefixLength
    ) throws IOException {

        if (!(address instanceof Inet4Address)) {

            return null;
        }

        byte[] ipBytes =
                address.getAddress();

        int ipInt =
                ((ipBytes[0] & 0xFF) << 24)
                        |
                        ((ipBytes[1] & 0xFF) << 16)
                        |
                        ((ipBytes[2] & 0xFF) << 8)
                        |
                        (ipBytes[3] & 0xFF);

        int mask;

        if (prefixLength == 0) {

            mask = 0;

        } else {

            mask =
                    (int)
                            (
                                    0xFFFFFFFFL
                                            << (32 - prefixLength)
                            );
        }

        int broadcastInt =
                ipInt | ~mask;

        byte[] broadcastBytes =
                new byte[]{
                        (byte) (broadcastInt >> 24),
                        (byte) (broadcastInt >> 16),
                        (byte) (broadcastInt >> 8),
                        (byte) broadcastInt
                };

        return InetAddress.getByAddress(
                broadcastBytes
        );
    }

    // =========================================================
    // VALID INTERFACE
    // =========================================================

    private static boolean isValidInterface(
            NetworkInterface networkInterface
    ) {

        if (networkInterface == null) {

            return false;
        }

        try {

            if (!networkInterface.isUp()) {

                return false;
            }

            if (networkInterface.isLoopback()) {

                return false;
            }

            if (networkInterface.isVirtual()) {

                return false;
            }

            String name =
                    networkInterface
                            .getDisplayName()
                            .toLowerCase();

            String interfaceName =
                    networkInterface
                            .getName()
                            .toLowerCase();

            // =================================================
            // VIRTUALBOX
            // =================================================

            if (name.contains("virtualbox")
                    || name.contains("host-only")
                    || interfaceName.contains("virtualbox")) {

                System.out.println(
                        "⏭ Interface ignorée : "
                                + networkInterface
                                .getDisplayName()
                );

                return false;
            }

            // =================================================
            // VMWARE
            // =================================================

            if (name.contains("vmware")
                    || interfaceName.contains("vmware")) {

                System.out.println(
                        "⏭ Interface ignorée : "
                                + networkInterface
                                .getDisplayName()
                );

                return false;
            }

            // =================================================
            // HYPER-V
            // =================================================

            if (name.contains("hyper-v")
                    || interfaceName.contains("hyper-v")
                    || name.contains("hyperv")
                    || interfaceName.contains("hyperv")) {

                System.out.println(
                        "⏭ Interface ignorée : "
                                + networkInterface
                                .getDisplayName()
                );

                return false;
            }

            // =================================================
            // BLUETOOTH
            // =================================================

            if (name.contains("bluetooth")
                    || interfaceName.contains("bluetooth")) {

                System.out.println(
                        "⏭ Interface ignorée : "
                                + networkInterface
                                .getDisplayName()
                );

                return false;
            }

            // =================================================
            // VPN / TAP / TUN
            // =================================================

            if (name.contains("tap-")
                    || name.contains("tap ")
                    || name.contains("tun")
                    || name.contains("vpn")
                    || name.contains("teredo")
                    || name.contains("loopback")
                    || interfaceName.contains("tap")
                    || interfaceName.contains("tun")
                    || interfaceName.contains("vpn")) {

                System.out.println(
                        "⏭ Interface ignorée : "
                                + networkInterface
                                .getDisplayName()
                );

                return false;
            }

            return true;

        } catch (Exception e) {

            return false;
        }
    }

    // =========================================================
    // VALID SERVER IP
    // =========================================================

    private static boolean isValidServerIp(
            String ip
    ) {

        if (ip == null
                || ip.isBlank()) {

            return false;
        }

        String cleanIp =
                ip.trim();

        // =====================================================
        // LOCALHOST
        // =====================================================

        if ("127.0.0.1".equals(cleanIp)
                || "localhost".equalsIgnoreCase(cleanIp)) {

            return false;
        }

        // =====================================================
        // VIRTUALBOX HOST-ONLY
        // =====================================================

        if (cleanIp.startsWith("192.168.56.")
                || cleanIp.startsWith("192.168.57.")) {

            return false;
        }

        // =====================================================
        // LINK-LOCAL
        // =====================================================

        if (cleanIp.startsWith("169.254.")) {

            return false;
        }

        // =====================================================
        // VALIDATE IPV4
        // =====================================================

        try {

            InetAddress address =
                    InetAddress.getByName(
                            cleanIp
                    );

            return address instanceof Inet4Address;

        } catch (Exception e) {

            return false;
        }
    }

    // =========================================================
    // SANITIZE SERVER NAME
    // =========================================================

    private static String sanitizeServerName(
            String serverName
    ) {

        if (serverName == null
                || serverName.isBlank()) {

            return "SecureServer";
        }

        String clean =
                serverName
                        .trim()
                        .replace(
                                "|",
                                "_"
                        );

        if (clean.length() > 100) {

            clean =
                    clean.substring(
                            0,
                            100
                    );
        }

        return clean;
    }

    // =========================================================
    // SAFE MESSAGE
    // =========================================================

    private static String safeMessage(
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