package com.simmc.blacksmith.forge.display;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Layered sound system for the forge.
 */
public class ForgeSounds {

    /**
     * Plays ambient forge sounds.
     */
    public static void playAmbient(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // Fire crackling
        world.playSound(location, Sound.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 0.3f, 1.0f);
    }

    /**
     * Plays strike sound based on accuracy.
     */
    public static void playStrike(Player player, Location location, double accuracy) {
        if (accuracy >= 0.9) {
            playPerfectStrike(player, location);
        } else if (accuracy >= 0.7) {
            playGoodStrike(player, location);
        } else if (accuracy >= 0.4) {
            playOkayStrike(player, location);
        } else {
            playMissStrike(player, location);
        }
    }

    private static void playPerfectStrike(Player player, Location location) {
        // Satisfying anvil ring
        player.playSound(location, Sound.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 1.0f, 1.3f);
        player.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 2.0f);
        player.playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.5f, 1.5f);
    }

    private static void playGoodStrike(Player player, Location location) {
        player.playSound(location, Sound.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 1.0f, 1.1f);
        player.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.4f, 1.5f);
    }

    private static void playOkayStrike(Player player, Location location) {
        player.playSound(location, Sound.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8f, 0.9f);
    }

    private static void playMissStrike(Player player, Location location) {
        player.playSound(location, Sound.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.6f, 0.6f);
        player.playSound(location, Sound.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.5f, 0.8f);
    }

    /**
     * Plays completion fanfare based on star rating.
     */
    public static void playCompletion(Player player, Location location, int starRating) {
        if (starRating >= 5) {
            // Legendary
            player.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
            player.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.8f, 1.5f);
        } else if (starRating >= 4) {
            // Epic
            player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
            player.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.6f, 1.2f);
        } else if (starRating >= 3) {
            // Good
            player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 0.9f);
        } else {
            // Basic
            player.playSound(location, Sound.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 0.7f, 1.0f);
        }
    }

    /**
     * Plays session start sound.
     */
    public static void playSessionStart(Player player, Location location) {
        player.playSound(location, Sound.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.7f, 0.8f);
        player.playSound(location, Sound.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 0.5f, 1.0f);
    }

    /**
     * Plays session cancel sound.
     */
    public static void playSessionCancel(Player player, Location location) {
        player.playSound(location, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 1.0f);
    }
}