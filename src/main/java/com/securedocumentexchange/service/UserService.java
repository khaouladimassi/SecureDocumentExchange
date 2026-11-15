package com.securedocumentexchange.service;

import com.securedocumentexchange.dao.UserDAO;
import com.securedocumentexchange.model.User;
import org.mindrot.jbcrypt.BCrypt;

public class UserService {

    private final UserDAO userDAO;

    public UserService() {
        userDAO = new UserDAO();
    }


    // =========================================================
    // INSCRIPTION
    // =========================================================

    public void register(
            String username,
            String password
    ) throws Exception {

        username = username.trim();

        if (username.isEmpty()) {
            throw new Exception(
                    "Le nom d'utilisateur est obligatoire."
            );
        }

        if (password == null || password.length() < 6) {
            throw new Exception(
                    "Le mot de passe doit contenir au moins 6 caractères."
            );
        }


        if (userDAO.usernameExists(username)) {

            throw new Exception(
                    "Ce nom d'utilisateur existe déjà."
            );
        }


        // BCrypt
        String hashedPassword =
                BCrypt.hashpw(
                        password,
                        BCrypt.gensalt(12)
                );


        User user =
                new User(
                        username,
                        hashedPassword,
                        "USER",
                        "PENDING"
                );


        if (!userDAO.createUser(user)) {

            throw new Exception(
                    "Impossible de créer le compte."
            );
        }
    }


    // =========================================================
    // AUTHENTIFICATION
    // =========================================================

    public User authenticate(
            String username,
            String password
    ) throws Exception {

        username = username.trim();

        User user =
                userDAO.findByUsername(
                        username
                );


        if (user == null) {

            throw new Exception(
                    "Nom d'utilisateur ou mot de passe incorrect."
            );
        }


        boolean passwordCorrect =
                BCrypt.checkpw(
                        password,
                        user.getPassword()
                );


        if (!passwordCorrect) {

            throw new Exception(
                    "Nom d'utilisateur ou mot de passe incorrect."
            );
        }


        return user;
    }
}