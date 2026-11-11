package br.com.puraresenha.puraarena;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import java.util.*;
import java.io.File;

public final class ArenaManager {
    private static final int HALF_SIZE = 22;
    private static final int WALL_HEIGHT = 5;
    private static final int LAYOUT_VERSION = 2;
    private final JavaPlugin plugin;
    private final List<Arena> arenas = new ArrayList<>();

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void prepare() {
        String worldName = plugin.getConfig().getString("arena.world", "puraarena");
        World world = Bukkit.getWorld(worldName);
        boolean worldFolderExists = new File(Bukkit.getWorldContainer(), worldName).isDirectory();
        if (world == null) {
            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(World.Environment.NORMAL);
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            world = creator.createWorld();
        }
        if (world == null) {
            plugin.getLogger().severe("Nao foi possivel criar/carregar o mundo dedicado das arenas: " + worldName);
            return;
        }

        int count = Math.max(1, Math.min(12, plugin.getConfig().getInt("arena.pool-size", 4)));
        int spacing = Math.max(56, plugin.getConfig().getInt("arena.spacing", 64));
        int baseY = Math.max(world.getMinHeight() + 5,
                Math.min(world.getMaxHeight() - 16, plugin.getConfig().getInt("arena.base-y", 80)));
        int startX = plugin.getConfig().getInt("arena.center-x", 0);
        int startZ = plugin.getConfig().getInt("arena.center-z", 0);

        boolean generated = plugin.getConfig().getBoolean("arena.generated", false);
        int generatedCount = plugin.getConfig().getInt("arena.generated-pool-size", 0);
        int layoutVersion = plugin.getConfig().getInt("arena.generated-layout-version", 0);
        if (!worldFolderExists || !generated || generatedCount < count || layoutVersion < LAYOUT_VERSION) {
            for (int i = 0; i < count; i++) {
                int centerX = startX + i * spacing;
                buildArena(world, centerX, startZ, baseY);
            }
            plugin.getConfig().set("arena.generated", true);
            plugin.getConfig().set("arena.generated-pool-size", count);
            plugin.getConfig().set("arena.generated-layout-version", LAYOUT_VERSION);
            plugin.saveConfig();
            plugin.getLogger().info("Preparadas " + count + " arenas reutilizaveis no mundo " + worldName + ".");
        }

        for (int i = 0; i < count; i++) {
            int centerX = startX + i * spacing;
            arenas.add(new Arena(world, centerX, startZ, baseY, i + 1));
            preload(world, centerX, startZ);
        }
        world.setSpawnLocation(new Location(world, startX, baseY + 1, startZ));
    }

    private void preload(World world, int centerX, int centerZ) {
        int minX = centerX - HALF_SIZE - 1;
        int maxX = centerX + HALF_SIZE + 1;
        int minZ = centerZ - HALF_SIZE - 1;
        int maxZ = centerZ + HALF_SIZE + 1;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                world.getChunkAt(cx, cz).load();
            }
        }
    }

    private void buildArena(World world, int centerX, int centerZ, int baseY) {
        int minX = centerX - HALF_SIZE;
        int maxX = centerX + HALF_SIZE;
        int minZ = centerZ - HALF_SIZE;
        int maxZ = centerZ + HALF_SIZE;

        // Arena ampla, totalmente aberta ao ceu. A borda invisivel impede que os jogadores caiam.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.getBlockAt(x, baseY, z).setType(Material.POLISHED_DEEPSLATE, false);
                boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                for (int y = baseY + 1; y <= baseY + WALL_HEIGHT; y++) {
                    world.getBlockAt(x, y, z).setType(edge ? Material.BARRIER : Material.AIR, false);
                }
                for (int y = baseY + WALL_HEIGHT + 1; y <= baseY + 12; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }

        // Marcadores discretos no chao para identificar os pontos de spawn.
        for (int[] marker : new int[][]{{centerX - 10,centerZ},{centerX + 10,centerZ}}) {
            world.getBlockAt(marker[0], baseY, marker[1]).setType(Material.SEA_LANTERN, false);
        }
    }

    public boolean hasArenas() {
        return !arenas.isEmpty();
    }

    public int available() {
        int count = 0;
        for (Arena arena : arenas) if (!arena.busy) count++;
        return count;
    }

    public Arena acquire() {
        for (Arena arena : arenas) {
            if (!arena.busy) {
                arena.busy = true;
                return arena;
            }
        }
        return null;
    }

    public void release(Arena arena) {
        if (arena == null) return;
        for (Entity entity : arena.world.getNearbyEntities(arena.bounds)) {
            if (!(entity instanceof Player) && (entity instanceof Item || entity instanceof Projectile)) {
                entity.remove();
            }
        }
        arena.busy = false;
    }

    public boolean isArena(Location location) {
        if (location == null || location.getWorld() == null) return false;
        for (Arena arena : arenas) {
            if (arena.world.equals(location.getWorld()) && arena.bounds.contains(
                    location.getX(), location.getY(), location.getZ())) return true;
        }
        return false;
    }

    public static final class Arena {
        private final World world;
        private final int centerX, centerZ, baseY, number;
        private final BoundingBox bounds;
        private boolean busy;

        private Arena(World world, int centerX, int centerZ, int baseY, int number) {
            this.world = world;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.baseY = baseY;
            this.number = number;
            this.bounds = new BoundingBox(centerX - HALF_SIZE, baseY - 1, centerZ - HALF_SIZE,
                    centerX + HALF_SIZE + 1, baseY + 128, centerZ + HALF_SIZE + 1);
        }

        public Location spawnOne() {
            return new Location(world, centerX - 6.5, baseY + 1, centerZ + 0.5, -90f, 0f);
        }

        public Location spawnTwo() {
            return new Location(world, centerX + 6.5, baseY + 1, centerZ + 0.5, 90f, 0f);
        }

        public int number() {
            return number;
        }
    }
}
