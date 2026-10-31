package com.twistylime.bossLand.guidebook.pages;

import com.twistylime.bossLand.BossLand;
import com.twistylime.bossLand.core.BossLandLoot;
import com.twistylime.bossLand.guidebook.PaginatedMenu;
import com.twistylime.bossLand.guidebook.menuutility.PlayerMenuUtility;
import com.twistylime.bossLand.guidebook.search.SearchPromptManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ItemsPageMenu extends PaginatedMenu {

    private ArrayList<ItemStack> items = new ArrayList<>();

    public ItemsPageMenu(PlayerMenuUtility playerMenuUtility) {
        super(playerMenuUtility);
    }

    @Override
    public String getMenuName() {
        return "BossLand | Предметы";
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
                case ARROW:
                    if (page > 0) {
                        page = page - 1;
                        super.open();
                    }
                    break;
                case SPECTRAL_ARROW:
                    if (!((index + 1) >= items.size())) {
                        page = page + 1;
                        super.open();
                    }
                    break;
                default:
                    // вещь: показываем, с каких боссов падает и как её скрафтить
                    openLoot(e.getCurrentItem());
                    break;
            }
        }
    }

    @Override
    public void setMenuItems() {
        addBigMenuBorder();

        BossLandLoot lootManager = BossLand.getLootManager();
        items = new ArrayList<>(lootManager.getWeaponsAndItems());

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
    private void openLoot(ItemStack clicked) {
        if (clicked == null || clicked.getItemMeta() == null) return;
        String name = clicked.getItemMeta().getDisplayName();
        if (name == null || name.isBlank()) return;
        playerMenuUtility.setLootToShow(name);
        new IndividualLootPageMenu(playerMenuUtility).open();
    }
}
