package com.boxer.server;

import com.boxer.ThreadManager;

import java.io.File;
import java.util.List;

/**
 * The type Server user interface manager.
 */
public class ServerUserInterfaceManager {
    /**
     * The constant INSTANCE.
     */
    private static final ServerUserInterfaceManager INSTANCE = new ServerUserInterfaceManager();

    /**
     * The Main controller.
     */
    private MainController mainController;

    /**
     * Instantiates a new Server user interface manager.
     */
    private ServerUserInterfaceManager() {
    }

    /**
     * Get instance server user interface manager.
     *
     * @return the server user interface manager
     */
    public static ServerUserInterfaceManager getInstance(){
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
     * Display file tree.
     *
     * @param users the users
     * @param files the files
     */
    public void displayFileTree(final List<String> users, final List<File> files) {
        ThreadManager.runOnUiThread(() -> {
            mainController.updateUsersList(users);
            mainController.displayFileTree(users, files);
        });
    }
}
