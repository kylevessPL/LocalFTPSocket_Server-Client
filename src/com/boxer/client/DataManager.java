package com.boxer.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * The type Data manager.
 */
public class DataManager {

    /**
     * The constant INSTANCE.
     */
    private static final DataManager INSTANCE = new DataManager();

    /**
     * The constant preferences_FileName.
     */
    private final static String preferences_FileName = "boxer.properties";
    /**
     * The Settings dir.
     */
    private final File settingsDir;
    /**
     * The Properties.
     */
    private final Properties properties = new Properties();

    /**
     * The Username.
     */
    private String username;
    /**
     * The Local dir.
     */
    private String localDir;

    /**
     * Get instance data manager.
     *
     * @return the data manager
     */
    public static DataManager getInstance(){
        return INSTANCE;
    }

    /**
     * Instantiates a new Data manager.
     */
    private DataManager() {
        // set default preferences
        username = "User";
        localDir = "MyBoxer_User";

        // make the settings directory
        settingsDir = new File("settings");

        if(settingsDir.exists()) {
            // load Boxer preferences
            loadPreferences();
        } else {
            // make settings directory
            settingsDir.mkdir();
            System.out.println("Settings folder not found, recreating...");
        }
    }

    /**
     * Load preferences.
     */
// loads Boxer preferences
    public void loadPreferences() {
        try {
            System.out.println("Loading preferences...");

            // load the properties
            try(FileInputStream fis = new FileInputStream(settingsDir.getName() + File.separator + preferences_FileName)) {
                properties.load(fis);

                // load username
                setUsername(properties.getProperty("Username", getUsername()));

                // load local directory
                setLocalDir(properties.getProperty("LocalDir", getLocalDir()));
            }
        } catch (FileNotFoundException fnf) {
            System.out.println("Preference file not found.");
        }
        catch(Exception e) {
            System.out.println("Error loading preferences!");
        }
    }

    /**
     * Save preferences.
     */
// saves Boxer preferences
    public void savePreferences() {
        try {
            System.out.println("Saving program preferences...");
            try(FileOutputStream fos = new FileOutputStream(settingsDir.getName() + File.separator + preferences_FileName)) {
                // save the username
                properties.put("Username", getUsername());

                // save the user local directory
                properties.put("LocalDir", getLocalDir());

                // save the properties to the file
                properties.store(fos, "Properties");
            }
            System.out.println("preferences saved");
        } catch (Exception e) {
            System.out.println("Error saving preferences!");
        }
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
     * Sets username.
     *
     * @param username the username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets local dir.
     *
     * @return the local dir
     */
    public String getLocalDir() {
        return localDir;
    }

    /**
     * Sets local dir.
     *
     * @param localDir the local dir
     */
    public void setLocalDir(String localDir) {
        this.localDir = localDir;
    }
}
