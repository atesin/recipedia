
package cl.netgamer.recipedia;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;

class Session
{
	private Main plugin;
	private Player player;
	private InventoryKeeper keeper;
	private List<Recipe> cachedRecipes;
	private List<List<ItemStack>> history = new ArrayList<List<ItemStack>>();
	private boolean intendedClosing = true;
	
	/*
	intendedClosing:
	
	If Player.openInventory() is called while another inventory is open (like from InventoryClickEvent)
	the current one is closed before, triggering an extra InventoryCloseEvent
	
	Setting this flag helps to distinguish beetween intentional InventoryCloseEvent's
	to execute them, or extra ones to skip them
	
	it also serves to know status to print permission notifications.
	*/
	
	
	/**
	Holds information about player's recipe browsing session, it is instanced when plugin command
	is typed and destroyed when player closes crafting inventory. Also it serves as bridge
	beetween the player actions and his crafting inventory.<br/><br/>

	It has 2 operation modes: result and recipe mode. In result mode container displays search results,
	hotbar shows tabs to navigate result pages, and crafting grid is empty. Recipe mode displays a recipe
	in crafting slots, a list of recipe products in container, and no tabs in hotbar.<br/><br/>

	It has 4 main methods: to load results and recipes, and to display (previously loaded) results and recipes.
	Each method may finally set contents on 3 inventory views: crafting grid, container, and hotbar.<br/><br>
	
	WARNING: nested constructors result in different hashes
	*/
	Session(Main plugin, Player player, List<ItemStack> products)
	{
		this.plugin = plugin;
		this.player = player;
		keeper = new InventoryKeeper(plugin, player);
		
		display(products);
		intendedClosing = false;
	}
	
	
	/** back to previous item(s) if any */
	void back()
	{
		if ( history.size() < 2 )
			return;
		history.remove(history.size() - 1);
		display(history.remove(history.size() - 1));
	}
	
	
	/** display current last results/recipes in history, used by constructor and back() */
	private void display(List<ItemStack> products)
	{
		// with 1 item = display recipes for that item, with more items = display results
		if ( products.size() == 1 )
			updateRecipes(products.get(0));
		else
			updateResults(products);
		player.updateInventory();
	}
	
	
	/** 2 birds with 1 stone: switch extraCloseEvent flag, restore player inventory on valid InventoryCloseEvent
	 *  @return inventory restoring success status
	 */
	boolean handleInventoryClosing()
	{
		if ( intendedClosing = !intendedClosing ) // switch flag
			keeper.restore();
		return intendedClosing; // return if close event were valid and inventory was restored
	}
	
	
	/** load search result items, switching to result mode, boolean returned is dummy */
	boolean updateResults(List<ItemStack> results)
	{
		// check result count limits
		if ( results == null || results.size() < 1 || results.size() > 243 )
			return false;
		
		// if given list is 1 search result (not 1 recipe), show item recipes at once
		if ( results.size() == 1 && !isShowingRecipes() )
		{
			updateRecipes(results.get(0));
			return false;
		}
		
		// all seem to be ok, proceed with listing updated search results
		cachedRecipes = null; // set result mode
		showRecipe(0, null, results.size()); // clear crafting grid
		showResults(results, 0); // show first page of search results
		keeper.setHotbar((int) ((results.size() + 26) / 27)); // show result page navigation tabs
		return history.add(results);
	}
	
	
	/** perform some checks before load recipes for given product */
	boolean updateRecipes(ItemStack product)
	{
		List<Recipe> recipes = plugin.recipes.getRecipesFor(product);
		
		// if no "normal" recipes found, try inverse ones before
		if ( recipes.isEmpty() )
		{
			if ( plugin.hasPermission(player, "ingredient", false) && updateResults(plugin.recipes.getProductsCraftedWith(product)) );
			return false;
		}
		// do the last permission check (no needded in first call because checks was previously done)
		else if ( !intendedClosing && !plugin.hasPermission(player, "recipe", false) )
			return false;
		
		// save product, collect recipe products with lore, cache recipes
		history.add(Arrays.asList(product));
		List<ItemStack> results = new ArrayList<ItemStack>();
		for (Recipe recipe : cachedRecipes = recipes)
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
			results.add(product);
		}
		
		// show results (recipe products) and crafting (first recipe), and pages (no pages)
		showRecipe(0, results.get(0), 0); // show first recipe in crafting grid
		showResults(results, 0); // show the only page of recipe products
		keeper.setHotbar(0); // clear result page navigation tabs
		return true;
	}
	
	
	/** display specified page of last or specified result items */
	void showResults(List<ItemStack> results, int page)
	{
		if ( results == null )
			results = history.get(history.size() - 1);
		page *= 27;
		keeper.setStorage(results.subList(page, Math.min(page + 27, results.size())));
	}

	
	/** display cached recipe number "index" with given item in result product slot */
	void showRecipe(int index, ItemStack product, int resultsCount)
	{
		// next openInventory() could pass execution to InventoryCloseEvent so mark to be skipped in advance
		intendedClosing = true;
		keeper.setCrafting(product == null? null: cachedRecipes.get(index), product, resultsCount); // will call openInventory()
	}
	
	
	/** true if session is running in recipe mode, false if in result mode */
	boolean isShowingRecipes()
	{
		return cachedRecipes != null;
	}
	
}
