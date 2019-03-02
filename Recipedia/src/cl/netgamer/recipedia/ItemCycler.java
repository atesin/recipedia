
package cl.netgamer.recipedia;

import org.bukkit.entity.Player;
import org.bukkit.inventory.FurnaceInventory;
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
				{
					this.cancel();
					return;
				}
				
				workbench.setMatrix(plugin.recipes.getIngredients((ShapedRecipe) recipe, cycle++));
				player.updateInventory();
			}
		}.runTaskTimer(plugin, 1, 40);
	}
	
	
	void randomizeFuel(FurnaceInventory furnace)
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
				
				furnace.setFuel(plugin.recipes.pickFuel(furnace.getFuel()));
				player.updateInventory();
			}
		}.runTaskTimer(plugin, 1, 40);
	}

}
