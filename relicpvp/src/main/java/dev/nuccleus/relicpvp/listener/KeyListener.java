package dev.nuccleus.relicpvp.listener;

import dev.nuccleus.relicpvp.RelicPvP;
import dev.nuccleus.relicpvp.data.PlayerDataStore.Stats;
import dev.nuccleus.relicpvp.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Right-clicking a level key unlocks that level. */
public final class KeyListener implements Listener {

    private final RelicPvP plugin;

    public KeyListener(RelicPvP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack hand = e.getItem();
        int tier = plugin.items().keyTier(hand);
        if (tier <= 0) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        Stats stats = plugin.players().get(p.getUniqueId());
        if (stats.level >= tier) {
            plugin.msg(p, "<yellow>You already have Level " + tier + ". Trade this key or save it!");
            return;
        }
        if (plugin.getConfig().getBoolean("keys.require-previous-level", true) && stats.level < tier - 1) {
            plugin.msg(p, "<red>You need <gold>Level " + (tier - 1) + "</gold> before this key works.");
            return;
        }

        stats.level = tier;
        hand.setAmount(hand.getAmount() - 1);
        p.showTitle(Title.title(Text.mm("<gold><bold>LEVEL " + tier), Text.mm("<yellow>New areas unlocked")));
        p.playSound(p.getLocation(), "ui.toast.challenge_complete", 1f, 1f);
        plugin.broadcast("<yellow>" + p.getName() + "</yellow> <gray>unlocked <gold>Level " + tier + "</gold>!");
        plugin.players().save();
    }
}
