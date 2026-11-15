package com.securedocumentexchange.controller;

import com.securedocumentexchange.dao.UserDAO;
import com.securedocumentexchange.model.User;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;

public class RegisterController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label messageLabel;


    private final UserDAO userDAO =
            new UserDAO();


    // =========================================================
    // INSCRIPTION UTILISATEUR
    // =========================================================

    @FXML
    private void register() {

        String username =
                usernameField
                        .getText()
                        .trim();

        String password =
                passwordField
                        .getText();

        String confirmPassword =
                confirmPasswordField
                        .getText();


        // =====================================================
        // VALIDATION
        // =====================================================

        if (
                username.isEmpty()
                        || password.isEmpty()
                        || confirmPassword.isEmpty()
        ) {

            showMessage(
                    "❌ Tous les champs sont obligatoires."
            );

            return;
        }


        if (username.length() < 3) {

            showMessage(
                    "❌ Le nom d'utilisateur doit contenir au moins 3 caractères."
            );

            return;
        }


        if (password.length() < 6) {

            showMessage(
                    "❌ Le mot de passe doit contenir au moins 6 caractères."
            );

            return;
        }


        if (!password.equals(confirmPassword)) {

            showMessage(
                    "❌ Les mots de passe ne correspondent pas."
            );

            return;
        }


        try {

            // =================================================
            // VERIFIER EXISTENCE
            // =================================================

            if (
                    userDAO.usernameExists(
                            username
                    )
            ) {

                showMessage(
                        "❌ Ce nom d'utilisateur existe déjà."
                );

                return;
            }


            // =================================================
            // HASH BCRYPT
            // =================================================

            String passwordHash =
                    BCrypt.hashpw(
                            password,
                            BCrypt.gensalt(12)
                    );


            // =================================================
            // CREATION
            //
            // IMPORTANT :
            // L'utilisateur ne peut pas choisir son rôle.
            //
            // ROLE   = USER
            // STATUS = PENDING
            // =================================================

            User user =
                    new User(
                            username,
                            passwordHash,
                            "USER",
                            "PENDING"
                    );


            boolean created =
                    userDAO.createUser(
                            user
                    );


            if (created) {

                showMessage(
                        "✅ Compte créé avec succès.\n"
                                + "Votre compte est maintenant en attente de validation par l'administrateur."
                );


                usernameField.clear();
                passwordField.clear();
                confirmPasswordField.clear();


            } else {

                showMessage(
                        "❌ Impossible de créer le compte."
                );
            }


        } catch (Exception e) {

            e.printStackTrace();

            showMessage(
                    "❌ Erreur : "
                            + e.getMessage()
            );
        }
    }


    // =========================================================
    // RETOUR LOGIN
    // =========================================================

    @FXML
    private void backToLogin() {

        try {

            FXMLLoader loader =
                    new FXMLLoader(
                            getClass()
                                    .getResource(
                                            "/ui/LoginView.fxml"
                                    )
                    );


            Scene scene =
                    new Scene(
                            loader.load()
                    );


            Stage stage =
                    (Stage)
                            usernameField
                                    .getScene()
                                    .getWindow();


            stage.setScene(scene);

            stage.setTitle(
                    "Secure Document Exchange - Connexion"
            );

            stage.show();


        } catch (Exception e) {

            e.printStackTrace();

            showMessage(
                    "❌ Impossible de revenir à la connexion."
            );
        }
    }


    // =========================================================
    // MESSAGE
    // =========================================================

    private void showMessage(
            String message
    ) {

        messageLabel.setText(
                message
        );
    }
}