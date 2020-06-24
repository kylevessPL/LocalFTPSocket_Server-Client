package com.boxer.client;

import com.boxer.ConsoleLog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.List;

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
     * The Username tf.
     */
    @FXML
    private TextField usernameTF;
    /**
     * The Log ta.
     */
    @FXML
    private TextArea logTA;
    /**
     * The Choose user cb.
     */
    @FXML
    private ChoiceBox<String> chooseUserCB;
    /**
     * The Share file lb.
     */
    @FXML
    private Label shareFileLB;
    /**
     * The Local dir lb.
     */
    @FXML
    private Label localDirLB;

    /**
     * The Communication manager.
     */
    private final CommunicationManager communicationManager = CommunicationManager.getInstance();
    /**
     * The Data manager.
     */
    private final DataManager dataManager = DataManager.getInstance();
    /**
     * The Directory chooser.
     */
    private final DirectoryChooser directoryChooser = new DirectoryChooser();
    /**
     * The File chooser.
     */
    private final FileChooser fileChooser = new FileChooser();
    /**
     * The User list.
     */
    private final ObservableList<String> userList = FXCollections.observableArrayList();
    /**
     * The User share list.
     */
    private final ObservableList<String> userShareList = FXCollections.observableArrayList();
    /**
     * The Local dir.
     */
    private File localDir;
    /**
     * The Share file path.
     */
    private File shareFilePath;
    /**
     * The Watcher.
     */
    WatchService watcher = null;
    /**
     * The Key.
     */
    WatchKey key = null;
    /**
     * The Local dir selected.
     */
    private boolean localDirSelected;
    /**
     * The Share file selected.
     */
    private boolean shareFileSelected;
    /**
     * The Watcher service running.
     */
    private boolean watcherServiceRunning;
    /**
     * The Dir icon.
     */
    private final Image dirIcon = new Image(getClass().getResourceAsStream("resources/icons/directory_icon.png"));
    /**
     * The User icon.
     */
    private final Image userIcon = new Image(getClass().getResourceAsStream("resources/icons/user_icon.png"));

    public void initialize(URL location, ResourceBundle resources) {
        // fill in last used username
        usernameTF.setText(dataManager.getUsername());

        // set up directory & share file chooser
        directoryChooser.setTitle("Select Folder");
        fileChooser.setTitle("Select File to Share");

        // set last used directory as default if exists
        if (new File(dataManager.getLocalDir()).isDirectory()) {
            directoryChooser.setInitialDirectory(new File(dataManager.getLocalDir()));
        }

        // set up UserListView
        userListView.setCellFactory(listView -> new ListCell<String>() {
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
        });

        // initialize UserListView & ShareUser ChoiceBox
        chooseUserCB.setItems(userShareList);
        userListView.setItems(userList);
        ConsoleLog.initialize(logTA);
        UserInterfaceManager.getInstance().register(this);
    }

    /**
     * Shutdown.
     */
    public void shutdown() {
        communicationManager.shutdown(true, clearEnvTask(true));
    }

    /**
     * Login bt on action.
     */
