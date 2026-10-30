package com.twistylime.bossLand.guidebook.pages;

import com.twistylime.bossLand.BossLand;
import com.twistylime.bossLand.core.BossLandShrines;
import com.twistylime.bossLand.guidebook.PaginatedMenu;
import com.twistylime.bossLand.guidebook.menuutility.PlayerMenuUtility;
import com.twistylime.bossLand.guidebook.search.SearchPromptManager;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class BossesPageMenu extends PaginatedMenu {
    public BossesPageMenu(PlayerMenuUtility playerMenuUtility) {
        super(playerMenuUtility);
    }

    @Override
    public String getMenuName() {
        return "BossLand | Боссы";
    }

    @Override
    public int getSlots() {
        return 54;
    }

    @Override
    public void handleMenu(InventoryClickEvent e) {
        ItemStack itemClicked = e.getCurrentItem();
        if(itemClicked != null){
            switch(e.getCurrentItem().getType()){
                case BARRIER:
                    e.getWhoClicked().closeInventory();
                    break;
                case NAME_TAG:
                    SearchPromptManager.begin((Player) e.getWhoClicked());
                    break;
                case WRITABLE_BOOK:
                    new GuideMainMenu(playerMenuUtility).open();
                    break;
                default:
                    // Раньше страница открывалась по типу материала иконки, из-за чего
                    // Ледяной Титан, Левиафан, Повелитель Нижнего мира, Колосс Края и
                    // Хранитель Леса не открывались вовсе. Теперь страница записана в
                    // саму иконку, так что работает любой босс из recipes.yml.
                    ItemMeta clickedItemMeta = e.getCurrentItem().getItemMeta();
                    if (clickedItemMeta == null) break;
                    NamespacedKey key = new NamespacedKey(BossLand.getPlugin(),"data");
                    String bossRecipe = clickedItemMeta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                    if (bossRecipe == null) break;
                    playerMenuUtility.setBossRecipeToShow(bossRecipe);
                    new IndividualBossRecipePageMenu(playerMenuUtility).open();
                    break;
            }
        }
    }

    @Override
    public void setMenuItems() {
        addBigMenuBorder();

        BossLandShrines shrinesManager = BossLand.getShrinesManager();
        // Список берём из recipes.yml: раньше он был вписан в код, и новые боссы (например Гнев)
        // просто не попадали в руководство. Порядок страниц — как в файле.
        ConfigurationSection shrineRecipes = BossLand.getPlugin().getBossConfig()
                .getRecipeConfiguration("shrine_recipes");
        ArrayList<String> bossData = new ArrayList<>();
        if (shrineRecipes != null)
            bossData.addAll(shrineRecipes.getKeys(false));
        ArrayList<ItemStack> bosses = new ArrayList<>();

        for(String boss: bossData){
            Map<String, Object> bossRecipe = shrinesManager.getShrineRecipe(boss);
            if (bossRecipe == null) continue;
            if(boss.contains("god") || boss.contains("devil") || boss.contains("death")){
                ItemStack resultBoss = (ItemStack) bossRecipe.get("result");
                if (resultBoss == null) continue;
                bosses.add(addDataToItem(resultBoss,boss));
            }
            else{
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) bossRecipe.get("result");
                if (resultMap != null) {
                    ItemStack resultBoss = (ItemStack) resultMap.get("item");
                    if (resultBoss == null) continue;
                    bosses.add(addDataToItem(resultBoss,boss));
                }
            }
        }

        if(!bosses.isEmpty()) {
            for(int i = 0; i < getMaxItemsPerPage(); i++) {
                index = getMaxItemsPerPage() * page + i;
                if(index >= bosses.size()) break;
                if (bosses.get(index) != null){
                    ItemStack bossItem = bosses.get(index);
                    inventory.addItem(bossItem);
                }
            }
        }

    }

    private ItemStack addDataToItem(ItemStack item, String data){
        ItemMeta itemToAddDataMeta = item.getItemMeta();
        if(itemToAddDataMeta != null){
            NamespacedKey key = new NamespacedKey(BossLand.getPlugin(),"data");
            itemToAddDataMeta.getPersistentDataContainer().set(key,PersistentDataType.STRING, data);
        }
        item.setItemMeta(itemToAddDataMeta);
        return item;
    }
}
