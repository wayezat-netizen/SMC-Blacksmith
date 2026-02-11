package com.simmc.blacksmith.forge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages spectating of forge sessions.
 */
public class ForgeSpectator {

    private final Map<UUID, Set<UUID>> spectators; // forger -> spectators

    public ForgeSpectator() {
        this.spectators = new HashMap<>();
    }

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

    public void removeSpectator(UUID forgerId, UUID spectatorId) {
        Set<UUID> set = spectators.get(forgerId);
        if (set != null) {
            set.remove(spectatorId);
            if (set.isEmpty()) {
                spectators.remove(forgerId);
            }
        }
    }

    public void clearSpectators(UUID forgerId) {
        Set<UUID> removed = spectators.remove(forgerId);
        if (removed != null) {
            removed.stream()
                    .map(Bukkit::getPlayer)
                    .filter(Objects::nonNull)
                    .forEach(p -> p.sendMessage("§7Forging session ended."));
        }
    }

    public Set<UUID> getSpectators(UUID forgerId) {
        return spectators.getOrDefault(forgerId, Collections.emptySet());
    }

    public void broadcastToSpectators(UUID forgerId, String message) {
        getSpectators(forgerId).stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(p -> p.sendMessage(message));
    }

    public void broadcastStrike(UUID forgerId, String forgerName, double accuracy, int hits, int total) {
        String accColor = accuracy >= 0.9 ? "§a§l" : accuracy >= 0.7 ? "§a" : accuracy >= 0.4 ? "§e" : "§c";
        String message = String.format("§7[§6⚒§7] §e%s §7struck! %s%.0f%% §7(%d/%d)",
                forgerName, accColor, accuracy * 100, hits, total);
        broadcastToSpectators(forgerId, message);
    }

    public void broadcastCompletion(UUID forgerId, String forgerName, int stars, String itemName) {
        StringBuilder starDisplay = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            starDisplay.append(i < stars ? "§6★" : "§7☆");
        }
        String message = String.format("§7[§6⚒§7] §e%s §acompleted forging! %s §7(%s)",
                forgerName, starDisplay, itemName);
        broadcastToSpectators(forgerId, message);
    }

    public int getSpectatorCount(UUID forgerId) {
        return getSpectators(forgerId).size();
    }

    public boolean isSpectating(UUID playerId) {
        return spectators.values().stream().anyMatch(s -> s.contains(playerId));
    }

    public Optional<UUID> getSpectatingTarget(UUID playerId) {
        return spectators.entrySet().stream()
                .filter(e -> e.getValue().contains(playerId))
                .map(Map.Entry::getKey)
                .findFirst();
    }
}