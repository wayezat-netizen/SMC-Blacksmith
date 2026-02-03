package com.simmc.blacksmith.forge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Allows players to spectate others forging.
 */
public class ForgeSpectator {

    private final Map<UUID, Set<UUID>> spectators; // forger -> spectators

    public ForgeSpectator() {
        this.spectators = new HashMap<>();
    }

    /**
     * Adds a spectator to watch a forger.
     */
    public boolean addSpectator(UUID forgerId, UUID spectatorId) {
        if (forgerId.equals(spectatorId)) return false;

        spectators.computeIfAbsent(forgerId, k -> new HashSet<>()).add(spectatorId);

        Player spectator = Bukkit.getPlayer(spectatorId);
        Player forger = Bukkit.getPlayer(forgerId);

        if (spectator != null && forger != null) {
            spectator.sendMessage("§7Now spectating §e" + forger.getName() + "§7's forging session.");
        }

        return true;
    }

    /**
     * Removes a spectator.
     */
    public void removeSpectator(UUID forgerId, UUID spectatorId) {
        Set<UUID> forgerSpectators = spectators.get(forgerId);
        if (forgerSpectators != null) {
            forgerSpectators.remove(spectatorId);
            if (forgerSpectators.isEmpty()) {
                spectators.remove(forgerId);
            }
        }
    }

    /**
     * Removes all spectators for a forger.
     */
    public void clearSpectators(UUID forgerId) {
        Set<UUID> removed = spectators.remove(forgerId);
        if (removed != null) {
            for (UUID spectatorId : removed) {
                Player spectator = Bukkit.getPlayer(spectatorId);
                if (spectator != null) {
                    spectator.sendMessage("§7Forging session ended.");
                }
            }
        }
    }

    /**
     * Gets all spectators for a forger.
     */
    public Set<UUID> getSpectators(UUID forgerId) {
        return spectators.getOrDefault(forgerId, Collections.emptySet());
    }

    /**
     * Broadcasts a message to all spectators.
     */
    public void broadcastToSpectators(UUID forgerId, String message) {
        for (UUID spectatorId : getSpectators(forgerId)) {
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator != null) {
                spectator.sendMessage(message);
            }
        }
    }

    /**
     * Broadcasts strike result to spectators.
     */
    public void broadcastStrike(UUID forgerId, String forgerName, double accuracy, int hits, int total) {
        String accColor = accuracy >= 0.9 ? "§a§l" : accuracy >= 0.7 ? "§a" : accuracy >= 0.4 ? "§e" : "§c";
        String message = String.format("§7[§6⚒§7] §e%s §7struck! %s%.0f%% §7(%d/%d)",
                forgerName, accColor, accuracy * 100, hits, total);
        broadcastToSpectators(forgerId, message);
    }

    /**
     * Broadcasts completion to spectators.
     */
    public void broadcastCompletion(UUID forgerId, String forgerName, int stars, String itemName) {
        StringBuilder starDisplay = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            starDisplay.append(i < stars ? "§6★" : "§7☆");
        }

        String message = String.format("§7[§6⚒§7] §e%s §acompleted forging! %s §7(%s)",
                forgerName, starDisplay, itemName);
        broadcastToSpectators(forgerId, message);
    }

    /**
     * Gets spectator count for a forger.
     */
    public int getSpectatorCount(UUID forgerId) {
        return getSpectators(forgerId).size();
    }

    /**
     * Checks if a player is spectating.
     */
    public boolean isSpectating(UUID playerId) {
        for (Set<UUID> forgerSpectators : spectators.values()) {
            if (forgerSpectators.contains(playerId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets who the player is spectating.
     */
    public UUID getSpectatingTarget(UUID playerId) {
        for (Map.Entry<UUID, Set<UUID>> entry : spectators.entrySet()) {
            if (entry.getValue().contains(playerId)) {
                return entry.getKey();
            }
        }
        return null;
    }
}