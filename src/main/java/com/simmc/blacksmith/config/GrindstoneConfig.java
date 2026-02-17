package com.simmc.blacksmith.config;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import com.simmc.blacksmith.repair.RepairConfigData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Configuration for grindstone repair system.
 *
 * PERMISSION-BASED REPAIR:
 * - skill.tiejiang.hejin.xiufu = can repair
 * - skill.tiejiang.hejin.cgl.X = X% durability restored
 * - smithing_repair_chance.hammer.X = X% success chance
 */
public class GrindstoneConfig {

    private final Map<String, RepairConfigData> repairConfigs;
    private final List<String> loadWarnings;
    private volatile ItemProviderRegistry itemRegistry;

    // GUI settings
    private String guiTitle = "&8&lRepair Station";
    private int guiSize = 27;
    private int inputSlot = 13;
    private int repairButtonSlot = 22;
    private int infoSlot = 4;

    // Hammer settings
    private boolean hammerRequired = true;
    private String hammerType = "smc";
    private String hammerId = "repair_hammer";
    private String hammerFallbackMaterial = "IRON_AXE";

    // Permission settings
    private String usePermission = "skill.tiejiang.hejin.xiufu";
    private String repairAmountPermission = "skill.tiejiang.hejin.cgl";
    private String successChancePermission = "smithing_repair_chance.hammer";
    private int defaultRepairAmount = 25;
    private int defaultSuccessChance = 50;

    // Vanilla behavior
    private boolean sneakForVanilla = true;

    // CE Furniture
    private Set<String> furnitureIds = new HashSet<>();

    public GrindstoneConfig() {
        this.repairConfigs = new LinkedHashMap<>();
        this.loadWarnings = new ArrayList<>();
    }

    public void load(FileConfiguration config) {
        repairConfigs.clear();
        loadWarnings.clear();
        furnitureIds.clear();

        // Load general settings
        sneakForVanilla = config.getBoolean("sneak_for_vanilla", true);

        // Load hammer settings
        ConfigurationSection hammerSection = config.getConfigurationSection("repair_hammer");
        if (hammerSection != null) {
            hammerRequired = hammerSection.getBoolean("enabled", true);
            hammerType = hammerSection.getString("type", "smc");
            hammerId = hammerSection.getString("id", "repair_hammer");
            hammerFallbackMaterial = hammerSection.getString("fallback_material", "IRON_AXE");
        }

        // Load GUI settings
        ConfigurationSection guiSection = config.getConfigurationSection("gui");
        if (guiSection != null) {
            guiTitle = guiSection.getString("title", "&8&lRepair Station");
            guiSize = guiSection.getInt("size", 27);
            inputSlot = guiSection.getInt("input_slot", 13);
            repairButtonSlot = guiSection.getInt("repair_button_slot", 22);
            infoSlot = guiSection.getInt("info_slot", 4);
        }

        // Load permission settings
        ConfigurationSection permSection = config.getConfigurationSection("permissions");
        if (permSection != null) {
            usePermission = permSection.getString("use_permission", "skill.tiejiang.hejin.xiufu");
            repairAmountPermission = permSection.getString("repair_amount_permission", "skill.tiejiang.hejin.cgl");
            successChancePermission = permSection.getString("success_chance_permission", "smithing_repair_chance.hammer");
            defaultRepairAmount = permSection.getInt("default_repair_amount", 25);
            defaultSuccessChance = permSection.getInt("default_success_chance", 50);
        }

        // Load furniture IDs
        ConfigurationSection furnitureSection = config.getConfigurationSection("furniture");
        if (furnitureSection != null && furnitureSection.getBoolean("enabled", false)) {
            furnitureIds.addAll(furnitureSection.getStringList("ids"));
        }

        // Load repair configs
        ConfigurationSection repairsSection = config.getConfigurationSection("repairs");
        if (repairsSection != null) {
            for (String key : repairsSection.getKeys(false)) {
                ConfigurationSection section = repairsSection.getConfigurationSection(key);
                if (section == null) continue;
                parseRepairConfig(key, section).ifPresent(data -> repairConfigs.put(key, data));
            }
        }
    }

    private Optional<RepairConfigData> parseRepairConfig(String id, ConfigurationSection section) {
        // Item ID - support both formats: "id" (client format) and "item_id" (old format)
        String itemId = section.getString("id", section.getString("item_id", ""));
        if (itemId.isEmpty()) {
            loadWarnings.add("[" + id + "] Missing id/item_id");
            return Optional.empty();
        }

        // Item type - support both formats: "type" and "item_type"
        String itemType = section.getString("type", section.getString("item_type", "smc"));

        // Per-item permission (client format)
        String permission = section.getString("permission", "");

        // Per-item repair chance permission prefix (client format)
        String repairChancePerm = section.getString("repair_chance_permission", "");

        // Input material - support both "input" and "repair_material" sections
        String inputId = "";
        String inputType = "minecraft";
        int inputAmount = 1;

        ConfigurationSection inputSection = section.getConfigurationSection("input");
        if (inputSection == null) {
            inputSection = section.getConfigurationSection("repair_material");
        }
        if (inputSection != null) {
            inputId = inputSection.getString("id", "");
            inputType = inputSection.getString("type", "minecraft");
            inputAmount = Math.max(1, inputSection.getInt("amount", 1));
        }

        return Optional.of(new RepairConfigData(
                id, itemId, itemType,
                permission,
                repairChancePerm,
                "",
                inputId, inputType, inputAmount
        ));
    }

    public void setItemRegistry(ItemProviderRegistry registry) {
        this.itemRegistry = registry;
    }

    public Optional<RepairConfigData> findByItem(ItemStack item) {
        if (item == null || itemRegistry == null) return Optional.empty();

        return repairConfigs.values().stream()
                .filter(config -> itemRegistry.matches(item, config.itemType(), config.itemId()))
                .findFirst();
    }

    // ==================== GETTERS ====================

    // GUI
    public String getGuiTitle() { return guiTitle.replace("&", "§"); }
    public int getGuiSize() { return guiSize; }
    public int getInputSlot() { return inputSlot; }
    public int getRepairButtonSlot() { return repairButtonSlot; }
    public int getInfoSlot() { return infoSlot; }

    // Hammer
    public boolean isHammerRequired() { return hammerRequired; }
    public String getHammerType() { return hammerType; }
    public String getHammerId() { return hammerId; }
    public String getHammerFallbackMaterial() { return hammerFallbackMaterial; }

    // Permissions
    public String getUsePermission() { return usePermission; }
    public String getRepairAmountPermission() { return repairAmountPermission; }
    public String getSuccessChancePermission() { return successChancePermission; }
    public int getDefaultRepairAmount() { return defaultRepairAmount; }
    public int getDefaultSuccessChance() { return defaultSuccessChance; }

    // Other
    public boolean isSneakForVanilla() { return sneakForVanilla; }
    public Set<String> getFurnitureIds() { return new HashSet<>(furnitureIds); }
    public boolean isFurnitureId(String id) { return furnitureIds.contains(id); }

    public Optional<RepairConfigData> getRepairConfig(String id) {
        return Optional.ofNullable(repairConfigs.get(id));
    }

    public Map<String, RepairConfigData> getRepairConfigs() {
        return new LinkedHashMap<>(repairConfigs);
    }

    public int getRepairConfigCount() {
        return repairConfigs.size();
    }

    public List<String> getWarnings() {
        return new ArrayList<>(loadWarnings);
    }
}