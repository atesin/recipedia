package cl.netgamer.recipedia;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
This class is just a non-interactive wrapper to InventoryType.WORKBENCH
since i discovered the guys at mojang (or bukkit) dropped
"(CraftingInventory) server.createInventory(InventoryType.WORKBENCH)"
type casting and the "interactiveness" causes me some troubles.
More info on "Server.createInventory(owner, type)" javadoc
*/

class Workbench
{
	private Inventory workbench;
	
	Workbench(Main plugin, Player player, String title)
	{
		workbench = plugin.getServer().createInventory(player, InventoryType.WORKBENCH, title);
	}
	
	
	/** access the internal inventory with this method. to get its hash, methods and etc. */
	Inventory getInventory()
	{
		return workbench;
	}
	
	
	/** this method is to emulate "CraftingInventory.setMatrix(contents)" */
	void setMatrix(ItemStack[] ingredients)
	{
		if ( ingredients.length > 9 )
			throw new IllegalArgumentException("Contents can't exceed 9 slots");
		
		int index = 9;
		while ( index > ingredients.length )
			workbench.setItem(--index, null);
		while ( index > 0 )
			workbench.setItem(index, ingredients[--index]);
	}
	
	
	/** this method is to emulate "CraftingInventory.setResult(stack)" */
	void setResult(ItemStack product)
	{
		workbench.setItem(0, product);
	}

}
