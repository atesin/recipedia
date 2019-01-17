
package cl.netgamer.recipedia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.RecipeChoice.MaterialChoice;


class RecipeBook
{
	
	Main plugin;
	private Map<String, List<String>> inverseRecipes = new HashMap<String, List<String>>();
	private List<String> fuels = new ArrayList<String>();
	
	
	RecipeBook(Main plugin)
	{
		this.plugin = plugin;
		inverseRecipes.put(null, new ArrayList<String>());
		inverseRecipes.put("AIR", new ArrayList<String>());
		inverseRecipes.put("LEGACY_AIR", new ArrayList<String>());
		
		Iterator<Recipe> recipes = plugin.getServer().recipeIterator();
		while ( recipes.hasNext() )
		{
			Recipe recipe = recipes.next();
			if ( Main.isEmpty(recipe.getResult()) )
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
		
		for (List<String> products : inverseRecipes.values())
			products.sort(null);
		plugin.getLogger().info("Loaded inverse recipes for "+inverseRecipes.size()+" ingredients");
		
		for (Material material : Material.values())
			if ( material.isFuel() )
				fuels.add(material.toString());
		plugin.getLogger().info("Loaded "+fuels.size()+" fuels");
	}
	
	
	private void registerInverseRecipe(Material ingredient, String product)
	{
		String ingredientName = ingredient.toString();
		if ( !inverseRecipes.containsKey(ingredientName) )
			inverseRecipes.put(ingredientName, new ArrayList<String>());
		
		List<String> products = inverseRecipes.get(ingredientName);
		if ( !products.contains(product) )
			products.add(product);
	}
	
	
	boolean isIngredient(ItemStack item)
	{
		return inverseRecipes.containsKey(item.getType().toString());
	}
	
	
	boolean isCraftable(ItemStack item)
	{
		// recipes to make air?
		if ( Main.isEmpty(item) )
			return false;
		
		return plugin.getServer().getRecipesFor(item).size() > 0 || isIngredient(item);
	}
	
	
	List<ItemStack> getInverseRecipeProducts(ItemStack ingredient)
	{
		List<ItemStack> products = new ArrayList<ItemStack>();
		
		for (String product : inverseRecipes.get(ingredient.getType().toString()))
			products.add(setLores(new ItemStack(Material.getMaterial(product))));
		
		return products;
	}
	
	
	ItemStack shuffleFuel(ItemStack fuel)
	{
		String fuelInput = fuel == null ? "" : fuel.getType().toString();
		String fuelOutput;
		do
			fuelOutput = fuels.get((int) (Math.random() * fuels.size()));
		while ( fuelInput.equals(fuelOutput) );
		return setLores(new ItemStack(Material.getMaterial(fuelOutput)));
	}
	
	
	ItemStack getIngredient(FurnaceRecipe recipe)
	{
		return setLores(recipe.getInput());
	}
	
	
	ItemStack[] getIngredients(ShapelessRecipe recipe)
	{
		ItemStack[] ingredients = new ItemStack[9];
		int index = 0;
		for (ItemStack ingredient : recipe.getIngredientList())
			ingredients[index++] = setLores(ingredient);
		return ingredients;
	}
	
	
	ItemStack[] getIngredients(ShapedRecipe recipe, int cycle)
	{
		/*
		furnace recipes seem to not having ingredient choices as they are single ingredient recipes
		seems all shapeless recipes have just 1 choice copied from main ingredients
		seems in all shaped recipes, main ingredients are also the 1st choice (except the stack amounts)

		shape = (String[rows]) {[abc],[def],[ghi]}... {[ab],[cd]}... etc
		ingredients = (Map<char,ItemStack>) a->WOOD, b->WOOD, c->STONE... etc
		choices = (Map<char,RecipeChoice>) a->[WOOD,STONE], b->[STICK]...
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
				List<Material> materials;
				if ( choiceMap == null )
					materials = new ArrayList<Material>();
				else
					materials = ((MaterialChoice) choiceMap.get(shape[row].charAt(col))).getChoices();
				ingredient.setType(materials.get(cycle % materials.size()));
				ingredients[row*3 + col + center] = setLores(ingredient);
			}
		return ingredients;
	}
	
	
	private ItemStack setLores(ItemStack item)
	{
		if ( item != null )
		{
			List<String> lores = new ArrayList<String>();
			
			int numRecipes = plugin.getServer().getRecipesFor(item).size();
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
