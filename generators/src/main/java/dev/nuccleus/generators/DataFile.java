package dev.nuccleus.generators;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** A YAML file in the plugin folder for things placed in-game (not hand-edited config). */
public final class DataFile {

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public DataFile(JavaPlugin plugin, String name) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), name);
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public YamlConfiguration yaml() {
        return yaml;
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + file.getName(), e);
        }
    }
}
