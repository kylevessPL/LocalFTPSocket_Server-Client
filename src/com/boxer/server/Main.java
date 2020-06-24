package com.boxer.server;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * The type Main.
 */
public class Main extends Application {
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("resources/fxml/main.fxml"));
        Parent root = loader.load();
        primaryStage.setTitle("BoxerServer");
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("resources/icons/server.png")));
        primaryStage.setScene(new Scene(root));
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(600);
        primaryStage.show();

        // shutdown any running threads when application exit button is pressed
        MainController controller = loader.getController();
        primaryStage.setOnCloseRequest(e -> controller.shutdown());
    }

    /**
     * The entry point of application.
     *
     * @param args the input arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}