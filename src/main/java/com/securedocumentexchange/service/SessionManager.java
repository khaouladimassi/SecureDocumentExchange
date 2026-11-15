package com.securedocumentexchange.service;

import com.securedocumentexchange.model.User;

/**
 * Gestion de la session de l'utilisateur actuellement connecté.
 */
public final class SessionManager {

    private static User currentUser;

    private SessionManager() {
    }

    // =========================================================
    // LOGIN
    // =========================================================

    public static void login(User user) {

        if (user == null) {
            throw new IllegalArgumentException(
                    "Utilisateur null."
            );
        }

        if (user.getIdUser() <= 0) {
            throw new IllegalArgumentException(
                    "ID utilisateur invalide : "
                            + user.getIdUser()
            );
        }

        currentUser = user;

        System.out.println(
                "✓ Session ouverte : "
                        + user.getUsername()
                        + " | ID = "
                        + user.getIdUser()
                        + " | Role = "
                        + user.getRole()
        );
    }

    // =========================================================
    // USER COURANT
    // =========================================================

    public static User getCurrentUser() {
        return currentUser;
    }

    // =========================================================
    // ID USER COURANT
    // =========================================================

    public static int getCurrentUserId() {

        if (currentUser == null) {
            return 0;
        }

        return currentUser.getIdUser();
    }

    // =========================================================
    // USERNAME
    // =========================================================

    public static String getCurrentUsername() {

        if (currentUser == null) {
            return null;
        }

        return currentUser.getUsername();
    }

    // =========================================================
    // ROLE
    // =========================================================

    public static String getCurrentRole() {

        if (currentUser == null) {
            return null;
        }

        return currentUser.getRole();
    }

    // =========================================================
    // ADMIN
    // =========================================================

    public static boolean isAdmin() {

        return currentUser != null
                && "ADMIN".equalsIgnoreCase(
                currentUser.getRole()
        );
    }

    // =========================================================
    // CONNECTÉ
    // =========================================================

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    // =========================================================
    // LOGOUT
    // =========================================================

    public static void logout() {

        if (currentUser != null) {

            System.out.println(
                    "✓ Déconnexion : "
                            + currentUser.getUsername()
            );
        }

        currentUser = null;
    }
}