package ru.mcfine.mcfinefarm;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import javax.security.auth.login.Configuration;
import java.util.*;

public final class McfineFarm extends JavaPlugin {

    public static HashMap<String, ProtectedRegion> regions = new HashMap<>();
    public static HashMap<ProtectedRegion, Integer> regionMined = new HashMap<>();
    public static HashSet<String> worlds = new HashSet<>();
    //public static HashMap<ProtectedRegion, FarmBlock> regionBlocks = new HashMap<>();
    public static HashMap<Material, FarmBlock> globalBlocks = new HashMap<>();
    public static Economy econ = null;
    public static JavaPlugin plugin;
    public static String currency = "$";
    public static String actionbarMessage = "<gold>Вы заработали <green><pay><currency><gold>!";
    public static double minAnnounce = 0.5;
    public static HashMap<ProtectedRegion, BukkitTask> updateTasks = new HashMap<>();
    public static HashMap<ProtectedRegion, ArrayList<Location>> cachedLocations = new HashMap<>();
    public static HashMap<ProtectedRegion, List<Material>> blockReplaced = new HashMap<>();
    public static HashMap<ProtectedRegion, List<BlockReplacer>> blockPool = new HashMap<>();
    public static HashMap<ProtectedRegion, List<Material>> blockCached = new HashMap<>();
    public static HashMap<ProtectedRegion, Double> replaceVolume = new HashMap<>();
    public static HashMap<ProtectedRegion, String> regionPermissions = new HashMap<>();
    public static String notReady = "<red>Растение еще недостаточно выросло!";
    public static String cancelled = "<red>Вы не можете ломать этот блок здесь!";
    public static long replaceBlockTimer = 5;
    public static long cooldownActionbar = 20;
    public static long keepInfoActionbar = 120;
    public static int replaceAmount = 3;
    public static int dropXp = 0;
    public static String moneyFormat = "##0.0";
    public static HashMap<String, ProtectedRegion> lumbermills = new HashMap<>();

