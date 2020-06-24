package com.boxer;

import javafx.scene.control.TextArea;

/**
 * The type Console log.
 */
public class ConsoleLog {
    /**
     * The constant INSTANCE.
     */
    private static ConsoleLog INSTANCE;

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static ConsoleLog getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Not initialized");
        }
        return INSTANCE;
    }

    /**
     * The Log field.
     */
    private final TextArea logField;

    /**
     * Instantiates a new Console log.
     *
     * @param logField the log field
     */
    private ConsoleLog(TextArea logField) {
        this.logField = logField;
    }

    /**
     * Initialize.
     *
     * @param logField the log field
     */
    public static void initialize(TextArea logField) {
        if (INSTANCE != null) {
            throw new IllegalStateException("Already initialized");
        }
        INSTANCE = new ConsoleLog(logField);
    }

    /**
     * Log.
     *
     * @param text the text
     */
    public void log(String text){
        ThreadManager.runOnUiThread(() -> logField.appendText(text));
    }
}
