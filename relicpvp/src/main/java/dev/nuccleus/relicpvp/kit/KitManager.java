package dev.nuccleus.relicpvp.kit;

import dev.nuccleus.relicpvp.RelicPvP;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** The starter kit: given on first join and every respawn. */
public final class KitManager implements Listener {

    private final RelicPvP plugin;
    private final List<String> lines = new ArrayList<>();
    /** Players who died with keepInventory on — they already have their stuff. */
    private final Set<UUID> keptInventory = new HashSet<>();

    public KitManager(RelicPvP plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        lines.clear();
        lines.addAll(plugin.getConfig().getStringList("kit.items"));
    }

    public void give(Player player) {
        PlayerInventory inv = player.getInventory();
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 0 || parts[0].isEmpty()) continue;
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    // keep 1
                }
            }
            List<ItemStack> stacks = plugin.items().createStacks(parts[0], amount);
            if (stacks.isEmpty()) {
                plugin.getLogger().warning("Kit item '" + parts[0] + "' is unknown");
                continue;
            }
            for (ItemStack stack : stacks) {
                plugin.items().markKit(stack);
                if (!equip(inv, stack)) plugin.items().give(player, stack);
            }
        }
    }

    private static boolean equip(PlayerInventory inv, ItemStack stack) {
        EquipmentSlot slot = stack.getType().getEquipmentSlot();
        switch (slot) {
            case HEAD -> { if (empty(inv.getHelmet())) { inv.setHelmet(stack); return true; } }
            case CHEST -> { if (empty(inv.getChestplate())) { inv.setChestplate(stack); return true; } }
            case LEGS -> { if (empty(inv.getLeggings())) { inv.setLeggings(stack); return true; } }
            case FEET -> { if (empty(inv.getBoots())) { inv.setBoots(stack); return true; } }
            default -> { }
        }
        return false;
    }

    private static boolean empty(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!e.getPlayer().hasPlayedBefore() && plugin.getConfig().getBoolean("kit.give-on-first-join", true)) {
            give(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        if (e.getKeepInventory()) {
            keptInventory.add(e.getEntity().getUniqueId());
            return;
        }
        if (plugin.getConfig().getBoolean("kit.remove-kit-items-on-death", true)) {
            e.getDrops().removeIf(plugin.items()::isKit);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        if (keptInventory.remove(player.getUniqueId())) return;
        if (!plugin.getConfig().getBoolean("kit.give-on-respawn", true)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) give(player);
        });
    }
}
