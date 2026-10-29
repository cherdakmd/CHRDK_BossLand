package com.twistylime.bossLand.guidebook.pages;

import com.twistylime.bossLand.BossLand;
import com.twistylime.bossLand.guidebook.PaginatedMenu;
import com.twistylime.bossLand.guidebook.menuutility.PlayerMenuUtility;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InfoPageMenu extends PaginatedMenu {

    public InfoPageMenu(PlayerMenuUtility playerMenuUtility) {
        super(playerMenuUtility);
    }

    @Override
    public String getMenuName() {
        return "BossLand | Информация о плагине";
    }

    @Override
    public int getSlots() {
        return 27;
    }

    @Override
    public void handleMenu(InventoryClickEvent e) {
        ItemStack itemClicked = e.getCurrentItem();
        if(itemClicked != null){
            switch (itemClicked.getType()){
                case WRITABLE_BOOK:
                    new GuideMainMenu(playerMenuUtility).open();
                    break;
                case BARRIER:
                    e.getWhoClicked().closeInventory();
                case NAME_TAG:
                    e.getWhoClicked().sendMessage("Эта функция находится в разработке и будет доступна в ближайших обновлениях.");
                    e.getWhoClicked().closeInventory();
                    break;
                case NETHER_STAR:
                    TextComponent message = new TextComponent("§a§lНажмите, чтобы открыть страницу плагина!");
                    message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.spigotmc.org/resources/boss-land.127099/"));
                    e.getWhoClicked().spigot().sendMessage(message);
                    e.getWhoClicked().closeInventory();
                    break;
                default:
                    break;
            }
        }
    }

    /** Версия берётся из plugin.yml: раньше здесь была жёсткая строка, которая отставала от релизов. */
    private static String pluginVersion() {
        var plugin = BossLand.getPlugin();
        if (plugin == null || plugin.getDescription() == null) return "?";
        return plugin.getDescription().getVersion();
    }

    @Override
    public void setMenuItems() {
        addSmallCreditMenuBorder();

        List<ItemStack> credits = new ArrayList<>();

        Map<String, List<String>> creditData = new LinkedHashMap<>();
        creditData.put(ChatColor.GOLD + "" + ChatColor.BOLD + "Версия плагина",
                List.of(ChatColor.YELLOW + "BossLand v" + pluginVersion()));
        creditData.put(ChatColor.AQUA + "" + ChatColor.BOLD + "Поддерживаемые версии Minecraft",
                List.of(ChatColor.GREEN + "1.21.5 – 26.3"));
        creditData.put(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Страница плагина",
                List.of(ChatColor.WHITE + "MIT License"));

        int[] slots = {11, 13, 15};
        int index = 0;

        for (Map.Entry<String, List<String>> entry : creditData.entrySet()) {
            ItemStack newCredit = new ItemStack(Material.NETHER_STAR, 1);
            ItemMeta meta = newCredit.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(entry.getKey()); // bold colored title
                meta.setLore(entry.getValue());     // description as lore
                newCredit.setItemMeta(meta);
            }
            credits.add(newCredit);
            inventory.setItem(slots[index++], newCredit);
        }
    }
}
