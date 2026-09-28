package fsftest;

import java.util.Map;
import java.util.TreeMap;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Silverfish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

// Test helper: acts like "another plugin" and reads state that commands can't show.
public final class FsfTest extends JavaPlugin implements Listener {

    private int infestedSpawns;
    private int campfireDeaths;
    private final Map<String, Integer> deaths = new TreeMap<>();
    private final Map<String, Integer> removals = new TreeMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Silverfish
                && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.POTION_EFFECT) {
            infestedSpawns++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Silverfish) {
            DamageType type = event.getDamageSource().getDamageType();
            if (type == DamageType.CAMPFIRE) {
                campfireDeaths++;
            }
            deaths.merge(type.getKey().getKey(), 1, Integer::sum);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        if (event.getEntity() instanceof Silverfish) {
            removals.merge(event.getCause().name(), 1, Integer::sum);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        String action = args[0];
        if (action.equals("stats")) {
            sender.sendMessage("infestedSpawns=" + infestedSpawns + " campfireDeaths=" + campfireDeaths
                    + " deathsByType=" + deaths + " removals=" + removals);
            return true;
        }
        if (action.equals("scan") && args.length == 7) {
            // Writes every block that isn't air or plain ground in a box to plugins/FsfTest/scan.txt.
            World world = getServer().getWorlds().get(0);
            int[] c = new int[6];
            for (int i = 0; i < 6; i++) {
                c[i] = Integer.parseInt(args[i + 1]);
            }
            StringBuilder out = new StringBuilder();
            for (int y = Math.min(c[1], c[4]); y <= Math.max(c[1], c[4]); y++) {
                for (int z = Math.min(c[2], c[5]); z <= Math.max(c[2], c[5]); z++) {
                    for (int x = Math.min(c[0], c[3]); x <= Math.max(c[0], c[3]); x++) {
                        org.bukkit.block.Block b = world.getBlockAt(x, y, z);
                        String type = b.getType().getKey().getKey();
                        if (!b.getType().isAir() && !type.equals("grass_block") && !type.equals("dirt")
                                && !type.equals("bedrock")) {
                            out.append(x).append(' ').append(y).append(' ').append(z).append(' ')
                               .append(b.getBlockData().getAsString()).append(System.lineSeparator());
                        }
                    }
                }
            }
            try {
                getDataFolder().mkdirs();
                java.nio.file.Files.writeString(getDataFolder().toPath().resolve("scan.txt"), out.toString());
            } catch (java.io.IOException e) {
                sender.sendMessage("scan failed: " + e.getMessage());
                return true;
            }
            sender.sendMessage("scan written");
            return true;
        }
        if (action.equals("resetstats")) {
            infestedSpawns = 0;
            campfireDeaths = 0;
            deaths.clear();
            removals.clear();
            sender.sendMessage("stats reset");
            return true;
        }

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
