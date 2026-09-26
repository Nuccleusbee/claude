package dev.nuccleus.relicpvp.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerDataStore {

    public static final class Stats {
        public int level;
        public int kills;
        public int deaths;
        public int streak;
    }

    private final DataFile file;
    private final Map<UUID, Stats> cache = new HashMap<>();

    public PlayerDataStore(JavaPlugin plugin) {
        this.file = new DataFile(plugin, "players.yml");
        YamlConfiguration yaml = file.yaml();
        for (String key : yaml.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Stats s = new Stats();
            s.level = yaml.getInt(key + ".level");
            s.kills = yaml.getInt(key + ".kills");
            s.deaths = yaml.getInt(key + ".deaths");
            s.streak = yaml.getInt(key + ".streak");
            cache.put(id, s);
        }
    }

    public Stats get(UUID id) {
        return cache.computeIfAbsent(id, k -> new Stats());
    }

    public void save() {
        YamlConfiguration yaml = file.yaml();
        cache.forEach((id, s) -> {
            String k = id.toString();
            yaml.set(k + ".level", s.level);
            yaml.set(k + ".kills", s.kills);
            yaml.set(k + ".deaths", s.deaths);
            yaml.set(k + ".streak", s.streak);
        });
        file.save();
    }
}
