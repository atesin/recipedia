
package cl.netgamer.recipedia;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;


/**
This class holds original player inventory contents and wraps player inventory methods.
It has 3 main methods, for setting crafting inventory, storage and hotbar;
caller methods are responsibile for update it. It also restores player inventory
 */

class InventoryKeeper
{
	private Main plugin;
	private Player player;
	private ItemStack[] inventoryBackup;
	private ItemCycler cycler;
	
	/** modifies the player inventory directly, caller methods are responsibile for update it */
	InventoryKeeper(Main plugin, Player player)
	{
		this.plugin = plugin;
		this.player = player;
		inventoryBackup = player.getInventory().getContents();
		cycler = new ItemCycler(plugin, player);
	}
	
	
	/** display components (and cycle them) on a new crafting inventory according recipe */
	void setCrafting(Recipe recipe, ItemStack product)
	{
		if ( recipe instanceof FurnaceRecipe )
		{
			FurnaceInventory furnace = (FurnaceInventory) player.openInventory(plugin.getServer().createInventory(player, InventoryType.FURNACE)).getTopInventory();
			// if above openInventory() is called from InventoryClickEvent, it calls registered InventoryCloseEvent from here
			furnace.setResult(product);
			furnace.setSmelting(plugin.recipes.getIngredient(recipe));
			cycler.randomizeFuel(furnace);
			return;
		}
		
		Workbench workbench = new Workbench(plugin, player);
		workbench.setResult(product);
		player.openInventory(workbench.getInventory()).getTopInventory();
		// if above openInventory() is called from InventoryClickEvent, it calls registered InventoryCloseEvent from here 
		
		if ( recipe instanceof ShapelessRecipe )
			workbench.setMatrix(plugin.recipes.getIngredients(recipe));
		else if ( recipe instanceof ShapedRecipe )
			cycler.cycleIngredients(workbench, recipe);
	}
	
	
	/** set player inventory contents in storage slots */
	void setStorage(List<ItemStack> results)
	{
		PlayerInventory backpack = player.getInventory();
		int index = 0;
		while ( index < results.size() && index < 27 )
			backpack.setItem(index + 9, results.get(index++));
		while ( index < 27 )	
			backpack.setItem(index++ + 9, null);
	}
	
	
	/** set tab icons (paper items with lore) in player hotbar */
	void setHotbar(int tabs)
	{
		PlayerInventory backpack = player.getInventory();
		int tab = 0;
		
		while ( tab < tabs )
		{
			ItemStack icon = new ItemStack(Material.PAPER, tab + 1);
			
			List<String> lores = new ArrayList<String>();
			lores.add(plugin.msg("pageNumber", tab + 1));
			ItemMeta meta = icon.getItemMeta();
			meta.setLore(lores);
			icon.setItemMeta(meta);

			backpack.setItem(tab++, icon);
		}
		while ( tab < 9 )
			backpack.setItem(tab++, null);
	}
	
	
	/** clean up things and restore player inventory */
	void restore()
	{
		player.getOpenInventory().getTopInventory().clear();
		player.getInventory().setContents(inventoryBackup);
		player.updateInventory();
	}

}
