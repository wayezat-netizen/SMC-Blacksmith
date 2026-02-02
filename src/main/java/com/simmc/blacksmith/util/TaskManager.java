package com.simmc.blacksmith.util;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages scheduled tasks for the plugin.
 * Provides methods to run tasks with automatic cleanup.
 */
public class TaskManager {

    private final JavaPlugin plugin;
    // FIXED: Use ConcurrentHashMap.newKeySet() for thread-safe Set
    private final Set<BukkitTask> activeTasks;

    public TaskManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.activeTasks = ConcurrentHashMap.newKeySet();
    }

    /**
     * Runs a task after a delay.
     */
    public BukkitTask runLater(Runnable task, long delayTicks) {
        BukkitTask bukkitTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error in delayed task", e);
                } finally {
                    activeTasks.remove(this);
                }
            }
        }.runTaskLater(plugin, delayTicks);
        activeTasks.add(bukkitTask);
        return bukkitTask;
    }

    /**
     * Runs a repeating task.
     */
    public BukkitTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bukkitTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error in timer task", e);
                }
            }
        }.runTaskTimer(plugin, delayTicks, periodTicks);
        activeTasks.add(bukkitTask);
        return bukkitTask;
    }

    /**
     * Runs a task asynchronously.
     */
    public BukkitTask runAsync(Runnable task) {
        BukkitTask bukkitTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error in async task", e);
                } finally {
                    activeTasks.remove(this);
                }
            }
        }.runTaskAsynchronously(plugin);
        activeTasks.add(bukkitTask);
        return bukkitTask;
    }

    /**
     * Runs a task asynchronously after a delay.
     */
    public BukkitTask runAsyncLater(Runnable task, long delayTicks) {
        BukkitTask bukkitTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error in async delayed task", e);
                } finally {
                    activeTasks.remove(this);
                }
            }
        }.runTaskLaterAsynchronously(plugin, delayTicks);
        activeTasks.add(bukkitTask);
        return bukkitTask;
    }

    /**
     * Cancels a specific task.
     */
    public void cancel(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            activeTasks.remove(task);
        }
    }

    /**
     * Cancels all active tasks.
     */
    public void cancelAll() {
        for (BukkitTask task : activeTasks) {
            try {
                if (!task.isCancelled()) {
                    task.cancel();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error cancelling task", e);
            }
        }
        activeTasks.clear();
    }

    /**
     * Gets the number of active tasks.
     */
    public int getActiveTaskCount() {
        return activeTasks.size();
    }
}