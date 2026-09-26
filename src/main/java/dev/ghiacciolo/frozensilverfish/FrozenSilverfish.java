package dev.ghiacciolo.frozensilverfish;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Silverfish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class FrozenSilverfish extends JavaPlugin implements Listener, TabExecutor {

    private static final String TAG = "frozen_silverfish";

    // Marks silverfish whose collisions we turned off, so we only restore those.
    private final NamespacedKey collisionsKey = new NamespacedKey(this, "collisions_off");

    private boolean enabled;
    private boolean disableCollisions;
    private boolean preventDrowning;
    private Set<String> worlds = Set.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

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

    private void loadSettings() {
        reloadConfig();
        enabled = getConfig().getBoolean("enabled", true);
        disableCollisions = getConfig().getBoolean("disable-collisions", true);
        preventDrowning = getConfig().getBoolean("prevent-drowning", true);
        worlds = new HashSet<>(getConfig().getStringList("worlds"));
    }

    // Fires for new spawns and for entities loaded from chunks.
    @EventHandler
    public void onEntityAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Silverfish silverfish) {
            apply(silverfish);
        }
    }

    // Swimming up is an AI goal, so frozen silverfish sink and would drown in
    // the water streams before reaching the killing chamber.
    @EventHandler(ignoreCancelled = true)
    public void onDrown(EntityDamageEvent event) {
        if (preventDrowning
                && event.getCause() == EntityDamageEvent.DamageCause.DROWNING
                && event.getEntity() instanceof Silverfish silverfish
                && !silverfish.isAware()
                && silverfish.getScoreboardTags().contains(TAG)) {
            event.setCancelled(true);
        }
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

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                loadSettings();
                int updated = applyToLoaded();
                sender.sendMessage("FrozenSilverfish reloaded. Updated " + updated + " silverfish.");
            }
            case "status" -> {
                int loaded = 0;
                int unaware = 0;
                int frozen = 0;
                for (World world : getServer().getWorlds()) {
                    for (Silverfish silverfish : world.getEntitiesByClass(Silverfish.class)) {
                        loaded++;
                        if (!silverfish.isAware()) {
                            unaware++;
                        }
                        if (silverfish.getScoreboardTags().contains(TAG)) {
                            frozen++;
                        }
                    }
                }
                sender.sendMessage("FrozenSilverfish is " + (enabled ? "enabled" : "disabled")
                        + (worlds.isEmpty() ? " in all worlds." : " in: " + String.join(", ", worlds) + "."));
                sender.sendMessage("Loaded silverfish: " + loaded + ", without AI: " + unaware
                        + ", frozen by this plugin: " + frozen + ".");
                sender.sendMessage("Collisions disabled: " + (disableCollisions ? "yes" : "no") + ".");
                sender.sendMessage("Drowning prevented: " + (preventDrowning ? "yes" : "no") + ".");
            }
            default -> sender.sendMessage("Usage: /" + label + " <reload|status>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "status").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
