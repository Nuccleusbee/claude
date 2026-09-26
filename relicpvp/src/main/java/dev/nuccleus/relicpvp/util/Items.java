package dev.nuccleus.relicpvp.util;

import dev.nuccleus.relicpvp.RelicPvP;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Builds items from config references: a vanilla material, a custom item id, or "key:<tier>". */
public final class Items {

    private final RelicPvP plugin;
    private final NamespacedKey keyTier;
    private final NamespacedKey kitTag;
    private final NamespacedKey customId;
    private final Map<String, ConfigurationSection> defs = new HashMap<>();

    public Items(RelicPvP plugin) {
        this.plugin = plugin;
        this.keyTier = new NamespacedKey(plugin, "key_tier");
        this.kitTag = new NamespacedKey(plugin, "kit_item");
        this.customId = new NamespacedKey(plugin, "item_id");
    }

    public void load(ConfigurationSection section) {
        defs.clear();
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection def = section.getConfigurationSection(id);
            if (def != null) defs.put(id.toLowerCase(Locale.ROOT), def);
        }
    }

    public Set<String> customIds() {
        return defs.keySet();
    }

    /** One stack of the item, amount capped at its max stack size. Null if the reference is unknown. */
    public ItemStack create(String ref, int amount) {
        if (ref == null) return null;
        String id = ref.toLowerCase(Locale.ROOT);
        ItemStack stack;
        if (id.startsWith("key:")) {
            try {
                stack = key(Integer.parseInt(id.substring(4)));
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (defs.containsKey(id)) {
            stack = build(id, defs.get(id));
        } else {
            Material material = Material.matchMaterial(ref);
            if (material == null || material.isAir() || !material.isItem()) return null;
            stack = new ItemStack(material);
        }
        stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
        return stack;
    }

    /** As many stacks as needed to hold {@code amount}. Empty if the reference is unknown. */
    public List<ItemStack> createStacks(String ref, int amount) {
        List<ItemStack> out = new ArrayList<>();
        ItemStack proto = create(ref, 1);
        if (proto == null) return out;
        int left = Math.max(1, amount);
        while (left > 0) {
            ItemStack stack = proto.clone();
            int n = Math.min(left, stack.getMaxStackSize());
            stack.setAmount(n);
            out.add(stack);
            left -= n;
        }
        return out;
    }

    private ItemStack build(String id, ConfigurationSection def) {
        Material material = Material.matchMaterial(def.getString("material", "STONE"));
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("Item '" + id + "' has an unknown material, using STONE");
            material = Material.STONE;
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        String name = def.getString("name");
        if (name != null) meta.displayName(Text.item(name));
        List<String> lore = def.getStringList("lore");
        if (!lore.isEmpty()) meta.lore(lore.stream().map(Text::item).toList());

        ConfigurationSection enchants = def.getConfigurationSection("enchants");
        if (enchants != null) {
            for (String key : enchants.getKeys(false)) {
                Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
                if (ench == null) {
                    plugin.getLogger().warning("Item '" + id + "' has unknown enchant '" + key + "'");
                    continue;
                }
                meta.addEnchant(ench, enchants.getInt(key, 1), true);
            }
        }
        if (def.getBoolean("unbreakable")) meta.setUnbreakable(true);
        if (def.getBoolean("glow")) meta.setEnchantmentGlintOverride(true);
        if (def.contains("custom-model-data")) meta.setCustomModelData(def.getInt("custom-model-data"));

        meta.getPersistentDataContainer().set(customId, PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack key(int tier) {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("keys");
        Material material = cfg == null ? null : Material.matchMaterial(cfg.getString("material", "TRIPWIRE_HOOK"));
        if (material == null || !material.isItem()) material = Material.TRIPWIRE_HOOK;
        String name = cfg == null ? "<gold><bold>Level {tier} Key" : cfg.getString("name", "<gold><bold>Level {tier} Key");
        List<String> lore = cfg == null ? List.of() : cfg.getStringList("lore");

        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        String t = String.valueOf(tier);
        meta.displayName(Text.item(name.replace("{tier}", t)));
        meta.lore(lore.stream().map(l -> Text.item(l.replace("{tier}", t))).toList());
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyTier, PersistentDataType.INTEGER, tier);
        stack.setItemMeta(meta);
        return stack;
    }

    /** The key tier of this item, or 0 if it isn't a key. */
    public int keyTier(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return 0;
        Integer tier = stack.getItemMeta().getPersistentDataContainer().get(keyTier, PersistentDataType.INTEGER);
        return tier == null ? 0 : tier;
    }

    public void markKit(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(kitTag, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
    }

    public boolean isKit(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(kitTag, PersistentDataType.BYTE);
    }

    /** Puts the item in the player's inventory, dropping whatever doesn't fit at their feet. */
    public void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }
}
