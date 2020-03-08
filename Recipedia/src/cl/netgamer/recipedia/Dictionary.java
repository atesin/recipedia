
package cl.netgamer.recipedia;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.bukkit.Material;
//import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.bukkit.craftbukkit.libs.org.apache.commons.io.filefilter.WildcardFileFilter;
import org.bukkit.inventory.ItemStack;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class Dictionary
{
	
	private Main plugin;
	private Map<Material, Set<String>> dictionary;
	private Set<String> locales;
	
	
	@SuppressWarnings("unchecked")
	Dictionary(Main plugin)
	{
		this.plugin = plugin;
		dictionary = new TreeMap<Material, Set<String>>();
		locales = new TreeSet<String>();
		
		// fill global dictionary entries with craftable items: MATERIAL = []

		for (Material material : Material.values())
			if ( plugin.recipes.isProduct(material) || plugin.recipes.isIngredient(material) )
				dictionary.put(material, new HashSet<String>());
		plugin.getLogger().info("Added "+dictionary.size()+" craftable material names to dictionary");
		
		// get global names map for craftable materials
		// globalNames: "global.name" = MATERIAL

		String version = plugin.getServer().getClass().getName().split("\\.")[3];
		Map<String, Material> globalNames = new HashMap<String, Material>();
		
		// access item's global name nms methods early
		try
		{
			Method methodItemAsNMSCopy = Class.forName("org.bukkit.craftbukkit."+version+".inventory.CraftItemStack").getMethod("asNMSCopy", ItemStack.class);
			Method methodGetItem = Class.forName("net.minecraft.server."+version+".ItemStack").getMethod("getItem");
			Method methodGetName = Class.forName("net.minecraft.server."+version+".Item").getMethod("getName"); // prior 1.13: getMethod("a")

			for (Material material : Material.values())
				if ( dictionary.containsKey(material) )
					globalNames.put(methodGetName.invoke(methodGetItem.invoke(methodItemAsNMSCopy.invoke(null, new ItemStack(material)))).toString(), material);
		}
		catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
		{
			plugin.getLogger().warning("Could not access internal item methods, seems something had changed in this craftbukkit release");
			plugin.getLogger().warning("Please report details in Recipedia homepage: https://dev.bukkit.org/projects/recipedia/");
			plugin.getLogger().warning("Search items by name was restricted to just material names");
			return; // a finally block would also be run just before this
		}
		
		// loop through '*.json' language files
		
		File[] langFiles = plugin.getDataFolder().listFiles((FileFilter)new WildcardFileFilter("*.json"));
		if ( langFiles != null )
			for (File langFile : langFiles)
			{
				// try to parse file as json
				JSONObject json;
				try
				{
					JSONParser parser = new JSONParser();
					FileReader file = new FileReader(langFile);
					json = (JSONObject) parser.parse(file);
					file.close(); // release file and free resources
				}
				catch (IOException | ParseException e)
				{
					plugin.getLogger().warning("Error loading language file '"+langFile.getName()+"', skipping");
					continue;
				}
				
				// walk json data nodes
				String langCode = "invalid";
				for (Entry<String, String> line : (Set<Entry<String, String>>) json.entrySet())
				{
					// "global.name": "Local Name"
					String globalName = line.getKey();
					String localName = line.getValue();
					
					// save language code for later query
					if ( globalName.equals("language.code") )
						langCode = localName;
					
					// add craftable block or item names, search optimized (strip accents and spaces, uppercase)
					// spanish example: Ca\u00f1a de az\u00facar (sugar_cane) -> CANADEAZUCAR
					else if ( globalName.matches("(block|item)\\.minecraft\\.[^.]*") && globalNames.containsKey(globalName) )
						dictionary.get(globalNames.get(globalName)).add(Main.stripAccents(localName).replaceAll(" +", "").toUpperCase());
				}
				locales.add(langCode);
				plugin.getLogger().info("Loaded '"+langCode+"' local names from file '"+langFile.getName()+"'");
			}
		
		if ( locales.size() < 1 )
			plugin.getLogger().info("No language files loaded, using just material names, more info in 'config.yml'");
	}
	
		
	/** return loaded language codes */
	String getLocales()
	{
		return String.join(", ", locales);
	}
	
	
	// keywords -> match non consecutive characters -> list(resulting itemstacks)
	/** return a list of matched items by given keywords */
	List<ItemStack> searchItemsByName(String... keywords)
	{
		List<ItemStack> result = new ArrayList<ItemStack>();
		
		String keyword = Main.stripAccents(String.join("", keywords)).replaceAll(" +", "").toUpperCase();
		String patternMaterial = keyword.replace("", ".*?"); // "foo" -> ".*?f.*?o.*?o.*?"
		String patternLocalName = ".*?"+keyword.replaceAll("(.)", "[^`]*?$1").substring(6)+".*?"; // "foo" -> ".*?f[^`]*?o[^`]*?o.*?"
		
		for (Entry<Material, Set<String>> itemNames : dictionary.entrySet())
			if ( itemNames.getKey().toString().matches(patternMaterial) || String.join("`", itemNames.getValue()).matches(patternLocalName) )
				result.add(plugin.recipes.setLores(new ItemStack(itemNames.getKey())));
		return result;
	}
	
}