// onClick method for login button
    @FXML
    void loginBT_OnAction() {
        // clear message label
        logTA.setText("Logging in...\n");

        // check that username and password are entered
        if (usernameTF.getCharacters().length() < 1) {
            logTA.appendText("Error, please enter username!\n");

            // show alert
            UserInterfaceManager.getInstance().addAlert(Alert.AlertType.ERROR, "Please enter username first!");
            return;
        }

        // make sure local directory is selected
        if (!localDirSelected) {
            logTA.appendText("Error, please select a local directory\n");

            // show alert
            UserInterfaceManager.getInstance().addAlert(Alert.AlertType.ERROR, "Please select a local directory!");
            return;
        }

        // save the server address and username entered to the data manager
        dataManager.setUsername(usernameTF.getText());

        // save user local directory choice to the data manager
        dataManager.setLocalDir(localDir.getAbsolutePath());

        // connect to the server, in separate thread
        communicationManager.connectToServer();
        if (communicationManager.isConnected()) {
            // set initial file chooser dir to user dir
            fileChooser.setInitialDirectory(new File(dataManager.getLocalDir()));

            communicationManager.syncFilesAndRunJobs();
        } else {
            logTA.appendText("Error, cannot establish a connection with server.\n");

            // show alert
            UserInterfaceManager.getInstance().addAlert(Alert.AlertType.ERROR, "Couldn't establish a connection with server!");
        }
    }

    /**
     * Local dir bt on action.
     */
    @FXML
    void localDirBT_OnAction() {
        // open output directory chooser
        localDir = directoryChooser.showDialog(null);

        // show selected folder
        if (localDir != null) {
            // flag as directory selected
            localDirSelected = true;

            // display output location
            String s = localDir.getAbsolutePath();
            if (s.length() > 38)
                s = s.substring(0, Math.min(s.length(), 38)) + "...";
            localDirLB.setText(s);
        }
    }

    /**
     * Choose file bt on action.
     */
    @FXML
    void chooseFileBT_OnAction() {
        // check if logged in
        if (!communicationManager.isConnected()) {
            logTA.appendText("Error, not logged in. Cannot share files.\n");

            // show alert
            UserInterfaceManager.getInstance().addAlert(Alert.AlertType.ERROR, "Please login first to start sharing files!");
            return;
        }

        // open file chooser
        shareFilePath = fileChooser.showOpenDialog(null);

        // show selected file
        if (shareFilePath != null) {
            if (!new File(localDirLB.getText() + "/" + shareFilePath.getName()).exists()) {
                // show alert
                Alert choice = new Alert(Alert.AlertType.CONFIRMATION);
                choice.setTitle("File not in user directory");
                choice.setHeaderText("The file you would like to share is not in user directory");
                choice.setContentText("Click OK to copy it to your user directory or cancel to abort");
                Optional<ButtonType> result = choice.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    try {
                        Path from = Paths.get(shareFilePath.getAbsolutePath());
                        Path to = Paths.get(localDir.getAbsolutePath() + "/" + shareFilePath.getName());
                        CopyOption[] options = new CopyOption[] {
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.COPY_ATTRIBUTES
                        };
                        Files.copy(from, to, options);
                    } catch (IOException e) {
                        logTA.appendText("Error, couldn't perform file copy operation.\n");

                        // show alert
                        UserInterfaceManager.getInstance().addAlert(Alert.AlertType.ERROR, "There was an error copying file to user directory!");
                        return;
                    }
                } else {
                    // clear file chooser selected file path
                    shareFilePath = null;
                    logTA.appendText("Shared file not in user directory, aborted as per user request.\n");
                    return;
                }
            }

            // flag as directory selected
            shareFileSelected = true;

            // display shared filename
            String s = shareFilePath.getName();
            if (s.length() > 20)
                s = s.substring(0, Math.min(s.length(), 20)) + "...";
            shareFileLB.setText(s);
        }
    }

    /**
     * Share file bt on action.
     */
    @FXML
    void shareFileBT_OnAction() {
        // check if logged in
        if (!communicationManager.isConnected()) {
            logTA.appendText("Error, not logged in. Cannot share files.\n");

            // show alert
            UserInterfaceManager.getInstance().addAlert(Alert.AlertType.ERROR, "Not logged in, cannot share files.");
            return;
        }

        // check if share target is selected
        if (!shareFileSelected) {
            logTA.appendText("Error, file to share not chosen.\n");

            // show alert
            UserInterfaceManager.getInstance().addAlert(Alert.AlertType.ERROR, "Please choose a file to share first!");
            return;
        }

        // check if share target user is selected
        if (chooseUserCB.getSelectionModel().isEmpty()) {
            logTA.appendText("Error, target user not selected. Cannot share file.\n");

            // show alert
            UserInterfaceManager.getInstance().addAlert(Alert.AlertType.ERROR, "Please choose share target user first!");
            return;
        }

        String shareFile = shareFilePath.getName();
        String shareUser = chooseUserCB.getSelectionModel().getSelectedItem();

        communicationManager.shareFileToServer(shareFile, shareUser);
    }

    /**
     * Open dir bt on action.
     *
     * @throws IOException the io exception
     */
    @FXML
    void openDirBT_OnAction() throws IOException {
        if (!communicationManager.isConnected()) {
            logTA.appendText("Error, not logged in. Login first to access your drive.\n");

            // show alert
            UserInterfaceManager.getInstance().addAlert(Alert.AlertType.ERROR, "Not logged in, cannot access user drive.");
            return;
        }

        Desktop desktop = Desktop.getDesktop();
        desktop.open(new File(dataManager.getLocalDir()));
    }

    /**
     * Display file tree.
     */