    @Override
    public void onEnable() {
        plugin = this;
        if (!setupEconomy() ) {
            getLogger().severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getConfig().options().copyDefaults();
        saveDefaultConfig();

        init();
    }

    public void init(){
        try {
            currency = getConfig().getString("currency");
        } catch (Exception ignored){}

        try {
            replaceBlockTimer = getConfig().getLong("replace-block-timer");
        } catch (Exception ignored){}

        try {
            cooldownActionbar = getConfig().getLong("cooldown-actionbar");
        } catch (Exception ignored){}

        try {
            keepInfoActionbar = getConfig().getLong("keep-info-actionbar");
        } catch (Exception ignored){}

        try {
            replaceAmount = getConfig().getInt("replace-amount");
        } catch (Exception ignored){}

        try {
            cancelled = getConfig().getString("cancelled");
        } catch (Exception ignored){}

        try {
            notReady = getConfig().getString("not-ready");
        } catch (Exception ignored){}

        try {
            actionbarMessage = getConfig().getString("action-bar");
        } catch (Exception ignored){}

        try {
            minAnnounce = getConfig().getDouble("min-announce");
        } catch (Exception ignored){}

        try {
            dropXp = getConfig().getInt("drop-xp");
        } catch (Exception ignored){}

        try {
            moneyFormat = getConfig().getString("money-format");
        } catch (Exception ignored){}


        getServer().getPluginManager().registerEvents(new Listeners(), this);

        for(String s : this.getConfig().getStringList("regions")){
            try {
                String regionName = s.split(":")[0];
                String worldName = s.split(":")[1];

                World world = Bukkit.getWorld(worldName);
                if(world==null) continue;

                RegionContainer regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
                ProtectedRegion region = regionContainer.get(BukkitAdapter.adapt(world)).getRegion(regionName);
                regions.put(regionName, region);
                worlds.add(worldName);
                regionMined.put(region, 0);
            } catch (Exception ex){
                ex.printStackTrace();
            }
        }

        for(String s : this.getConfig().getStringList("lumbermills")){
            try {
                String regionName = s.split(":")[0];
                String worldName = s.split(":")[1];

                World world = Bukkit.getWorld(worldName);
                if(world==null) continue;

                RegionContainer regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
                ProtectedRegion region = regionContainer.get(BukkitAdapter.adapt(world)).getRegion(regionName);
                lumbermills.put(regionName, region);
                worlds.add(worldName);
            } catch (Exception ex){
                ex.printStackTrace();
            }
        }

        HashMap<ProtectedRegion, List<Material>> regionReplacer = new HashMap<>();
        try {
            Set<String> regionNames = this.getConfig().getConfigurationSection("blocks-to-replace").getKeys(false);
            for(String s : regionNames){
                List<String> blockList = this.getConfig().getStringList("blocks-to-replace."+s);
                List<Material> materialList = new ArrayList<>();
                for(String block : blockList){
                    if(block.equalsIgnoreCase("none")) break;
                    Material blockMaterial = Material.getMaterial(block.toUpperCase());
                    materialList.add(blockMaterial);
                }
                regionReplacer.put(regions.get(s), materialList);
            }
            blockReplaced = regionReplacer;
        } catch (Exception ex){
            ex.printStackTrace();
        }


        HashMap<ProtectedRegion, List<BlockReplacer>> regionReplacer2 = new HashMap<>();
        try {
            Set<String> regionNames = this.getConfig().getConfigurationSection("blocks-replace-with").getKeys(false);
            for(String s : regionNames){
                List<String> blockList = this.getConfig().getStringList("blocks-replace-with."+s);
                List<BlockReplacer> materialList = new ArrayList<>();
                for(String block : blockList){
                    if(block.equalsIgnoreCase("none")) break;
                    Material blockMaterial = Material.getMaterial(block.split(":")[0].toUpperCase());
                    materialList.add(new BlockReplacer(blockMaterial, Double.parseDouble(block.split(":")[1])));
                }
                regionReplacer2.put(regions.get(s), materialList);
            }
            blockPool = regionReplacer2;
        } catch (Exception ex){
            ex.printStackTrace();
        }

        for(Map.Entry<ProtectedRegion, List<BlockReplacer>> entry : blockPool.entrySet()){
            double volume = 0.0;

            for(BlockReplacer blockReplacer : entry.getValue()){
                volume+=blockReplacer.weight;
            }

            replaceVolume.put(entry.getKey(), volume);
        }

        HashMap<ProtectedRegion, List<Material>> regionCache = new HashMap<>();
        try {
            Set<String> regionNames = this.getConfig().getConfigurationSection("blocks-to-cache").getKeys(false);
            for(String s : regionNames){
                List<String> blockList = this.getConfig().getStringList("blocks-to-cache."+s);
                List<Material> materialList = new ArrayList<>();
                for(String block : blockList){
                    if(block.equalsIgnoreCase("none")) break;
                    Material blockMaterial = Material.getMaterial(block.toUpperCase());
                    materialList.add(blockMaterial);
                }
                regionCache.put(regions.get(s), materialList);
            }
            blockCached = regionCache;
        } catch (Exception ex){
            ex.printStackTrace();
        }


        HashMap<ProtectedRegion, String> permissionsTemp = new HashMap<>();
        try {
            Set<String> regionNames = this.getConfig().getConfigurationSection("region-permissions").getKeys(false);
            for(String s : regionNames){
                String perm = this.getConfig().getString("region-permissions."+s);
                permissionsTemp.put(regions.get(s), perm);
            }
            regionPermissions = permissionsTemp;
        } catch (Exception ex){
            ex.printStackTrace();
        }


        for(String s : this.getConfig().getStringList("regions")){
            String regionName = s.split(":")[0];
            String worldName = s.split(":")[1];
            World world = Bukkit.getWorld(worldName);
            if(world==null) continue;
            RegionContainer regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
            ProtectedRegion region = regionContainer.get(BukkitAdapter.adapt(world)).getRegion(regionName);
            HashSet<Location> openBlocks = new HashSet<>();
            if(blockCached.get(region).size() != 0) {
                for (BlockVector3 vector3 : new CuboidRegion(BukkitAdapter.adapt(world), region.getMinimumPoint(), region.getMaximumPoint())) {
                    int x = vector3.getBlockX();
                    int y = vector3.getBlockY();
                    int z = vector3.getBlockZ();
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.getType().equals(Material.AIR)){
                        if(block.getType().equals(Material.COBBLESTONE)){
                            block.getWorld().setType(block.getLocation(), Material.STONE);
                        }
                        continue;
                    }
                    if (blockCached.get(region).contains(world.getBlockAt(x + 1, y, z).getType())) {
                        openBlocks.add(new Location(world, x + 1, y, z));
                        continue;
                    }
                    if (blockCached.get(region).contains(world.getBlockAt(x - 1, y, z).getType())) {
                        openBlocks.add(new Location(world, x - 1, y, z));
                        continue;
                    }
                    if (blockCached.get(region).contains(world.getBlockAt(x, y + 1, z).getType())) {
                        openBlocks.add(new Location(world, x, y + 1, z));
                        continue;
                    }
                    if (blockCached.get(region).contains(world.getBlockAt(x, y - 1, z).getType())) {
                        openBlocks.add(new Location(world, x, y - 1, z));
                        continue;
                    }
                    if (blockCached.get(region).contains(world.getBlockAt(x, y, z + 1).getType())) {
                        openBlocks.add(new Location(world, x, y, z + 1));
                        continue;
                    }
                    if (blockCached.get(region).contains(world.getBlockAt(x, y, z - 1).getType())) {
                        openBlocks.add(new Location(world, x, y, z - 1));
                    }
                }
            }
            cachedLocations.put(region, new ArrayList<>(openBlocks));
        }




        Set<String> blocks = new HashSet<>();
        try {
            blocks = this.getConfig().getConfigurationSection("global-blocks").getKeys(false);
        } catch (Exception ex){
            ex.printStackTrace();
        }


        ConfigurationSection section = this.getConfig().getConfigurationSection("global-blocks");
        for(String s : blocks){
            Material material = Material.AIR;
            double payout = 1.0;
            try {
                material = Material.getMaterial(section.getConfigurationSection(s).getString("replace").toUpperCase());
                payout = section.getConfigurationSection(s).getDouble("payout");
                globalBlocks.put(Material.getMaterial(s.toUpperCase()), new FarmBlock(material, payout));
            } catch (Exception ex){
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        for(BukkitTask task : updateTasks.values()){
            if(task!=null && !task.isCancelled()) task.cancel();
        }
        if(Listeners.cooldownTask != null && !Listeners.cooldownTask.isCancelled()) Listeners.cooldownTask.cancel();
        cachedLocations= new HashMap<>();
        globalBlocks = new HashMap<>();
        worlds = new HashSet<>();
        regionMined = new HashMap<>();
        regions = new HashMap<>();
        blockReplaced = new HashMap<>();
        blockPool = new HashMap<>();
        replaceVolume = new HashMap<>();
        regionPermissions = new HashMap<>();
        lumbermills = new HashMap<>();
    }

    public static void incrementMinedBlocks(ProtectedRegion region){
        if(blockPool.get(region).size() == 0) return;
        if(regionMined.get(region) == 0){
            if(updateTasks.containsKey(region) && updateTasks.get(region) != null && !updateTasks.get(region).isCancelled()) updateTasks.get(region).cancel();
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    boolean res = updateRegion(region);
                    if(res) regionMined.put(region, regionMined.get(region)-1);
                    if(regionMined.get(region) == 0) this.cancel();
                }
            }.runTaskTimer(McfineFarm.plugin, replaceBlockTimer, replaceBlockTimer);
            updateTasks.put(region, task);
        } else{
            if(!updateTasks.containsKey(region)) {
                BukkitTask task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        boolean res = updateRegion(region);
                        if (res) regionMined.put(region, regionMined.get(region) - 1);
                        if (regionMined.get(region) == 0) this.cancel();
                    }
                }.runTaskTimer(McfineFarm.plugin, replaceBlockTimer, replaceBlockTimer);
                updateTasks.put(region, task);
            }
        }
        regionMined.put(region, regionMined.get(region)+1);
    }

    public static Material getReplacer(ProtectedRegion region){
        double volume = replaceVolume.get(region);
        double rng = Math.random()*volume;
        double counter = 0;
        Material material = Material.BEDROCK;
        for(BlockReplacer blockReplacer : blockPool.get(region)){
            counter+=blockReplacer.weight;
            material = blockReplacer.material;
            if(counter<=rng){
                break;
            }
        }
        return material;
    }

    public static boolean updateRegion(ProtectedRegion region){
        for(int i = 0;i<replaceAmount;i++) {
            for (int j = 0; j < 3; j++) {
                Location location = cachedLocations.get(region).get((int) (Math.random() * (cachedLocations.get(region).size())));
                if (blockReplaced.get(region).contains(location.getBlock().getType())) {
                    location.getWorld().setType(location.toBlockLocation(), getReplacer(region));
                    return true;
                }
            }
        }
        return false;
    }

    private static class BlockReplacer{
        public Material material;
        public double weight;

        public BlockReplacer(Material material, double weight) {
            this.material = material;
            this.weight = weight;
        }
    }


    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return true;
    }
}
