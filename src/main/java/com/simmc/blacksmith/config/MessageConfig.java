package com.simmc.blacksmith.config;

import com.simmc.blacksmith.util.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration handler for all plugin messages.
 * Supports color codes and placeholders.
 */
public class MessageConfig {

    //messages
    private String furnaceTitle;
    private String anvilTitle;
    private String vanillaAnvil;
    private String repairFailed;
    private String repairSuccess;
    private String noPermission;
    private String invalidItem;
    private String missingMaterials;

    //Forge messages
    private String forgeSessionActive;
    private String forgeUnknownRecipe;
    private String forgeStarted;
    private String forgePerfectHit;
    private String forgeGoodHit;
    private String forgeOkayHit;
    private String forgeMiss;
    private String forgeComplete;
    private String forgeRefunded;
    private String conditionNotMet;

    //Repair messages
    private String itemNotDamaged;

    //Furnace messages
    private String bellowsUsed;

    public void load(FileConfiguration config) {
        // Existing messages
        furnaceTitle = config.getString("furnace_title", "Furnace");
        anvilTitle = config.getString("anvil_title", "Anvil");
        vanillaAnvil = config.getString("vanilla_anvil", "Shift click anvil to use normal anvil");
        repairFailed = config.getString("grindstone_repair_failed", "Repair failed!");
        repairSuccess = config.getString("grindstone_repair_success", "Repair Successful!");
        noPermission = config.getString("grindstone_no_perms", "You do not have permission to do this!");
        invalidItem = config.getString("grindstone_invalid", "This item cannot be repaired");
        missingMaterials = config.getString("grindstone_materials", "Missing materials! You need %d %s");

        //Forge messages
        forgeSessionActive = config.getString("forge_session_active", "§cYou already have an active forging session!");
        forgeUnknownRecipe = config.getString("forge_unknown_recipe", "§cUnknown recipe: %s");
        forgeStarted = config.getString("forge_started", "§aForging session started! Click the hammer to strike.");
        forgePerfectHit = config.getString("forge_perfect_hit", "§aPerfect hit!");
        forgeGoodHit = config.getString("forge_good_hit", "§eGood hit!");
        forgeOkayHit = config.getString("forge_okay_hit", "§6Okay hit.");
        forgeMiss = config.getString("forge_missed_hit", "§cMissed!");
        forgeComplete = config.getString("forge_complete", "§aForging complete! Quality: %s");
        forgeRefunded = config.getString("forge_refunded", "§eForging session cancelled. Materials refunded.");
        conditionNotMet = config.getString("condition_not_met", "§cYou don't meet the requirements for this recipe.");

        //Repair messages
        itemNotDamaged = config.getString("item_not_damaged", "§eThis item doesn't need repair.");

        //Furnace messages
        bellowsUsed = config.getString("bellows_used", "§6You pump the bellows, increasing the temperature!");
    }

    //EXISTING GETTERS

    public String getFurnaceTitle() {
        return ColorUtil.colorize(furnaceTitle);
    }

    public String getAnvilTitle() {
        return ColorUtil.colorize(anvilTitle);
    }

    public String getVanillaAnvil() {
        return ColorUtil.colorize(vanillaAnvil);
    }

    public String getRepairFailed() {
        return ColorUtil.colorize(repairFailed);
    }

    public String getRepairSuccess() {
        return ColorUtil.colorize(repairSuccess);
    }

    public String getNoPermission() {
        return ColorUtil.colorize(noPermission);
    }

    public String getInvalidItem() {
        return ColorUtil.colorize(invalidItem);
    }

    public String getMissingMaterials(int count, String itemName) {
        return ColorUtil.colorize(String.format(missingMaterials, count, itemName));
    }

    //FORGE GETTERS
    public String getForgeSessionActive() {
        return ColorUtil.colorize(forgeSessionActive);
    }

    public String getForgeUnknownRecipe(String recipeId) {
        return ColorUtil.colorize(String.format(forgeUnknownRecipe, recipeId));
    }

    public String getForgeStarted() {
        return ColorUtil.colorize(forgeStarted);
    }

    public String getForgePerfectHit() {
        return ColorUtil.colorize(forgePerfectHit);
    }

    public String getForgeGoodHit() {
        return ColorUtil.colorize(forgeGoodHit);
    }

    public String getForgeOkayHit() {
        return ColorUtil.colorize(forgeOkayHit);
    }

    public String getForgeMiss() {
        return ColorUtil.colorize(forgeMiss);
    }

    public String getForgeComplete(int stars, String starDisplay) {
        return ColorUtil.colorize(String.format(forgeComplete, starDisplay));
    }

    public String getForgeRefunded() {
        return ColorUtil.colorize(forgeRefunded);
    }

    public String getConditionNotMet() {
        return ColorUtil.colorize(conditionNotMet);
    }

    //REPAIR GETTERS
    public String getItemNotDamaged() {
        return ColorUtil.colorize(itemNotDamaged);
    }

    //FURNACE GETTERS

    public String getBellowsUsed() {
        return ColorUtil.colorize(bellowsUsed);
    }
}