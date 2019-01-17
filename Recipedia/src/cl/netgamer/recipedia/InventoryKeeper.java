
package cl.netgamer.recipedia;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;


class InventoryKeeper
{
	/*
	standard procedure:
	- set hotbar (can be 0 to clear)
	- set storage
	- set crafting (set recipe and result to null to get an empty workbench)
	*/
	
	private Main plugin;
	private Player player;
	private PlayerInventory playerInventory;
	private ItemStack[] inventoryBackup;

	
	InventoryKeeper(Main plugin, Player player)
	{
		this.player = player;
		this.plugin = plugin;
		playerInventory = player.getInventory();
		inventoryBackup = player.getInventory().getContents();
	}
	
	
	void updateStorage(List<ItemStack> products)
	{
		//// setStorageContents() is bogus
		//for (int i = products.size(); i < 27; ++i)
		//	products.add(null);
		//player.getInventory().setStorageContents(products.toArray(new ItemStack[0]));
		
		PlayerInventory storage = player.getInventory();
		int index = 0;
		while ( index < products.size() )
			storage.setItem(index + 9, products.get(index++));
		while ( index < 27 )	
			storage.setItem(index++ + 9, null);
		
		player.updateInventory();
	}
	
	
	void updateHotbar(int tabs)
	{
		int tab = 0;
		while ( tab < tabs )
		{
			ItemStack icon = new ItemStack(Material.PAPER, tab + 1);
			
			List<String> lores = new ArrayList<String>();
			lores.add(plugin.msg("pageNumber", tab + 1));
			ItemMeta meta = icon.getItemMeta();
			meta.setLore(lores);
			icon.setItemMeta(meta);

			playerInventory.setItem(tab++, icon);
		}
		while ( tab < 9 )
			playerInventory.setItem(tab++, null);
		player.updateInventory();
	}
	
	
	void updateCrafting(Player player, Recipe recipe, ItemStack product)
	{
		if ( recipe instanceof FurnaceRecipe )
		{
			FurnaceInventory furnace = (FurnaceInventory) plugin.getServer().createInventory(player, InventoryType.FURNACE);
			furnace.setResult(product);
			furnace.setSmelting(plugin.recipes.getIngredient((FurnaceRecipe) recipe));
			player.openInventory(furnace);
			shuffleFuel(plugin, player, furnace);
			player.updateInventory();
			return;
		}
		
		//CraftingInventory workbench = (CraftingInventory) Bukkit.createInventory(player, InventoryType.WORKBENCH);
		CraftingInventory workbench = (CraftingInventory) player.openWorkbench(null, true).getTopInventory();
		workbench.setResult(product);
		//player.openInventory(workbench);
		
		if ( recipe instanceof ShapelessRecipe )
			workbench.setMatrix(plugin.recipes.getIngredients((ShapelessRecipe) recipe));
		else if ( recipe instanceof ShapedRecipe )
			cycleIngredients(plugin, player, workbench, (ShapedRecipe) recipe);
		player.updateInventory();
	}
	
	
	void restore()
	{
		player.getInventory().setContents(inventoryBackup);
		player.openWorkbench(null, true);
		player.updateInventory();
		
		//for (StackTraceElement e : Thread.currentThread().getStackTrace())
		//	System.out.println(e);
	}
	
	
	private void cycleIngredients(Main plugin, Player player, CraftingInventory workbench, ShapedRecipe recipe)
	{
		new BukkitRunnable()
		{
			int cycle = 0;
			
			@Override
			public void run()
			{
				if (!player.isOnline() || player.getOpenInventory().getTopInventory() != workbench)
				{
					this.cancel();
					return;
				}
				
				workbench.setMatrix(plugin.recipes.getIngredients(recipe, cycle++));
				player.updateInventory();
			}
		}.runTaskTimer(plugin, 1, 40);
	}
	
	
	private void shuffleFuel(Main plugin, Player player, FurnaceInventory furnace)
	{
		new BukkitRunnable()
		{
			@Override
			public void run()
			{
				if (!player.isOnline() || player.getOpenInventory().getTopInventory() != furnace)
				{
					this.cancel();
					return;
				}
				
				furnace.setFuel(plugin.recipes.shuffleFuel(furnace.getFuel()));
				player.updateInventory();
			}
		}.runTaskTimer(plugin, 1, 40);
	}

}
