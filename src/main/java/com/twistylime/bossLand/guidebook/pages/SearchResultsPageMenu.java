package com.twistylime.bossLand.guidebook.pages;

import com.twistylime.bossLand.BossLand;
import com.twistylime.bossLand.guidebook.PaginatedMenu;
import com.twistylime.bossLand.guidebook.menuutility.PlayerMenuUtility;
import com.twistylime.bossLand.guidebook.search.GuideSearch;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Результаты поиска по руководству: клик открывает страницу рецепта. */
public class SearchResultsPageMenu extends PaginatedMenu {

    private static final NamespacedKey KEY_DATA = new NamespacedKey(BossLand.getPlugin(), "data");
    private static final NamespacedKey KEY_KIND = new NamespacedKey(BossLand.getPlugin(), "searchKind");

    public SearchResultsPageMenu(PlayerMenuUtility playerMenuUtility) {
        super(playerMenuUtility);
    }

    @Override
    public String getMenuName() {
        String query = playerMenuUtility.getSearchQuery();
        String title = "BossLand | Поиск: " + query;
        return title.length() > 32 ? title.substring(0, 32) : title;
    }

    @Override
    public int getSlots() {
        return 54;
    }

    @Override
    public void handleMenu(InventoryClickEvent e) {
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        switch (clicked.getType()) {
            case BARRIER -> e.getWhoClicked().closeInventory();
            case WRITABLE_BOOK -> new GuideMainMenu(playerMenuUtility).open();
            case NAME_TAG -> e.getWhoClicked().sendMessage("§7Вы уже в поиске: нажмите §fПоиск§7 в любом меню.");
            default -> {
                String key = clicked.getItemMeta().getPersistentDataContainer().get(KEY_DATA, PersistentDataType.STRING);
                String kind = clicked.getItemMeta().getPersistentDataContainer().get(KEY_KIND, PersistentDataType.STRING);
                if (key == null || kind == null) return;
                if (GuideSearch.Kind.BOSS.name().equals(kind)) {
                    playerMenuUtility.setBossRecipeToShow(key);
                    new IndividualBossRecipePageMenu(playerMenuUtility).open();
                } else {
                    playerMenuUtility.setItemRecipeToShow(key);
                    new IndividualItemRecipePageMenu(playerMenuUtility).open();
                }
            }
        }
    }

    @Override
    public void setMenuItems() {
        addBigMenuBorder();

        List<GuideSearch.Entry> results = GuideSearch.search(playerMenuUtility.getSearchQuery());
        List<ItemStack> icons = new ArrayList<>();
        for (GuideSearch.Entry entry : results) {
            icons.add(asIcon(entry));
        }

        if (icons.isEmpty()) {
            inventory.setItem(22, createItem(Material.BARRIER,
                    ChatColor.RED + "Ничего не найдено", false));
            return;
        }

        for (int i = 0; i < getMaxItemsPerPage(); i++) {
            index = getMaxItemsPerPage() * page + i;
            if (index >= icons.size()) break;
            inventory.addItem(icons.get(index));
        }
    }

    private ItemStack asIcon(GuideSearch.Entry entry) {
        ItemStack icon = entry.icon() == null ? new ItemStack(Material.PAPER) : entry.icon().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.add("");
        lore.add("§8Категория: §7" + entry.kind().label());
        lore.add("§eНажмите, чтобы открыть");
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(KEY_DATA, PersistentDataType.STRING, entry.key());
        meta.getPersistentDataContainer().set(KEY_KIND, PersistentDataType.STRING, entry.kind().name());

        icon.setItemMeta(meta);
        return icon;
    }
}
