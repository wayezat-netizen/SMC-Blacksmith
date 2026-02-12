package com.simmc.blacksmith.forge;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.HammerConfig;
import com.simmc.blacksmith.config.MessageConfig;
import com.simmc.blacksmith.forge.display.ForgeDisplay;
import com.simmc.blacksmith.integration.PlaceholderAPIHook;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import com.simmc.blacksmith.quench.QuenchingManager;
import com.simmc.blacksmith.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active forge sessions.
 *
 * FIXES:
 * - Added timeout handling
 * - Added proper cancel functionality
 * - Fixed session cleanup for GUI reopening
 * - Added forceEndSession for disconnects
 */
public class ForgeManager {

    private static final Set<Material> ANVIL_MATERIALS = EnumSet.of(
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL
    );

    private static final int ANVIL_SEARCH_RADIUS = 5;
    private static final int CLEANUP_DELAY_TICKS = 40;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemProviderRegistry itemRegistry;

    private final Map<UUID, ForgeSession> sessions;
    private final Map<UUID, ForgeDisplay> displays;
    private final Map<UUID, Location> playerAnvilLocations;
    private final Map<UUID, HammerConfig.HammerType> playerHammerTypes;

    private BukkitTask tickTask;

    public ForgeManager(JavaPlugin plugin, ConfigManager configManager, ItemProviderRegistry itemRegistry) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRegistry = itemRegistry;
        this.sessions = new ConcurrentHashMap<>();
        this.displays = new ConcurrentHashMap<>();
        this.playerAnvilLocations = new ConcurrentHashMap<>();
        this.playerHammerTypes = new ConcurrentHashMap<>();

