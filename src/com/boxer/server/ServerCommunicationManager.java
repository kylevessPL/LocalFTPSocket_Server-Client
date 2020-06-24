package com.boxer.server;

import com.boxer.ConsoleLog;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The type Server communication manager.
 */
public class ServerCommunicationManager {
    /**
     * The constant LOGGER.
     */
    private static final Logger LOGGER = Logger.getLogger(ServerCommunicationManager.class.getName());
    /**
     * The constant INSTANCE.
     */
    private static final ServerCommunicationManager INSTANCE = new ServerCommunicationManager();

    /**
     * The constant PORT_NUMBER.
     */
// variables
    static final int PORT_NUMBER = 59090;

    /**
     * The Executor.
     */
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    /**
     * The Server socket.
     */
    private ServerSocket serverSocket = null;
    /**
     * The Users.
     */
    private final Map<String, Client> users = new HashMap<>();

    /**
     * Instantiates a new Server communication manager.
     */
// private constructor to create single instance only
    private ServerCommunicationManager() {
    }

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static ServerCommunicationManager getInstance() {
        return INSTANCE;
    }

    /**
     * Is started boolean.
     *
     * @return the boolean
     */
    public boolean isStarted() {
        return serverSocket != null;
    }

    /**
     * Start server socket.
     *
     * @throws IOException the io exception
     */
    public void startServerSocket() throws IOException {
        serverSocket = new ServerSocket(PORT_NUMBER);
    }

