package dev.nuccleus.relicpvp.zone;

import dev.nuccleus.relicpvp.RelicPvP;
import dev.nuccleus.relicpvp.util.Entities;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

public final class ZoneListener implements Listener {

    private final RelicPvP plugin;
    private final Map<UUID, Long> lastMessage = new HashMap<>();

    public ZoneListener(RelicPvP plugin) {
        this.plugin = plugin;
    }

    private int level(Player p) {
        return plugin.players().get(p.getUniqueId()).level;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ() && from.getWorld() == to.getWorld()) return;

        Player p = e.getPlayer();
        if (p.hasPermission(RelicPvP.BYPASS_ZONES)) return;
        int level = level(p);
        int required = plugin.zones().requiredLevel(to);
        if (required <= level) return;

        if (plugin.zones().requiredLevel(from) > level) {
            // Already inside somewhere they shouldn't be (e.g. level was reset): send them to spawn.
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> p.teleport(p.getWorld().getSpawnLocation()));
        } else {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            e.setTo(back);
            Vector push = from.toVector().subtract(to.toVector()).setY(0);
            if (push.lengthSquared() > 0) p.setVelocity(push.normalize().multiply(0.6).setY(0.25));
        }
        deny(p, required);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission(RelicPvP.BYPASS_ZONES)) return;
        int required = plugin.zones().requiredLevel(e.getTo());
        if (required > level(p)) {
            e.setCancelled(true);
            deny(p, required);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = Entities.playerSource(e.getDamager());
        if (attacker == null || attacker == victim) return;
        if (plugin.zones().pvpAllowed(victim.getLocation()) && plugin.zones().pvpAllowed(attacker.getLocation())) return;
        e.setCancelled(true);
        if (cooldownOk(attacker)) plugin.msg(attacker, "<red>PvP is disabled here.");
    }

    // Block generators handle their own breaks at LOWEST and cancel the event, so ignoreCancelled skips them.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    private boolean canBuild(Player p, Location l) {
        if (p.hasPermission(RelicPvP.BYPASS_BUILD) || plugin.zones().buildAllowed(l)) return true;
        if (cooldownOk(p)) plugin.msg(p, "<red>You can't build here.");
        return false;
    }

    private void deny(Player p, int required) {
        if (!cooldownOk(p)) return;
        plugin.msg(p, "<red>This area needs <gold>Level " + required + "</gold>. Beat a boss for the key!");
        p.playSound(p.getLocation(), "block.note_block.bass", 1f, 0.5f);
    }

    private boolean cooldownOk(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(p.getUniqueId());
        if (last != null && now - last < 1500) return false;
        lastMessage.put(p.getUniqueId(), now);
        return true;
    }
}
