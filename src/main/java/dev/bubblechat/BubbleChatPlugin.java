package dev.bubblechat;

import dev.bubblechat.listeners.ChatListener;
import dev.bubblechat.managers.BubbleManager;
import dev.bubblechat.managers.ConfigManager;
import dev.bubblechat.utils.FoliaUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * BubbleChat - Folia-compatible chat bubble plugin
 * Uses TextDisplay entities (1.19.4+) for rendering
 * Fully async-safe and region-scheduler aware
 */
public class BubbleChatPlugin extends JavaPlugin {

    private static BubbleChatPlugin instance;
    private ConfigManager configManager;
    private BubbleManager bubbleManager;
    private boolean placeholderApiEnabled = false;

    @Override
    public void onEnable() {
        instance = this;

        // Check Folia support
        if (FoliaUtil.isFolia()) {
            getLogger().info("Folia detected — using RegionScheduler & EntityScheduler.");
        } else {
            getLogger().info("Running on Paper/Spigot — using BukkitScheduler.");
        }

        // Save default config
        saveDefaultConfig();

        // Init managers
        configManager = new ConfigManager(this);
        bubbleManager = new BubbleManager(this);

        // Register PlaceholderAPI expansion if present
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderApiEnabled = true;
            getLogger().info("PlaceholderAPI found — placeholder support enabled.");
        }

        // Register listeners
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        getLogger().info("BubbleChat v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        // Clean up all active bubbles safely
        if (bubbleManager != null) {
            bubbleManager.removeAllBubbles();
        }
        getLogger().info("BubbleChat disabled — all bubbles removed.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = configManager.getPrefix();

        if (!command.getName().equalsIgnoreCase("bubblechat")) return false;

        if (args.length == 0) {
            sender.sendMessage(prefix + "&7BubbleChat v" + getDescription().getVersion());
            sender.sendMessage(prefix + "&7/bc reload &8- &fReload config");
            sender.sendMessage(prefix + "&7/bc toggle &8- &fToggle bubbles for yourself");
            sender.sendMessage(prefix + "&7/bc info &8- &fShow plugin info");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("bubblechat.admin")) {
                    sender.sendMessage(prefix + configManager.getNoPermission());
                    return true;
                }
                reloadConfig();
                configManager.reload();
                sender.sendMessage(prefix + configManager.getReloadMessage());
            }
            case "toggle" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(prefix + "&cOnly players can use this command.");
                    return true;
                }
                boolean current = bubbleManager.isToggled(player);
                bubbleManager.setToggled(player, !current);
                sender.sendMessage(prefix + (!current
                        ? configManager.getToggledOn()
                        : configManager.getToggledOff()));
            }
            case "info" -> {
                sender.sendMessage(prefix + "&fRunning on: " + (FoliaUtil.isFolia() ? "&aFolia" : "&ePaper/Spigot"));
                sender.sendMessage(prefix + "&fPAPI: " + (placeholderApiEnabled ? "&aEnabled" : "&cDisabled"));
                sender.sendMessage(prefix + "&fActive bubbles: &b" + bubbleManager.getActiveBubbleCount());
            }
            default -> sender.sendMessage(prefix + "&cUnknown subcommand.");
        }
        return true;
    }

    public static BubbleChatPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public BubbleManager getBubbleManager() {
        return bubbleManager;
    }

    public boolean isPlaceholderApiEnabled() {
        return placeholderApiEnabled;
    }

    public void debug(String message) {
        if (configManager.isDebug()) {
            getLogger().log(Level.INFO, "[DEBUG] " + message);
        }
    }
}