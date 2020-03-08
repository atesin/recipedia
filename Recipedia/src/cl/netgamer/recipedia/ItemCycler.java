
package cl.netgamer.recipedia;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.scheduler.BukkitRunnable;

/** cycle materials and fuels in a crafting inventory */
public class ItemCycler
{
	Main plugin;
	Player player;
	
	public ItemCycler(Main plugin, Player player)
	{
		this.plugin = plugin;
		this.player = player;
	}
	
	
	void cycleIngredients(Workbench workbench, Recipe recipe)
	{
		new BukkitRunnable()
		{
			int cycle = 0;
			
			@Override
			public void run()
			{
				if (!player.isOnline() || player.getOpenInventory().getTopInventory() != workbench.getInventory() )
					this.cancel();
				else
				{
					workbench.setMatrix(plugin.recipes.getIngredients((ShapedRecipe) recipe, cycle++));
					player.updateInventory();
				}
			}
		}.runTaskTimer(plugin, 1, 40);
	}
	
	
	//void randomizeFuel(FurnaceInventory furnace)
	void randomizeFuel(Inventory furnace)
	{
		new BukkitRunnable()
		{
			@Override
			public void run()
			{
				if (!player.isOnline() || player.getOpenInventory().getTopInventory() != furnace)
					this.cancel();
				else
				{
					//furnace.setFuel(plugin.recipes.pickFuel(furnace.getFuel()));
					furnace.setItem(1, plugin.recipes.pickFuel(furnace.getItem(1)));
					player.updateInventory();
				}
			}
		}.runTaskTimer(plugin, 1, 40);
	}

}
