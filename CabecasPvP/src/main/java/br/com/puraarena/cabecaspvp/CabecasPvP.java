package br.com.puraarena.cabecaspvp;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class CabecasPvP extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("CabecasPvP 1.0.1 habilitado.");
    }

    @Override
    public void onDisable() {
        getLogger().info("CabecasPvP desabilitado.");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Só gera cabeça quando outro jogador causa a morte.
        // Mortes por mobs, quedas, ambiente e outras causas não geram cabeça.
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            getLogger().warning("Não foi possível criar a cabeça de " + victim.getName() + ".");
            return;
        }

        // A cabeça usa a skin da vítima, não a skin de quem matou.
        meta.setOwningPlayer(victim);
        meta.setDisplayName("§eCabeça de §f" + victim.getName());
        meta.setLore(List.of(
                "§7Assassinado por: §c" + killer.getName()
        ));
        head.setItemMeta(meta);

        // Acrescenta a cabeça sem substituir nem remover os drops normais.
        event.getDrops().add(head);
    }
}
