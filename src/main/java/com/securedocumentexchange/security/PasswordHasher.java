package com.securedocumentexchange.security;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordHasher {

    /**
     * Génère le hash BCrypt d'un mot de passe.
     */
    public static String hashPassword(String password) {

        return BCrypt.hashpw(password, BCrypt.gensalt());

    }

    /**
     * Vérifie si le mot de passe correspond au hash.
     */
    public static boolean verifyPassword(String password, String hashedPassword) {

        return BCrypt.checkpw(password, hashedPassword);

    }

}