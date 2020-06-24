package com.boxer;

import javafx.application.Platform;

/**
 * The type Thread manager.
 */
public class ThreadManager {
    /**
     * Run on ui thread.
     *
     * @param runnable the runnable
     */
    public static void runOnUiThread(Runnable runnable) {
        // don't invoke Platform.runLater if on UI thread already
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }
}
