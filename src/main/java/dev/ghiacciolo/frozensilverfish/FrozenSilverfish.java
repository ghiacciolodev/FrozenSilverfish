package dev.ghiacciolo.frozensilverfish;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Silverfish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class FrozenSilverfish extends JavaPlugin implements Listener, TabExecutor {

    private static final String TAG = "frozen_silverfish";

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
        disableCollisions = getConfig().getBoolean("disable-collisions", false);
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
        if (isActiveIn(silverfish.getWorld())) {
            boolean changed = false;
            // setAware(false) only stops the AI goals. setAI(false) would also stop
            // gravity and movement, leaving the mob floating and breaking the water
            // transport. With setAware the mob still falls, gets pushed by water
            // and takes knockback.
            if (silverfish.isAware()) {
                silverfish.setAware(false);
                changed = true;
            }
            silverfish.addScoreboardTag(TAG);
            // Collidable is not saved with the entity, so it is set on every load.
            boolean collidable = !disableCollisions;
            if (silverfish.isCollidable() != collidable) {
                silverfish.setCollidable(collidable);
                changed = true;
            }
            return changed;
        }

        // Aware is saved with the entity, so silverfish we froze earlier stay
        // frozen until we restore them here.
        if (silverfish.getScoreboardTags().contains(TAG)) {
            silverfish.setAware(true);
            silverfish.setCollidable(true);
            silverfish.removeScoreboardTag(TAG);
            return true;
        }
        return false;
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
                for (World world : getServer().getWorlds()) {
                    for (Silverfish silverfish : world.getEntitiesByClass(Silverfish.class)) {
                        loaded++;
                        if (!silverfish.isAware()) {
                            unaware++;
                        }
                    }
                }
                sender.sendMessage("FrozenSilverfish is " + (enabled ? "enabled" : "disabled")
                        + (worlds.isEmpty() ? " in all worlds." : " in: " + String.join(", ", worlds) + "."));
                sender.sendMessage("Loaded silverfish: " + loaded + ", without AI: " + unaware + ".");
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
