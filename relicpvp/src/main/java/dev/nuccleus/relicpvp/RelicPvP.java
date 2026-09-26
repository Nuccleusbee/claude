package dev.nuccleus.relicpvp;

import dev.nuccleus.relicpvp.boss.BossManager;
import dev.nuccleus.relicpvp.command.GeneratorCommand;
import dev.nuccleus.relicpvp.command.RelicCommand;
import dev.nuccleus.relicpvp.command.StatsCommand;
import dev.nuccleus.relicpvp.data.DataFile;
import dev.nuccleus.relicpvp.data.PlayerDataStore;
import dev.nuccleus.relicpvp.generator.GeneratorManager;
import dev.nuccleus.relicpvp.kit.KitManager;
import dev.nuccleus.relicpvp.listener.KeyListener;
import dev.nuccleus.relicpvp.listener.PlayerListener;
import dev.nuccleus.relicpvp.mine.BlockGeneratorManager;
import dev.nuccleus.relicpvp.rare.RareSpawnManager;
import dev.nuccleus.relicpvp.util.Items;
import dev.nuccleus.relicpvp.util.Text;
import dev.nuccleus.relicpvp.zone.ZoneListener;
import dev.nuccleus.relicpvp.zone.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;

public final class RelicPvP extends JavaPlugin {

    public static final String BYPASS_ZONES = "relicpvp.bypass.zones";
    public static final String BYPASS_BUILD = "relicpvp.bypass.build";

    private DataFile data;
    private PlayerDataStore players;
    private Items items;
    private ZoneManager zones;
    private GeneratorManager generators;
    private BlockGeneratorManager blockGenerators;
    private BossManager bosses;
    private RareSpawnManager rares;
    private KitManager kits;
    private String prefix = "";

    @Override
    public void onEnable() {
        saveDefaultConfig();

        data = new DataFile(this, "data.yml");
        players = new PlayerDataStore(this);
        items = new Items(this);
        zones = new ZoneManager(this);
        generators = new GeneratorManager(this);
        blockGenerators = new BlockGeneratorManager(this);
        bosses = new BossManager(this);
        rares = new RareSpawnManager(this);
        kits = new KitManager(this);

        reloadSettings();
        zones.loadData();
        generators.loadData();
        blockGenerators.loadData();
        rares.loadData();

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new ZoneListener(this), this);
        pm.registerEvents(new KeyListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(kits, this);
        pm.registerEvents(bosses, this);
        pm.registerEvents(rares, this);
        pm.registerEvents(blockGenerators, this);

        bind("relic", new RelicCommand(this));
        bind("stats", new StatsCommand(this));
        bind("generator", new GeneratorCommand(this));

        generators.start();
        bosses.start();
        rares.start();

        // Autosave player stats every 5 minutes.
        Bukkit.getScheduler().runTaskTimer(this, players::save, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        if (bosses != null) bosses.shutdown();
        if (rares != null) rares.shutdown();
        if (blockGenerators != null) blockGenerators.shutdown();
        if (players != null) players.save();
        if (data != null) data.save();
    }

    /** Re-reads config.yml. Placed things (generators, zones, spawns) live in data.yml and are kept. */
    public void reloadSettings() {
        reloadConfig();
        prefix = getConfig().getString("prefix", "");
        items.load(getConfig().getConfigurationSection("items"));
        kits.loadConfig();
        bosses.loadConfig();
        rares.loadConfig();
    }

    private void bind(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command /" + name + " missing from plugin.yml");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public void msg(CommandSender to, String miniMessage) {
        to.sendMessage(Text.mm(prefix + miniMessage));
    }

    public void broadcast(String miniMessage) {
        Bukkit.broadcast(Text.mm(prefix + miniMessage));
    }

    public DataFile data() { return data; }
    public PlayerDataStore players() { return players; }
    public Items items() { return items; }
    public ZoneManager zones() { return zones; }
    public GeneratorManager generators() { return generators; }
    public BlockGeneratorManager blockGenerators() { return blockGenerators; }
    public BossManager bosses() { return bosses; }
    public RareSpawnManager rares() { return rares; }
    public KitManager kits() { return kits; }
}
