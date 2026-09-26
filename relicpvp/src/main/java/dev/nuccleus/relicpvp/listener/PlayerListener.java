package dev.nuccleus.relicpvp.listener;

import dev.nuccleus.relicpvp.RelicPvP;
import dev.nuccleus.relicpvp.data.PlayerDataStore.Stats;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.World;

/** Kills, deaths and kill streaks. */
public final class PlayerListener implements Listener {

    private final RelicPvP plugin;

    public PlayerListener(RelicPvP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Stats v = plugin.players().get(victim.getUniqueId());
        v.deaths++;
        int endedStreak = v.streak;
        v.streak = 0;

        Player killer = victim.getKiller();
        if (killer == null || killer == victim) return;
        Stats k = plugin.players().get(killer.getUniqueId());
        k.kills++;
        k.streak++;
        if (k.streak % 5 == 0) {
            plugin.broadcast("<red><bold>" + killer.getName() + "</bold> is on a <gold>" + k.streak + "</gold> kill streak!");
        }
        if (endedStreak >= 5) {
            plugin.broadcast("<yellow>" + killer.getName() + " ended " + victim.getName() + "'s " + endedStreak + " kill streak!");
        }
    }

    /** Players who aren't in the RelicPvP world get sent to its spawn. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        World main = plugin.mainWorld();
        Player p = e.getPlayer();
        if (main != null && p.getWorld() != main) p.teleport(main.getSpawnLocation());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        World main = plugin.mainWorld();
        if (main != null && !e.isBedSpawn() && !e.isAnchorSpawn()) e.setRespawnLocation(main.getSpawnLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.players().save();
    }
}
