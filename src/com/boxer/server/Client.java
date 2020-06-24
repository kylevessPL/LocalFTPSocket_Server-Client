package com.boxer.server;

import java.io.File;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * The type Client.
 */
public class Client {
    /**
     * The Username.
     */
    private final String username;
    /**
     * The User dir.
     */
    private final File userDir;
    /**
     * The Shared files.
     */
    private final List<File> sharedFiles;
    /**
     * The Socket.
     */
    private final Socket socket;
    /**
     * The User job.
     */
    private ExecutorService userJob;

    /**
     * Instantiates a new Client.
     *
     * @param username    the username
     * @param userDir     the user dir
     * @param sharedFiles the shared files
     * @param socket      the socket
     */
    public Client(String username, File userDir, List<File> sharedFiles, Socket socket) {
        this.username = username;
        this.userDir = userDir;
        this.sharedFiles = sharedFiles;
        this.socket = socket;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Client client = (Client) o;
        return Objects.equals(username, client.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    /**
     * Gets username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets user dir.
     *
     * @return the user dir
     */
    public File getUserDir() {
        return userDir;
    }

    /**
     * Gets shared files.
     *
     * @return the shared files
     */
    public List<File> getSharedFiles() {
        return sharedFiles;
    }

    /**
     * Gets socket.
     *
     * @return the socket
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Sets user job.
     *
     * @param userJob the user job
     */
    public void setUserJob(ExecutorService userJob) {
        this.userJob = userJob;
    }

    /**
     * Gets user job.
     *
     * @return the user job
     */
    public ExecutorService getUserJob() {
        return userJob;
    }
}
