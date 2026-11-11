package br.com.puraresenha.puraarena;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class ReplayManager implements Listener, CommandExecutor, TabCompleter {
    private static final String MENU_TITLE = "§8Replays PuraArena";
    private static final String WATCH_ITEM = "§b§lASSISTIR REPLAY";
    private static final int SAMPLE_PERIOD = 2;
    private static final int MAX_FRAMES = 18000;
    private static final int MAX_FILES = 20;
    private final JavaPlugin plugin;
    private final Map<UUID, Recording> recordings = new HashMap<>();
    private final Map<UUID, Playback> playbacks = new HashMap<>();
    private final Map<UUID, List<ReplayRecord>> menus = new HashMap<>();
    private World replayWorld;
    private int nextInstance;

    public ReplayManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(Player a, Player b) {
        stopRecording(a.getUniqueId(), false);
        ReplayRecord record = new ReplayRecord();
        record.id = UUID.randomUUID().toString();
        record.started = System.currentTimeMillis();
        record.a = a.getUniqueId();
        record.b = b.getUniqueId();
        record.nameA = a.getName();
        record.nameB = b.getName();
        Location startA = a.getLocation(), startB = b.getLocation();
        record.baseAx = startA.getX(); record.baseAy = startA.getY(); record.baseAz = startA.getZ();
        record.baseBx = startB.getX(); record.baseBy = startB.getY(); record.baseBz = startB.getZ();
        record.frames = new ArrayList<>();
        Recording recording = new Recording(record);
        recordings.put(a.getUniqueId(), recording);
        recordings.put(b.getUniqueId(), recording);
        recording.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> capture(recording, a, b), 1L, SAMPLE_PERIOD);
    }

    public void finish(UUID a, UUID b) {
        Recording recording = recordings.get(a);
        if (recording == null) recording = recordings.get(b);
        if (recording == null) return;
        stopRecording(recording.record.a, true);
    }

    public void abort(UUID a, UUID b) {
        Recording recording = recordings.get(a);
        if (recording == null) recording = recordings.get(b);
        if (recording != null) stopRecording(recording.record.a, true);
    }

    private void capture(Recording recording, Player a, Player b) {
        if (!a.isOnline() || !b.isOnline() || recording.record.frames.size() >= MAX_FRAMES) {
            stopRecording(recording.record.a, true);
            return;
        }
        ReplayFrame frame = new ReplayFrame();
        Location la = a.getLocation(), lb = b.getLocation();
        frame.ax = la.getX() - recording.record.baseAx;
        frame.ay = la.getY() - recording.record.baseAy;
        frame.az = la.getZ() - recording.record.baseAz;
        frame.ayaw = la.getYaw(); frame.apitch = la.getPitch(); frame.ahp = a.getHealth();
        frame.bx = lb.getX() - recording.record.baseBx;
        frame.by = lb.getY() - recording.record.baseBy;
        frame.bz = lb.getZ() - recording.record.baseBz;
        frame.byaw = lb.getYaw(); frame.bpitch = lb.getPitch(); frame.bhp = b.getHealth();
        if (recording.record.frames.size() % 10 == 0) {
            frame.aarmor = cloneItems(a.getInventory().getArmorContents());
            frame.barmor = cloneItems(b.getInventory().getArmorContents());
            frame.ahand = cloneItem(a.getInventory().getItemInMainHand());
            frame.bhand = cloneItem(b.getInventory().getItemInMainHand());
            frame.aoff = cloneItem(a.getInventory().getItemInOffHand());
            frame.boff = cloneItem(b.getInventory().getItemInOffHand());
        }
        recording.record.frames.add(frame);
    }

    private ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) copy[i] = cloneItem(items[i]);
        return copy;
    }

    private ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private void stopRecording(UUID key, boolean save) {
        Recording recording = recordings.remove(key);
        if (recording == null) return;
        recordings.remove(recording.record.a);
        recordings.remove(recording.record.b);
        if (recording.task != null) recording.task.cancel();
        if (save && recording.record.frames.size() >= 10) save(recording.record);
    }

    private void save(ReplayRecord record) {
        try {
            File dir = new File(plugin.getDataFolder(), "replays");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Nao foi possivel criar a pasta de replays");
            File file = new File(dir, record.started + "-" + record.id + ".dat");
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(new FileOutputStream(file))) {
                out.writeObject(record);
            }
            cleanup(dir);
            Player a = Bukkit.getPlayer(record.a), b = Bukkit.getPlayer(record.b);
            if (a != null) a.sendMessage("§bReplay salvo! Use /replay ou clique em ASSISTIR REPLAY.");
            if (b != null) b.sendMessage("§bReplay salvo! Use /replay ou clique em ASSISTIR REPLAY.");
        } catch (Exception ex) {
            plugin.getLogger().severe("Falha ao salvar replay: " + ex.getMessage());
        }
    }

    private void cleanup(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".dat"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        long cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
        for (int i = 0; i < files.length; i++) {
            if (i >= MAX_FILES || files[i].lastModified() < cutoff) files[i].delete();
        }
    }

    private List<ReplayRecord> loadFor(UUID player) {
        List<ReplayRecord> found = new ArrayList<>();
        File dir = new File(plugin.getDataFolder(), "replays");
        File[] files = dir.listFiles((d, name) -> name.endsWith(".dat"));
        if (files == null) return found;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (File file : files) {
            try (BukkitObjectInputStream in = new BukkitObjectInputStream(new FileInputStream(file))) {
                Object value = in.readObject();
                if (value instanceof ReplayRecord record && (record.a.equals(player) || record.b.equals(player))) found.add(record);
            } catch (Exception ex) {
                plugin.getLogger().warning("Replay ignorado por arquivo invalido: " + file.getName());
            }
            if (found.size() >= MAX_FILES) break;
        }
        return found;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem assistir replays.");
            return true;
        }
        if (args.length > 0) {
            Playback playback = playbacks.get(player.getUniqueId());
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "sair" -> stopPlayback(player.getUniqueId(), true);
                case "pausar" -> {
                    if (playback == null) player.sendMessage("§cVoce nao esta assistindo um replay.");
                    else { playback.paused = !playback.paused; player.sendMessage(playback.paused ? "§eReplay pausado." : "§aReplay continuando."); }
                }
                case "reiniciar" -> {
                    if (playback == null) player.sendMessage("§cVoce nao esta assistindo um replay.");
                    else { playback.index = 0; playback.accumulator = 0; player.sendMessage("§aReplay reiniciado."); }
                }
                case "camera" -> {
                    if (playback == null) player.sendMessage("§cVoce nao esta assistindo um replay.");
                    else { playback.cameraB = !playback.cameraB; player.setSpectatorTarget(playback.cameraB ? playback.b : playback.a); player.sendMessage("§bCamera: " + (playback.cameraB ? playback.record.nameB : playback.record.nameA)); }
                }
                case "velocidade" -> {
                    if (playback == null) player.sendMessage("§cVoce nao esta assistindo um replay.");
                    else if (args.length < 2 || !Set.of("0.5", "1", "2").contains(args[1])) player.sendMessage("§7Use /replay velocidade 0.5, 1 ou 2.");
                    else { playback.speed = Double.parseDouble(args[1]); player.sendMessage("§aVelocidade: " + args[1] + "x"); }
                }
                default -> player.sendMessage("§7/replay | sair | pausar | reiniciar | camera | velocidade 0.5|1|2");
            }
            return true;
        }
        openMenu(player);
        return true;
    }

    private void openMenu(Player player) {
        List<ReplayRecord> records = loadFor(player.getUniqueId());
        menus.put(player.getUniqueId(), records);
        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE);
        if (records.isEmpty()) {
            inv.setItem(13, item(Material.BARRIER, "§cNenhum replay encontrado", "§7Suas partidas gravadas aparecerao aqui."));
        } else {
            for (int i = 0; i < Math.min(27, records.size()); i++) {
                ReplayRecord r = records.get(i);
                String date = new SimpleDateFormat("dd/MM HH:mm").format(new Date(r.started));
                inv.setItem(i, item(Material.FILLED_MAP, "§b" + r.nameA + " §7vs §c" + r.nameB,
                        "§7Data: §f" + date, "§7Duracao: §f" + (r.frames.size() * SAMPLE_PERIOD / 20) + "s",
                        "§eClique para assistir"));
            }
        }
        player.openInventory(inv);
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void menuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !MENU_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        List<ReplayRecord> records = menus.get(player.getUniqueId());
        if (records == null || slot < 0 || slot >= Math.min(27, records.size())) return;
        player.closeInventory();
        play(player, records.get(slot));
    }

    @EventHandler
    public void replayButton(PlayerInteractEvent event) {
        ItemStack held = event.getItem();
        if (held == null || !held.hasItemMeta() || !WATCH_ITEM.equals(held.getItemMeta().getDisplayName())) return;
        event.setCancelled(true);
        openMenu(event.getPlayer());
    }

    @EventHandler
    public void replayButtonClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null
                || !event.getCurrentItem().hasItemMeta()) return;
        if (WATCH_ITEM.equals(event.getCurrentItem().getItemMeta().getDisplayName())) {
            event.setCancelled(true);
            player.closeInventory();
            openMenu(player);
        }
    }

    private void play(Player viewer, ReplayRecord record) {
        if (playbacks.containsKey(viewer.getUniqueId())) {
            viewer.sendMessage("§cVoce ja esta assistindo um replay. Use /replay sair.");
            return;
        }
        if (record.frames == null || record.frames.size() < 2) {
            viewer.sendMessage("§cEsse replay esta vazio ou incompleto.");
            return;
        }
        if (replayWorld == null) {
            WorldCreator creator = new WorldCreator("puraarena_replays");
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            replayWorld = creator.createWorld();
        }
        if (replayWorld == null) {
            viewer.sendMessage("§cNao foi possivel carregar o mundo de replays.");
            return;
        }
        int instance = nextInstance++;
        int centerX = instance * 80;
        int centerZ = 0;
        buildReplayFloor(centerX, centerZ);
        ViewerState old = new ViewerState(viewer);
        Location destination = new Location(replayWorld, centerX, 82, centerZ);
        viewer.closeInventory();
        viewer.getInventory().clear();
        viewer.setGameMode(GameMode.SPECTATOR);
        viewer.teleport(destination);
        ArmorStand standA = createGhost(centerX - 3, centerZ, record.nameA);
        ArmorStand standB = createGhost(centerX + 3, centerZ, record.nameB);
        Playback playback = new Playback(viewer, old, record, standA, standB);
        playbacks.put(viewer.getUniqueId(), playback);
        viewer.setSpectatorTarget(standA);
        viewer.sendMessage("§bReplay iniciado: §f" + record.nameA + " §7vs §f" + record.nameB);
        viewer.sendMessage("§7Use §f/replay sair §7para encerrar.");
        playback.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> playbackTick(playback, centerX, centerZ), 1L, SAMPLE_PERIOD);
    }

    private void buildReplayFloor(int x, int z) {
        for (int dx = -18; dx <= 18; dx++) {
            for (int dz = -18; dz <= 18; dz++) {
                replayWorld.getBlockAt(x + dx, 79, z + dz).setType(Material.SMOOTH_STONE, false);
            }
        }
    }

    private ArmorStand createGhost(int x, int z, String name) {
        ArmorStand stand = replayWorld.spawn(new Location(replayWorld, x, 80, z), ArmorStand.class);
        stand.setVisible(true);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setGravity(false);
        stand.setCustomName("§e" + name);
        stand.setCustomNameVisible(true);
        stand.setInvulnerable(true);
        return stand;
    }

    private void playbackTick(Playback playback, int centerX, int centerZ) {
        if (!playback.viewer.isOnline() || playback.index >= playback.record.frames.size()) {
            stopPlayback(playback.viewer.getUniqueId(), true);
            return;
        }
        if (playback.paused) return;
        playback.accumulator += playback.speed;
        int steps = (int) playback.accumulator;
        playback.accumulator -= steps;
        for (int step = 0; step < steps && playback.index < playback.record.frames.size(); step++) {
            ReplayFrame f = playback.record.frames.get(playback.index++);
            moveGhost(playback.a, centerX - 3 + f.ax, 80 + f.ay, centerZ + f.az, f.ayaw, f.apitch, f.ahp);
            moveGhost(playback.b, centerX + 3 + f.bx, 80 + f.by, centerZ + f.bz, f.byaw, f.bpitch, f.bhp);
            if (f.aarmor != null) applyEquipment(playback.a, f.aarmor, f.ahand, f.aoff);
            if (f.barmor != null) applyEquipment(playback.b, f.barmor, f.bhand, f.boff);
        }
        if (playback.index % 100 == 0) playback.viewer.sendActionBar("§bReplay §7" + playback.index * SAMPLE_PERIOD / 20 + "s");
    }

    private void moveGhost(ArmorStand stand, double x, double y, double z, float yaw, float pitch, double hp) {
        Location location = new Location(replayWorld, x, y, z, yaw, pitch);
        stand.teleport(location);
        stand.setCustomName("§e" + (int)Math.ceil(Math.max(0, hp)) + "❤");
    }

    private void applyEquipment(ArmorStand stand, ItemStack[] armor, ItemStack hand, ItemStack off) {
        EntityEquipment equipment = stand.getEquipment();
        if (equipment == null) return;
        equipment.setArmorContents(cloneItems(armor));
        equipment.setItemInMainHand(cloneItem(hand));
        equipment.setItemInOffHand(cloneItem(off));
    }

    private void stopPlayback(UUID id, boolean restore) {
        Playback playback = playbacks.remove(id);
        if (playback == null) return;
        if (playback.task != null) playback.task.cancel();
        playback.a.remove();
        playback.b.remove();
        Player viewer = Bukkit.getPlayer(id);
        if (restore && viewer != null && viewer.isOnline()) playback.old.restore(viewer);
        if (viewer != null && viewer.isOnline()) viewer.sendMessage("§7Replay encerrado.");
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stopPlayback(id, true);
        menus.remove(id);
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(playbacks.keySet())) stopPlayback(id, true);
        for (UUID id : new ArrayList<>(recordings.keySet())) stopRecording(id, true);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("sair", "pausar", "reiniciar", "camera", "velocidade") : args.length == 2 && args[0].equalsIgnoreCase("velocidade") ? List.of("0.5", "1", "2") : Collections.emptyList();
    }

    private static final class Recording {
        final ReplayRecord record;
        BukkitTask task;
        Recording(ReplayRecord record) { this.record = record; }
    }

    private static final class Playback {
        final Player viewer;
        final ViewerState old;
        final ReplayRecord record;
        final ArmorStand a, b;
        int index;
        double speed = 1.0, accumulator;
        boolean paused, cameraB;
        BukkitTask task;
        Playback(Player viewer, ViewerState old, ReplayRecord record, ArmorStand a, ArmorStand b) {
            this.viewer = viewer; this.old = old; this.record = record; this.a = a; this.b = b;
        }
    }

    private static final class ViewerState {
        final Location location;
        final GameMode mode;
        final ItemStack[] contents, armor;
        final ItemStack off;
        final double health;
        final int food, level, fire;
        final float saturation, exp;
        ViewerState(Player player) {
            location = player.getLocation().clone();
            mode = player.getGameMode();
            contents = cloneItems(player.getInventory().getStorageContents());
            armor = cloneItems(player.getInventory().getArmorContents());
            ItemStack heldOff = player.getInventory().getItemInOffHand();
            off = heldOff == null ? null : heldOff.clone();
            health = player.getHealth();
            food = player.getFoodLevel(); saturation = player.getSaturation();
            level = player.getLevel(); exp = player.getExp(); fire = player.getFireTicks();
        }
        void restore(Player player) {
            player.setSpectatorTarget(null);
            player.teleport(location);
            player.setGameMode(mode);
            player.getInventory().clear();
            player.getInventory().setStorageContents(cloneItems(contents));
            player.getInventory().setArmorContents(cloneItems(armor));
            player.getInventory().setItemInOffHand(off == null ? null : off.clone());
            player.setFoodLevel(food); player.setSaturation(saturation);
            player.setLevel(level); player.setExp(exp); player.setFireTicks(fire);
            player.setHealth(Math.max(1, Math.min(health, player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue())));
        }
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) copy[i] = items[i] == null ? null : items[i].clone();
        return copy;
    }

    private static final class ReplayRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        String id, nameA, nameB;
        long started;
        UUID a, b;
        double baseAx, baseAy, baseAz, baseBx, baseBy, baseBz;
        ArrayList<ReplayFrame> frames;
    }

    private static final class ReplayFrame implements Serializable {
        private static final long serialVersionUID = 1L;
        double ax, ay, az, ahp, bx, by, bz, bhp;
        float ayaw, apitch, byaw, bpitch;
        ItemStack[] aarmor, barmor;
        ItemStack ahand, bhand, aoff, boff;
    }
}
