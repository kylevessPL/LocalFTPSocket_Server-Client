package com.boxer.client;

import com.boxer.ConsoleLog;
import com.boxer.ThreadManager;
import com.boxer.exceptions.BoxerException;
import javafx.scene.control.Alert;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * The type Communication manager.
 */
public class CommunicationManager {
    /**
     * The constant LOGGER.
     */
    private static final Logger LOGGER = Logger.getLogger(CommunicationManager.class.getName());
    /**
     * The constant INSTANCE.
     */
    private static final CommunicationManager INSTANCE = new CommunicationManager();

    /**
     * The constant PORT_NUMBER.
     */
// variables
    static final int PORT_NUMBER = 59090;

    /**
     * The Executor.
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    /**
     * The User jobs.
     */
    private final ScheduledExecutorService userJobs = Executors.newScheduledThreadPool(3);

    /**
     * The constant ENTRY_POINT.
     */
    private static final Semaphore ENTRY_POINT = new Semaphore(1);

    /**
     * The Socket.
     */
    private Socket socket;
    /**
     * The Input.
     */
    private DataInputStream input;
    /**
     * The Output.
     */
    private DataOutputStream output;
    /**
     * The Watcher.
     */
    private WatchService watcher;
    /**
     * The Key.
     */
    WatchKey key = null;

    /**
     * Instantiates a new Communication manager.
     */
// private constructor to create single instance only
    private CommunicationManager() {
    }

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static CommunicationManager getInstance() {
        return INSTANCE;
    }

    /**
     * Connect to server.
     */
    public void connectToServer() {
        // try to connect
        try {
            InetAddress ip = InetAddress.getLocalHost();

            // establish the connection with server on port 59090
            socket = new Socket(ip, PORT_NUMBER);
            if (socket.isConnected()) {
                // logged in successfully, save preferences
                DataManager.getInstance().savePreferences();
                input = new DataInputStream(socket.getInputStream());
                output = new DataOutputStream(socket.getOutputStream());
                executor.submit(this::runAtStart);
            }
        } catch (Exception e) {
            LOGGER.severe("Error, couldn't establish a stable connection with server.");
        }
    }

    /**
     * Sync files and run jobs.
     */
    public void syncFilesAndRunJobs() {
        executor.submit(this::runJobs);
    }

