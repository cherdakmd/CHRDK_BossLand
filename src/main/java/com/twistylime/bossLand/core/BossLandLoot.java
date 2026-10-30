package com.twistylime.bossLand.core;

import com.twistylime.bossLand.BossLand;
import com.twistylime.bossLand.config.BossLandConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Predicate;

public class BossLandLoot {
    private final BossLandItems itemManager;
    private final Map<String, String> bossLootMap;

    public BossLandLoot(BossLandItems itemManager, BossLandConfiguration config) {
        this.itemManager = itemManager;
        bossLootMap = config.getBossLootIdMap();
    }

    /**
     * Броня из дропа боссов. Раньше список был вписан в код девятью парами «босс — номер лута»,
     * из-за чего вещи новых боссов в руководство не попадали.
     */
    public List<ItemStack> getArmors(){
        return lootMatching(BossLandLoot::isArmor);
    }

    /**
     * Оружие и прочие вещи из дропа боссов: всё, что не осколок и не броня.
     *
     * Список ищется по всему луту, а не перечислен в коде: из-за перечисления в руководстве
     * не было ни Морозного Клинка, ни Зова Прилива, ни Тесака Инферно, ни Клинка Пустоты,
     * ни Зелёного Стража, ни мечей Гнева — то есть всего оружия боссов 3.8.
     */
    public List<ItemStack> getWeaponsAndItems(){
        return lootMatching(item -> !isArmor(item) && !isShard(item));
    }

    /**
     * Все осколки, которые должен видеть игрок.
     *
     * Список ищется по всему дропу боссов, а не вписан в код: раньше он перечислял десять пар
     * «босс — номер лута», и осколок любого нового босса в руководство просто не попадал.
     */
    public List<ItemStack> getShards(){
        List<ItemStack> shards = lootMatching(this::isShard);

        // осколок Края не падает с боссов — его крафтят, но в руководстве он тоже должен быть
        ItemStack crystalShard = itemManager.getEnderCrystalShard();
        if (crystalShard == null || crystalShard.getItemMeta() == null)
            return shards;
        String crystalName = crystalShard.getItemMeta().getDisplayName();
        if (crystalName == null || crystalName.isBlank())
            return shards;
        for (ItemStack shard : shards) {
            if (crystalName.equals(displayNameOf(shard)))
                return shards;
        }
        shards.add(crystalShard);
        return shards;
    }

    /**
     * Перебирает весь лут боссов и собирает подходящие под условия предметы.
     * Одинаковые по имени вещи попадают в список один раз.
     */
    private List<ItemStack> lootMatching(Predicate<ItemStack> filter) {
        List<ItemStack> found = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (String bossWithId : new TreeSet<>(bossLootMap.keySet())) {
            int colon = bossWithId.lastIndexOf(':');
            if (colon <= 0) continue;
            ItemStack item = getLoot(bossWithId.substring(0, colon), bossWithId.substring(colon + 1));
            if (item == null || item.getItemMeta() == null) continue;
            String name = item.getItemMeta().getDisplayName();
            if (name == null || name.isBlank() || !seen.add(name)) continue;
            if (filter.test(item)) found.add(item);
        }
        return found;
    }

    private static boolean isArmor(ItemStack item) {
        if (item == null) return false;
        String type = item.getType().name();
        return type.endsWith("_HELMET") || type.endsWith("_CHESTPLATE")
                || type.endsWith("_LEGGINGS") || type.endsWith("_BOOTS")
                || type.equals("ELYTRA");
    }

    private boolean isShard(ItemStack item) {
        BossLand plugin = BossLand.getPlugin();
        return plugin != null && plugin.isBossShard(item);
    }

    private static String displayNameOf(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        String name = item.getItemMeta().getDisplayName();
        return name == null || name.isBlank() ? null : name;
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