        startTickTask();
    }

    // ==================== TICK LOOP ====================

    private void startTickTask() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
    }

    private void tick() {
        if (sessions.isEmpty()) return;

        List<UUID> playerIds = new ArrayList<>(sessions.keySet());

        for (UUID playerId : playerIds) {
            tickSession(playerId);
        }
    }

    private void tickSession(UUID playerId) {
        ForgeSession session = sessions.get(playerId);
        if (session == null) return;

        if (session.isTimedOut()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                handleTimeout(player, session);
            } else {
                cleanup(playerId);
            }
            return;
        }

        if (!session.isActive()) {
            cleanup(playerId);
            return;
        }

        session.tick();

        ForgeDisplay display = displays.get(playerId);
        if (display != null && display.isValid()) {
            display.tick(session);
        }

        if (session.isComplete()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                completeSession(player);
            } else {
                cleanup(playerId);
            }
        }
    }

    private void handleTimeout(Player player, ForgeSession session) {
        UUID playerId = player.getUniqueId();
        MessageConfig messages = configManager.getMessageConfig();

        if (session.getHitsCompleted() > 0) {
            player.sendMessage("§c§lTIME OUT! §7Forging incomplete...");

            int stars = session.calculateStarRating();
            ForgeRecipe recipe = session.getRecipe();

            if (stars > 0) {
                ItemStack resultItem = createResultItem(recipe, stars);
                if (resultItem != null) {
                    player.sendMessage("§7You managed to salvage a §e" + stars + "-star §7item.");
                    handleForgeResult(player, resultItem, stars, session, recipe);
                } else {
                    refundMaterials(player, recipe);
                    player.sendMessage("§7Your materials have been refunded.");
                }
            } else {
                refundMaterials(player, recipe);
                player.sendMessage("§7Your materials have been refunded.");
            }
        } else {
            player.sendMessage("§c§lTIME OUT! §7No progress was made.");
            refundMaterials(player, session.getRecipe());
            player.sendMessage("§7Your materials have been refunded.");
        }

        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.5f);
        cleanup(playerId);
    }

    // ==================== SESSION LIFECYCLE ====================

    public boolean startSession(Player player, String recipeId, Location anvilLocation) {
        UUID playerId = player.getUniqueId();
        MessageConfig messages = configManager.getMessageConfig();

        ValidationResult validation = validateSessionStart(player, recipeId);
        if (!validation.success()) {
            player.sendMessage(validation.message());
            return false;
        }

        ForgeRecipe recipe = validation.recipe();

        Location actualAnvil = resolveAnvilLocation(player, anvilLocation);
        if (actualAnvil == null) {
            player.sendMessage("§cYou must be near an anvil to forge!");
            return false;
        }

        if (!consumeMaterials(player, recipe)) {
            player.sendMessage(messages.getMissingMaterials(recipe.getInputAmount(), recipe.getInputId()));
            return false;
        }

        ForgeSession session = new ForgeSession(playerId, recipe, actualAnvil);
        sessions.put(playerId, session);

        HammerConfig.HammerType hammerType = playerHammerTypes.remove(playerId);
        if (hammerType != null) {
            session.setHammerBonuses(hammerType.speedBonus(), hammerType.accuracyBonus());
        }

        ForgeDisplay display = new ForgeDisplay(playerId, actualAnvil, recipe);
        display.spawn();
        displays.put(playerId, display);

        player.playSound(actualAnvil, Sound.BLOCK_ANVIL_PLACE, 0.8f, 0.9f);
        player.sendMessage(messages.getForgeStarted());
        player.sendMessage("§e§lLEFT CLICK §7the targets with your §6HAMMER§7!");
        player.sendMessage("§7Type §c/forge cancel §7or §cSNEAK + RIGHT-CLICK §7to cancel.");

        return true;
    }

    private ValidationResult validateSessionStart(Player player, String recipeId) {
        UUID playerId = player.getUniqueId();
        MessageConfig messages = configManager.getMessageConfig();

        if (sessions.containsKey(playerId)) {
            return ValidationResult.fail(messages.getForgeSessionActive());
        }

        QuenchingManager quenchManager = SMCBlacksmith.getInstance().getQuenchingManager();
        if (quenchManager != null && quenchManager.hasActiveSession(playerId)) {
            return ValidationResult.fail("§cComplete your current session first!");
        }

        ForgeRecipe recipe = configManager.getBlacksmithConfig().getRecipe(recipeId);
        if (recipe == null) {
            return ValidationResult.fail(messages.getForgeUnknownRecipe(recipeId));
        }

        if (recipe.hasPermission() && !player.hasPermission(recipe.getPermission())) {
            return ValidationResult.fail(messages.getNoPermission());
        }

        if (!checkCondition(player, recipe)) {
            return ValidationResult.fail(messages.getConditionNotMet());
        }

        return ValidationResult.success(recipe);
    }

    private record ValidationResult(boolean success, String message, ForgeRecipe recipe) {
        static ValidationResult success(ForgeRecipe recipe) {
            return new ValidationResult(true, null, recipe);
        }
        static ValidationResult fail(String message) {
            return new ValidationResult(false, message, null);
        }
    }

    private void completeSession(Player player) {
        UUID playerId = player.getUniqueId();
        ForgeSession session = sessions.get(playerId);
        if (session == null || !session.isActive()) return;

        session.setActive(false);

        int stars = session.calculateStarRating();
        ForgeRecipe recipe = session.getRecipe();

        ForgeDisplay display = displays.get(playerId);
        if (display != null) {
            display.showCompletion(stars);
        }
        playCompletionEffects(player, session.getAnvilLocation(), stars);

        ItemStack resultItem = createResultItem(recipe, stars);

        String starDisplay = ColorUtil.formatStars(stars, 5);
        player.sendMessage(configManager.getMessageConfig().getForgeComplete(stars, starDisplay));

        executeCommand(player, recipe, stars);

        if (resultItem != null) {
            handleForgeResult(player, resultItem, stars, session, recipe);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> cleanup(playerId), CLEANUP_DELAY_TICKS);
    }

    private ItemStack createResultItem(ForgeRecipe recipe, int stars) {
        if (recipe.usesBaseItem()) {
            return itemRegistry.getItem(recipe.getBaseItemType(), recipe.getBaseItemId(), 1);
        }

        ForgeResult result = recipe.getResult(stars);
        if (result != null) {
            return itemRegistry.getItem(result.type(), result.id(), result.amount());
        }

        return null;
    }

    private void handleForgeResult(Player player, ItemStack item, int stars, ForgeSession session, ForgeRecipe recipe) {
        QuenchingManager quenchManager = SMCBlacksmith.getInstance().getQuenchingManager();

        if (quenchManager != null && !quenchManager.hasActiveSession(player.getUniqueId())) {
            quenchManager.startQuenching(player, item, stars, session.getAnvilLocation(), recipe);
        } else {
            giveItem(player, item);
        }
    }

    public void cancelSession(UUID playerId) {
        ForgeSession session = sessions.get(playerId);
        if (session == null) return;

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            refundMaterials(player, session.getRecipe());
            player.sendMessage(configManager.getMessageConfig().getForgeRefunded());
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.0f);
        }

        session.cancel();
        cleanup(playerId);
    }

    public void forceEndSession(UUID playerId) {
        ForgeSession session = sessions.get(playerId);
        if (session == null) return;

        session.cancel();
        cleanup(playerId);
    }

    private void cleanup(UUID playerId) {
        ForgeSession session = sessions.remove(playerId);
        if (session != null) {
            session.cleanup();
        }

        ForgeDisplay display = displays.remove(playerId);
        if (display != null) {
            display.remove();
        }

        playerAnvilLocations.remove(playerId);
        playerHammerTypes.remove(playerId);
    }

    // ==================== HIT PROCESSING ====================

    public void processPointHit(Player player, UUID hitboxId) {
        UUID playerId = player.getUniqueId();
        ForgeSession session = sessions.get(playerId);

        if (session == null || !session.isActive()) return;

        double accuracy = session.processHit(hitboxId);
        if (accuracy < 0) return;

        ForgeDisplay display = displays.get(playerId);
        if (display != null) {
            display.onHit(accuracy);
        }

        sendHitFeedback(player, accuracy);
    }

    private void sendHitFeedback(Player player, double accuracy) {
        MessageConfig messages = configManager.getMessageConfig();

        if (accuracy >= 0.9) {
            player.sendMessage(messages.getForgePerfectHit());
        } else if (accuracy >= 0.7) {
            player.sendMessage(messages.getForgeGoodHit());
        } else {
            player.sendMessage(messages.getForgeMiss());
        }
    }

    // ==================== UTILITIES ====================

    private Location resolveAnvilLocation(Player player, Location provided) {
        if (provided != null && isAnvilBlock(provided.getBlock())) {
            return provided;
        }

        Location stored = playerAnvilLocations.remove(player.getUniqueId());
        if (stored != null && isAnvilBlock(stored.getBlock())) {
            return stored;
        }

        return findNearbyAnvil(player);
    }

    private Location findNearbyAnvil(Player player) {
        RayTraceResult rayTrace = player.rayTraceBlocks(5.0);
        if (rayTrace != null && rayTrace.getHitBlock() != null) {
            Block hitBlock = rayTrace.getHitBlock();
            if (isAnvilBlock(hitBlock)) {
                return hitBlock.getLocation();
            }
        }

        Location center = player.getLocation();
        for (int x = -ANVIL_SEARCH_RADIUS; x <= ANVIL_SEARCH_RADIUS; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -ANVIL_SEARCH_RADIUS; z <= ANVIL_SEARCH_RADIUS; z++) {
                    Block block = center.clone().add(x, y, z).getBlock();
                    if (isAnvilBlock(block)) {
                        return block.getLocation();
                    }
                }
            }
        }

        return null;
    }

    private boolean isAnvilBlock(Block block) {
        return block != null && ANVIL_MATERIALS.contains(block.getType());
    }

    private boolean checkCondition(Player player, ForgeRecipe recipe) {
        if (!recipe.hasCondition()) {
            return true;
        }

        String condition = recipe.getCondition();
        if (condition == null || condition.trim().isEmpty()) {
            return true;
        }

        PlaceholderAPIHook papi = SMCBlacksmith.getInstance().getPapiHook();

        if (papi == null || !papi.isAvailable()) {
            return true;
        }

        try {
            return papi.checkCondition(player, condition);
        } catch (Exception e) {
            plugin.getLogger().warning("[Forge] Error checking condition: " + e.getMessage());
            return true;
        }
    }

    private boolean consumeMaterials(Player player, ForgeRecipe recipe) {
        if (!recipe.hasInput()) return true;

        String type = recipe.getInputType();
        String id = recipe.getInputId();
        int required = recipe.getInputAmount();

        int found = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (item != null && itemRegistry.matches(item, type, id)) {
                found += item.getAmount();
                if (found >= required) break;
            }
        }

        if (found < required) return false;

        int remaining = required;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !itemRegistry.matches(item, type, id)) continue;

            int take = Math.min(remaining, item.getAmount());
            remaining -= take;

            int newAmount = item.getAmount() - take;
            if (newAmount <= 0) {
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(newAmount);
            }
        }

        return true;
    }

    private void refundMaterials(Player player, ForgeRecipe recipe) {
        if (!recipe.hasInput()) return;

        ItemStack refund = itemRegistry.getItem(recipe.getInputType(), recipe.getInputId(), recipe.getInputAmount());
        if (refund != null) {
            giveItem(player, refund);
        }
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        overflow.values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private void playCompletionEffects(Player player, Location loc, int stars) {
        if (stars >= 5) {
            player.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        } else if (stars >= 3) {
            player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
        } else {
            player.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    private void executeCommand(Player player, ForgeRecipe recipe, int stars) {
        String command = recipe.getRunAfterCommand();
        if (command == null || command.isEmpty()) return;

        String parsed = command
                .replace("%player%", player.getName())
                .replace("<player>", player.getName())
                .replace("%stars%", String.valueOf(stars))
                .replace("<stars>", String.valueOf(stars));

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
    }

    public Location getAndRemovePlayerAnvilLocation(UUID playerId) {
        return playerAnvilLocations.remove(playerId);
    }

    public HammerConfig.HammerType getAndRemovePlayerHammerType(UUID playerId) {
        return playerHammerTypes.remove(playerId);
    }

    // ==================== PUBLIC API ====================

    public void setPlayerAnvilLocation(UUID playerId, Location location) {
        playerAnvilLocations.put(playerId, location.clone());
    }

    public void setPlayerHammerType(UUID playerId, HammerConfig.HammerType hammerType) {
        playerHammerTypes.put(playerId, hammerType);
    }

    public void cancelAllSessions() {
        new ArrayList<>(sessions.keySet()).forEach(this::cancelSession);
    }

    public void shutdown() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }
        cancelAllSessions();
    }

    public void reload() {
        cancelAllSessions();
    }

    public boolean hasActiveSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public ForgeSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}