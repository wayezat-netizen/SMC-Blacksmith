package com.simmc.blacksmith.util;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Centralized task management for SMCBlacksmith.
 * Provides scheduled task execution with automatic cleanup and categorization.
 */
public class TaskManager {

    private final JavaPlugin plugin;
    private final Set<BukkitTask> activeTasks;
    private final Map<String, Set<BukkitTask>> categorizedTasks;

    public TaskManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.activeTasks = ConcurrentHashMap.newKeySet();
        this.categorizedTasks = new ConcurrentHashMap<>();
    }

    // ==================== BASIC TASK METHODS ====================

    public BukkitTask runSync(Runnable task) {
        return runLater(task, 0L);
    }

    public BukkitTask runLater(Runnable task, long delayTicks) {
        return runLater(null, task, delayTicks);
    }

    public BukkitTask runLater(String category, Runnable task, long delayTicks) {
        TrackedRunnable runnable = new TrackedRunnable(task, true);
        BukkitTask bukkitTask = runnable.runTaskLater(plugin, delayTicks);
        runnable.setTask(bukkitTask); // Store reference for cleanup
        trackTask(category, bukkitTask);
        return bukkitTask;
    }

    public BukkitTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        return runTimer(null, task, delayTicks, periodTicks);
    }

    public BukkitTask runTimer(String category, Runnable task, long delayTicks, long periodTicks) {
        TrackedRunnable runnable = new TrackedRunnable(task, false);
        BukkitTask bukkitTask = runnable.runTaskTimer(plugin, delayTicks, periodTicks);
        runnable.setTask(bukkitTask);
        trackTask(category, bukkitTask);
        return bukkitTask;
    }

    // ==================== ASYNC TASK METHODS ====================

    public BukkitTask runAsync(Runnable task) {
        return runAsync(null, task);
    }

    public BukkitTask runAsync(String category, Runnable task) {
        TrackedRunnable runnable = new TrackedRunnable(task, true);
        BukkitTask bukkitTask = runnable.runTaskAsynchronously(plugin);
        runnable.setTask(bukkitTask);
        trackTask(category, bukkitTask);
        return bukkitTask;
    }

    public BukkitTask runAsyncLater(Runnable task, long delayTicks) {
        return runAsyncLater(null, task, delayTicks);
    }

    public BukkitTask runAsyncLater(String category, Runnable task, long delayTicks) {
        TrackedRunnable runnable = new TrackedRunnable(task, true);
        BukkitTask bukkitTask = runnable.runTaskLaterAsynchronously(plugin, delayTicks);
        runnable.setTask(bukkitTask);
        trackTask(category, bukkitTask);
        return bukkitTask;
    }

    public BukkitTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        return runAsyncTimer(null, task, delayTicks, periodTicks);
    }

    public BukkitTask runAsyncTimer(String category, Runnable task, long delayTicks, long periodTicks) {
        TrackedRunnable runnable = new TrackedRunnable(task, false);
        BukkitTask bukkitTask = runnable.runTaskTimerAsynchronously(plugin, delayTicks, periodTicks);
        runnable.setTask(bukkitTask);
        trackTask(category, bukkitTask);
        return bukkitTask;
    }

    // ==================== TASK MANAGEMENT ====================

    private void trackTask(String category, BukkitTask task) {
        activeTasks.add(task);

        if (category != null) {
            categorizedTasks.computeIfAbsent(category, k -> ConcurrentHashMap.newKeySet())
                    .add(task);
        }
    }

    private void untrackTask(BukkitTask task) {
        if (task == null) return;
        activeTasks.remove(task);

        for (Set<BukkitTask> categoryTasks : categorizedTasks.values()) {
            categoryTasks.remove(task);
        }
    }

    public void cancel(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            untrackTask(task);
        }
    }

    public void cancelCategory(String category) {
        Set<BukkitTask> tasks = categorizedTasks.remove(category);
        if (tasks != null) {
            for (BukkitTask task : tasks) {
                cancelSafely(task);
                activeTasks.remove(task);
            }
        }
    }

    public void cancelAll() {
        for (BukkitTask task : activeTasks) {
            cancelSafely(task);
        }
        activeTasks.clear();
        categorizedTasks.clear();
    }

    private void cancelSafely(BukkitTask task) {
        try {
            if (!task.isCancelled()) {
                task.cancel();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error cancelling task " + task.getTaskId(), e);
        }
    }

    // ==================== STATISTICS ====================

    public int getActiveTaskCount() {
        return activeTasks.size();
    }

    public int getCategoryTaskCount(String category) {
        Set<BukkitTask> tasks = categorizedTasks.get(category);
        return tasks != null ? tasks.size() : 0;
    }

    public Set<String> getCategories() {
        return categorizedTasks.keySet();
    }

    // ==================== INNER CLASS ====================

    /**
     * BukkitRunnable wrapper with exception handling and auto-cleanup.
     */
    private class TrackedRunnable extends BukkitRunnable {
        private final Runnable task;
        private final boolean removeOnComplete;
        private volatile BukkitTask taskReference;

        TrackedRunnable(Runnable task, boolean removeOnComplete) {
            this.task = task;
            this.removeOnComplete = removeOnComplete;
        }

        void setTask(BukkitTask task) {
            this.taskReference = task;
        }

        @Override
        public void run() {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error in scheduled task", e);
            } finally {
                if (removeOnComplete && taskReference != null) {
                    untrackTask(taskReference);
                }
            }
        }
    }

    // ==================== TASK CATEGORIES ====================

    public static final String CATEGORY_FURNACE = "furnace";
    public static final String CATEGORY_FORGE = "forge";
    public static final String CATEGORY_QUENCH = "quench";
    public static final String CATEGORY_DISPLAY = "display";
}