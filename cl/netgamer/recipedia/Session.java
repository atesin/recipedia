
package cl.netgamer.recipedia;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;

/**
Holds information about player's browsing recipes session, it is instanced when plugin command
is typed and destroyed when player closes crafting inventory. Also it serves as bridge
beetween the player actions and his crafting inventory.<br/><br/>

It has 2 operation modes: result and recipe mode. In result mode container displays search results,
hotbar shows tabs to navigate result pages, and crafting grid is empty. Recipe mode displays a recipe
in crafting slots, a list of recipe products in container, and no tabs in hotbar.<br/><br/>

It has 4 main methods: to load results and recipes, and to display (previously loaded) results and recipes.
Each method may finally set contents on 3 inventory views: crafting grid, container, and hotbar.
*/


class Session
{
	private Main plugin;
	private Player player;
	private InventoryKeeper keeper;
	private ItemStack firstProduct;
	private List<ItemStack> firstResults;
	private List<ItemStack> currentResults;
	private List<Recipe> currentRecipes;
	private boolean calledFromClick = true;
	
	/*
	calledFromClick:
	player.openInventory() called from InventoryClickEvent also execute registered InventoryCloseEvent,
	setting this flag just before this method helps to decide if "execute" real or "skip" fake close events,
	also serves to guess if should echo denied permission actions.
	*/
	
	Session(Main plugin, Player player, ItemStack product)
	{
		newSession(plugin, player, product, null);
	}
	
	
	Session(Main plugin, Player player, List<ItemStack> products)
	{
		newSession(plugin, player, null, products);
	}
	
	
	/** ex constructor, can't cascade constructors because result in different hashes, internal use */
	private void newSession(Main plugin, Player player, ItemStack product, List<ItemStack> results)
	{
		this.plugin = plugin;
		this.player = player;
		keeper = new InventoryKeeper(plugin, player);
				
		firstProduct = product;
		firstResults = results;
		replay();
		calledFromClick = false;
	}
	
	
	/** back to starting condition */
	void replay()
	{
		// clear previous cached
		currentResults = null;
		currentRecipes = null;
		
		// action according operation mode
		if ( firstProduct == null )
			updateResults(firstResults);
		else
			updateRecipes(firstProduct);
		player.updateInventory();
	}
	
	
	/** restore player inventory and return status, if close event is real */
	boolean executeClose()
	{
		// 2 birds with 1 stone: switch calledFromClick flag and restore player inventory if close event was not called from click
		if ( calledFromClick = !calledFromClick )
			keeper.restore();
		return calledFromClick;
	}
	
	
	/** load search result items, switching to result mode, boolean returned is dummy */
	boolean updateResults(List<ItemStack> results)
	{
		// set result mode
		currentRecipes = null;
		updateResultsPrivate(results);
		return true;
	}
	
	
	/** load search result items, without modifying operation mode so can list recipe products also, internal use */
	private void updateResultsPrivate(List<ItemStack> results)
	{
		// result count out of limits
		if ( results == null || results.size() < 1 ||  results.size() > 243)
			return;
		
		// if given list is 1 search result (not 1 recipe), show item recipes at once
		if ( results.size() == 1 && !inRecipeMode() )
		{
			updateRecipes(results.get(0));
			return;
		}
		
		// all seem to be ok, proceed with listing updated search results
		currentResults = results;
		showRecipe(9, null); // clear crafting grid
		showResults(0); // show first page of search results
		keeper.setHotbar((int) ((results.size() + 26) / 27)); // show result page navigation tabs
	}
	
	
	/** perform some checks before load recipes for given product */
	void updateRecipes(ItemStack product)
	{
		List<Recipe> recipes = plugin.recipes.getRecipesFor(product);
		
		// if no "normal" recipes found, try inverse ones before
		if ( recipes.isEmpty() )
		{
			if ( plugin.hasPermission(player, "ingredient", false) && updateResults(plugin.recipes.getProductsMadeWith(product)) );
			return;
		}
		// do the last permission check (no needded in first call because checks was previously done)
		else if ( !calledFromClick && !plugin.hasPermission(player, "recipe", false) )
			return;
		
		// cache recipes to minimize server recipes iteration (and set recipe mode)
		currentRecipes = recipes;
		
		// collect recipe products with lore
		currentResults = new ArrayList<ItemStack>();
		for (Recipe recipe : currentRecipes)
		{
			product = recipe.getResult();
			
			List<String> lores = new ArrayList<String>();
			lores.add(plugin.msg(recipe.getClass().getSimpleName()));
			if ( plugin.recipes.isIngredient(product) )
				lores.add(plugin.msg("ingredient"));
			if ( product.getType().isFuel() )
				lores.add(plugin.msg("fuel"));
			
			ItemMeta meta = product.getItemMeta();
			meta.setLore(lores);
			product.setItemMeta(meta);

			currentResults.add(product);
		}
		
		// show results (recipe products) and crafting (first recipe), and tabs (no tabs)
		showRecipe(9, currentResults.get(0)); // show first recipe in crafting grid
		showResults(0); // show the only page of recipe products
		keeper.setHotbar(0); // clear result page navigation tabs
	}
	
	
	/** display specified page of loaded search result items */
	void showResults(int page)
	{
		page *= 27;
		keeper.setStorage(currentResults.subList(page, Math.min(page + 27, currentResults.size())));
	}

	
	/** display loaded recipe number "index" with given item in resulting product slot */
	void showRecipe(int index, ItemStack product)
	{
		// next openInventory() could pass execution to InventoryCloseEvent so mark to be skipped in advance
		calledFromClick = true;
		keeper.setCrafting(product == null? null: currentRecipes.get(index - 9), product); // will call openInventory()
	}
	
	
	/** true if session is running in recipe mode, false if in result mode */
	boolean inRecipeMode()
	{
		return currentRecipes != null;
	}
	
}
