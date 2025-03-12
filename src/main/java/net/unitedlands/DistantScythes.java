package net.unitedlands;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DistantScythes extends JavaPlugin implements Listener {

    private Set<String> validScytheIds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        List<String> scytheList = config.getStringList("scythes");
        validScytheIds = new HashSet<>();
        for (String id : scytheList) {
            validScytheIds.add(id.toLowerCase());
        }

        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only process right-clicking a block (ignore left click and air clicks).
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType() == Material.AIR) {
            return;
        }

        // Check against the list from the config file for valid scythes.
        CustomStack customStack = CustomStack.byItemStack(heldItem);
        if (customStack == null || !validScytheIds.contains(customStack.getId().toLowerCase())) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Check if the clicked block is an ageable crop and is fully grown.
        if (!(clickedBlock.getBlockData() instanceof Ageable ageableData)) {
            return;
        }
        if (ageableData.getAge() < ageableData.getMaximumAge()) {
            return;
        }

        // Cancel the event so that vanilla interaction does not occur.
        event.setCancelled(true);

        // Get the corresponding seed for this crop.
        Material seedMaterial = getSeedMaterial(clickedBlock);
        boolean hasSeed = (seedMaterial != null && removeSeed(player, seedMaterial));

        // Harvest the crop, get the drops as if broken with the held tool.
        Collection<ItemStack> drops = clickedBlock.getDrops(heldItem);
        // Drop each item at the block location.
        for (ItemStack drop : drops) {
            // Skip one seed from the drops so it’s not duplicated.
            if (hasSeed && seedMaterial != null && drop.getType() == seedMaterial && drop.getAmount() > 0) {
                drop.setAmount(drop.getAmount() - 1);
                // Only remove one seed from the drop list.
                hasSeed = false;
                if (drop.getAmount() > 0) {
                    clickedBlock.getWorld().dropItemNaturally(clickedBlock.getLocation(), drop);
                }
            } else {
                clickedBlock.getWorld().dropItemNaturally(clickedBlock.getLocation(), drop);
            }
        }

        // Store the original crop block type before removing it.
        Material originalType = clickedBlock.getType();

        // Remove the crop block so that the drops are not duplicated by a natural block break.
        clickedBlock.setType(Material.AIR);

        // If the player had a seed in inventory, replant the crop.
        if (seedMaterial != null) {
            clickedBlock.setType(originalType);
            Ageable newAgeable = (Ageable) clickedBlock.getBlockData();
            newAgeable.setAge(0);
            clickedBlock.setBlockData(newAgeable);
        }

        // Reduce durability of the scythe tool.
        reduceItemDurability(player, heldItem);
    }

    // Gets the proper seed item for a given crop block.
    private Material getSeedMaterial(Block block) {
        Material type = block.getType();
        if (type == Material.WHEAT) {
            return Material.WHEAT_SEEDS;
        } else if (type == Material.CARROTS) {
            return Material.CARROT;
        } else if (type == Material.POTATOES) {
            return Material.POTATO;
        } else if (type == Material.BEETROOTS) {
            return Material.BEETROOT;
        }
        return null;
    }

    // Dynamically removes seeds to avoid duplicates.
    private boolean removeSeed(Player player, Material seedMaterial) {
        ItemStack seedStack = new ItemStack(seedMaterial, 1);
        return player.getInventory().removeItem(seedStack).isEmpty();
    }

    // Reduce the durability of an item, if it is less than 1 it will break.
    private void reduceItemDurability(Player player, ItemStack item) {
        if (!(item.getItemMeta() instanceof Damageable meta)) return;
        int newDamage = meta.getDamage() + 1;
        int maxDurability = item.getType().getMaxDurability();

        if (newDamage >= maxDurability) {
            // Play break sound effect at the player's location.
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            // Remove the broken item.
            if (player.getInventory().getItemInMainHand().equals(item)) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                player.getInventory().remove(item);
            }
        } else {
            meta.setDamage(newDamage);
            item.setItemMeta(meta);
        }
    }
}