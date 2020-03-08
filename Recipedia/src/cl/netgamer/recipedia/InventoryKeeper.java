
package cl.netgamer.recipedia;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
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
	
	
	void setCraftingBlank(String title, ItemStack product)
	{
		Workbench workbench = new Workbench(plugin, player, title);
		workbench.setResult(product);
		player.openInventory(workbench.getInventory()).getTopInventory();
		// if above openInventory() is called from InventoryClickEvent, it calls registered InventoryCloseEvent from here 
	}
	
	
	/** display components (and cycle them) on a new crafting inventory according recipe */
	void setCrafting(Recipe recipe, ItemStack product, int resultsCount)
	{
		String title = plugin.msg(resultsCount < 1? recipe.getClass().getSimpleName(): "resultsCount" , resultsCount).substring(2);
		
		 // campfire has not inventory and does not uses fuel (also see stonecutter bug below)
		if ( recipe instanceof CampfireRecipe || recipe instanceof StonecuttingRecipe )
		{
			Inventory furnace = player.openInventory(plugin.getServer().createInventory(player, InventoryType.FURNACE, title)).getTopInventory();
			// if above openInventory() is called from InventoryClickEvent, it calls registered InventoryCloseEvent from here
			furnace.setItem(0, plugin.recipes.getIngredient(recipe));
			furnace.setItem(2, product);
			return;
		}
		
		// other cooking recipes: furnace, blast and smoker
		if ( recipe instanceof CookingRecipe ) // slot 0=crafting, 1=fuel, 2=result
		{
			//FurnaceInventory furnace = (FurnaceInventory) player.openInventory(plugin.getServer().createInventory(player, InventoryType.FURNACE)).getTopInventory();
			Inventory furnace = player.openInventory(plugin.getServer().createInventory(player, InventoryType.FURNACE, title)).getTopInventory();
			// if above openInventory() is called from InventoryClickEvent, it calls registered InventoryCloseEvent from here
			furnace.setItem(0, plugin.recipes.getIngredient(recipe));
			furnace.setItem(2, product);
			cycler.randomizeFuel(furnace);
			return;
		}
		
		/* // BUG: items set in stonecutter inventory render invisible in client, use workaround above
		if ( recipe instanceof StonecuttingRecipe ) // slot 0=crafting, 1=result
		{
			Inventory cutter = player.openInventory(plugin.getServer().createInventory(player, InventoryType.STONECUTTER, title)).getTopInventory();
			// if above openInventory() is called from InventoryClickEvent, it calls registered InventoryCloseEvent from here
			cutter.setItem(0, plugin.recipes.getIngredient(recipe));
			cutter.setItem(1, product);
			// BUG: items are there but invisible
			return;
		}
		*/
		
		Workbench workbench = new Workbench(plugin, player, title);
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
