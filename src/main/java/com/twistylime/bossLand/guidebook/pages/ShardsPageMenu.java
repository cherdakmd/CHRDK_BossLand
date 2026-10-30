package com.twistylime.bossLand.guidebook.pages;

import com.twistylime.bossLand.BossLand;
import com.twistylime.bossLand.core.BossLandLoot;
import com.twistylime.bossLand.core.BossLandRecipes;
import com.twistylime.bossLand.guidebook.PaginatedMenu;
import com.twistylime.bossLand.guidebook.menuutility.PlayerMenuUtility;
import com.twistylime.bossLand.guidebook.search.SearchPromptManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ShardsPageMenu extends PaginatedMenu {

    private ArrayList<ItemStack> items = new ArrayList<>();

    public ShardsPageMenu(PlayerMenuUtility playerMenuUtility) {
        super(playerMenuUtility);
    }

    @Override
    public String getMenuName() {
        return "BossLand | Осколки";
    }

    @Override
    public int getSlots() {
        return 54;
    }

    @Override
    public void handleMenu(InventoryClickEvent e) {
        ItemStack itemClicked = e.getCurrentItem();
        Player playerWhoClicked = (Player) e.getWhoClicked();
        if(itemClicked != null){
            switch (itemClicked.getType()){
                case BARRIER:
                    playerWhoClicked.closeInventory();
                    break;
                case NAME_TAG:
                    SearchPromptManager.begin(playerWhoClicked);
                    break;
                case ARROW:
                    if (page > 0){
                        page = page - 1;
                        super.open();
                    }
                    break;
                case SPECTRAL_ARROW:
                    if (!((index + 1) >= items.size())){
                        page = page + 1;
                        super.open();
                    }
                    break;
                case WRITABLE_BOOK:
                    new GuideMainMenu(playerMenuUtility).open();
                    break;
                default:
                    // осколок: показываем, с каких боссов он падает и что из него крафтится
                    if (!BossLand.getPlugin().isBossShard(itemClicked))
                        break;
                    ItemMeta clickedMeta = itemClicked.getItemMeta();
                    if (clickedMeta == null)
                        break;
                    playerMenuUtility.setShardToShow(clickedMeta.getDisplayName());
                    new IndividualShardPageMenu(playerMenuUtility).open();
                    break;
            }
        }
    }

    @Override
    public void setMenuItems() {
        addBigMenuBorder();

        BossLandLoot lootManager = BossLand.getLootManager();
        items = new ArrayList<>(lootManager.getShards());

        // К каждому осколку дописываем, во что он идёт: список берём из recipes.yml,
        // поэтому он не устареет, даже если рецепты поменяют.
        BossLandRecipes recipesManager = BossLand.getRecipeManager();
        if (recipesManager != null) {
            ArrayList<ItemStack> described = new ArrayList<>();
            for (ItemStack shard : items)
                described.add(withCraftList(shard, recipesManager));
            items = described;
        }

        if(!items.isEmpty()) {
            for(int i = 0; i < getMaxItemsPerPage(); i++) {
                index = getMaxItemsPerPage() * page + i;
                if(index >= items.size()) break;
                if (items.get(index) != null){
                    ItemStack bossItem = items.get(index);
                    inventory.addItem(bossItem);
                }
            }
        }

    }

    /** Дописывает в лор осколка список предметов, в крафт которых он идёт. */
    private ItemStack withCraftList(ItemStack shard, BossLandRecipes recipesManager) {
        List<String> crafts = recipesManager.craftsFrom(shard);
        if (crafts.isEmpty())
            return shard;
        ItemStack described = shard.clone();
        ItemMeta meta = described.getItemMeta();
        if (meta == null)
            return shard;
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.add("");
        lore.add("§6§lИдёт в крафт:");
        lore.add("§f" + String.join("§7, §f", crafts));
        meta.setLore(lore);
        described.setItemMeta(meta);
        return described;
    }

}
