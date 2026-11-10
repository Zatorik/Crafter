package br.com.puraresenha.crafttudo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CraftTudoPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final int MENU_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int MAX_CRAFTS = 4096;
    private final Map<UUID, Long> lastCraft = new HashMap<>();
    private List<CraftingRecipe> recipes = List.of();

    @Override
    public void onEnable() {
        refreshRecipes();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("crafttudo") != null) getCommand("crafttudo").setExecutor(this);
        getLogger().info("CraftTudo 2.0 ativado. Receitas carregadas: " + recipes.size());
    }

    private void refreshRecipes() {
        List<CraftingRecipe> found = new ArrayList<>();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (recipe instanceof CraftingRecipe crafting && crafting.getResult() != null
                    && crafting.getResult().getType() != Material.AIR) found.add(crafting);
        }
        found.sort(Comparator.comparing(r -> r.getResult().getType().name()));
        recipes = List.copyOf(found);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Este comando só pode ser usado por um jogador.");
            return true;
        }
        if (!player.hasPermission("crafttudo.use")) {
            player.sendMessage(ChatColor.RED + "Você não tem permissão para usar o CraftTudo.");
            return true;
        }
        openMenu(player, 0);
        return true;
    }

    private void openMenu(Player player, int page) {
        int pages = Math.max(1, (recipes.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        MenuHolder holder = new MenuHolder(safePage);
        Inventory inv = Bukkit.createInventory(holder, MENU_SIZE, ChatColor.DARK_GREEN + "CraftTudo " + ChatColor.GRAY + "| " + (safePage + 1) + "/" + pages);
        holder.inventory = inv;
        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, recipes.size());
        for (int i = start; i < end; i++) {
            ItemStack icon = recipes.get(i).getResult().clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Clique para fabricar a quantidade máxima.");
                lore.add(ChatColor.YELLOW + "O plugin verifica os materiais e o espaço.");
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inv.setItem(i - start, icon);
        }
        if (safePage > 0) inv.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "Página anterior"));
        inv.setItem(49, named(Material.BOOK, ChatColor.GREEN + "CraftTudo 2.0"));
        if (safePage + 1 < pages) inv.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "Próxima página"));
        player.openInventory(inv);
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); item.setItemMeta(meta); }
        return item;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (slot == 45) { openMenu(player, holder.page - 1); return; }
        if (slot == 53) { openMenu(player, holder.page + 1); return; }
        if (slot < 0 || slot >= PAGE_SIZE) return;
        int recipeIndex = holder.page * PAGE_SIZE + slot;
        if (recipeIndex >= recipes.size()) return;
        long now = System.currentTimeMillis();
        long previous = lastCraft.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 350) return;
        lastCraft.put(player.getUniqueId(), now);
        int crafted = craftMaximum(player, recipes.get(recipeIndex));
        if (crafted > 0) {
            player.sendMessage(ChatColor.GREEN + "CraftTudo: " + ChatColor.WHITE + "fabricado " + crafted + "x " + recipes.get(recipeIndex).getResult().getType().name() + ".");
        } else {
            player.sendMessage(ChatColor.RED + "Não foi possível fabricar: confira os materiais e o espaço no inventário.");
        }
        openMenu(player, holder.page);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    private int craftMaximum(Player player, CraftingRecipe recipe) {
        List<RecipeChoice> ingredients = ingredientChoices(recipe);
        if (ingredients.isEmpty()) return 0;
        ItemStack[] original = player.getInventory().getStorageContents();
        int maximum = 0;
        ItemStack[] working = cloneContents(original);
        for (int attempt = 0; attempt < MAX_CRAFTS; attempt++) {
            ItemStack[] next = cloneContents(working);
            List<ItemStack> remainders = new ArrayList<>();
            boolean possible = true;
            for (RecipeChoice choice : ingredients) {
                int found = findMatching(next, choice);
                if (found < 0) { possible = false; break; }
                ItemStack consumed = next[found].clone();
                next[found].setAmount(next[found].getAmount() - 1);
                if (next[found].getAmount() <= 0) next[found] = null;
                ItemStack remainder = remainderFor(consumed.getType());
                if (remainder != null) remainders.add(remainder);
            }
            if (!possible) break;
            Inventory simulation = Bukkit.createInventory(null, 36);
            simulation.setStorageContents(next);
            Map<Integer, ItemStack> overflow = simulation.addItem(recipe.getResult().clone());
            for (ItemStack remainder : remainders) overflow.putAll(simulation.addItem(remainder));
            if (!overflow.isEmpty()) break;
            working = simulation.getStorageContents();
            maximum++;
        }
        if (maximum == 0) return 0;
        player.getInventory().setStorageContents(working);
        player.updateInventory();
        return maximum;
    }

    private List<RecipeChoice> ingredientChoices(CraftingRecipe recipe) {
        List<RecipeChoice> choices = new ArrayList<>();
        if (recipe instanceof ShapelessRecipe shapeless) {
            choices.addAll(shapeless.getChoiceList());
        } else if (recipe instanceof ShapedRecipe shaped) {
            Map<Character, RecipeChoice> choiceMap = shaped.getChoiceMap();
            for (String row : shaped.getShape()) {
                for (int i = 0; i < row.length(); i++) {
                    RecipeChoice choice = choiceMap.get(row.charAt(i));
                    if (choice != null) choices.add(choice);
                }
            }
        }
        return choices;
    }

    private int findMatching(ItemStack[] contents, RecipeChoice choice) {
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR && choice.test(item)) return i;
        }
        return -1;
    }

    private ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
        return copy;
    }

    private ItemStack remainderFor(Material input) {
        return switch (input) {
            case WATER_BUCKET, LAVA_BUCKET, MILK_BUCKET -> new ItemStack(Material.BUCKET);
            case HONEY_BOTTLE -> new ItemStack(Material.GLASS_BOTTLE);
            case MUSHROOM_STEW, BEETROOT_SOUP, RABBIT_STEW, SUSPICIOUS_STEW -> new ItemStack(Material.BOWL);
            default -> null;
        };
    }

    private static final class MenuHolder implements InventoryHolder {
        private final int page;
        private Inventory inventory;
        private MenuHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
