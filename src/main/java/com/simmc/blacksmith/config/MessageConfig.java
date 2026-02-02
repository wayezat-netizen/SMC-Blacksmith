package com.simmc.blacksmith.config;

import com.simmc.blacksmith.util.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Manages all configurable messages for the plugin.
 */
public class MessageConfig {

    // GUI Titles
    private String furnaceTitle;
    private String anvilTitle;

    // General Messages
    private String vanillaAnvil;
    private String noPermission;

    // Repair Messages
    private String repairFailed;
    private String repairSuccess;
    private String invalidItem;
    private String missingMaterials;

    // Forge Messages
    private String forgeSessionActive;
    private String forgeUnknownRecipe;
    private String forgeStarted;
    private String forgePerfectHit;
    private String forgeGoodHit;
    private String forgeMiss;
    private String forgeComplete;

    // Command Messages
    private String commandPlayerOnly;
    private String commandNoPermission;
    private String commandUsage;
    private String commandReloadSuccess;
    private String commandReloadFailed;

    public void load(FileConfiguration config) {
        // GUI Titles
        furnaceTitle = config.getString("furnace_title", "Furnace");
        anvilTitle = config.getString("anvil_title", "Anvil");

        // General Messages
        vanillaAnvil = config.getString("vanilla_anvil", "Shift click anvil to use normal anvil");
        noPermission = config.getString("no_permission", "&cYou do not have permission to do this!");

        // Repair Messages
        repairFailed = config.getString("grindstone_repair_failed", "&cRepair failed!");
        repairSuccess = config.getString("grindstone_repair_success", "&aRepair Successful!");
        invalidItem = config.getString("grindstone_invalid", "&cThis item cannot be repaired");
        missingMaterials = config.getString("grindstone_materials", "&cMissing materials! You need %d %s");

        // Forge Messages
        forgeSessionActive = config.getString("forge_session_active", "&cYou already have an active forging session!");
        forgeUnknownRecipe = config.getString("forge_unknown_recipe", "&cUnknown recipe: %s");
        forgeStarted = config.getString("forge_started", "&aForging session started! Click the hammer to strike.");
        forgePerfectHit = config.getString("forge_perfect_hit", "&a&lPerfect hit!");
        forgeGoodHit = config.getString("forge_good_hit", "&aGood hit!");
        forgeMiss = config.getString("forge_miss", "&cMiss!");
        forgeComplete = config.getString("forge_complete", "&aForging complete! Quality: %d stars");

        // Command Messages
        commandPlayerOnly = config.getString("command_player_only", "&cThis command can only be used by players!");
        commandNoPermission = config.getString("command_no_permission", "&cYou don't have permission to use this command!");
        commandUsage = config.getString("command_usage", "&cUsage: %s");
        commandReloadSuccess = config.getString("command_reload_success", "&aConfiguration reloaded successfully!");
        commandReloadFailed = config.getString("command_reload_failed", "&cFailed to reload configuration!");
    }

    // GUI Titles
    public String getFurnaceTitle() { return ColorUtil.colorize(furnaceTitle); }
    public String getAnvilTitle() { return ColorUtil.colorize(anvilTitle); }

    // General Messages
    public String getVanillaAnvil() { return ColorUtil.colorize(vanillaAnvil); }
    public String getNoPermission() { return ColorUtil.colorize(noPermission); }

    // Repair Messages
    public String getRepairFailed() { return ColorUtil.colorize(repairFailed); }
    public String getRepairSuccess() { return ColorUtil.colorize(repairSuccess); }
    public String getInvalidItem() { return ColorUtil.colorize(invalidItem); }
    public String getMissingMaterials(int count, String itemName) {
        return ColorUtil.colorize(String.format(missingMaterials, count, itemName));
    }

    // Forge Messages
    public String getForgeSessionActive() { return ColorUtil.colorize(forgeSessionActive); }
    public String getForgeUnknownRecipe(String recipeId) {
        return ColorUtil.colorize(String.format(forgeUnknownRecipe, recipeId));
    }
    public String getForgeStarted() { return ColorUtil.colorize(forgeStarted); }
    public String getForgePerfectHit() { return ColorUtil.colorize(forgePerfectHit); }
    public String getForgeGoodHit() { return ColorUtil.colorize(forgeGoodHit); }
    public String getForgeMiss() { return ColorUtil.colorize(forgeMiss); }
    public String getForgeComplete(int stars) {
        return ColorUtil.colorize(String.format(forgeComplete, stars));
    }

    // Command Messages
    public String getCommandPlayerOnly() { return ColorUtil.colorize(commandPlayerOnly); }
    public String getCommandNoPermission() { return ColorUtil.colorize(commandNoPermission); }
    public String getCommandUsage(String usage) {
        return ColorUtil.colorize(String.format(commandUsage, usage));
    }
    public String getCommandReloadSuccess() { return ColorUtil.colorize(commandReloadSuccess); }
    public String getCommandReloadFailed() { return ColorUtil.colorize(commandReloadFailed); }
}