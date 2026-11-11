package br.com.puraresenha.puraarena;
import org.bukkit.*;import org.bukkit.attribute.Attribute;import org.bukkit.command.*;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.entity.PlayerDeathEvent;import org.bukkit.event.inventory.InventoryClickEvent;import org.bukkit.event.player.*;import org.bukkit.inventory.*;import org.bukkit.inventory.meta.ItemMeta;import org.bukkit.enchantments.Enchantment;import org.bukkit.NamespacedKey;import org.bukkit.Registry;import org.bukkit.util.io.BukkitObjectInputStream;import org.bukkit.util.io.BukkitObjectOutputStream;import org.bukkit.plugin.java.JavaPlugin;import org.bukkit.scheduler.BukkitTask;import java.io.*;import java.util.*;
public final class PuraArenaPlugin extends JavaPlugin implements Listener,CommandExecutor,TabCompleter{
 private final Map<UUID,State> saved=new HashMap<>();private final Map<UUID,UUID> fights=new HashMap<>(),invites=new HashMap<>();private final Set<UUID> queue=new LinkedHashSet<>();private final Map<UUID,BukkitTask> timers=new HashMap<>();private final Map<UUID,String> kits=new HashMap<>();private final Map<UUID,Integer>wins=new HashMap<>();private final Map<UUID,ArenaManager.Arena> activeArenas=new HashMap<>();private final Set<UUID> postMatch=new HashSet<>(),rematchRequests=new HashSet<>();private final Map<UUID,BukkitTask> postTimers=new HashMap<>();private final Map<UUID,State> spectatorStates=new HashMap<>();private final Map<UUID,UUID> spectating=new HashMap<>();private ArenaManager arenaManager;
 public void onEnable(){saveDefaultConfig();arenaManager=new ArenaManager(this);arenaManager.prepare();getServer().getPluginManager().registerEvents(this,this);for(String c:List.of("gladiador","x1","arena","spec")){getCommand(c).setExecutor(this);getCommand(c).setTabCompleter(this);}recover();getLogger().info("PuraArena ativada.");}
 private boolean ready(){return arenaManager!=null&&arenaManager.hasArenas();}
 public boolean onCommand(CommandSender sender,Command cmd,String label,String[] a){if(!(sender instanceof Player p)){sender.sendMessage("Apenas jogadores.");return true;}String c=cmd.getName();
 if(c.equals("gladiador")){if(a.length==0){menu(p);return true;}if(a[0].equalsIgnoreCase("entrar")){join(p);return true;}if(a[0].equalsIgnoreCase("sair")){queue.remove(p.getUniqueId());p.sendMessage("§7Voce saiu da fila.");return true;}p.sendMessage("/gladiador [entrar|sair]");return true;}
 if(c.equals("x1")){if(a.length==0){p.sendMessage("/x1 jogador|aceitar|recusar|cancelar|rematch|lobby");return true;}switch(a[0].toLowerCase()){case "aceitar"->accept(p);case "recusar"->decline(p);case "cancelar"->cancel(p);case "rematch"->requestRematch(p);case "lobby"->leavePostMatch(p);default->{Player t=Bukkit.getPlayerExact(a[0]);if(t==null||t.equals(p)){p.sendMessage("§cJogador invalido.");return true;}invite(p,t);}}return true;}\n if(c.equals("spec")){if(a.length==0){Player target=Bukkit.getOnlinePlayers().stream().filter(x->fights.containsKey(x.getUniqueId())&&!postMatch.contains(x.getUniqueId())).findFirst().orElse(null);if(target==null){p.sendMessage("§cNao ha partidas em andamento.");return true;}startSpectating(p,target);return true;}Player target=Bukkit.getPlayerExact(a[0]);if(target==null){p.sendMessage("§cJogador nao encontrado.");return true;}startSpectating(p,target);return true;}
 if(a.length>0&&a[0].equalsIgnoreCase("top")){p.sendMessage("§6PuraArena §7| Vitorias: §a"+wins.getOrDefault(p.getUniqueId(),0));}else p.sendMessage("/arena top");return true;}
 private ItemStack item(Material m,String n,String l){ItemStack i=new ItemStack(m);ItemMeta x=i.getItemMeta();x.setDisplayName(n);x.setLore(List.of(l));i.setItemMeta(x);return i;}
 private void menu(Player p){Inventory i=Bukkit.createInventory(null,27,"§8PuraArena");i.setItem(10,item(Material.IRON_SWORD,"§aGladiador livre","§7Entrar na fila"));i.setItem(13,item(Material.NETHERITE_CHESTPLATE,"§eKits","§7Atual: "+kits.getOrDefault(p.getUniqueId(),"PvP")));i.setItem(16,item(Material.DIAMOND_SWORD,"§bX1 particular","§7Use /x1 jogador"));i.setItem(22,item(Material.BARRIER,"§cSair da fila","§7"));p.openInventory(i);}
 @EventHandler public void menuClick(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p))return;String title=e.getView().getTitle();if(!title.equals("§8PuraArena")&&!title.equals("§8Kits PuraArena"))return;e.setCancelled(true);if(e.getCurrentItem()==null)return;int slot=e.getRawSlot();if(title.equals("§8PuraArena")){if(slot==10){p.closeInventory();join(p);}if(slot==13){Inventory k=Bukkit.createInventory(null,27,"§8Kits PuraArena");Material spear=Material.matchMaterial("SPEAR");k.setItem(10,item(Material.DIAMOND_SWORD,"§aPvP","§7Kit padrao de servidor"));k.setItem(11,item(Material.MACE,"§6Mace","§7Mace com Rajada de Vento"));k.setItem(12,item(spear==null?Material.TRIDENT:spear,"§bSpear","§7Lanca com Lunge III"));k.setItem(13,item(Material.MACE,"§dMace + Spear","§7Os dois equipamentos especiais"));k.setItem(14,item(Material.SHIELD,"§fClassico","§7Kit simples de combate"));p.openInventory(k);}if(slot==16){p.closeInventory();p.sendMessage("§eUse /x1 jogador.");}if(slot==22){queue.remove(p.getUniqueId());p.closeInventory();p.sendMessage("§7Fila cancelada.");}}else{String kit=switch(slot){case 10->"PvP";case 11->"Mace";case 12->"Spear";case 13->"MaceSpear";case 14->"Classico";default->null;};if(kit!=null){kits.put(p.getUniqueId(),kit);p.closeInventory();p.sendMessage("§aKit escolhido: "+kit);}}}
 private void join(Player p){if(!ready()){p.sendMessage("§cAs arenas do PuraArena ainda nao foram preparadas. Verifique o console.");return;}if(saved.containsKey(p.getUniqueId())){p.sendMessage("§cVoce ja esta em partida.");return;}queue.add(p.getUniqueId());p.sendMessage("§aVoce entrou na fila.");Iterator<UUID> it=queue.iterator();if(!it.hasNext())return;UUID a=it.next();if(!it.hasNext())return;UUID b=it.next();queue.remove(a);queue.remove(b);Player x=Bukkit.getPlayer(a),y=Bukkit.getPlayer(b);if(x!=null&&y!=null&&!start(x,y)){queue.add(a);queue.add(b);p.sendMessage("§eTodas as arenas estao ocupadas. Voce continua na fila.");}}
 private void invite(Player a,Player b){if(!ready()||saved.containsKey(a.getUniqueId())||saved.containsKey(b.getUniqueId())||invites.containsKey(b.getUniqueId())){a.sendMessage("§cNao foi possivel enviar o convite.");return;}invites.put(b.getUniqueId(),a.getUniqueId());a.sendMessage("§aConvite enviado. Expira em 60 segundos.");b.sendMessage("§e"+a.getName()+" te chamou para X1. /x1 aceitar ou /x1 recusar (60s).");timers.put(b.getUniqueId(),Bukkit.getScheduler().runTaskLater(this,()->{UUID from=invites.remove(b.getUniqueId());timers.remove(b.getUniqueId());if(from!=null){Player x=Bukkit.getPlayer(from);if(x!=null)x.sendMessage("§cConvite expirou.");if(b.isOnline())b.sendMessage("§cConvite expirado.");}},1200L));}
 private void accept(Player p){UUID id=invites.remove(p.getUniqueId());timer(p);if(id==null){p.sendMessage("§cSem convite pendente.");return;}Player a=Bukkit.getPlayer(id);if(a==null){p.sendMessage("§cDesafiante offline.");return;}start(a,p);}
 private void decline(Player p){UUID id=invites.remove(p.getUniqueId());timer(p);if(id==null){p.sendMessage("§cSem convite.");return;}Player a=Bukkit.getPlayer(id);if(a!=null)a.sendMessage("§c"+p.getName()+" recusou.");p.sendMessage("§7Convite recusado.");}
 private void cancel(Player p){UUID target=null;for(var e:invites.entrySet())if(e.getValue().equals(p.getUniqueId())){target=e.getKey();break;}if(target==null){p.sendMessage("§cNenhum convite enviado.");return;}invites.remove(target);timer(Bukkit.getPlayer(target));Player t=Bukkit.getPlayer(target);if(t!=null)t.sendMessage("§cConvite cancelado pelo desafiante.");p.sendMessage("§aConvite cancelado.");}
 private void timer(Player p){if(p==null)return;BukkitTask t=timers.remove(p.getUniqueId());if(t!=null)t.cancel();}
 private boolean start(Player a,Player b){if(!ready()||saved.containsKey(a.getUniqueId())||saved.containsKey(b.getUniqueId()))return false;ArenaManager.Arena arena=arenaManager.acquire();if(arena==null){a.sendMessage("§eTodas as arenas estao ocupadas.");b.sendMessage("§eTodas as arenas estao ocupadas.");return false;}queue.remove(a.getUniqueId());queue.remove(b.getUniqueId());postMatch.remove(a.getUniqueId());postMatch.remove(b.getUniqueId());rematchRequests.remove(a.getUniqueId());rematchRequests.remove(b.getUniqueId());activeArenas.put(a.getUniqueId(),arena);activeArenas.put(b.getUniqueId(),arena);saved.put(a.getUniqueId(),State.take(a));saved.put(b.getUniqueId(),State.take(b));persist();fights.put(a.getUniqueId(),b.getUniqueId());fights.put(b.getUniqueId(),a.getUniqueId());prepare(a,arena.spawnOne());prepare(b,arena.spawnTwo());give(a,kits.getOrDefault(a.getUniqueId(),"PvP"));give(b,kits.getOrDefault(b.getUniqueId(),"PvP"));a.sendMessage("§6PURA ARENA §7X1 contra §e"+b.getName());b.sendMessage("§6PURA ARENA §7X1 contra §e"+a.getName());return true;}
 private void prepare(Player p,Location l){p.closeInventory();p.getInventory().clear();p.getInventory().setArmorContents(new ItemStack[4]);p.getInventory().setItemInOffHand(null);p.setExp(0);p.setLevel(0);p.setFireTicks(0);p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());p.setFoodLevel(20);p.setSaturation(20);p.setGameMode(GameMode.SURVIVAL);p.teleport(l);}
 private void enchant(ItemStack item,String key,int level){Enchantment e=Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));if(e!=null)item.addUnsafeEnchantment(e,level);}
 private ItemStack gear(Material material,String... enchants){ItemStack item=new ItemStack(material);for(int n=0;n+1<enchants.length;n+=2)enchant(item,enchants[n],Integer.parseInt(enchants[n+1]));return item;}
 private void armor(PlayerInventory i,String prefix){
  ItemStack helmet=gear(Material.valueOf(prefix+"_HELMET"),"protection","4","unbreaking","3","mending","1","respiration","3","aqua_affinity","1");
  ItemStack chest=gear(Material.valueOf(prefix+"_CHESTPLATE"),"protection","4","unbreaking","3","mending","1");
  ItemStack legs=gear(Material.valueOf(prefix+"_LEGGINGS"),"protection","4","unbreaking","3","mending","1");
  ItemStack boots=gear(Material.valueOf(prefix+"_BOOTS"),"protection","4","unbreaking","3","mending","1","feather_falling","4","depth_strider","3");
  i.setHelmet(helmet);i.setChestplate(chest);i.setLeggings(legs);i.setBoots(boots);
 }
 private void add(PlayerInventory i,Material m,int amount,String... enchants){ItemStack item=gear(m,enchants);item.setAmount(amount);i.addItem(item);}
 private void potion(PlayerInventory i,org.bukkit.potion.PotionType type,int amount){
  ItemStack item=new ItemStack(Material.POTION,amount);
  org.bukkit.inventory.meta.PotionMeta meta=(org.bukkit.inventory.meta.PotionMeta)item.getItemMeta();
  meta.setBasePotionType(type);item.setItemMeta(meta);i.addItem(item);
 }
 private void commonPvP(Player p,boolean bow){
  PlayerInventory i=p.getInventory();armor(i,"NETHERITE");
  add(i,Material.DIAMOND_SWORD,1,"sharpness","5","unbreaking","3","fire_aspect","2");
  add(i,Material.DIAMOND_AXE,1,"sharpness","5","unbreaking","3");
  if(bow){add(i,Material.BOW,1,"power","5","unbreaking","3","flame","1");add(i,Material.ARROW,64);}
  add(i,Material.SHIELD,1,"unbreaking","3","mending","1");
  add(i,Material.ENDER_PEARL,16);add(i,Material.GOLDEN_APPLE,16);add(i,Material.ENCHANTED_GOLDEN_APPLE,2);
  add(i,Material.WATER_BUCKET,1);add(i,Material.COBWEB,16);add(i,Material.COBBLESTONE,64);add(i,Material.COOKED_BEEF,32);
  potion(i,org.bukkit.potion.PotionType.STRENGTH,2);
  potion(i,org.bukkit.potion.PotionType.SWIFTNESS,2);
  potion(i,org.bukkit.potion.PotionType.FIRE_RESISTANCE,1);
  i.setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
 }
 private void basePvP(Player p){commonPvP(p,true);}
 private void give(Player p,String k){
  PlayerInventory i=p.getInventory();
  switch(k){
   case "Mace"->{
    commonPvP(p,false);
    add(i,Material.MACE,1,"density","5","wind_burst","3","unbreaking","3","mending","1");
    add(i,Material.ELYTRA,1,"unbreaking","3","mending","1");add(i,Material.FIREWORK_ROCKET,64);
    add(i,Material.WIND_CHARGE,32);
   }
   case "Spear"->{
    commonPvP(p,true);
    Material spear=Material.matchMaterial("SPEAR");
    add(i,spear==null?Material.TRIDENT:spear,1,"lunge","3","sharpness","5","unbreaking","3","mending","1");
   }
   case "MaceSpear"->{
    commonPvP(p,true);
    add(i,Material.MACE,1,"density","5","wind_burst","3","unbreaking","3","mending","1");
    add(i,Material.ELYTRA,1,"unbreaking","3","mending","1");add(i,Material.FIREWORK_ROCKET,64);
    Material spear=Material.matchMaterial("SPEAR");
    add(i,spear==null?Material.TRIDENT:spear,1,"lunge","3","sharpness","5","unbreaking","3","mending","1");
    add(i,Material.WIND_CHARGE,32);
   }
   case "Classico"->{
    armor(i,"IRON");
    add(i,Material.IRON_SWORD,1,"sharpness","4","unbreaking","3");
    add(i,Material.IRON_AXE,1,"sharpness","3","unbreaking","3");
    add(i,Material.SHIELD,1,"unbreaking","3");
    add(i,Material.ENDER_PEARL,4);add(i,Material.GOLDEN_APPLE,8);
    add(i,Material.COOKED_BEEF,24);add(i,Material.WATER_BUCKET,1);
   }
   default->commonPvP(p,true);
  }
 }
 @EventHandler public void death(PlayerDeathEvent e){
  Player loser=e.getEntity();UUID id=loser.getUniqueId(),other=fights.get(id);if(other==null||postMatch.contains(id))return;
  Location deathLocation=loser.getLocation().clone();e.getDrops().clear();e.setDroppedExp(0);e.setKeepInventory(true);
  Player winner=Bukkit.getPlayer(other);postMatch.add(id);postMatch.add(other);
  if(winner!=null){wins.merge(other,1,Integer::sum);winner.sendTitle("§a§lVOCE GANHOU!","§7"+loser.getName()+" foi derrotado",10,70,20);winner.sendMessage("§aVitoria! Use a estrela para pedir revanche ou a cama para voltar.");setEndItems(winner);}
  loser.sendTitle("§c§lVOCE PERDEU!","§7Voce esta em modo espectador",10,70,20);
  Bukkit.getScheduler().runTask(this,()->{loser.spigot().respawn();Bukkit.getScheduler().runTaskLater(this,()->{
   if(!loser.isOnline())return;loser.setGameMode(GameMode.SPECTATOR);loser.teleport(deathLocation);setEndItems(loser);
   loser.sendMessage("§cVoce perdeu. Continue assistindo ou escolha uma opcao na hotbar.");
   if(winner!=null&&winner.isOnline())winner.sendMessage("§e"+loser.getName()+" esta assistindo a partida.");
   BukkitTask task=Bukkit.getScheduler().runTaskLater(this,()->finishPair(id,other),20L*30);
   postTimers.put(id,task);postTimers.put(other,task);
  },1L);});
 }
 private void setEndItems(Player p){p.getInventory().setItem(7,item(Material.NETHER_STAR,"§e§lPEDIR REMATCH","§7Clique para jogar novamente"));p.getInventory().setItem(8,item(Material.RED_BED,"§c§lVOLTAR AO LOBBY","§7Voltar ao local anterior"));}
 private void requestRematch(Player p){UUID id=p.getUniqueId(),other=fights.get(id);if(other==null||!postMatch.contains(id)){p.sendMessage("§cVoce nao tem uma partida encerrada para revanche.");return;}rematchRequests.add(id);Player rival=Bukkit.getPlayer(other);p.sendMessage("§ePedido de rematch enviado. Aguardando o adversario.");if(rival!=null)rival.sendMessage("§e"+p.getName()+" pediu revanche. Clique na estrela ou use /x1 rematch.");if(rematchRequests.contains(other)&&postMatch.contains(other)){cancelPostTimer(id,other);rematchRequests.remove(id);rematchRequests.remove(other);postMatch.remove(id);postMatch.remove(other);Player a=Bukkit.getPlayer(id),b=Bukkit.getPlayer(other);if(a!=null&&b!=null){ArenaManager.Arena arena=activeArenas.get(id);prepare(a,arena.spawnOne());prepare(b,arena.spawnTwo());give(a,kits.getOrDefault(id,"PvP"));give(b,kits.getOrDefault(other,"PvP"));a.sendTitle("§6§lREMATCH!","§7A revanche comecou",5,35,10);b.sendTitle("§6§lREMATCH!","§7A revanche comecou",5,35,10);}}}
 private void leavePostMatch(Player p){UUID id=p.getUniqueId(),other=fights.get(id);if(other==null||!postMatch.contains(id)){p.sendMessage("§cVoce nao esta na fase final de uma partida.");return;}finishPair(id,other);}
 private void cancelPostTimer(UUID a,UUID b){BukkitTask t=postTimers.remove(a);postTimers.remove(b);if(t!=null)t.cancel();}
 private void finishPair(UUID a,UUID b){cancelPostTimer(a,b);Player x=Bukkit.getPlayer(a),y=Bukkit.getPlayer(b);if(x!=null)restore(x);if(y!=null)restore(y);postMatch.remove(a);postMatch.remove(b);rematchRequests.remove(a);rematchRequests.remove(b);fights.remove(a);fights.remove(b);releaseArena(a,b);}
 private void startSpectating(Player spectator,Player target){UUID id=spectator.getUniqueId();if(spectator.equals(target)||saved.containsKey(id)||spectatorStates.containsKey(id)){spectator.sendMessage("§cVoce nao pode entrar no espectador agora.");return;}UUID targetId=target.getUniqueId();if(!fights.containsKey(targetId)||postMatch.contains(targetId)){spectator.sendMessage("§cEsse jogador nao esta em uma luta ativa.");return;}spectatorStates.put(id,State.take(spectator));spectating.put(id,targetId);spectator.getInventory().clear();spectator.setGameMode(GameMode.SPECTATOR);spectator.teleport(target.getLocation());spectator.getInventory().setItem(8,item(Material.RED_BED,"§c§lSAIR DO ESPECTADOR","§7Voltar ao local anterior"));spectator.sendMessage("§aAssistindo a luta de "+target.getName()+". Use a cama para sair.");}
 private void stopSpectating(Player p){UUID id=p.getUniqueId();State state=spectatorStates.remove(id);spectating.remove(id);if(state!=null){state.putBack(p);p.sendMessage("§aVoce saiu do espectador.");}}
 private void restore(Player p){State s=saved.remove(p.getUniqueId());if(s!=null)s.putBack(p);persist();}
 private void releaseArena(UUID a,UUID b){ArenaManager.Arena arena=activeArenas.remove(a);ArenaManager.Arena other=activeArenas.remove(b);if(arena==null)arena=other;if(arena!=null)arenaManager.release(arena);tryStartQueue();}
 private void tryStartQueue(){while(queue.size()>=2&&arenaManager.available()>0){Iterator<UUID> it=queue.iterator();UUID a=it.next(),b=it.next();Player x=Bukkit.getPlayer(a),y=Bukkit.getPlayer(b);if(x==null||y==null){if(x==null)queue.remove(a);if(y==null)queue.remove(b);continue;}queue.remove(a);queue.remove(b);if(!start(x,y)){queue.add(a);queue.add(b);break;}}}
 @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId(),other=fights.get(id);queue.remove(id);if(spectatorStates.containsKey(id)){spectatorStates.remove(id);spectating.remove(id);}if(other!=null){cancelPostTimer(id,other);Player p=Bukkit.getPlayer(other);if(p!=null){p.sendMessage("§aPartida encerrada por desconexao.");restore(p);}fights.remove(id);fights.remove(other);postMatch.remove(id);postMatch.remove(other);rematchRequests.remove(id);rematchRequests.remove(other);releaseArena(id,other);}}
 @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(this,()->restore(e.getPlayer()),1L);}\n @EventHandler public void quitSpectator(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();State st=spectatorStates.remove(id);spectating.remove(id);}
 @EventHandler public void arenaBreak(org.bukkit.event.block.BlockBreakEvent e){if(arenaManager!=null&&arenaManager.isArena(e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler public void arenaPlace(org.bukkit.event.block.BlockPlaceEvent e){if(arenaManager!=null&&arenaManager.isArena(e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler public void arenaExplosion(org.bukkit.event.entity.EntityExplodeEvent e){if(arenaManager!=null&&arenaManager.isArena(e.getLocation()))e.blockList().clear();}
 @EventHandler public void arenaBlockExplosion(org.bukkit.event.block.BlockExplodeEvent e){if(arenaManager!=null&&arenaManager.isArena(e.getBlock().getLocation()))e.blockList().clear();}
 @EventHandler public void drop(PlayerDropItemEvent e){if(saved.containsKey(e.getPlayer().getUniqueId())||spectatorStates.containsKey(e.getPlayer().getUniqueId()))e.setCancelled(true);}\n @EventHandler public void matchItem(PlayerInteractEvent e){Player p=e.getPlayer();if(e.getItem()==null)return;String n=e.getItem().hasItemMeta()?e.getItem().getItemMeta().getDisplayName():"";if(n.equals("§e§lPEDIR REMATCH")){e.setCancelled(true);requestRematch(p);}else if(n.equals("§c§lVOLTAR AO LOBBY")){e.setCancelled(true);leavePostMatch(p);}else if(n.equals("§c§lSAIR DO ESPECTADOR")){e.setCancelled(true);stopSpectating(p);}}\n @EventHandler public void matchInventory(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p)||e.getCurrentItem()==null||!e.getCurrentItem().hasItemMeta())return;String n=e.getCurrentItem().getItemMeta().getDisplayName();if(n.equals("§e§lPEDIR REMATCH")||n.equals("§c§lVOLTAR AO LOBBY")||n.equals("§c§lSAIR DO ESPECTADOR")){e.setCancelled(true);if(n.equals("§e§lPEDIR REMATCH"))requestRematch(p);else if(n.equals("§c§lVOLTAR AO LOBBY"))leavePostMatch(p);else stopSpectating(p);}}
 @EventHandler public void pickup(org.bukkit.event.entity.EntityPickupItemEvent e){if(e.getEntity() instanceof Player p&&saved.containsKey(p.getUniqueId()))e.setCancelled(true);}
 @EventHandler public void command(PlayerCommandPreprocessEvent e){if(saved.containsKey(e.getPlayer().getUniqueId())){String m=e.getMessage().toLowerCase(Locale.ROOT);if(m.equals("/x1 rematch")||m.equals("/x1 lobby"))return;e.setCancelled(true);e.getPlayer().sendMessage("§cComandos bloqueados durante a luta.");}}\n @EventHandler public void damage(org.bukkit.event.entity.EntityDamageEvent e){if(e.getEntity() instanceof Player p&&postMatch.contains(p.getUniqueId()))e.setCancelled(true);}
 @EventHandler public void teleport(PlayerTeleportEvent e){if(saved.containsKey(e.getPlayer().getUniqueId())&&e.getCause()!=PlayerTeleportEvent.TeleportCause.PLUGIN)e.setCancelled(true);}
 private void persist(){for(var e:saved.entrySet())getConfig().set("recovery."+e.getKey(),e.getValue().encode());if(getConfig().isConfigurationSection("recovery"))for(String k:new HashSet<>(getConfig().getConfigurationSection("recovery").getKeys(false)))if(!saved.containsKey(UUID.fromString(k)))getConfig().set("recovery."+k,null);saveConfig();}
 private void recover(){if(!getConfig().isConfigurationSection("recovery"))return;for(String k:getConfig().getConfigurationSection("recovery").getKeys(false))try{saved.put(UUID.fromString(k),State.decode(getConfig().getString("recovery."+k)));}catch(Exception ex){getLogger().severe("Falha ao recuperar dados de "+k);}}
 public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(args.length!=1)return Collections.emptyList();if(c.getName().equalsIgnoreCase("x1"))return List.of("aceitar","recusar","cancelar","rematch","lobby");if(c.getName().equalsIgnoreCase("spec"))return Bukkit.getOnlinePlayers().stream().filter(p->fights.containsKey(p.getUniqueId())&&!postMatch.contains(p.getUniqueId())).map(Player::getName).toList();return Collections.emptyList();}
 private static class State implements Serializable{private static final long serialVersionUID=1L;String world;double x,y,z,hp;float yaw,pitch,exp,sat;int food,level,fire;GameMode mode;ItemStack[] contents,armor;ItemStack off;
 static State take(Player p){State s=new State();Location l=p.getLocation();s.world=l.getWorld().getName();s.x=l.getX();s.y=l.getY();s.z=l.getZ();s.yaw=l.getYaw();s.pitch=l.getPitch();s.contents=p.getInventory().getStorageContents();s.armor=p.getInventory().getArmorContents();s.off=p.getInventory().getItemInOffHand();s.hp=p.getHealth();s.food=p.getFoodLevel();s.sat=p.getSaturation();s.exp=p.getExp();s.level=p.getLevel();s.fire=p.getFireTicks();s.mode=p.getGameMode();return s;}
 void putBack(Player p){World w=Bukkit.getWorld(world);if(w!=null)p.teleport(new Location(w,x,y,z,yaw,pitch));p.getInventory().clear();p.getInventory().setStorageContents(contents);p.getInventory().setArmorContents(armor);p.getInventory().setItemInOffHand(off);p.setGameMode(mode);p.setFoodLevel(food);p.setSaturation(sat);p.setExp(exp);p.setLevel(level);p.setFireTicks(fire);p.setHealth(Math.max(1,Math.min(hp,p.getAttribute(Attribute.MAX_HEALTH).getValue())));}
 String encode(){try{ByteArrayOutputStream b=new ByteArrayOutputStream();BukkitObjectOutputStream o=new BukkitObjectOutputStream(b);o.writeObject(this);o.close();return Base64.getEncoder().encodeToString(b.toByteArray());}catch(Exception e){throw new IllegalStateException(e);}}
 static State decode(String v)throws Exception{BukkitObjectInputStream i=new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(v)));State s=(State)i.readObject();i.close();return s;}}
}