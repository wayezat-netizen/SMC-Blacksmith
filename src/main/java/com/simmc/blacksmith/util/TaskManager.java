package com.simmc.blacksmith.util;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;

public class TaskManager {

    private final JavaPlugin plugin;
    private final Set<BukkitTask> activeTasks;

    public TaskManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.activeTasks = new HashSet<>();
    }

    public BukkitTask runLater(Runnable task, long delayTicks) {
        BukkitTask bukkitTask = new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
                activeTasks.remove(this);
            }
        }.runTaskLater(plugin, delayTicks);
        activeTasks.add(bukkitTask);
        return bukkitTask;
    }

    public BukkitTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bukkitTask = new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTaskTimer(plugin, delayTicks, periodTicks);
        activeTasks.add(bukkitTask);
        return bukkitTask;
    }

    public BukkitTask runAsync(Runnable task) {
        BukkitTask bukkitTask = new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
                activeTasks.remove(this);
            }
        }.runTaskAsynchronously(plugin);
        activeTasks.add(bukkitTask);
        return bukkitTask;
    }

    public void cancel(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            activeTasks.remove(task);
        }
    }

    public void cancelAll() {
        for (BukkitTask task : new HashSet<>(activeTasks)) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        activeTasks.clear();
    }
}