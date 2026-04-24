package dev.bubblechat.managers;

import dev.bubblechat.BubbleChatPlugin;
import dev.bubblechat.utils.FoliaUtil;
import dev.bubblechat.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BubbleManager {

    private final BubbleChatPlugin plugin;
    private final ConfigManager config;

    private record BubbleEntry(TextDisplay display, UUID playerUUID, int slot) {}

    // player UUID -> stack of bubbles (index 0 = ล่างสุด/ใหม่สุด)
    private final Map<UUID, Deque<BubbleEntry>> bubbleStacks = new ConcurrentHashMap<>();

    // entity id -> Y extra offset สำหรับ follow task
    private final Map<Integer, Double> bubbleExtraOffset = new ConcurrentHashMap<>();

    // entity id -> follow task (Paper/Spigot only)
    private final Map<Integer, org.bukkit.scheduler.BukkitTask> followTasks = new ConcurrentHashMap<>();

    // players who toggled bubbles off
    private final Set<UUID> disabledPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public BubbleManager(BubbleChatPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    // ─────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────

    public void showBubble(Player player, String message) {
        if (!player.isOnline()) return;
        if (disabledPlayers.contains(player.getUniqueId())) return;

        if (config.isFilterEnabled()) {
            message = TextUtil.filterMessage(message, config.getFilterWords(), config.getFilterReplacement());
        }

        final String processed = TextUtil.applyPlaceholders(player, message, plugin.isPlaceholderApiEnabled());
        final Component bubbleComponent = buildBubbleComponent(player, processed);
        final UUID uuid = player.getUniqueId();

        // ── Step 1: ดัน offset ของ bubble เก่าขึ้น ──
        Deque<BubbleEntry> stack = bubbleStacks.computeIfAbsent(uuid, k -> new ArrayDeque<>());

        // ถ้า stack เต็ม ให้ลบ bubble เก่าสุดออก (อันบนสุด)
        if (stack.size() >= config.getBubbleStackSize()) {
            BubbleEntry oldest = stack.peekLast();
            if (oldest != null) {
                stack.pollLast();
                removeSingleBubble(oldest.display());
            }
        }

        // เลื่อน offset ของทุก bubble ที่มีอยู่ขึ้น 1 slot
        for (BubbleEntry entry : stack) {
            double newOffset = bubbleExtraOffset.getOrDefault(entry.display().getEntityId(), 0.0) + config.getBubbleStackSpacing();
            bubbleExtraOffset.put(entry.display().getEntityId(), newOffset);
        }

        // ── Step 2: Spawn bubble ใหม่ที่ slot 0 (ตำแหน่งปกติ) ──
        final Location spawnLoc = player.getLocation().add(0, getDisplayHeight(player), 0);

        FoliaUtil.runAtLocation(plugin, spawnLoc, () -> {
            if (!player.isOnline()) return;

            TextDisplay display = spawnDisplay(spawnLoc, bubbleComponent);
            if (display == null) return;

            BubbleEntry entry = new BubbleEntry(display, uuid, 0);
            bubbleExtraOffset.put(display.getEntityId(), 0.0);

            // เพิ่มเข้า stack ที่ตำแหน่งหน้าสุด (ล่างสุด = ใหม่สุด)
            stack.addFirst(entry);

            plugin.debug("Spawned bubble [" + display.getEntityId() + "] stack size=" + stack.size());

            scheduleFollowTask(player, display);
            scheduleRemoval(player, display, stack);
        });
    }

    public void removePlayerBubbles(Player player) {
        Deque<BubbleEntry> stack = bubbleStacks.remove(player.getUniqueId());
        if (stack == null) return;
        for (BubbleEntry entry : stack) {
            removeSingleBubble(entry.display());
        }
    }

    public void removeAllBubbles() {
        bubbleStacks.forEach((uuid, stack) -> {
            for (BubbleEntry entry : stack) {
                safeRemoveDisplay(entry.display());
            }
        });
        bubbleStacks.clear();
        bubbleExtraOffset.clear();
        followTasks.values().forEach(org.bukkit.scheduler.BukkitTask::cancel);
        followTasks.clear();
    }

    public List<Player> getNearbyPlayers(Player speaker, double range) {
        double rangeSquared = range * range;
        return speaker.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distanceSquared(speaker.getLocation()) <= rangeSquared)
                .toList();
    }

    public List<Player> getNearbyPlayers(Player speaker) {
        return getNearbyPlayers(speaker, config.getProximityRange());
    }

    public boolean isToggled(Player player) {
        return !disabledPlayers.contains(player.getUniqueId());
    }

    public void setToggled(Player player, boolean enabled) {
        if (enabled) {
            disabledPlayers.remove(player.getUniqueId());
        } else {
            disabledPlayers.add(player.getUniqueId());
            removePlayerBubbles(player);
        }
    }

    public int getActiveBubbleCount() {
        return bubbleStacks.values().stream().mapToInt(Deque::size).sum();
    }

    // ─────────────────────────────────────────────────────────
    // Spawn
    // ─────────────────────────────────────────────────────────

    private TextDisplay spawnDisplay(Location location, Component text) {
        return location.getWorld().spawn(location, TextDisplay.class, display -> {
            display.text(text);
            display.setPersistent(false);
            display.setDefaultBackground(false);

            try {
                display.setBillboard(Display.Billboard.valueOf(config.getBillboard().toUpperCase()));
            } catch (IllegalArgumentException e) {
                display.setBillboard(Display.Billboard.CENTER);
            }

            display.setViewRange(config.getViewRange());
            display.setShadowed(config.hasShadow());
            display.setTextOpacity((byte) Math.min(127, config.getOpacity()));

            int bgColor = config.getBackgroundColor();
            if (bgColor == 0) {
                display.setDefaultBackground(true);
            } else {
                display.setBackgroundColor(Color.fromARGB(bgColor));
            }

            try {
                display.setAlignment(TextDisplay.TextAlignment.valueOf(config.getAlignment().toUpperCase()));
            } catch (IllegalArgumentException e) {
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
            }

            float scale = config.getScale();
            display.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0, 0, 0, 1)
            ));

            display.setTeleportDuration(config.getFollowInterpolationTicks());

            if (config.isFadeIn()) {
                display.setInterpolationDuration(config.getFadeInTicks());
                display.setInterpolationDelay(-1);
            }
        });
    }

    // ─────────────────────────────────────────────────────────
    // Follow Task
    // ─────────────────────────────────────────────────────────

    private void scheduleFollowTask(Player player, TextDisplay display) {
        if (FoliaUtil.isFolia()) {
            foliaFollowTask(player, display);
        } else {
            bukkitFollowTask(player, display);
        }
    }

    private void foliaFollowTask(Player player, TextDisplay display) {
        player.getScheduler().runAtFixedRate(plugin, task -> {
            if (!display.isValid() || !player.isOnline()) {
                task.cancel();
                return;
            }

            double extraY = bubbleExtraOffset.getOrDefault(display.getEntityId(), 0.0);
            Location target = player.getLocation().add(0, getDisplayHeight(player) + extraY, 0);
            display.teleportAsync(target);

        }, null, 1L, 1L);
    }

    private void bukkitFollowTask(Player player, TextDisplay display) {
        org.bukkit.scheduler.BukkitTask task = org.bukkit.Bukkit.getScheduler()
                .runTaskTimer(plugin, () -> {
                    if (!display.isValid() || !player.isOnline()) return;

                    double extraY = bubbleExtraOffset.getOrDefault(display.getEntityId(), 0.0);
                    Location target = player.getLocation().add(0, getDisplayHeight(player) + extraY, 0);
                    display.teleport(target);
                }, 1L, 1L);

        followTasks.put(display.getEntityId(), task);
    }

    // ─────────────────────────────────────────────────────────
    // Removal & Lifecycle
    // ─────────────────────────────────────────────────────────

    private void scheduleRemoval(Player player, TextDisplay display, Deque<BubbleEntry> stack) {
        int duration = config.getBubbleDuration();
        int fadeOutTicks = config.isFadeOut() ? config.getFadeOutTicks() : 0;
        int fadeStartTick = Math.max(1, duration - fadeOutTicks);

        if (config.isFadeOut() && fadeOutTicks > 0) {
            FoliaUtil.runAtEntityDelayed(plugin, display, () -> startFadeOut(display), fadeStartTick);
        }

        FoliaUtil.runAtEntityDelayed(plugin, display, () -> {
            stack.removeIf(e -> e.display().getEntityId() == display.getEntityId());
            bubbleExtraOffset.remove(display.getEntityId());
            cancelFollowTask(display);
            safeRemoveDisplay(display);

            if (stack.isEmpty()) {
                bubbleStacks.remove(player.getUniqueId());
            }
        }, duration);
    }

    private void startFadeOut(TextDisplay display) {
        if (!display.isValid()) return;
        display.setInterpolationDuration(config.getFadeOutTicks());
        display.setTextOpacity((byte) 0);
        display.setInterpolationDelay(-1);
    }

    private void removeSingleBubble(TextDisplay display) {
        bubbleExtraOffset.remove(display.getEntityId());
        cancelFollowTask(display);
        FoliaUtil.runAtEntity(plugin, display, () -> safeRemoveDisplay(display));
    }

    private void safeRemoveDisplay(TextDisplay display) {
        try {
            if (display.isValid()) display.remove();
        } catch (Exception ignored) {}
    }

    private void cancelFollowTask(TextDisplay display) {
        org.bukkit.scheduler.BukkitTask task = followTasks.remove(display.getEntityId());
        if (task != null) task.cancel();
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private Component buildBubbleComponent(Player player, String message) {
        List<String> wrappedLines = TextUtil.wrapText(message, config.getMaxLineLength());
        int maxLines = config.getMaxLines();
        List<String> lines = new ArrayList<>(Math.min(wrappedLines.size(), maxLines) + 1);

        if (config.isShowName()) {
            lines.add(TextUtil.format(config.getNameFormat(), player.getName(), message));
        }

        wrappedLines.stream()
                .limit(maxLines)
                .map(line -> TextUtil.format(config.getBubbleFormat(), player.getName(), line))
                .forEach(lines::add);

        return TextUtil.buildMultilineComponent(lines);
    }

    private double getDisplayHeight(Player player) {
        return player.getHeight() + config.getHeightOffset();
    }
}