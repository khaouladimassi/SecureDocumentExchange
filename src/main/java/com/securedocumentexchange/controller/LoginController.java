package com.securedocumentexchange.controller;

import com.securedocumentexchange.model.User;
import com.securedocumentexchange.service.AuthenticationService;
import com.securedocumentexchange.service.SessionManager;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;

/**
 * Controller de la page de connexion.
 *
 * Responsabilités :
 * - Récupérer le nom d'utilisateur et le mot de passe.
 * - Authentifier l'utilisateur.
 * - Créer la session utilisateur.
 * - Rediriger vers l'interface ADMIN ou USER.
 * - Ouvrir la page d'inscription.
 */
public class LoginController {

    // =========================================================
    // FXML
    // =========================================================

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label messageLabel;


    // =========================================================
    // SERVICES
    // =========================================================

    private final AuthenticationService authenticationService =
            new AuthenticationService();


    // =========================================================
    // CONNEXION
    // =========================================================

    @FXML
    private void login() {

        String username =
                usernameField == null
                        ? ""
                        : usernameField.getText().trim();

        String password =
                passwordField == null
                        ? ""
                        : passwordField.getText();


        // =====================================================
        // VALIDATION
        // =====================================================

        if (username.isEmpty() || password.isEmpty()) {

            setMessage(
                    "❌ Entrez votre nom d'utilisateur et votre mot de passe."
            );

            return;
        }


        try {

            // =================================================
            // AUTHENTIFICATION
            // =================================================

            User user =
                    authenticationService.login(
                            username,
                            password
                    );


            // =================================================
            // AUTHENTIFICATION ÉCHOUÉE
            // =================================================

            if (user == null) {

                String error;

                try {

                    error =
                            authenticationService.getLoginError(
                                    username,
                                    password
                            );

                } catch (Exception ignored) {

                    error =
                            "Nom d'utilisateur ou mot de passe incorrect.";
                }

                if (error == null || error.isBlank()) {

                    error =
                            "Nom d'utilisateur ou mot de passe incorrect.";
                }

                setMessage("❌ " + error);

                return;
            }


            // =================================================
            // VÉRIFICATION ID
            // =================================================

            if (user.getIdUser() <= 0) {

                setMessage(
                        "❌ ID utilisateur invalide."
                );

                return;
            }


            // =================================================
            // VÉRIFICATION DU RÔLE
            // =================================================

            String role = user.getRole();

            if (role == null || role.isBlank()) {

                setMessage(
                        "❌ Le rôle de l'utilisateur est invalide."
                );

                return;
            }


            // =================================================
            // CRÉATION DE SESSION
            // =================================================

            SessionManager.login(user);


            // =================================================
            // LOG CONSOLE
            // =================================================

            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "✓ AUTHENTIFICATION RÉUSSIE"
            );

            System.out.println(
                    "Username : "
                            + user.getUsername()
            );

            System.out.println(
                    "User ID  : "
                            + user.getIdUser()
            );

            System.out.println(
                    "Role     : "
                            + role
            );

            System.out.println(
                    "========================================"
            );


            // =================================================
            // ADMIN
            // =================================================

            if ("ADMIN".equalsIgnoreCase(role)) {

                openAdminView(user);

                return;
            }


            // =================================================
            // USER
            // =================================================

            if ("USER".equalsIgnoreCase(role)) {

                openUserView(user);

                return;
            }


            // =================================================
            // RÔLE INCONNU
            // =================================================

            SessionManager.logout();

            setMessage(
                    "❌ Rôle utilisateur inconnu : " + role
            );


        } catch (Exception e) {

            e.printStackTrace();

            setMessage(
                    "❌ Erreur de connexion : "
                            + getSafeErrorMessage(e)
            );
        }
    }


    // =========================================================
    // OUVRIR USER VIEW
    // =========================================================

    private void openUserView(
            User user
    ) throws Exception {

        URL resource =
                getClass().getResource(
                        "/ui/UserView.fxml"
                );

        if (resource == null) {

            throw new IllegalStateException(
                    "Fichier /ui/UserView.fxml introuvable."
            );
        }


        FXMLLoader loader =
                new FXMLLoader(resource);


        Scene scene =
                new Scene(
                        loader.load()
                );


        UserController controller =
                loader.getController();


        if (controller == null) {

            throw new IllegalStateException(
                    "Impossible de récupérer UserController."
            );
        }


        // =====================================================
        // TRANSMETTRE L'UTILISATEUR AUTHENTIFIÉ
        // =====================================================

        controller.setAuthenticatedUser(
                user
        );


        // =====================================================
        // RÉCUPÉRER LA FENÊTRE ACTUELLE
        // =====================================================

        Stage stage =
                getCurrentStage();


        stage.setScene(scene);

        stage.setTitle(
                "Secure Document Exchange - Utilisateur"
        );

        stage.setResizable(false);

        stage.show();
    }


    // =========================================================
    // OUVRIR ADMIN VIEW
    // =========================================================

    private void openAdminView(
            User user
    ) throws Exception {

        URL resource =
                getClass().getResource(
                        "/ui/AdminView.fxml"
                );

        if (resource == null) {

            throw new IllegalStateException(
                    "Fichier /ui/AdminView.fxml introuvable."
            );
        }


        FXMLLoader loader =
                new FXMLLoader(resource);


        Scene scene =
                new Scene(
                        loader.load()
                );


        AdminController controller =
                loader.getController();


        if (controller == null) {

            throw new IllegalStateException(
                    "Impossible de récupérer AdminController."
            );
        }


        // =====================================================
        // TRANSMETTRE L'ID DE L'ADMIN
        // =====================================================

        controller.setAdminUserId(
                user.getIdUser()
        );


        // =====================================================
        // RÉCUPÉRER LA FENÊTRE ACTUELLE
        // =====================================================

        Stage stage =
                getCurrentStage();


        stage.setScene(scene);

        stage.setTitle(
                "Secure Document Exchange - Administration"
        );

        stage.setResizable(false);

        stage.show();
    }


    // =========================================================
    // OUVRIR REGISTER
    // =========================================================

    @FXML
    private void openRegister() {

        try {

            URL resource =
                    getClass().getResource(
                            "/ui/RegisterView.fxml"
                    );

            if (resource == null) {

                throw new IllegalStateException(
                        "Fichier /ui/RegisterView.fxml introuvable."
                );
            }


            FXMLLoader loader =
                    new FXMLLoader(resource);


            Scene scene =
                    new Scene(
                            loader.load()
                    );


            Stage stage =
                    getCurrentStage();


            stage.setScene(scene);

            stage.setTitle(
                    "Secure Document Exchange - Inscription"
            );

            stage.setResizable(false);

            stage.show();


        } catch (Exception e) {

            e.printStackTrace();

            showAlert(
                    "Erreur",
                    "Impossible d'ouvrir la page d'inscription.\n\n"
                            + getSafeErrorMessage(e)
            );
        }
    }


    // =========================================================
    // RÉCUPÉRER LA FENÊTRE ACTUELLE
    // =========================================================

    private Stage getCurrentStage() {

        if (usernameField == null
                || usernameField.getScene() == null
                || usernameField.getScene().getWindow() == null) {

            throw new IllegalStateException(
                    "Impossible de récupérer la fenêtre actuelle."
            );
        }

        return (Stage)
                usernameField
                        .getScene()
                        .getWindow();
    }


    // =========================================================
    // MESSAGE D'ERREUR
    // =========================================================

    private void setMessage(
            String message
    ) {

        if (messageLabel != null) {

            messageLabel.setText(
                    message == null
                            ? ""
                            : message
            );
        }
    }


    // =========================================================
    // MESSAGE EXCEPTION
    // =========================================================

    private String getSafeErrorMessage(
            Exception e
    ) {

        if (e == null) {
            return "Erreur inconnue.";
        }

        String message =
                e.getMessage();

        if (message == null
                || message.isBlank()) {

            return e.getClass()
                    .getSimpleName();
        }

        return message;
    }


    // =========================================================
    // ALERT
    // =========================================================

    private void showAlert(
            String title,
            String message
    ) {

        Alert alert =
                new Alert(
                        Alert.AlertType.ERROR
                );

        alert.setTitle(
                title
        );

        alert.setHeaderText(
                null
        );

        alert.setContentText(
                message
        );

        alert.showAndWait();
    }
}