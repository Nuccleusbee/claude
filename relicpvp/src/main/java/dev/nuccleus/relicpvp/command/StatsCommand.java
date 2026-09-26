package dev.nuccleus.relicpvp.command;

import dev.nuccleus.relicpvp.RelicPvP;
import dev.nuccleus.relicpvp.data.PlayerDataStore.Stats;
import dev.nuccleus.relicpvp.util.Text;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class StatsCommand implements TabExecutor {

    private final RelicPvP plugin;

    public StatsCommand(RelicPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        OfflinePlayer target;
        if (args.length > 0) {
            target = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (target == null) {
                plugin.msg(sender, "<red>Never seen a player called " + Text.esc(args[0]));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            plugin.msg(sender, "<red>Usage: /stats <player>");
            return true;
        }

        Stats s = plugin.players().get(target.getUniqueId());
        String kdr = s.deaths == 0 ? String.valueOf(s.kills) : String.format(Locale.ROOT, "%.2f", (double) s.kills / s.deaths);
        plugin.msg(sender, "<gold><bold>" + target.getName());
        sender.sendMessage(Text.mm("<gray>Level: <gold>" + s.level));
        sender.sendMessage(Text.mm("<gray>Kills: <green>" + s.kills + " <gray>Deaths: <red>" + s.deaths + " <gray>K/D: <white>" + kdr));
        sender.sendMessage(Text.mm("<gray>Kill streak: <yellow>" + s.streak));
        String nextBoss = plugin.bosses().bossForKey(s.level + 1);
        if (nextBoss != null) {
            sender.sendMessage(Text.mm("<gray>Next: defeat " + nextBoss + " <gray>for the <gold>Level " + (s.level + 1) + " Key"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
