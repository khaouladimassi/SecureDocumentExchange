package com.securedocumentexchange.network;

import com.securedocumentexchange.security.TLSManager;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

public class TLSServer {

    private SSLServerSocket serverSocket;

    private volatile boolean running = false;

    private static final int PORT = 5000;

    private final ClientHandler.Listener listener;


    public TLSServer(
            ClientHandler.Listener listener
    ) {

        this.listener = listener;
    }


    // =========================================================
    // START
    // =========================================================

    public void start()
            throws Exception {

        if (running) {
            return;
        }


        SSLContext context =
                TLSManager
                        .createServerSSLContext();


        SSLServerSocketFactory factory =
                context
                        .getServerSocketFactory();


        serverSocket =
                (SSLServerSocket)
                        factory.createServerSocket(
                                PORT
                        );


        serverSocket.setEnabledProtocols(
                new String[]{
                        "TLSv1.3",
                        "TLSv1.2"
                }
        );


        running = true;


        System.out.println();
        System.out.println(
                "================================="
        );
        System.out.println(
                " SECURE DOCUMENT TLS SERVER"
        );
        System.out.println(
                "================================="
        );
        System.out.println(
                "TCP/TLS : 5000"
        );
        System.out.println(
                "✓ Serveur TLS démarré"
        );


        while (running) {

            try {

                SSLSocket client =
                        (SSLSocket)
                                serverSocket.accept();


                ClientHandler handler =
                        new ClientHandler(
                                client,
                                listener
                        );


                Thread thread =
                        new Thread(
                                handler,
                                "ClientHandler"
                        );


                thread.setDaemon(true);

                thread.start();


            } catch (Exception e) {

                if (running) {

                    System.err.println(
                            "❌ Erreur client : "
                                    + e.getMessage()
                    );
                }
            }
        }
    }


    // =========================================================
    // STOP
    // =========================================================

    public void stop() {

        running = false;


        try {

            if (serverSocket != null) {
                serverSocket.close();
            }

        } catch (Exception ignored) {
        }
    }


    public boolean isRunning() {
        return running;
    }
}