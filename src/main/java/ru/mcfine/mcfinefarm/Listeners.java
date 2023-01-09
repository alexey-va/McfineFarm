package ru.mcfine.mcfinefarm;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.util.*;


public class Listeners  implements Listener {

    private static final HashMap<String, PaymentCounter> counter = new HashMap<>();
    private static final DecimalFormat df = new DecimalFormat(McfineFarm.moneyFormat);
    private static final HashMap<String, Integer> playerCooldown = new HashMap<>();
    public static BukkitTask cooldownTask = null;
    public static BukkitTask clearTask = null;
    private static final HashSet<Material> crops = new HashSet<>(
            Arrays.asList(Material.WHEAT, Material.POTATOES, Material.CARROTS,
                    Material.BEETROOTS)
    );

    private static final HashSet<Material> woods = new HashSet<>(
            Arrays.asList(Material.OAK_LOG, Material.DARK_OAK_LOG, Material.SPRUCE_LOG,
                    Material.BIRCH_LOG, Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.MANGROVE_LOG,
                    Material.OAK_LEAVES, Material.ACACIA_LEAVES, Material.AZALEA_LEAVES, Material.MANGROVE_LEAVES,
                    Material.DARK_OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES)
    );

    @EventHandler
    public void onPlayerBreak(BlockBreakEvent event){

        if(!McfineFarm.worlds.contains(event.getBlock().getLocation().getWorld().getName())) return;
        if(event.getPlayer().hasPermission("mcfinefarm.bypass")) return;
        String regionName = null;
        ProtectedRegion region = null;
        int x = event.getBlock().getX();
        int y = event.getBlock().getY();
        int z = event.getBlock().getZ();
        for(Map.Entry<String, ProtectedRegion> entry : McfineFarm.regions.entrySet()){
            if(entry.getValue().contains(x, y, z)){
                regionName = entry.getKey();
                region = entry.getValue();
                break;
            }
        }

        boolean lumbermill = false;
        if(regionName == null){
            for(Map.Entry<String, ProtectedRegion> entry : McfineFarm.lumbermills.entrySet()){
                if(entry.getValue().contains(x, y, z)){
                    regionName = entry.getKey();
                    region = entry.getValue();
                    lumbermill = true;
                    break;
                }
            }
        }

        if(regionName == null) return;

        if(McfineFarm.regionPermissions.containsKey(region) && !event.getPlayer().hasPermission( McfineFarm.regionPermissions.get(region)) ){
            event.setCancelled(true);
            event.getPlayer().sendActionBar(ChatColor.RED+"Вы не можете ломать этот блок здесь!");
            return;
        }

        Block block = event.getBlock();

        if(lumbermill){
            if (!woods.contains(block.getType())) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(ChatColor.RED+"Вы не можете ломать этот блок здесь!");
            }
            return;
        }

        Ageable ageable = null;
        if(crops.contains(block.getType())){
            ageable = (Ageable) block.getBlockData();
            if(ageable.getAge() != ageable.getMaximumAge()){
                event.setCancelled(true);
                return;
            }
        }

        if(!McfineFarm.globalBlocks.containsKey(block.getType())){
            event.setCancelled(true);
            event.getPlayer().sendActionBar(ChatColor.RED+"Вы не можете ломать этот блок здесь!");
            return;
        }
        FarmBlock farmBlock = McfineFarm.globalBlocks.get(block.getType());


        event.setDropItems(false);
        event.setExpToDrop(McfineFarm.dropXp);
        event.setCancelled(true);
        if(crops.contains(event.getBlock().getType())){
            ageable.setAge(0);
            block.getWorld().setBlockData(block.getLocation(), ageable);
        } else{
            block.getLocation().getWorld().setType(block.getLocation(), farmBlock.getMaterial());
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                sendIncome(event.getPlayer(), farmBlock.getPayout());
            }
        }.runTaskAsynchronously(McfineFarm.plugin);

        McfineFarm.incrementMinedBlocks(region);
    }


    private static void sendIncome(Player player, double pay){
        PaymentCounter paymentCounter = counter.get(player.getName());
        if(paymentCounter == null){
            paymentCounter = new PaymentCounter(pay);
            counter.put(player.getName(), paymentCounter);
            createClearTask();
        }
        else paymentCounter.addPayment(pay);
        paymentCounter.setDirty(false);

        final double payment = paymentCounter.getPayment();

        new BukkitRunnable() {
            @Override
            public void run() {
                EconomyResponse response = McfineFarm.econ.depositPlayer(player, pay);
                if(!response.transactionSuccess()){
                    player.sendMessage("Произошла ашибка. Сообщите админу!");
                }

                if(McfineFarm.actionbarMessage.length() > 0) {
                    if (!playerCooldown.containsKey(player.getName())) {
                        player.sendActionBar(ChatColor.GOLD+"Вы заработали "+ChatColor.GREEN+df.format(payment)+ChatColor.RESET+McfineFarm.currency+ChatColor.GOLD+"!");
                        createCooldownTask(player.getName());
                    }
                }
            }
        }.runTaskAsynchronously(McfineFarm.plugin);

    }

    private static void createCooldownTask(String playerName){
        playerCooldown.put(playerName, (int) McfineFarm.cooldownActionbar);
        if(cooldownTask == null || cooldownTask.isCancelled()) {
            cooldownTask = new BukkitRunnable() {
                @Override
                public void run() {
                    ArrayList<String> toRemove = new ArrayList<>();
                    for (String s : playerCooldown.keySet()) {
                        int res = playerCooldown.get(s) - 2;
                        if (res <= 0) toRemove.add(s);
                        else playerCooldown.put(s, res);

                        if (playerCooldown.size() == 0) this.cancel();
                    }

                    for(String s : toRemove){
                        playerCooldown.remove(s);
                    }
                }
            }.runTaskTimerAsynchronously(McfineFarm.plugin, 2L, 2L);
        }
    }

    private static void createClearTask(){
        if(clearTask == null || clearTask.isCancelled()){
            clearTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if(counter.size() == 0){
                        this.cancel();
                        return;
                    }

                    ArrayList<String> toRemove = new ArrayList<>();
                    for(Map.Entry<String, PaymentCounter> entry : counter.entrySet()){
                        if(entry.getValue().dirty) toRemove.add(entry.getKey());
                        else entry.getValue().setDirty(true);
                    }
                    for(String s : toRemove) counter.remove(s);
                }
            }.runTaskTimerAsynchronously(McfineFarm.plugin, 100L, 100L);
        }
    }

    private static class PaymentCounter{
        public double payment;
        public boolean dirty = false;

        public PaymentCounter(double paymen) {
            this.payment = paymen;
        }

        public void addPayment(double pay){
            this.payment = this.payment + pay;
        }

        public void setDirty(boolean dirty){
            this.dirty = dirty;
        }

        public double getPayment(){
            return payment;
        }
    }
}
