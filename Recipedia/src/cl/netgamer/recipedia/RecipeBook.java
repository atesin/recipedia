
package cl.netgamer.recipedia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.RecipeChoice.MaterialChoice;

/** maintain recipes and items reference, inverse recipes, ingredients, fuels, etc. */
class RecipeBook
{
	private Main plugin;
	private Map<String, Set<String>> inverseRecipes = new HashMap<String, Set<String>>();
	private List<String> fuels = new ArrayList<String>();
	
	RecipeBook(Main plugin)
	{
		this.plugin = plugin;
		
		Iterator<Recipe> recipes = plugin.getServer().recipeIterator();
		while ( recipes.hasNext() )
		{
			Recipe recipe = recipes.next();
			if ( Main.isEmptyItem(recipe.getResult()) ) // recipes to make air???
				continue;
			
			String product = recipe.getResult().getType().toString();
			
			if ( recipe instanceof FurnaceRecipe )
				registerInverseRecipe(((FurnaceRecipe) recipe).getInput().getType(), product);
			
			else if ( recipe instanceof ShapelessRecipe )
				for (ItemStack ingredient : ((ShapelessRecipe) recipe).getIngredientList())
					registerInverseRecipe(ingredient.getType(), product);
			
			else
				for (RecipeChoice choices : ((ShapedRecipe) recipe).getChoiceMap().values())
					if ( choices != null )
						for (Material choice : ((MaterialChoice) choices).getChoices())
							registerInverseRecipe(choice, product);
		}
		
		plugin.getLogger().info("Loaded inverse recipes for "+inverseRecipes.size()+" ingredients");
		
		for (Material material : Material.values())
			if ( material.isFuel() )
				fuels.add(material.toString());
		plugin.getLogger().info("Loaded "+fuels.size()+" fuels");
	}
	
	
	/** build an ingredient list with its resulting products, at init time (internal use) */
	private void registerInverseRecipe(Material ingredient, String product)
	{
		String ingredientName = ingredient.toString();
		if ( !inverseRecipes.containsKey(ingredientName) )
			inverseRecipes.put(ingredientName, new TreeSet<String>());
		inverseRecipes.get(ingredientName).add(product);
	}
	
	
	/** return if items of given material can be used as ingredient to craft other items */
	boolean isIngredient(Material material)
	{
		// skip air as an ingredient
		return !Main.isEmptyItem(material) && inverseRecipes.containsKey(material.toString());
	}
	
	
	/** return if given item can be used as ingredient to craft other items */
	boolean isIngredient(ItemStack item)
	{
		return !Main.isEmptyItem(item) && isIngredient(item.getType());
	}
	
	
	/** return if there is some recipe for crafting an item of this material */
	boolean isProduct(Material material)
	{
		return material != null && isProduct(new ItemStack(material));
	}
	
	
	/** return if there is some recipe for crafting this item */
	boolean isProduct(ItemStack item)
	{
		return getRecipesFor(item).size() > 0;
	}
	
	
	/** get a list of crafting recipes for the given item, excluding recipes to make air (yes, there are) */
	List<Recipe> getRecipesFor(ItemStack product)
	{
		if ( Main.isEmptyItem(product) )
			return new ArrayList<Recipe>();
		return plugin.getServer().getRecipesFor(product);
	}
	
	
	/** get a list of products that can be crafted from the given ingredient */
	List<ItemStack> getProductsMadeWith(ItemStack ingredient)
	{
		List<ItemStack> products = new ArrayList<ItemStack>();
		
		if ( isIngredient(ingredient) )
			for (String product : inverseRecipes.get(ingredient.getType().toString()))
				products.add(setLores(new ItemStack(Material.getMaterial(product))));
		return products;
	}
	
	
	/** return some random fuel item, other than passed if any */
	ItemStack pickFuel(ItemStack fuel)
	{
		String fuelInput = fuel == null? "": fuel.getType().toString(), fuelOutput;
		do
			fuelOutput = fuels.get((int) (Math.random() * fuels.size()));
		while ( fuelInput.equals(fuelOutput) );
		return setLores(new ItemStack(Material.getMaterial(fuelOutput)));
	}
	
	
	/** get the ingredient of a furnace recipe */
	ItemStack getIngredient(Recipe recipe)
	{
		return setLores(((FurnaceRecipe) recipe).getInput());
	}
	
	
	/** get ingredients from a shapeless recipe */ 
	ItemStack[] getIngredients(Recipe recipe)
	{
		ItemStack[] ingredients = new ItemStack[9];
		int index = 0;
		for (ItemStack ingredient : ((ShapelessRecipe) recipe).getIngredientList())
			ingredients[index++] = setLores(ingredient);
		return ingredients;
	}
	
	
	/** get ingredient choices from a shaped recipe according cycle */
	ItemStack[] getIngredients(ShapedRecipe recipe, int cycle)
	{
		/*
		furnace recipes seem to not having ingredient choices as they are single ingredient recipes
		seems all shapeless recipes have just 1 choice copied from main ingredients
		seems in all shaped recipes, main ingredients are also the 1st choice (except the stack amounts)

		shape = (String[rows]) {[abc],[def],[ghi]}... {[ab],[cd]}... etc
		ingredients = (Map<char,ItemStack>) a->WOOD, b->WOOD, c->STONE... etc
		choices = (Map<char,RecipeChoice>) a->[WOOD,STONE], b->[STICK], c->null...
		to access choices cast RecipeChoice to MaterialChoice
		*/

		String[] shape = recipe.getShape();
		int center = 1;

		if ( shape.length < 3 )
			center += 3;
		if ( shape[0].length() > 2 || shape[1].length() > 2 || ( shape.length > 2 && shape[2].length() > 2 ) )
			--center;
		
		ItemStack[] ingredients = new ItemStack[9];
		for (int row = 0; row < shape.length; ++row)
			for (int col = 0; col < shape[row].length(); ++col)
			{
				ItemStack ingredient = recipe.getIngredientMap().get(shape[row].charAt(col));
				Map<Character, RecipeChoice> choiceMap = recipe.getChoiceMap();
				List<Material> materials = null;
				
				if ( choiceMap != null )
				{
					RecipeChoice choice = choiceMap.get(shape[row].charAt(col));
					if ( choice != null )
						materials = ((MaterialChoice) choice).getChoices();
				}
				
				if ( materials != null )
				{
					ingredient.setType(materials.get(cycle % materials.size()));
					ingredients[row*3 + col + center] = setLores(ingredient);
				}
			}
		return ingredients;
	}
	
	
	/** add item information tooltip */
	ItemStack setLores(ItemStack item)
	{
		if ( item != null )
		{
			List<String> lores = new ArrayList<String>();
			
			int numRecipes = getRecipesFor(item).size();
			if ( numRecipes > 0 )
				lores.add(plugin.msg("recipesCount", numRecipes));
			if ( isIngredient(item) )
				lores.add(plugin.msg("ingredient"));
			if ( item.getType().isFuel() )
				lores.add(plugin.msg("fuel"));
			
			ItemMeta meta = item.getItemMeta();
			meta.setLore(lores);
			item.setItemMeta(meta);
		}
		return item;
	}

}
