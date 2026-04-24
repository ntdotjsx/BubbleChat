package dev.bubblechat.managers;

import dev.bubblechat.BubbleChatPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Centralized config access with typed getters.
 */
public class ConfigManager {

    private final BubbleChatPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(BubbleChatPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    // ── Proximity ─────────────────────────────────
    public boolean isProximityEnabled() {
        return config.getBoolean("proximity.enabled", true);
    }

    public double getProximityRange() {
        return config.getDouble("proximity.range", 20.0);
    }

    public boolean isCancelOriginalChat() {
        return config.getBoolean("proximity.cancel-original-chat", false);
    }

    // ── Bubble ────────────────────────────────────
    public int getFollowInterpolationTicks() {
        return config.getInt("follow-interpolation-ticks", 1);
    }

    public int getBubbleDuration() {
        return config.getInt("bubble.duration", 80);
    }

    public int getMaxLineLength() {
        return config.getInt("bubble.max-line-length", 30);
    }

    public int getMaxLines() {
        return config.getInt("bubble.max-lines", 3);
    }

    public String getBubbleFormat() {
        return config.getString("bubble.format", "&f%message%");
    }

    public boolean isShowName() {
        return config.getBoolean("bubble.show-name", true);
    }

    public String getNameFormat() {
        return config.getString("bubble.name-format", "&e&l%player_name%");
    }

    // ── TextDisplay ───────────────────────────────
    public double getHeightOffset() {
        return config.getDouble("text-display.height-offset", 0.35);
    }

    public String getBillboard() {
        return config.getString("text-display.billboard", "CENTER");
    }

    public float getViewRange() {
        return (float) config.getDouble("text-display.view-range", 32.0);
    }

    public float getScale() {
        return (float) config.getDouble("text-display.scale", 1.0);
    }

    public boolean hasShadow() {
        return config.getBoolean("text-display.shadow", true);
    }

    public String getAlignment() {
        return config.getString("text-display.alignment", "CENTER");
    }

    public int getOpacity() {
        return config.getInt("text-display.opacity", 220);
    }

    public int getBackgroundColor() {
        return config.getInt("text-display.background-color", 0x40000000);
    }

    // ── Animation ─────────────────────────────────
    public boolean isFadeIn() {
        return config.getBoolean("animation.fade-in", true);
    }

    public boolean isFadeOut() {
        return config.getBoolean("animation.fade-out", true);
    }

    public int getFadeInTicks() {
        return config.getInt("animation.fade-in-ticks", 5);
    }

    public int getFadeOutTicks() {
        return config.getInt("animation.fade-out-ticks", 10);
    }

    // ── Filter ────────────────────────────────────
    public boolean isFilterEnabled() {
        return config.getBoolean("filter.enabled", false);
    }

    public String getFilterReplacement() {
        return config.getString("filter.replacement", "***");
    }

    public List<String> getFilterWords() {
        return config.getStringList("filter.words");
    }

    // ── Messages ──────────────────────────────────
    public String getPrefix() {
        return colorize(config.getString("messages.prefix", "&8[&aBubbleChat&8] "));
    }

    public String getReloadMessage() {
        return colorize(config.getString("messages.reload", "&aConfiguration reloaded!"));
    }

    public String getToggledOn() {
        return colorize(config.getString("messages.toggled-on", "&aChat bubbles enabled!"));
    }

    public String getToggledOff() {
        return colorize(config.getString("messages.toggled-off", "&cChat bubbles disabled!"));
    }

    public String getNoPermission() {
        return colorize(config.getString("messages.no-permission", "&cNo permission!"));
    }

    // ── Per-world ─────────────────────────────────

    /**
     * Check if BubbleChat is enabled in the given world.
     * World overrides take priority over global setting.
     */
    public boolean isEnabledInWorld(String worldName) {
        String path = "worlds." + worldName + ".enabled";
        if (config.contains(path)) {
            return config.getBoolean(path);
        }
        return true; // default: enabled
    }

    /**
     * Get proximity range for a specific world (falls back to global).
     */
    public double getProximityRangeForWorld(String worldName) {
        String path = "worlds." + worldName + ".proximity.range";
        if (config.contains(path)) {
            return config.getDouble(path);
        }
        return getProximityRange();
    }

    /**
     * Get cancel-original-chat setting for a specific world (falls back to global).
     */
    public boolean isCancelOriginalChatForWorld(String worldName) {
        String path = "worlds." + worldName + ".proximity.cancel-original-chat";
        if (config.contains(path)) {
            return config.getBoolean(path);
        }
        return isCancelOriginalChat();
    }

    /**
     * Get proximity enabled for a specific world (falls back to global).
     */
    public boolean isProximityEnabledForWorld(String worldName) {
        String path = "worlds." + worldName + ".proximity.enabled";
        if (config.contains(path)) {
            return config.getBoolean(path);
        }
        return isProximityEnabled();
    }

    public boolean isPerWorldPermissions() {
        return config.getBoolean("per-world-permissions", false);
    }

    public int getBubbleStackSize() {
        return config.getInt("bubble-stack-size", 3);
    }

    public double getBubbleStackSpacing() {
        return config.getDouble("bubble-stack-spacing", 0.3);
    }

    public boolean isDebug() {
        return config.getBoolean("debug", false);
    }

    // ── Utility ───────────────────────────────────
    private String colorize(String text) {
        return text.replace("&", "§");
    }
}