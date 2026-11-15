package com.securedocumentexchange;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage)
            throws Exception {

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

        stage.setTitle(
                "Secure Document Exchange"
        );

        stage.setScene(scene);

        stage.setResizable(false);

        stage.show();
    }


    public static void main(String[] args) {

        launch(args);
    }
}