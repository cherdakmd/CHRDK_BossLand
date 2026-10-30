package com.twistylime.bossLand.core;

import com.twistylime.bossLand.BossLand;
import com.twistylime.bossLand.config.BossLandConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class BossLandLoot {
    private final BossLandItems itemManager;
    private final Map<String, String> bossLootMap;

    public BossLandLoot(BossLandItems itemManager, BossLandConfiguration config) {
        this.itemManager = itemManager;
        bossLootMap = config.getBossLootIdMap();
    }

    public List<ItemStack> getArmors(){
        List<ItemStack> armors = new ArrayList<>();
        Map<String, String> bossWithArmorIds = new HashMap<>();
        bossWithArmorIds.put("KingSlime","0");
        bossWithArmorIds.put("KillerBunny","2");
        bossWithArmorIds.put("PapaPanda","1");
        bossWithArmorIds.put("WitherSkeletonKing","2");
        bossWithArmorIds.put("IllagerKing","3");
        bossWithArmorIds.put("DrownedGod","2");
        bossWithArmorIds.put("PharaohGod","2");
        bossWithArmorIds.put("AetherGod","0");
        bossWithArmorIds.put("Devil","0");

        for (Map.Entry<String, String> entry : bossWithArmorIds.entrySet()) {
            String boss = entry.getKey();
            String armorId = entry.getValue();
            armors.add(getLoot(boss,armorId));
        }
        return armors;
    }

    public List<ItemStack> getWeaponsAndItems(){
        List<ItemStack> itemsAndWeapons = new ArrayList<>();
        Map<String, List<String>> bossWithItemAndWeaponIds = new HashMap<>();
        bossWithItemAndWeaponIds.put("GhastLord",List.of("2","3"));
        bossWithItemAndWeaponIds.put("WitherSkeletonKing",List.of("1"));
        bossWithItemAndWeaponIds.put("ZombieKing",List.of("1","2","3"));
        bossWithItemAndWeaponIds.put("IllagerKing",List.of("4"));
        bossWithItemAndWeaponIds.put("EvilWizard",List.of("3"));
        bossWithItemAndWeaponIds.put("Giant",List.of("1","2"));
        bossWithItemAndWeaponIds.put("DrownedGod",List.of("0","1"));
        bossWithItemAndWeaponIds.put("PharaohGod",List.of("0","1"));
        bossWithItemAndWeaponIds.put("AetherGod",List.of("1","2"));
        bossWithItemAndWeaponIds.put("Demon",List.of("1"));
        bossWithItemAndWeaponIds.put("Devil",List.of("1"));
        bossWithItemAndWeaponIds.put("Death",List.of("0","1"));

        for (Map.Entry<String, List<String>> entry : bossWithItemAndWeaponIds.entrySet()) {
            String boss = entry.getKey();
            List<String> weaponAndItemIds = entry.getValue();
            for(String itemId: weaponAndItemIds){
                itemsAndWeapons.add(getLoot(boss,itemId));
            }
        }
        return itemsAndWeapons;
    }

    /**
     * Все осколки, которые должен видеть игрок.
     *
     * Список ищется по всему дропу боссов, а не вписан в код: раньше он перечислял десять пар
     * «босс — номер лута», и осколок любого нового босса в руководство просто не попадал.
     */
    public List<ItemStack> getShards(){
        List<ItemStack> shards = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (String bossWithId : new TreeSet<>(bossLootMap.keySet())) {
            int colon = bossWithId.lastIndexOf(':');
            if (colon <= 0) continue;
            ItemStack item = getLoot(bossWithId.substring(0, colon), bossWithId.substring(colon + 1));
            if (item == null || item.getItemMeta() == null) continue;
            String name = item.getItemMeta().getDisplayName();
            if (name == null || name.isBlank() || !seen.add(name)) continue;
            if (isShard(item)) shards.add(item);
        }

        // осколок Края не падает с боссов — его крафтят, но в руководстве он тоже должен быть
        ItemStack crystalShard = itemManager.getEnderCrystalShard();
        if (crystalShard != null && crystalShard.getItemMeta() != null) {
            String name = crystalShard.getItemMeta().getDisplayName();
            if (name != null && !seen.contains(name)) shards.add(crystalShard);
        }
        return shards;
    }

    private boolean isShard(ItemStack item) {
        BossLand plugin = BossLand.getPlugin();
        return plugin != null && plugin.isBossShard(item);
    }

    public List<String> getLootNames(String bossName){
        List<String> lootNames = new ArrayList<>();
        String prefix = bossName + ":";
        for (Map.Entry<String, String> entry : bossLootMap.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                lootNames.add(entry.getValue());
            }
        }
        return lootNames;
    } // for displaying in command rather than using ids - Need to implement in commands section autotab

    public ItemStack getLootFromName(String name, String bossType){
        String id = getLootIdFromName(name.toUpperCase().replace(" ","_"));
        return getLoot(bossType,id);
    }

    private String getLootIdFromName(String lootName){
        for (Map.Entry<String, String> entry : bossLootMap.entrySet()) {
            if (entry.getValue().replace("_"," ").equalsIgnoreCase(lootName.replace("_"," "))) {
                String[] parts = entry.getKey().split(":");
                if (parts.length == 2) {
                    return parts[1];
                }
            }
        }
        return null;
    }

    private ItemStack getLoot(String bossType, String lootId){
        return itemManager.getItem(bossType, lootId);
    }
}
