package com.boxer.client;

import com.boxer.ThreadManager;
import javafx.scene.control.Alert;

import java.util.List;

/**
 * The type User interface manager.
 */
public class UserInterfaceManager {
    /**
     * The constant INSTANCE.
     */
    private static final UserInterfaceManager INSTANCE = new UserInterfaceManager();

    /**
     * The Main controller.
     */
    private MainController mainController;

    /**
     * Instantiates a new User interface manager.
     */
    private UserInterfaceManager() {
    }

    /**
     * Get instance user interface manager.
     *
     * @return the user interface manager
     */
    public static UserInterfaceManager getInstance(){
        return INSTANCE;
    }

    /**
     * Register.
     *
     * @param controller the controller
     */
    public void register(MainController controller){
        this.mainController = controller;
    }

    /**
     * Gets main controller.
     *
     * @return the main controller
     */
    public MainController getMainController() {
        return mainController;
    }

    /**
     * Add alert.
     *
     * @param alertType the alert type
     * @param message   the message
     */
    public void addAlert(Alert.AlertType alertType, String message) {
        ThreadManager.runOnUiThread(() -> {
            Alert alert = new Alert(alertType, message);
            alert.showAndWait();
        });
    }

    /**
     * Update user list.
     *
     * @param userList the user list
     */
    public void updateUserList(final List<String> userList) {
        ThreadManager.runOnUiThread(() -> mainController.updateUserLists(userList));
    }

    /**
     * Display file tree.
     */
    public void displayFileTree() {
        ThreadManager.runOnUiThread(() -> mainController.displayFileTree());
    }

    /**
     * Clear after success share file.
     */
    public void clearAfterSuccessShareFile() {
        ThreadManager.runOnUiThread(() -> mainController.onSuccessShareFile());
    }
}
