package com.securedocumentexchange.service;

import com.securedocumentexchange.dao.UserDAO;
import com.securedocumentexchange.model.User;
import org.mindrot.jbcrypt.BCrypt;

public class AuthenticationService {

    private final UserDAO userDAO;

    public AuthenticationService() {
        userDAO = new UserDAO();
    }

    // =========================================================
    // LOGIN
    // =========================================================

    public User login(
            String username,
            String password
    ) {

        try {

            User user =
                    userDAO.findByUsername(username);

            // -------------------------------------------------
            // Utilisateur inexistant
            // -------------------------------------------------

            if (user == null) {
                return null;
            }

            // -------------------------------------------------
            // Vérification BCrypt
            // -------------------------------------------------

            if (user.getPassword() == null
                    || user.getPassword().isBlank()) {

                return null;
            }

            if (!BCrypt.checkpw(
                    password,
                    user.getPassword()
            )) {

                return null;
            }

            // -------------------------------------------------
            // Compte REFUSED = connexion interdite
            // -------------------------------------------------

            if ("REFUSED".equalsIgnoreCase(
                    user.getStatus()
            )) {

                return null;
            }

            // -------------------------------------------------
            // PENDING ou ACCEPTED = connexion autorisée
            // -------------------------------------------------

            return user;

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }
    }


    // =========================================================
    // MESSAGE D'ERREUR
    // =========================================================

    public String getLoginError(
            String username,
            String password
    ) {

        try {

            User user =
                    userDAO.findByUsername(username);

            if (user == null) {

                return "Utilisateur inexistant.";
            }

            if (user.getPassword() == null
                    || user.getPassword().isBlank()) {

                return "Mot de passe non configuré.";
            }

            if (!BCrypt.checkpw(
                    password,
                    user.getPassword()
            )) {

                return "Mot de passe incorrect.";
            }

            if ("REFUSED".equalsIgnoreCase(
                    user.getStatus()
            )) {

                return "Compte refusé par l'administrateur.";
            }

            return "Erreur d'authentification.";

        } catch (Exception e) {

            e.printStackTrace();

            return "Erreur lors de l'authentification.";
        }
    }
}