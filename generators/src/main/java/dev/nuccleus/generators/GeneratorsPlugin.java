package dev.nuccleus.generators;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class GeneratorsPlugin extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private DataFile data;
    private BlockGeneratorManager blocks;
    private DropGeneratorManager drops;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = new DataFile(this, "data.yml");
        blocks = new BlockGeneratorManager(this);
        drops = new DropGeneratorManager(this);
        blocks.loadData();
        drops.loadData();

        Bukkit.getPluginManager().registerEvents(blocks, this);
        drops.start();

        PluginCommand command = getCommand("generator");
        if (command != null) {
            GeneratorCommand executor = new GeneratorCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (blocks != null) blocks.shutdown();
        if (data != null) data.save();
    }

    public void msg(CommandSender to, String miniMessage) {
        to.sendMessage(MM.deserialize(getConfig().getString("prefix", "") + miniMessage));
    }

    public static String esc(String raw) {
        return MM.escapeTags(raw);
    }

    public DataFile data() { return data; }
    public BlockGeneratorManager blocks() { return blocks; }
    public DropGeneratorManager drops() { return drops; }
}