    /**
     * Handle client connections.
     */
    public void handleClientConnections() {
        // handle user connections in new thread
        executor.scheduleWithFixedDelay(() -> {
            ConsoleLog consoleLog = ConsoleLog.getInstance();
            try {
                // listen for incoming connections
                final Socket socket = serverSocket.accept();

                // finally add user to the server
                addUser(socket);
            } catch (IOException e) {
                consoleLog.log("Error, couldn't handle new client connection request!\n");
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * Add user.
     *
     * @param socket the socket
     */
    private void addUser(Socket socket) {
        ConsoleLog consoleLog = ConsoleLog.getInstance();
        String userName = null;
        try {
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            DataInputStream dis = new DataInputStream(socket.getInputStream());

            try {
                userName = dis.readUTF();
                if (users.containsKey(userName)) {
                    // send -1 code if username already exists
                    dos.writeInt(-1);
                    throw new MainController.UserNameExists();
                } else {
                    // add new user to database
                    File userDir = new File(System.getProperty("user.dir") + "/" + userName);
                    if (!userDir.exists()) {
                        userDir.mkdir();
                    }
                    Client client = new Client(userName, userDir, new ArrayList<>(), socket);
                    users.put(userName, client);

                    // refresh user file tree
                    displayFileTree();

                    // send 65 code on successful connection
                    dos.writeInt(65);
                    consoleLog.log("New user " + userName + " connected!\n");
                }
            } catch (MainController.UserNameExists e) {
                System.out.println("Error, couldn't add new user: " + e.getLocalizedMessage() + "!");
                consoleLog.log("Error, couldn't add new user: " + e.getLocalizedMessage() + "!\n");
                return;
            }

            Client client = users.get(userName);

            // send userlist
            if (dis.readInt() == 30) {
                sendUserList(client);
            } else {
                throw new SocketException();
            }
            File[] files = createFilesToSend(consoleLog, client);
            if (files == null) return;

            // ...and user directory
            if (dis.readInt() == 10) {
                System.out.println("sending files...");
                sendFiles(client, files, true);
            } else {
                throw new SocketException();
            }
        } catch (Exception e) {
            System.out.println("Error, couldn't establish a connection with user!");
            consoleLog.log("Error, couldn't establish a connection with user!\n");

            // disconnect redundant client
            disconnectClient(users.get(userName));

            consoleLog.log("User not added!\n");
        }

        // delegate user jobs to separate threads
        ExecutorService userJob = Executors.newFixedThreadPool(1);
        users.get(userName).setUserJob(userJob);
        final Client userClient = users.get(userName);
        userJob.submit(() -> {
            try {
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                while (socket.isConnected()) {
                    int reply;
                    LOGGER.info("waiting for client...");
                    reply = dis.readInt();

                    LOGGER.info("reply from client: " + reply);

                    switch (reply) {
                        case 10:
                            shareFileAvailable(userClient);
                            break;
                        case 20:
                            shareFile(userClient);
                            break;
                        case 30:
                            sendUserList(userClient);
                            break;
                        case 40:
                            receiveFiles(userClient);
                            break;
                        case 50:
                            deleteFile(userClient);
                            break;
                        default:
                            throw new SocketException();
                    }
                }
            } catch (Exception e) {
                System.out.println("Error, couldn't establish a connection with user " + userClient.getUsername() + "!");
                consoleLog.log("Error, couldn't establish a connection with user " + userClient.getUsername() + "!\n");

                // disconnect redundant client
                disconnectClient(userClient);
            }
        });
    }

    /**
     * Display file tree.
     */
    private void displayFileTree() {
        List<String> userList = new ArrayList<>(users.keySet());
        List<File> files = userList.stream()
                .map(user -> users.get(user).getUserDir())
                .collect(Collectors.toList());
        ServerUserInterfaceManager.getInstance().displayFileTree(userList, files);
    }

    /**
     * Create files to send file [ ].
     *
     * @param consoleLog the console log
     * @param client     the client
     * @return the file [ ]
     */
    private File[] createFilesToSend(ConsoleLog consoleLog, Client client) {
        // create list for files
        List<File> fileList = new ArrayList<>();

        try {
            // add files to list recursively
            Files.walk(client.getUserDir().toPath())
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> fileList.add(path.toFile()));
        } catch (IOException e) {
            System.out.println("Error, couldn't get user files!");
            consoleLog.log("Error, couldn't get user files!\n");

            // disconnect redundant client
            disconnectClient(client);

            consoleLog.log("User not added!\n");
            return null;
        }

        File[] files = new File[fileList.size()];
        fileList.toArray(files);
        return files;
    }

    /**
     * Delete file.
     *
     * @param client the client
     */
    private void deleteFile(Client client) {
        ConsoleLog consoleLog = ConsoleLog.getInstance();
        String userName = client.getUsername();
        Socket socket = client.getSocket();

        try {
            BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
            DataInputStream dis = new DataInputStream(bis);
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            dos.writeInt(55);
            String fileName = dis.readUTF();
            File file = new File(client.getUserDir().getAbsolutePath() + "/" + fileName);
            file.delete();

            consoleLog.log("Successfully synced files with user " + userName + ".\n");

            // refresh file tree
            displayFileTree();
        } catch (SocketException e) {
            consoleLog.log("Error, couldn't establish a connection with user " + userName + "!\n");

            // disconnect redundant client and send updated list to users
            disconnectClient(client);
        } catch (Exception e) {
            consoleLog.log("Error, couldn't sync files with user " + userName + "!\n");

            // disconnect redundant client and send updated list to users
            disconnectClient(client);
        }
    }

    /**
     * Receive files.
     *
     * @param client the client
     */
    private void receiveFiles(Client client) {
        ConsoleLog consoleLog = ConsoleLog.getInstance();
        String userName = client.getUsername();
        Socket socket = client.getSocket();

        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            dos.writeInt(45);

            int filesCount = dis.readInt();
            File[] files = new File[filesCount];
            for (int i = 0; i < filesCount; i++) {
                long length = dis.readLong();
                String fileFullName = dis.readUTF();
                files[i] = new File(client.getUserDir() + File.separator + fileFullName);
                files[i].getParentFile().mkdirs();
                FileOutputStream fos = new FileOutputStream(files[i]);
                byte[] buffer = new byte[8192];
                int count;
                long total = 0;
                while (total < length && (count = dis.read(buffer, 0, length - total > buffer.length ? buffer.length : (int) (length - total))) > 0) {
                    fos.write(buffer, 0, count);
                    total += count;
                }
                fos.flush();
            }

            consoleLog.log("Successfully synced files with user " + userName + ".\n");

            // display file tree
            displayFileTree();
        } catch (SocketException e) {
            consoleLog.log("Error, couldn't establish a connection with user " + userName + "!\n");

            // disconnect redundant client and send updated list to users
            disconnectClient(client);
        } catch (Exception e) {
            consoleLog.log("Error, couldn't sync files with user " + userName + "!\n");

            // disconnect redundant client and send updated list to users
            disconnectClient(client);
        }
    }

    /**
     * Send user list.
     *
     * @param client the client
     */
    private void sendUserList(Client client) {
        Socket socket = client.getSocket();
        String userName = client.getUsername();

        try {
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            // send userlist to socket
            String usersToSend = String.join(",", users.keySet());
            dos.writeUTF(usersToSend);
        } catch (IOException e) {
            System.out.println("Error, couldn't establish a connection with user " + userName + "!");
            ConsoleLog.getInstance().log("Error, couldn't establish a connection with user " + userName + "!\n");

            // disconnect client
            disconnectClient(client);
        }
    }

    /**
     * Share file.
     *
     * @param client the client
     * @throws Exception the exception
     */
    private void shareFile(Client client) throws Exception {
        Socket socket = client.getSocket();
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

        dos.writeInt(25);

        String shareFile = dis.readUTF();
        String shareUser = dis.readUTF();
        Client clientToShare = users.get(shareUser);

        synchronized (clientToShare.getSocket()) {
            File file;
            try {
                file = getFile(client, shareFile);
                clientToShare.getSharedFiles().add(file);
            } catch (FileNotFoundException e) {
                System.out.println("Error, user " + client.getUsername() + " would like to share file: " + shareFile + " with user " + shareUser + " but file doesn't exists on the server!");
                ConsoleLog.getInstance().log("Error, user " + client.getUsername() + " would like to share file: " + shareFile + " with user " + shareUser + " but file doesn't exists on the server!\n");
            }
        }
    }

    /**
     * Gets file.
     *
     * @param client   the client
     * @param fileName the file name
     * @return the file
     * @throws IOException the io exception
     */
    private File getFile(Client client, String fileName) throws IOException {
        File file;

        Optional<Path> filePath;
        try (Stream<Path> logFiles = Files.walk(client.getUserDir().toPath())) {
            filePath = logFiles
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .findFirst();
        }

        if (filePath.isPresent()) {
            file = filePath.get().toFile();
        } else {
            throw new FileNotFoundException();
        }

        return file;
    }

    /**
     * Share file available.
     *
     * @param client the client
     * @throws Exception the exception
     */
    private void shareFileAvailable(Client client) throws Exception {
        DataOutputStream dos = new DataOutputStream(client.getSocket().getOutputStream());

        if (!client.getSharedFiles().isEmpty()) {
            List<File> fileList = client.getSharedFiles();
            File[] files = new File[fileList.size()];
            fileList.toArray(files);

            sendFiles(client, files, false);

            // clear list of shared files
            client.getSharedFiles().clear();
        } else {
            LOGGER.info("sending reply to client...");
            dos.writeInt(1);
        }
    }

    /**
     * Send files.
     *
     * @param client   the client
     * @param files    the files
     * @param ownFiles the own files
     */
    private void sendFiles(Client client, File[] files, boolean ownFiles) {
        ConsoleLog consoleLog = ConsoleLog.getInstance();
        Socket socket = client.getSocket();
        String userName = client.getUsername();

        try {
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            dos.writeInt(15);

            dos.writeInt(files.length);

            for (File f : files) {
                long length = f.length();
                dos.writeLong(length);

                // get relative path to file
                String fullName = f.getAbsolutePath()
                        .replace(System.getProperty("user.dir"), "");

                if (ownFiles){
                    String clientNamePath = "\\\\" + client.getUsername();
                    fullName = fullName.replaceFirst(clientNamePath, "");
                }

                dos.writeUTF(fullName);
                FileInputStream fis = new FileInputStream(f);
                byte[] buffer = new byte[8192];
                int count;
                long total = 0;
                while (total < length && (count = fis.read(buffer, 0, length - total > buffer.length ? buffer.length : (int) (length - total))) > 0) {
                    dos.write(buffer, 0, count);
                    total += count;
                }
                dos.flush();
            }

            consoleLog.log("Successfully synced files with user " + userName + ".\n");
        } catch (SocketException e) {
            consoleLog.log("Error, couldn't establish a connection with user " + userName + "!\n");

            // disconnect redundant client and send updated list to users
            disconnectClient(client);
        } catch (Exception e) {
            consoleLog.log("Error, couldn't sync files with user " + userName + "!\n");

            // disconnect redundant client and send updated list to users
            disconnectClient(client);
        }
    }

    /**
     * Shutdown.
     */
    public void shutdown() {
        // disconnect server
        disconnectServer();

        // shutdown any running threads
        try {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("Application has not been properly shut down!");
        } finally {
            // cancel any unfinished tasks
            executor.shutdownNow();
        }
    }

    /**
     * Disconnect server.
     */
    private void disconnectServer() {
        ConsoleLog consoleLog = ConsoleLog.getInstance();
        System.out.println("Disconnecting server...");
        consoleLog.log("Disconnecting server...\n");

        try {
            // disconnect server
            serverSocket.close();
        } catch (Exception e) {
            consoleLog.log("Error, couldn't disconnect!\n");
        }

        consoleLog.log("Server disconnected successfully.\n");
    }

    /**
     * Disconnect client.
     *
     * @param client the client
     */
    private void disconnectClient(Client client) {
        ConsoleLog consoleLog = ConsoleLog.getInstance();

        // get username
        final String user = client.getUsername();

        consoleLog.log("Disconnecting user " + user + "...\n");
        System.out.println("Disconnecting user " + user + "...");

        // shutdown any running user jobs
        ExecutorService userJob = client.getUserJob();
        try {
            userJob.shutdown();
            userJob.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("Application has not been properly shut down!");
        } finally {
            // cancel any unfinished tasks
            userJob.shutdownNow();
        }

        try {
            // disconnect client
            users.remove(client.getUsername());
        } catch (Exception e) {
            consoleLog.log("Error, couldn't disconnect!\n");
        }

        System.out.println("User " + user + " disconnected.");
        consoleLog.log("User " + user + " disconnected.\n");

        try {
            // update file tree
            displayFileTree();
        } catch (Exception e) {
            consoleLog.log("Error, couldn't update file tree!\n");
        }
    }
}
