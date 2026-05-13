package dev.bubblechat.listeners;

import dev.bubblechat.BubbleChatPlugin;
import dev.bubblechat.managers.BubbleManager;
import dev.bubblechat.managers.ConfigManager;
import dev.bubblechat.utils.FoliaUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;

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
     * ignoreCancelled = false เพราะ FireChat cancel event แล้ว resend เอง
     * ถ้าใช้ ignoreCancelled = true จะไม่ได้รับ event เลย bubble ไม่ขึ้น
     *
     * เพื่อป้องกัน double bubble: ถ้า event ถูก cancel อยู่แล้ว (FireChat cancel ไว้)
     * เราแค่ spawn bubble โดยไม่แตะ viewers เพราะ FireChat จัดการ chat box เองแล้ว
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();

        if (!player.hasPermission("bubblechat.use")) return;
        if (config.isPerWorldPermissions() && !player.hasPermission("bubblechat.world." + worldName)) return;
        if (!config.isEnabledInWorld(worldName)) return;

        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.debug("Chat from " + player.getName() + " cancelled=" + event.isCancelled() + ": " + rawMessage);

        boolean alreadyCancelled = event.isCancelled(); // FireChat cancelled = true

        if (config.isProximityEnabledForWorld(worldName) && !player.hasPermission("bubblechat.bypass.proximity")) {
            handleProximityChat(event, player, rawMessage, worldName, alreadyCancelled);
        } else {
            if (!alreadyCancelled
                    && config.isCancelOriginalChatForWorld(worldName)
                    && !player.hasPermission("bubblechat.bypass.cancel")) {
                event.setCancelled(true);
            }
            dispatchBubble(player, rawMessage);
        }
    }

    private void handleProximityChat(AsyncChatEvent event, Player player, String rawMessage,
                                     String worldName, boolean alreadyCancelled) {
        double radius = getEffectiveRadius(worldName);
        List<Player> nearbyPlayers = bubbleManager.getNearbyPlayers(player, radius);

        // ถ้า FireChat cancel event ไว้แล้ว แปลว่า FireChat จัดการ chat box เองแล้ว
        // ไม่ต้อง removeIf viewers เพราะ event นั้น cancel อยู่แล้ว ไม่มีใครรับ
        // แค่ spawn bubble ให้คนที่อยู่ในรัศมีก็พอ
        if (!alreadyCancelled) {
            event.viewers().removeIf(audience -> {
                if (!(audience instanceof Player viewer)) return false;
                return !nearbyPlayers.contains(viewer);
            });

            if (config.isCancelOriginalChatForWorld(worldName)
                    && !player.hasPermission("bubblechat.bypass.cancel")) {
                event.setCancelled(true);
            }
        }

        org.bukkit.Location loc = player.getLocation();
        FoliaUtil.runAtLocation(plugin, loc, () -> {
            if (!player.isOnline()) return;
            bubbleManager.showBubble(player, rawMessage);
        });
    }

    private double getEffectiveRadius(String worldName) {
        Plugin fireChat = Bukkit.getPluginManager().getPlugin("FireChat");
        if (fireChat != null && fireChat.isEnabled()) {
            FileConfiguration fireChatConfig = fireChat.getConfig();
            int fireChatRadius = fireChatConfig.getInt("local-chat-radius", -1);
            if (fireChatRadius > 0) {
                plugin.debug("Synced radius from FireChat: " + fireChatRadius);
                return fireChatRadius;
            }
        }
        return config.getProximityRangeForWorld(worldName);
    }

    private void dispatchBubble(Player player, String message) {
        org.bukkit.Location loc = player.getLocation();
        FoliaUtil.runAtLocation(plugin, loc, () -> {
            if (!player.isOnline()) return;
            bubbleManager.showBubble(player, message);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        org.bukkit.Location loc = player.getLocation();
        FoliaUtil.runAtLocation(plugin, loc, () -> {
            bubbleManager.removePlayerBubbles(player);
        });
    }
}
