package com.boxer.server;

import com.boxer.ConsoleLog;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * The type Main controller.
 */
public class MainController implements Initializable {
    /**
     * The File tree view.
     */
// UI elements
    @FXML
    private TreeView<String> fileTreeView;
    /**
     * The User list view.
     */
    @FXML
    private ListView<String> userListView;
    /**
     * The Log ta.
     */
    @FXML
    private TextArea logTA;

    /**
     * The User list.
     */
    private final ObservableList<String> userList = FXCollections.observableArrayList();
    /**
     * The Dir icon.
     */
    private final Image dirIcon = new Image(getClass().getResourceAsStream("resources/icons/directory_icon.png"));
    /**
     * The User icon.
     */
    private final Image userIcon = new Image(getClass().getResourceAsStream("resources/icons/user_icon.png"));

    /**
     * The Communication manager.
     */
    private final ServerCommunicationManager communicationManager = ServerCommunicationManager.getInstance();

    /**
     * Shutdown.
     */
    public void shutdown() {
        communicationManager.shutdown();
    }

    /**
     * The type User name exists.
     */
    public static class UserNameExists extends SocketException {
        /**
         * Instantiates a new User name exists.
         */
        public UserNameExists() {
            super("username already in-use");
        }
    }

    public void initialize(URL location, ResourceBundle resources) {
        // set up UserListView
        Platform.runLater(() -> userListView.setCellFactory(listView -> new ListCell<String>() {
            private final ImageView icon = new ImageView(userIcon);

            @Override
            public void updateItem(String user, boolean empty) {
                super.updateItem(user, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(user);
                    setGraphic(icon);
                }
            }
        }));

        // initialize server socket
        while (!communicationManager.isStarted()) {
            try {
                communicationManager.startServerSocket();
            } catch (IOException e) {
                // show alert
                ButtonType retry = new ButtonType("Retry", ButtonBar.ButtonData.OK_DONE);
                ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                Alert choice = new Alert(Alert.AlertType.WARNING, "Click retry to try to start service again or cancel to abort and exit BoxerServer app", retry, cancel);
                choice.setTitle("Couldn't initialize server");
                choice.setHeaderText("Server port is probably busy");
                Optional<ButtonType> result = choice.showAndWait();
                if (result.isPresent() && result.get() == cancel) {
                    // exit app upon user request
                    communicationManager.shutdown();
                    Platform.exit();
                }
            }
        }

        // initialize UserListView
        userListView.setItems(userList);
        ConsoleLog.initialize(logTA);
        ServerUserInterfaceManager.getInstance().register(this);
        communicationManager.handleClientConnections();
    }

    /**
     * Display file tree.
     *
     * @param users the users
     * @param files the files
     */
// use recursive method to build and display file tree
    protected void displayFileTree(List<String> users, List<File> files) {
        // set up file tree
        TreeItem<String> rootItem = new TreeItem<>();
        rootItem.setExpanded(true);

        // build file tree for each user
        for (int i = 0; i < files.size(); i++) {
            File userDir = files.get(i);
            String userName = users.get(i);
            TreeItem<String> userNode = new TreeItem<>(userName + ": /", new ImageView(userIcon));
            buildUserFileTree(userDir, userNode);
            rootItem.getChildren().add(userNode);
        }

        // set the dummy tree root and hide it afterwards
        fileTreeView.setRoot(rootItem);
        fileTreeView.setShowRoot(false);
    }

    /**
     * Build user file tree.
     *
     * @param dir      the dir
     * @param userNode the user node
     */
// builds the tree view of particular user
    private void buildUserFileTree(File dir, TreeItem<String> userNode) {
        // display the files
        File[] files = dir.listFiles();
        for (File file : files) {
            if (!file.isHidden()) {
                TreeItem<String> node;

                // if a directory, build a subtree node
                if (file.isDirectory()) {
                    node = new TreeItem<>(file.getName(), new ImageView(dirIcon));
                    buildUserFileTree(file, node);
                } else {
                    node = new TreeItem<>(file.getName());
                }

                // add file to file tree
                userNode.getChildren().add(node);
            }
        }
    }

    /**
     * Update users list.
     *
     * @param users the users
     */
    public void updateUsersList(List<String> users) {
        userList.clear();
        userList.addAll(users);
    }
}