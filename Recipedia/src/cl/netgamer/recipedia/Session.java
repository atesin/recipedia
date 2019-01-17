
package cl.netgamer.recipedia;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;


class Session
{
	
	/*
	ESTA CLASE MANTIENE INFORMACION DE LA SESION DE BROWSING RECIPES DEL JUGADOR
	SE CREA AL ESCRIBIR EL COMANDO
	
	mantiene informacion como
	
	- inventario original del jugador
	- comando escrito (en caso de reescribirlo)
	- item buscado (en caso de reescribirlo)
	*/
	
	/*
	session has 2 operation modes
	- show search results   = populated hotbar, empty crafting slots
	- show crafting recipes = empty hotbar, populated crafting slots
	
	any action should involve 3 steps in player inventory view
	- update hotbar (just needed when updating products or recipes)
	- update storage (except in "recipes" mode)
	- update crafting, applies just to crafting slots
	
	actions:
	- restore (before close)
	- reset
	- show recipe     = update crafting inventory
	- update recipes  = update products + clear hotbar + browse page 0 + show recipe 0
	- update products = update hotbar + show page page 0 + clear crafting
	- show page       = update storage inventory
	
	*/
	
	private Main plugin;
	private Player player;
	private InventoryKeeper playerInventory;
	private ItemStack firstProduct = null;
	private List<ItemStack> firstProducts = null;
	private List<ItemStack> products;
	private List<Recipe> recipes;
	private boolean openingInventory = false;
	private int methodCalls = 0;
	
	// openingInventory:
	// Player.openInventory() called from InventoryClickEvent also trigger an additional InventoryCloseEvent,
	// setting this flag just before Player.openInventory() helps to skip "fake" InventoryClickEvent's
	
	// methodCalls:
	// first Player.openInventory() is called from onCommand() so it doesn't trigger a an additional
	// InventoryCloseEvent, i need a way to count method calls so i can skip it
	
	Session(Main plugin, Player player, ItemStack product)
	{
		this.plugin = plugin;
		this.player = player;
		playerInventory = new InventoryKeeper(plugin, player);
		
		firstProduct = product;
		updateRecipes(product);
	}
	
	
	Session(Main plugin, Player player, List<ItemStack> products)
	{
		this.plugin = plugin;
		this.player = player;
		playerInventory = new InventoryKeeper(plugin, player);
				
		firstProducts = products;
		updateProducts(products);
	}
	
	
	Player getOwner()
	{
		return player;
	}
	
	
	boolean fakeClose()
	{
		if ( openingInventory )
		{
			openingInventory = false;
			return true;
		}
		
		playerInventory.restore();
		return false;
	}
	
	
	void replay()
	{
		products = null;
		recipes = null;
		
		// action according operation mode
		if ( firstProduct != null )
			updateRecipes(firstProduct);
		else
			updateProducts(firstProducts);
	}
	
	
	void showRecipe(ItemStack product, int index)
	{
		if ( ++methodCalls > 1 )
			openingInventory = true;
		
		// if no showing recipe this point is never reached
		//if ( plugin.hasPermission(player, "recipe") )
			playerInventory.updateCrafting(player, recipes.get(index - 9), product);
	}
	
	
	void updateRecipes(ItemStack product)
	{
		// update products + clear hotbar + browse page 0 + show recipe 0
		
		if ( !plugin.hasPermission(player, "recipe") )
			return;
		
		// cache recipes to minimize server recipes iteration (and set "recipe" mode)
		recipes = plugin.getServer().getRecipesFor(product);
		
		// if no "normal" recipes found, try inverse ones
		if ( recipes.size() < 1 )
		{
			updateProducts(plugin.recipes.getInverseRecipeProducts(product));
			return;
		}
		
		List<ItemStack> recipeProducts = new ArrayList<ItemStack>();
		for (Recipe recipe : recipes)
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

			recipeProducts.add(product);
		}
		
		updateProductsPrivate(recipeProducts);
	}
	
	
	void updateProducts(List<ItemStack> products)
	{
		recipes = null;
		updateProductsPrivate(products);
	}
	
	
	private void updateProductsPrivate(List<ItemStack> products)
	{
		// areSearchResult true if products are search results (throught commands or inverse recipes search)
		// - update hotbar, clear crafting recipe
		// areSearchResult false if products are recipe products
		// - clear hotbar, update crafting recipe
		
		// update hotbar + show page page 0 + clear crafting
		
		// if products < 1 : no results
		// if products > 9 pages : too many results
		// if product == 1 : show recipes
		
		if ( products == null || products.size() < 1 )
			player.sendMessage("no results");
		
		else if ( products.size() > 243 )
			player.sendMessage("too many results");
		
		else if ( products.size() == 1 && !isShowingRecipe() )
			updateRecipes(products.get(0));
		
		else
		{
			this.products = products;
			showPage(0);
			if ( methodCalls > 1 )
				openingInventory = true;
			
			if ( isShowingRecipe() )
			{
				playerInventory.updateHotbar(0);
				playerInventory.updateCrafting(player, recipes.get(0), products.get(0));
			}
			
			else
			{
				playerInventory.updateHotbar((int) ((products.size() + 26) / 27));
				playerInventory.updateCrafting(player, null, null);
			}
		}
	}
	
	
	void showPage(int page)
	{
		++methodCalls;
		int from = page * 27;
		int to = Math.min(from + 27, products.size());
		playerInventory.updateStorage(products.subList(from, to));
	}

	
	boolean isShowingRecipe()
	{
		return recipes != null;
	}

}
