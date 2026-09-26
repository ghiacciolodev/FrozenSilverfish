package fsftest;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Silverfish;
import org.bukkit.plugin.java.JavaPlugin;

// Test helper: acts like "another plugin" and reads state that commands can't show.
public final class FsfTest extends JavaPlugin {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        String action = args[0];
        String tag = args[args.length - 1];
        NamespacedKey key = NamespacedKey.fromString("frozensilverfish:collisions_off");
        StringBuilder out = new StringBuilder();
        for (World world : getServer().getWorlds()) {
            for (Silverfish s : world.getEntitiesByClass(Silverfish.class)) {
                if (!s.getScoreboardTags().contains(tag)) continue;
                switch (action) {
                    case "aware" -> s.setAware(Boolean.parseBoolean(args[1]));
                    case "collide" -> s.setCollidable(Boolean.parseBoolean(args[1]));
                    default -> { }
                }
                out.append("aware=").append(s.isAware())
                   .append(" collidable=").append(s.isCollidable())
                   .append(" frozenTag=").append(s.getScoreboardTags().contains("frozen_silverfish"))
                   .append(" marker=").append(s.getPersistentDataContainer().has(key))
                   .append("; ");
            }
        }
        sender.sendMessage(out.length() == 0 ? "none" : out.toString());
        return true;
    }
}
