package dev.bubblechat.listeners;

import dev.bubblechat.BubbleChatPlugin;
import dev.bubblechat.managers.BubbleManager;
import dev.bubblechat.managers.ConfigManager;
import dev.bubblechat.utils.FoliaUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

/**
 * Listens to chat events and triggers bubble spawning.
 *
 * Folia safety notes:
 *   - AsyncChatEvent fires on an async thread — do NOT modify world state here.
 *   - World/entity operations are dispatched via FoliaUtil.runAtLocation/runAtEntity.
 *   - The listener itself is registered on the main thread at plugin startup.
 */
public class ChatListener implements Listener {

    private final BubbleChatPlugin plugin;
    private final BubbleManager bubbleManager;
    private final ConfigManager config;

    public ChatListener(BubbleChatPlugin plugin) {
        this.plugin = plugin;
        this.bubbleManager = plugin.getBubbleManager();
        this.config = plugin.getConfigManager();
    }

    /**
     * Handle player chat — using MONITOR priority to run after other plugins
     * (e.g., chat formatters, permission plugins) have processed the event.
     *
     * AsyncChatEvent is fired asynchronously on Paper/Folia.
     * We capture everything we need from the event here, then dispatch
     * world operations to the correct region scheduler.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();

        // Permission check
        if (!player.hasPermission("bubblechat.use")) return;

        // Per-world permission gate
        if (config.isPerWorldPermissions() && !player.hasPermission("bubblechat.world." + worldName)) return;

        // Per-world enabled check
        if (!config.isEnabledInWorld(worldName)) return;

        // Extract plain text from the Adventure Component
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        plugin.debug("Chat from " + player.getName() + " in world " + worldName + ": " + rawMessage);

        // If proximity mode is enabled for this world
        if (config.isProximityEnabledForWorld(worldName) && !player.hasPermission("bubblechat.bypass.proximity")) {
            handleProximityChat(event, player, rawMessage, worldName);
        } else {
            // Cancel original chat globally if configured
            if (config.isCancelOriginalChatForWorld(worldName) && !player.hasPermission("bubblechat.bypass.cancel")) {
                event.setCancelled(true);
            }
            dispatchBubble(player, rawMessage);
        }
    }

    private void handleProximityChat(AsyncChatEvent event, Player player, String rawMessage, String worldName) {
        List<Player> nearbyPlayers = bubbleManager.getNearbyPlayers(player,
                config.getProximityRangeForWorld(worldName));

        // Filter event recipients to only nearby players; keep console audience
        event.viewers().removeIf(audience -> {
            if (!(audience instanceof Player viewer)) return false;
            return !nearbyPlayers.contains(viewer);
        });

        // Cancel original chat if configured for this world
        if (config.isCancelOriginalChatForWorld(worldName) && !player.hasPermission("bubblechat.bypass.cancel")) {
            event.setCancelled(true);
        }

        dispatchBubble(player, rawMessage);
    }

    /**
     * Dispatch bubble creation to the correct region thread.
     * This is called from an async context, so we use FoliaUtil to hop to the right thread.
     */
    private void dispatchBubble(Player player, String message) {
        // Capture location snapshot for region scheduling
        // Location is safe to read from async context (it's a snapshot)
        org.bukkit.Location loc = player.getLocation();

        FoliaUtil.runAtLocation(plugin, loc, () -> {
            if (!player.isOnline()) return;
            bubbleManager.showBubble(player, message);
        });
    }

    /**
     * Clean up when a player disconnects.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // runAtEntity won't work for disconnected player — use location-based scheduler
        org.bukkit.Location loc = player.getLocation();
        FoliaUtil.runAtLocation(plugin, loc, () -> {
            bubbleManager.removePlayerBubbles(player);
        });
    }
}