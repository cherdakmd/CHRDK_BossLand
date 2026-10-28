package com.twistylime.bossLand.guidebook;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class MenuListener implements Listener {

    @EventHandler
    public void onMenuClick(InventoryClickEvent e){
        Inventory topInventory = e.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();
        if(holder instanceof Menu){
            e.setCancelled(true);
            Menu menu = (Menu) holder;
            if(e.getCurrentItem() == null || e.getClickedInventory() == null){
                return;
            }
            if(e.getClickedInventory().equals(topInventory)) {
                // Centralized pagination for PaginatedMenu
                if (menu instanceof PaginatedMenu pm) {
                    if (e.getCurrentItem().getType() == Material.ARROW) {
                        // Previous page
                        if (pm.getPage() > 0) {
                            pm.setPage(pm.getPage() - 1);
                            pm.open();
                        }
                        return;
                    } else if (e.getCurrentItem().getType() == Material.SPECTRAL_ARROW) {
                        // Next page
                        pm.setPage(pm.getPage() + 1);
                        pm.open();
                        return;
                    }
                }
                menu.handleMenu(e);
            }
        }
    }
}
