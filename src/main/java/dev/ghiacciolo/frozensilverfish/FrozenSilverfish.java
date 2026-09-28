package dev.ghiacciolo.frozensilverfish;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Silverfish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class FrozenSilverfish extends JavaPlugin implements Listener, TabExecutor {

    private static final String TAG = "frozen_silverfish";

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    // Marks silverfish whose collisions we turned off, so we only restore those.
    private final NamespacedKey collisionsKey = new NamespacedKey(this, "collisions_off");

    private boolean enabled;
    private boolean disableCollisions;
    private boolean preventDrowning;
    private boolean pushOffCampfires;
    private Set<String> worlds = Set.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String warning : loadSettings()) {
            getLogger().warning(warning);
        }
        getLogger().info(describeSettings());

        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand command = getCommand("frozensilverfish");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        int updated = applyToLoaded();
        if (updated > 0) {
            getLogger().info("Updated " + updated + " loaded silverfish.");
        }
    }

    /** Loads the config and returns warnings about it, if any. */
    private List<String> loadSettings() {
        reloadConfig();
        enabled = getConfig().getBoolean("enabled", true);
        disableCollisions = getConfig().getBoolean("disable-collisions", true);
        preventDrowning = getConfig().getBoolean("prevent-drowning", true);
        pushOffCampfires = getConfig().getBoolean("push-off-campfires", true);
        worlds = new HashSet<>(getConfig().getStringList("worlds"));

        // A typo here makes the plugin inactive everywhere without any error,
        // so point it out. Only a warning, because the world may load later.
        List<String> warnings = new ArrayList<>();
        for (String name : worlds) {
            if (getServer().getWorld(name) == null) {
                warnings.add("World '" + name + "' in the worlds list is not loaded. "
                        + "Check the name, it is case sensitive.");
            }
        }
        return warnings;
    }

    private String describeSettings() {
        if (!enabled) {
            return "Disabled in the config.";
        }
        return "Active in " + (worlds.isEmpty() ? "all worlds" : String.join(", ", worlds))
                + ", collisions disabled: " + (disableCollisions ? "yes" : "no")
                + ", drowning prevented: " + (preventDrowning ? "yes" : "no")
                + ", campfire push: " + (pushOffCampfires ? "yes" : "no") + ".";
    }

    // Fires for new spawns and for entities loaded from chunks.
    @EventHandler
    public void onEntityAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Silverfish silverfish) {
            apply(silverfish);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        DamageType type = event.getDamageSource().getDamageType();
        if (type != DamageType.DROWN && type != DamageType.CAMPFIRE) {
            return;
        }
        if (!(event.getEntity() instanceof Silverfish silverfish)
                || silverfish.isAware()
                || !silverfish.getScoreboardTags().contains(TAG)) {
            return;
        }

        if (type == DamageType.DROWN) {
            // Swimming up is an AI goal, so frozen silverfish sink and would drown
            // in the water streams before reaching the killing chamber.
            if (preventDrowning) {
                event.setCancelled(true);
            }
        } else if (pushOffCampfires) {
            pushOffCampfire(silverfish);
        }
    }

    /**
     * Without AI a silverfish that lands on a campfire never walks off and burns
     * there. The campfire hurts it about twice a second, and every hit pushes it
     * towards a random open side until it falls off. This only runs for
     * silverfish that are actually burning, so it costs nothing for the others.
     */
    private void pushOffCampfire(Silverfish silverfish) {
        Block block = silverfish.getLocation().getBlock();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Pick a random side that isn't a wall. Other campfires count as open,
        // since they are low and the silverfish slides over them.
        BlockFace side = null;
        int open = 0;
        for (BlockFace face : SIDES) {
            Block next = block.getRelative(face);
            if ((next.isPassable() || isCampfire(next.getType())) && random.nextInt(++open) == 0) {
                side = face;
            }
        }

        Vector push;
        if (side != null) {
            push = side.getDirection();
        } else {
            double angle = random.nextDouble(Math.PI * 2);
            push = new Vector(Math.cos(angle), 0, Math.sin(angle));
        }
        silverfish.setVelocity(push.multiply(0.3).setY(0.2));
    }

    private static boolean isCampfire(Material type) {
        return type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE;
    }

    private int applyToLoaded() {
        int updated = 0;
        for (World world : getServer().getWorlds()) {
            for (Silverfish silverfish : world.getEntitiesByClass(Silverfish.class)) {
                if (apply(silverfish)) {
                    updated++;
                }
            }
        }
        return updated;
    }

    private boolean isActiveIn(World world) {
        return enabled && (worlds.isEmpty() || worlds.contains(world.getName()));
    }

    /** Freezes or restores a silverfish. Returns true if anything changed. */
    private boolean apply(Silverfish silverfish) {
        boolean active = isActiveIn(silverfish.getWorld());
        boolean aiChanged = updateAi(silverfish, active);
        boolean frozenByUs = silverfish.getScoreboardTags().contains(TAG);
        boolean collisionsChanged = updateCollisions(silverfish, frozenByUs && disableCollisions);
        return aiChanged || collisionsChanged;
    }

    private boolean updateAi(Silverfish silverfish, boolean freeze) {
        if (freeze) {
            // Already unaware means it's either frozen by us already, or another
            // plugin turned its AI off. In that case we don't tag it, so we never
            // turn its AI back on later.
            if (!silverfish.isAware()) {
                return false;
            }
            // setAware(false) only stops the AI goals. setAI(false) would also stop
            // gravity and movement, leaving the mob floating and breaking the water
            // transport. With setAware the mob still falls, gets pushed by water
            // and takes knockback.
            silverfish.setAware(false);
            silverfish.addScoreboardTag(TAG);
            return true;
        }

        // Aware is saved with the entity, so silverfish we froze earlier stay
        // frozen until we restore them here.
        if (!silverfish.getScoreboardTags().contains(TAG)) {
            return false;
        }
        silverfish.setAware(true);
        silverfish.removeScoreboardTag(TAG);
        return true;
    }

    private boolean updateCollisions(Silverfish silverfish, boolean disable) {
        PersistentDataContainer data = silverfish.getPersistentDataContainer();
        if (disable) {
            // Collidable is not saved with the entity, so after a chunk load it is
            // back to true and has to be set again. If it's already false and not
            // marked, another plugin did it and we leave it alone.
            if (!silverfish.isCollidable()) {
                return false;
            }
            silverfish.setCollidable(false);
            data.set(collisionsKey, PersistentDataType.BOOLEAN, true);
            return true;
        }

        if (!data.has(collisionsKey)) {
            return false;
        }
        silverfish.setCollidable(true);
        data.remove(collisionsKey);
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /" + label + " <reload|status>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                List<String> warnings = loadSettings();
                int updated = applyToLoaded();
                sender.sendMessage("FrozenSilverfish reloaded. Updated " + updated + " silverfish.");
                sender.sendMessage(describeSettings());
                for (String warning : warnings) {
                    sender.sendMessage("Warning: " + warning);
                }
            }
            case "status" -> {
                int loaded = 0;
                int frozen = 0;
                int collisionsOff = 0;
                int unawareNotOurs = 0;
                for (World world : getServer().getWorlds()) {
                    for (Silverfish silverfish : world.getEntitiesByClass(Silverfish.class)) {
                        loaded++;
                        boolean ours = silverfish.getScoreboardTags().contains(TAG);
                        if (ours) {
                            frozen++;
                        } else if (!silverfish.isAware()) {
                            unawareNotOurs++;
                        }
                        if (silverfish.getPersistentDataContainer().has(collisionsKey)) {
                            collisionsOff++;
                        }
                    }
                }
                sender.sendMessage("FrozenSilverfish " + getPluginMeta().getVersion() + ": " + describeSettings());
                sender.sendMessage("Loaded silverfish: " + loaded + ", frozen by this plugin: " + frozen
                        + ", collisions turned off by this plugin: " + collisionsOff + ".");
                // Silverfish another plugin turned off: useful to spot overlaps.
                sender.sendMessage("Without AI but not frozen by this plugin: " + unawareNotOurs + ".");
            }
            default -> sender.sendMessage("Usage: /" + label + " <reload|status>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "status").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