    /**
     * Run at start.
     */
    private void runAtStart() {
        // sync files with server
        ConsoleLog consoleLog = ConsoleLog.getInstance();

        // lemme introduce myself
        try {
            userIntroduce();

            getUserList();
            consoleLog.log("Successfully obtained user list from server.\n");

            receiveFiles();
            consoleLog.log("File sync with server completed successfully.\n");

            // logged in ok
            consoleLog.log("Login successful.\n");

            // show alert
            UserInterfaceManager.getInstance().addAlert(Alert.AlertType.INFORMATION, "Successfully logged-in.");
        } catch (BoxerException e) {
            switch (e.getExceptionType()) {
                case INTRODUCE_ERROR:
                    consoleLog.log("Error, " + e.getLocalizedMessage() + "!\n");

                    try {
                        // disconnect client
                        socket.close();
                        socket = null;
                    } catch (Exception ex) {
                        consoleLog.log("An error occurred!\n");
                    }

                    // clear the environment
                    ThreadManager.runOnUiThread(UserInterfaceManager.getInstance().getMainController().clearEnvTask(true));

                    // show alert
                    UserInterfaceManager.getInstance().addAlert(Alert.AlertType.ERROR, "Error, " + e.getLocalizedMessage() + "!\n");

                    // terminate user job threads
                    try {
                        JobManager.getInstance().cancelAllJobs();
                        userJobs.awaitTermination(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {}

                    break;
                case GET_USER_LIST_ERROR:
                    consoleLog.log("Error, couldn't receive list of users from server!\n");
                    disconnectFromServer(false, UserInterfaceManager.getInstance().getMainController().clearEnvTask(true));

                    // show alert
                    UserInterfaceManager.getInstance().addAlert(Alert.AlertType.ERROR, "Couldn't get list of users!");
                    break;
                case SYNC_FILES_ERROR:
                    consoleLog.log("Error, couldn't sync files with server!\n");
                    disconnectFromServer(false, UserInterfaceManager.getInstance().getMainController().clearEnvTask(true));

                    // show alert
                    UserInterfaceManager.getInstance().addAlert(Alert.AlertType.ERROR, "Couldn't sync files with server!");
            }
        }
    }

    /**
     * Run jobs.
     */
    private void runJobs() {
        ConsoleLog consoleLog = ConsoleLog.getInstance();
        runSyncFileJob(consoleLog);
        runGetUsersJob(consoleLog);
        runDirWatcherJob(consoleLog);
    }

    /**
     * Run dir watcher job.
     *
     * @param consoleLog the console log
     */
    private void runDirWatcherJob(ConsoleLog consoleLog) {
        JobManager jobManager = JobManager.getInstance();
        if (jobManager.getDirWatcherJob() == null || jobManager.getDirWatcherJob().isCancelled()) {
            ScheduledFuture<?> dirWatcherJob = userJobs.scheduleWithFixedDelay(() -> {
                try {
                    LOGGER.info("dirWatcher service started");
                    watcher = FileSystems.getDefault().newWatchService();
                    Path dir = Paths.get(DataManager.getInstance().getLocalDir());
                    dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                    while (true) {
                        try {
                            key = watcher.take();
                        } catch (InterruptedException e) {
                            LOGGER.info("dirWatcher service stopped");
                            jobManager.cancelDirWatcherJob();
                            return;
                        }
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();

                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> ev = (WatchEvent<Path>) event;
                            Path fileName = ev.context();

                            if (kind == ENTRY_CREATE || kind == ENTRY_DELETE || kind == ENTRY_MODIFY) {
                                // refresh file tree
                                UserInterfaceManager.getInstance().displayFileTree();

                                LOGGER.info("event kind: " + kind + ", file affected: " + fileName);

                                if (kind == ENTRY_CREATE || kind == ENTRY_MODIFY) {
                                    sendFiles(fileName.toFile());
                                    LOGGER.info("successfully synced local " + (Files.isDirectory(fileName) ? "directory " : "file ") + fileName.toString() + " changes with MyBoxer");
                                    consoleLog.log("Successfully synced local " + (Files.isDirectory(fileName) ? "directory " : "file ") + fileName.toString() + " changes with your MyBoxer.\n");
                                } else {
                                    deleteFileCode(fileName.toString());
                                    LOGGER.info("successfully removed " + fileName.toString() + (Files.isDirectory(fileName) ? " directory" : " file") + " from MyBoxer");
                                    consoleLog.log("Successfully removed " + fileName.toString() + (Files.isDirectory(fileName) ? " directory" : " file") + " from your MyBoxer.\n");

                                    // check if the entry affected the whole local dir
                                    // ...if so, recreate
                                    File localDir = new File(DataManager.getInstance().getLocalDir());
                                    if (!localDir.exists()) {
                                        localDir.mkdir();
                                    }
                                }
                            }
                        }
                        boolean valid = key.reset();
                        if (!valid) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Error, DirWatcher service stopped unexpectedly." + e.getMessage());
                    disconnectFromServer(false, UserInterfaceManager.getInstance().getMainController().clearEnvTask(true));

                    // show alert
                    UserInterfaceManager.getInstance().addAlert(Alert.AlertType.WARNING, "You are going to be log out now!");
                }
            }, 3, 4, TimeUnit.SECONDS);
            jobManager.setDirWatcherJob(dirWatcherJob);
        }
    }

    /**
     * Send files.
     *
     * @param file the file
     * @throws Exception the exception
     */
    private void sendFiles(File file) throws Exception {
        try {
            ENTRY_POINT.acquire();
            LOGGER.info("sendFiles acquired semaphore");

            // create list for files
            List<File> files = new ArrayList<>();

            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            dos.writeInt(40);
            if (dis.readInt() != 45) {
                throw new SocketException();
            }

            // add files to list recursively
            File fileChanged = new File(DataManager.getInstance().getLocalDir() + File.separator + file.getName());
            Files.walk(fileChanged.toPath())
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> files.add(path.toFile()));
            List<File> filesPaths = files.stream()
                    .map(f -> new File(f.getAbsolutePath().replace(DataManager.getInstance().getLocalDir(), "")))
                    .collect(Collectors.toList());
            dos.writeInt(files.size());

            for (int i = 0; i < filesPaths.size(); i++) {
                File fileToSend = files.get(i);
                long length = fileToSend.length();
                dos.writeLong(length);

                // get relative path to file
                String fullName = filesPaths.get(i).getPath();
                dos.writeUTF(fullName);
                FileInputStream fis = new FileInputStream(fileToSend);
                byte[] buffer = new byte[8192];
                int count;
                long total = 0;
                while (total < length && (count = fis.read(buffer, 0, length - total > buffer.length ? buffer.length : (int) (length - total))) > 0) {
                    dos.write(buffer, 0, count);
                    total += count;
                }
                dos.flush();
            }
        } finally {
            LOGGER.info("sendFiles release semaphore");
            ENTRY_POINT.release();
        }
    }

    /**
     * Delete file code.
     *
     * @param deleteFile the delete file
     * @throws Exception the exception
     */
    private void deleteFileCode(String deleteFile) throws Exception {
        try {
            ENTRY_POINT.acquire();
            LOGGER.info("deleteFile acquired semaphore");
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            dos.writeInt(50);
            if (dis.readInt() != 55) {
                throw new SocketException();
            }
            dos.writeUTF(deleteFile);
        } finally {
            LOGGER.info("deleteFile release semaphore");
            ENTRY_POINT.release();
        }
    }

    /**
     * Run get users job.
     *
     * @param consoleLog the console log
     */
    private void runGetUsersJob(ConsoleLog consoleLog) {
        JobManager jobManager = JobManager.getInstance();
        if (jobManager.getUsersJob() == null || jobManager.getUsersJob().isCancelled()) {
            ScheduledFuture<?> usersJob = userJobs.scheduleWithFixedDelay(() -> {
                try {
                    LOGGER.info("getUserJob before start");
                    ENTRY_POINT.acquire();
                    LOGGER.info("getUserJob started");

                    // refresh list of users
                    try {
                        getUserList();
                    } catch (Exception e) {
                        consoleLog.log("Error, couldn't receive list of users from server!\n");
                        disconnectFromServer(false, UserInterfaceManager.getInstance().getMainController().clearEnvTask(true));

                        // show alert
                        UserInterfaceManager.getInstance().addAlert(Alert.AlertType.ERROR, "Couldn't get list of users!");
                        jobManager.cancelGetUsersJob();
                    }
                } catch (Exception e) {
                    consoleLog.log("Error, UserListMonitor Service stopped unexpectedly!\n");
                    disconnectFromServer(false, UserInterfaceManager.getInstance().getMainController().clearEnvTask(true));

                    // show alert
                    UserInterfaceManager.getInstance().addAlert(Alert.AlertType.WARNING, "There was a problem with UserListMonitor Service, data may not be synced correctly!");
                    jobManager.cancelGetUsersJob();
                } finally {
                    ENTRY_POINT.release();
                }
            }, 1, 4, TimeUnit.SECONDS);
            jobManager.setUsersJob(usersJob);
        }
    }

    /**
     * Run sync file job.
     *
     * @param consoleLog the console log
     */
    private void runSyncFileJob(ConsoleLog consoleLog) {
        JobManager jobManager = JobManager.getInstance();
        if (jobManager.getSyncFilesJob() == null || jobManager.getSyncFilesJob().isCancelled()) {
            ScheduledFuture<?> syncFilesJob = userJobs.scheduleWithFixedDelay(() -> {
                try {
                    LOGGER.info("receiveFilesJob before start");
                    ENTRY_POINT.acquire();
                    LOGGER.info("receiveFilesJob started");

                    // ask server if new shared file available
                    try {
                        receiveFiles();
                    } catch (Exception e) {
                        consoleLog.log("Error, couldn't establish a connection with the server!\nConnection with server lost!\n");

                        // show alert
                        UserInterfaceManager.getInstance().addAlert(Alert.AlertType.ERROR, "Couldn't establish a connection with server!");
                        jobManager.cancelSyncFilesJob();
                    }
                } catch (Exception e) {
                    consoleLog.log("Error, SharedFileMonitor Service stopped unexpectedly!\n");
                    disconnectFromServer(false, UserInterfaceManager.getInstance().getMainController().clearEnvTask(true));

                    // show alert
                    UserInterfaceManager.getInstance().addAlert(Alert.AlertType.WARNING, "There was a problem with SharedFileMonitor Service, data may not be synced correctly!");
                    jobManager.cancelSyncFilesJob();
                } finally {
                    ENTRY_POINT.release();
                }
                LOGGER.info("receiveFilesJob ended");
            }, 2, 4, TimeUnit.SECONDS);
            jobManager.setSyncFilesJob(syncFilesJob);
        }
    }

    /**
     * User introduce.
     *
     * @throws BoxerException the boxer exception
     */
    private void userIntroduce() throws BoxerException {
        try {
            output.writeUTF(DataManager.getInstance().getUsername());
            int reply = input.readInt();
            if (reply != 65) {
                if (reply == -1) {
                    throw new BoxerException(BoxerException.ExceptionType.INTRODUCE_ERROR, "username already in-use");
                } else {
                    throw new BoxerException(BoxerException.ExceptionType.INTRODUCE_ERROR, "error in connection");
                }
            }
        } catch (Exception e) {
            throw new BoxerException(BoxerException.ExceptionType.INTRODUCE_ERROR, e.getMessage());
        }
    }

    /**
     * Gets user list.
     *
     * @throws BoxerException the boxer exception
     */
    private void getUserList() throws BoxerException {
        try {
            output.writeInt(30);

            // read userlist from the socket
            String users = input.readUTF();
            String[] result = users.split(",");
            List<String> userList = new ArrayList<>();
            userList.add("Me");

            String thisUsername = DataManager.getInstance().getUsername();
            for (Object object : result) {
                if (object instanceof String) {
                    String userName = (String) object;

                    // add to userlist if it's not me
                    if (!userName.equals(thisUsername)) {
                        userList.add(userName);
                    }
                }
            }

            UserInterfaceManager.getInstance().updateUserList(userList);
        } catch (Exception e) {
            throw new BoxerException(BoxerException.ExceptionType.GET_USER_LIST_ERROR, e.getMessage());
        }
    }

    /**
     * Receive files.
     *
     * @throws BoxerException the boxer exception
     */
    private void receiveFiles() throws BoxerException {
        try {
            output.writeInt(10);
            LOGGER.info("waiting for server...");
            int reply = input.readInt();
            LOGGER.info("get reply from server: " + reply);
            if (reply != 15) {
                return;
            }

            int filesCount = input.readInt();
            File[] files = new File[filesCount];
            for (int i = 0; i < filesCount; i++) {
                long length = input.readLong();
                String fileFullName = input.readUTF();
                files[i] = new File(DataManager.getInstance().getLocalDir() + File.separator + fileFullName);
                files[i].getParentFile().mkdirs();
                FileOutputStream fos = new FileOutputStream(files[i]);
                byte[] buffer = new byte[8192];
                int count;
                long total = 0;
                while (total < length && (count = input.read(buffer, 0, length - total > buffer.length ? buffer.length : (int) (length - total))) > 0) {
                    fos.write(buffer, 0, count);
                    total += count;
                }
                fos.flush();
            }

            LOGGER.info("synced files with server");
            ConsoleLog.getInstance().log("Successfully synced files with server.\n");

            // display file tree
            UserInterfaceManager.getInstance().displayFileTree();
        } catch (Exception e) {
            throw new BoxerException(BoxerException.ExceptionType.SYNC_FILES_ERROR, e.getMessage());
        }
    }

    /**
     * Shutdown.
     *
     * @param onExit                 the on exit
     * @param clearUiEnvironmentTask the clear ui environment task
     */
    public void shutdown(boolean onExit, Runnable clearUiEnvironmentTask) {
        // shutdown any running threads
        try {
            executor.shutdown();
            userJobs.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            userJobs.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("Application has not been properly shut down!");
        } finally {
            // cancel any unfinished tasks
            executor.shutdownNow();
            userJobs.shutdownNow();
        }
        disconnectFromServer(onExit, clearUiEnvironmentTask);
    }

    /**
     * Disconnect from server.
     *
     * @param onExit                 the on exit
     * @param clearUiEnvironmentTask the clear ui environment task
     */
// disconnect the user from server
    private void disconnectFromServer(boolean onExit, Runnable clearUiEnvironmentTask) {
        try {
            // disconnect client
            socket.close();
            socket = null;

            ConsoleLog.getInstance().log("Disconnecting...\n");
            LOGGER.info("Disconnecting...");
        } catch (Exception e) {
            ConsoleLog.getInstance().log("Error, couldn't disconnect!\n");
        }

        // clear the environment
        ThreadManager.runOnUiThread(clearUiEnvironmentTask);

        LOGGER.info("User disconnected.");
        ConsoleLog.getInstance().log("User disconnected.\n");

        if (!onExit) {

            // show alert
            UserInterfaceManager.getInstance().addAlert(Alert.AlertType.INFORMATION, "You have been disconnected!");
        }
    }

    /**
     * Is connected boolean.
     *
     * @return the boolean
     */
    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    /**
     * Share file to server.
     *
     * @param shareFile the share file
     * @param shareUser the share user
     */
    public void shareFileToServer(String shareFile, String shareUser) {
        // share file, in a separate thread
        ConsoleLog consoleLog = ConsoleLog.getInstance();
        executor.submit(() -> {
            consoleLog.log("Sharing file " + shareFile + " with user " + shareUser + "...\n");

            try {
                shareFileCode(shareFile, shareUser);
            } catch (BoxerException e) {
                consoleLog.log("Error, couldn't establish a connection with server!\n");

                // show alert
                UserInterfaceManager.getInstance().addAlert(Alert.AlertType.ERROR, "There was a problem sharing file with user" + shareUser + "!");
            }

            consoleLog.log("Successfully shared file " + shareFile + " with user " + shareUser + ".\n");
            UserInterfaceManager.getInstance().clearAfterSuccessShareFile();

            // show alert
            UserInterfaceManager.getInstance().addAlert(Alert.AlertType.INFORMATION, "Successfully shared file " + shareFile + " with user " + shareUser + ".");
        });
    }

    /**
     * Share file code.
     *
     * @param shareFile the share file
     * @param shareUser the share user
     * @throws BoxerException the boxer exception
     */
    private void shareFileCode(String shareFile, String shareUser) throws BoxerException {
        try {
            output.writeInt(20);
            if (input.readInt() != 25) {
                throw new BoxerException(BoxerException.ExceptionType.SHARE_FILE_ERROR, "Cannot share file");
            }

            output.writeUTF(shareFile);
            output.writeUTF(shareUser);
        } catch (IOException e) {
            throw new BoxerException(BoxerException.ExceptionType.SHARE_FILE_ERROR, e.getMessage());
        }
    }
}
