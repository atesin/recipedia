
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
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class Dictionary
{
	
	private Main plugin;
	private Map<Material, String> dictionary = new HashMap<Material, String>();
	private String locales = "Material";
	
	
	public Dictionary(Main plugin)
	{
		this.plugin = plugin;
		
		/*
		process example:
		locales    : <globalName, Set<localNames>> : "item.minecraft.iron_pickaxe"={"Iron Pickaxe","Pico de Hierro"}
		material   : <globalName, materialName>    : "item.minecraft.iron_pickaxe"="IRON_PICKAXE"
		dictionary : <localNames, materialName>    : "Iron Pickaxe`Pico de Hierro"="IRON_PICKAXE"
		*/
		
		// gain access to nms item methods, try this first to avoid wasting further efforts in case of fail
		Method itemAsNMSCopyMethod = null;
		Method itemGlobalNameMethod = null;
		String version = plugin.getServer().getClass().getName().split("\\.")[3];
		try
		{
			itemAsNMSCopyMethod = Class.forName("org.bukkit.craftbukkit."+version+".inventory.CraftItemStack").getMethod("asNMSCopy", ItemStack.class);
			itemGlobalNameMethod = Class.forName("net.minecraft.server."+version+".ItemStack").getMethod("j"); // prior 1.13: getMethod("a")
		}
		catch (ClassNotFoundException | NoSuchMethodException | SecurityException e)
		{
			plugin.getLogger().warning("Could not access internal item methods, seem something had changed in this craftbukkit release");
			plugin.getLogger().warning("Please report it in Recipedia homepage: https://dev.bukkit.org/projects/recipedia/");
			plugin.getLogger().warning("Search items by name was restricted to just Material names");
			return;
		}

		// loop language files to read global and localized names
		Map<String, Set<String>> localNames = new HashMap<String, Set<String>>();
		File[] langFiles = plugin.getDataFolder().listFiles((FileFilter)new WildcardFileFilter("*.json"));
		if ( langFiles != null )
			for (File langFile : langFiles)
				localNames = readFileLines(langFile, localNames);
		
		// no local names loaded = no names to search
		if ( localNames.isEmpty() )
		{
			plugin.getLogger().warning("No local names loaded, read 'config.yml' to know how to enable them");
			plugin.getLogger().warning("Search items by name was restricted to just Material names");
			return;
		}
		else
			plugin.getLogger().info("Found localized names for "+localNames.size()+" items");
		
		// walk throught materials to fill dictionary with local and material names
		// had to add "api-version: 1.13" in plugin.yml to get updated materials
		for (Material material : Material.values())
		{
			// skip uncraftable items
			if ( !plugin.recipes.isProduct(material) || !plugin.recipes.isIngredient(material) )
				continue;
			
			/*
			(Material)               COBBLESTONE
			  "+"
			    (globalName)         block.minecraft.cobblestone
			      "+"
			        (localNames)     block.minecraft.cobblestone: [Roca, Piedra labrada]
			          "="
			            (dictionary) COBBLESTONE: "Roca`Piedra labrada"
			*/
			
			// try to get global name by item stack and fill dictionary with names
			try
			{
				String globalName = itemGlobalNameMethod.invoke(itemAsNMSCopyMethod.invoke(null, new ItemStack(material))).toString();
				if ( localNames.containsKey(globalName) )
					dictionary.put(material, String.join("`", localNames.get(globalName)));
			}
			catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
			{
				plugin.getLogger().warning("Could not get item global names, seem something changed in this craftbukkit release");
				plugin.getLogger().warning("Please report it in Recipedia homepage: https://dev.bukkit.org/projects/recipedia/");
				plugin.getLogger().warning("Search items by name was restricted to just Material names");
				return;
			}
		}
		plugin.getLogger().info("Added "+dictionary.size()+" craftable items to dictionary");
	}
	
	
	/** adds local names to localNames<globalName, <localName[, localName2...]>> from json file */
	private Map<String, Set<String>> readFileLines(File langFile, Map<String, Set<String>> localNames)
	{
		plugin.getLogger().info("Loading file '"+langFile.getName()+"'...");
		
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
			plugin.getLogger().warning("Error loading file, check your config and language file... skipping.");
			return localNames;
		}
		
		/*
		(localNames)     block.minecraft.cobblestone: [Roca]
		 "+"
		  (line)         "block.minecraft.cobblestone": "Piedra labrada"
		   "="
		    (localNames) block.minecraft.cobblestone: [Roca, Piedra labrada]
		*/
		
		String globalName, localName;
		for (Entry<String, String> line : (Set<Entry<String, String>>) json.entrySet())
		{
			// [globalName, localName]
			globalName = line.getKey();
			localName = line.getValue();
			
			// save language code for later query
			if ( globalName.equals("language.code") )
				locales += ", "+localName;
			
			// skip non block or item names
			else if ( !globalName.matches("(block|item)\\.minecraft\\.[^.]*") )
				continue;
			
			// <globalName, {localName[,localName2...]}>
			if ( !localNames.containsKey(globalName) )
				localNames.put(globalName, new HashSet<String>());
			localNames.get(globalName).add(localName);
			
		}
		return localNames;
	}
	
	/** return loaded language codes */
	String getLocales()
	{
		return locales;
	}
	
	
	// keywords --> list(result itemstacks)
	/** return a list of matched items by given keywords */
	List<ItemStack> searchItemsByName(String... keywords)
	{
		List<ItemStack> result = new ArrayList<ItemStack>();
		if (keywords.length < 1)
			return result;
		
		String patternMaterial = "(?i).*?"+String.join(".*?", keywords)+".*?"; // "(?i).*?foo.*?bar.?*"
		String patternLocalname = "(?i).*?"+String.join("[^`]*?", keywords)+".*?"; // "(?i).*?foo[^`]*?bar.*?"
		for (Entry<Material, String> itemNames : dictionary.entrySet())
			if ( itemNames.getKey().toString().matches(patternMaterial) || itemNames.getValue().matches(patternLocalname) )
				result.add(plugin.recipes.setLores(new ItemStack(itemNames.getKey())));
		return result;
	}
	
}
