package dev.nuccleus.generators;

import java.util.List;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** The generator wand: left-click a block for corner 1, right-click for corner 2 (like WorldEdit). */
public final class WandListener implements Listener {

    private final GeneratorsPlugin plugin;
    private final NamespacedKey wandKey;

    public WandListener(GeneratorsPlugin plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "wand");
    }

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        MiniMessage mm = MiniMessage.miniMessage();
        meta.displayName(mm.deserialize("<gold><bold>Generator Wand").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                mm.deserialize("<gray>Left-click: corner 1").decoration(TextDecoration.ITALIC, false),
                mm.deserialize("<gray>Right-click: corner 2").decoration(TextDecoration.ITALIC, false),
                mm.deserialize("<yellow>Then /generator").decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    private boolean isWand(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !isWand(e.getItem())) return;
        Action action = e.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("generators.admin")) return;
        e.setCancelled(true);

        Block block = e.getClickedBlock();
        if (block == null) return;
        boolean first = action == Action.LEFT_CLICK_BLOCK;
        plugin.selection().set(p.getUniqueId(), first, block);
        String size = plugin.selection().complete(p.getUniqueId())
                ? " <gray>(" + plugin.selection().volume(p.getUniqueId()) + " blocks selected)" : "";
        plugin.msg(p, "<green>" + (first ? "Corner 1" : "Corner 2") + " set to " + block.getX() + ", " + block.getY() + ", " + block.getZ() + "." + size);
    }

    /** Creative players would otherwise break the block they left-click. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        if (isWand(e.getPlayer().getInventory().getItemInMainHand()) && e.getPlayer().hasPermission("generators.admin")) {
            e.setCancelled(true);
        }
    }
}
