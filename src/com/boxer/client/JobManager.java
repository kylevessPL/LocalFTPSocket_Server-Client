package com.boxer.client;

import java.util.concurrent.ScheduledFuture;

/**
 * The type Job manager.
 */
public class JobManager {
    /**
     * The constant INSTANCE.
     */
    private static final JobManager INSTANCE = new JobManager();

    /**
     * The Sync files job.
     */
    private ScheduledFuture<?> syncFilesJob;
    /**
     * The Users job.
     */
    private ScheduledFuture<?> usersJob;
    /**
     * The Dir watcher job.
     */
    private ScheduledFuture<?> dirWatcherJob;

    /**
     * Instantiates a new Job manager.
     */
    private JobManager() {
    }

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static JobManager getInstance() {
        return INSTANCE;
    }

    /**
     * Gets sync files job.
     *
     * @return the sync files job
     */
    public ScheduledFuture<?> getSyncFilesJob() {
        return syncFilesJob;
    }

    /**
     * Sets sync files job.
     *
     * @param syncFilesJob the sync files job
     */
    public void setSyncFilesJob(ScheduledFuture<?> syncFilesJob) {
        this.syncFilesJob = syncFilesJob;
    }

    /**
     * Gets users job.
     *
     * @return the users job
     */
    public ScheduledFuture<?> getUsersJob() {
        return usersJob;
    }

    /**
     * Sets users job.
     *
     * @param usersJob the users job
     */
    public void setUsersJob(ScheduledFuture<?> usersJob) {
        this.usersJob = usersJob;
    }

    /**
     * Gets dir watcher job.
     *
     * @return the dir watcher job
     */
    public ScheduledFuture<?> getDirWatcherJob() {
        return dirWatcherJob;
    }

    /**
     * Sets dir watcher job.
     *
     * @param dirWatcherJob the dir watcher job
     */
    public void setDirWatcherJob(ScheduledFuture<?> dirWatcherJob) {
        this.dirWatcherJob = dirWatcherJob;
    }

    /**
     * Cancel sync files job.
     */
    public void cancelSyncFilesJob() {
        if (syncFilesJob != null) {
            syncFilesJob.cancel(true);
        }
    }

    /**
     * Cancel all jobs.
     */
    public void cancelAllJobs() {
        cancelGetUsersJob();
        cancelDirWatcherJob();
        cancelSyncFilesJob();
    }

    /**
     * Cancel get users job.
     */
    public void cancelGetUsersJob() {
        if (usersJob != null) {
            usersJob.cancel(true);
        }
    }

    /**
     * Cancel dir watcher job.
     */
    public void cancelDirWatcherJob() {
        if (dirWatcherJob != null) {
            dirWatcherJob.cancel(true);
        }
    }
}