// use recursive method to build and display file tree
    protected void displayFileTree() {
        // set up file tree
        TreeItem<String> rootItem = new TreeItem<>("MyBoxer: /", new ImageView(userIcon));
        rootItem.setExpanded(true);

        // set the tree root
        fileTreeView.setRoot(rootItem);

        // start building the file tree
        buildFileTree(localDir, rootItem);
    }

    /**
     * Build file tree.
     *
     * @param dir      the dir
     * @param treeNode the tree node
     */
// builds the tree view of the files
    private void buildFileTree(File dir, TreeItem<String> treeNode) {
        // display the files
        File[] files = dir.listFiles();
        for (File file : files) {
            if (!file.isHidden()) {
                TreeItem<String> node;
                // if a directory, build a subtree node
                if (file.isDirectory()) {
                    node = new TreeItem<>(file.getName(), new ImageView(dirIcon));
                    buildFileTree(file, node);
                } else {
                    node = new TreeItem<>(file.getName());
                }

                // add file to file tree
                treeNode.getChildren().add(node);
            }
        }
    }

    /**
     * Clear dir.
     *
     * @param dir the dir
     */
// use recursive method to clear dir content with its subdirectories
    private void clearDir(File dir) {
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                clearDir(file);
            }
            file.delete();
        }
    }

    /**
     * On success share file.
     */
    protected void onSuccessShareFile() {
        // set all sharing-related to default
        shareFileLB.setText("No file selected");
        fileChooser.setInitialFileName(null);
        shareFilePath = null;
        chooseUserCB.getSelectionModel().clearSelection();
    }

    /**
     * Clear env task runnable.
     *
     * @param clearLocalDirPath the clear local dir path
     * @return the runnable
     */
    public Runnable clearEnvTask(final boolean clearLocalDirPath) {
        return () -> {
            // reset all to defaults
            if (clearLocalDirPath) {
                localDirLB.setText("Select a local directory...");

                // clear user directory content
                if (localDirSelected) {
                    clearDir(localDir);
                }

                localDir = null;
                localDirSelected = false;
                fileChooser.setInitialDirectory(null);
            }

            shareFileLB.setText("No file selected");
            shareFilePath = null;
            shareFileSelected = false;
            userList.clear();
            userListView.getSelectionModel().clearSelection();
            userListView.getItems().clear();
            chooseUserCB.getSelectionModel().clearSelection();
            chooseUserCB.getItems().clear();
            fileTreeView.setRoot(null);

            // stop DirWatcher Service if running
            if (watcherServiceRunning) {
                key = null;
                watcher = null;
                watcherServiceRunning = false;
            }
        };
    }

    /**
     * Update user lists.
     *
     * @param newUsersList the new users list
     */
    public void updateUserLists(List<String> newUsersList) {
        List<String> oldUserList = new ArrayList<>(userList);

        // update userlist if necessary
        if(!oldUserList.equals(newUsersList)) {
            userList.clear();
            userList.addAll(newUsersList);
            userShareList.clear();
            userShareList.addAll(newUsersList.subList(1, newUsersList.size()));
        }
    }
}
