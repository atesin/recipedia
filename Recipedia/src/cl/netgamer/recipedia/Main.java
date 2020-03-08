
package cl.netgamer.recipedia;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.libs.org.apache.commons.codec.digest.DigestUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/*
about storing player instances in collections...

this post in the *official* thread has a very good technical explanation, by desht:
https://bukkit.org/threads/common-mistakes.100544/#post-1333980

these also, by desht, Comphenix and others, from comment #35 to #43 :
https://bukkit.org/threads/common-mistakes.100544/page-2#post-1342775

these too, from spigot and bukkit forums:
https://www.spigotmc.org/threads/storing-a-player-object-in-a-list.165400/
https://bukkit.org/threads/storing-players-bad-practice.80469/
*/


public final class Main extends JavaPlugin implements Listener
{
	private FileConfiguration conf;
	private Map<Player, Session> sessions = new HashMap<Player, Session>();
	private Dictionary dictionary;
	RecipeBook recipes;
	
	public void onEnable()
	{
		saveDefaultConfig();
		conf = getConfig();
		recipes = new RecipeBook(this);
		dictionary = new Dictionary(this);
		
		getServer().getPluginManager().registerEvents(this, this);
		getServer().broadcastMessage("\u00A7eRecipedia plugin reloaded");
		getLogger().info("Plugin ready to work");
	}

	
	public void onDisable()
	{
		for (Entry<Player, Session> session : sessions.entrySet())
		{
			session.getValue().handleInventoryClosing(); // restore player inventory
			sessions.remove(session.getKey()); // remove session before trigger InventoryCloseEvent to avoid extra handling
			session.getKey().closeInventory(); // safely used in onDisable(), see InventoryClickEvent javadoc
		}
	}
	
	
	@EventHandler
	public void onWorldSave(WorldSaveEvent e)
	{
		// sessions cleanup to prevent eventual memory leaks
		for (Player player: sessions.keySet())
			if ( !player.isOnline() )
				sessions.remove(player);
	}
	
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent e)
	{
		Session session = sessions.get(e.getPlayer());
		if ( session != null && session.handleInventoryClosing() )
			sessions.remove(e.getPlayer());
	}
	
	
	@EventHandler
	public void onInventoryClose(InventoryCloseEvent e)
	{
		Session session = sessions.get((Player) e.getPlayer());
		if ( session != null && session.handleInventoryClosing() )
			sessions.remove((Player) e.getPlayer());
	}
	
	
	@EventHandler(priority = EventPriority.LOWEST)
	public void onInventoryClick(InventoryClickEvent e)
	{
		// skip if plugin is not being used
		if ( !sessions.containsKey(e.getWhoClicked()) )
			return;
		
		// plugin is being used, so get some vars
		e.setCancelled(true);
		ItemStack item = e.getCurrentItem();
		SlotType slot = e.getSlotType();
		Player player = (Player) e.getWhoClicked();
		Session session = sessions.get(player);
		
		// click outside with...
		if ( slot == SlotType.OUTSIDE )
		{
			// left click -> back to previous results/recipes
			if ( e.getClick() == ClickType.LEFT )
				session.back();
			
			// right click -> close session
			// i know this should be scheduled but wtf, these are not my items :)
			// see InventoryClickEvent javadoc
			else if ( e.getClick() == ClickType.RIGHT )
				player.closeInventory();
			
			// other click type -> do nothing
			return;
		}
		
		// click an empty slot -> do nothing
		else if ( isEmptyItem(item) )
			return;
		
		// click an item in...
		
		// hotbar -> navigate page
		else if ( slot == SlotType.QUICKBAR )
			session.showResults(null, e.getSlot());
		
		// right click -> list inverse recipes (entering in result mode)
		else if ( e.getClick() == ClickType.RIGHT && hasPermission(player, "ingredient", false) )
			session.updateResults(recipes.getProductsCraftedWith(item));
		
		// not left click -> do nothing
		else if ( e.getClick() != ClickType.LEFT )
			return;
		
		// left click an item in...
		
		// bottom container inventory slot while in recipe mode -> show recipe in grid
		else if ( slot == SlotType.CONTAINER && session.isShowingRecipes() )
			session.showRecipe(e.getSlot() - 9, item, 0);
		
		// any other than result -> list recipe products + show 1st recipe (entering in recipe mode)
		else if ( slot != SlotType.RESULT )
			session.updateRecipes(item);
		
		// none of the above -> do nothing and skip inventory updating
		else
			return;
		
		player.updateInventory();
	}
	
	
	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
	{
		if ( command.getName().matches("(?i)recipe(dia|hand|target)") )
			return new ArrayList<String>();
		return super.onTabComplete(sender, command, alias, args);
	}

	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String alias, String[] args)
	{
		// special /recipedia subcommands
		if ( command.getName().equalsIgnoreCase("recipedia") )
		{
			// display /recipedia help page for everyone
			if ( args.length < 1 )
				return die(sender, "helpPage", dictionary.getLocales(), listPermissions(sender));	
			
			// validate and process 'reload' subcommand
			if ( sender.isOp() && args[0].equalsIgnoreCase("reload") )
			{
				// calculate current token and from 10 seconds ago, just use the first 4 chars
				String token0 = "RP"+System.currentTimeMillis();
				String token1 = "RP"+(System.currentTimeMillis() - 10000); // 10 secs ago
				token0 = DigestUtils.sha1Hex(token0.substring(0, token0.length() - 4)).substring(0, 4);
				token1 = DigestUtils.sha1Hex(token1.substring(0, token1.length() - 4)).substring(0, 4);
				
				// if this token fails then try the previous
				if ( args.length > 1 && ( args[1].equalsIgnoreCase(token0) || args[1].equalsIgnoreCase(token1) ) )
				{
					onDisable();
					onEnable();
				}
				else
					sender.sendMessage("\u00A7eAre you sure?, to confirm please type '/recipedia reload "+token0+"'");
				return true;
			}
		}
		
		// exit on other command
		if ( !command.getName().equalsIgnoreCase("recipedia") && !command.getName().equalsIgnoreCase("recipehand") && !command.getName().equalsIgnoreCase("recipetarget") )
			return true;
		
		// filter online players
		if ( !(sender instanceof Player) )
		{
			sender.sendMessage("\u00A7eThis command requires Minecraft 3D Client");
			return true;
		}
		Player player = (Player) sender;
		
		// process /recipedia search command
		if ( command.getName().equalsIgnoreCase("recipedia") && hasPermission(player, "search", true) )
			return displayResults(player, dictionary.searchItemsByName(args));
		
		// process /recipehand command
		if ( command.getName().equalsIgnoreCase("recipehand") && hasPermission(player, "hand", true) )
			return displayRecipes(player, player.getInventory().getItemInMainHand());
		
		// process /recipetarget command
		if ( command.getName().equalsIgnoreCase("recipetarget") && hasPermission(player, "target", true) )
			return displayRecipes(player, new Sight(player).getTargetItem());
		
		return true;
	}
	
	
	private boolean displayRecipes(Player player, ItemStack item)
	{
		if ( recipes.isProduct(item) )
		{
			if ( hasPermission(player, "recipe", true) )
				// read line 32 comments about storing player instances in collections
				sessions.put(player, new Session(this, player, Arrays.asList(item)));
			return true;
		}
		
		// if no "normal" recipes found, try inverse ones
		if ( recipes.isIngredient(item) )
		{
			if ( hasPermission(player, "ingredient", true) )
				displayResults(player, recipes.getProductsCraftedWith(item));
		}
		
		else
			die(player, "noRecipesFound");
		return true;
	}
	
	
	private boolean displayResults(Player player, List<ItemStack> results)
	{
		if ( results.size() < 1 )
			return die(player, "noItemsFound");
		if ( results.size() > 243 )
			return die(player, "tooManyResults");
		if ( results.size() == 1 )
			return displayRecipes(player, results.get(0));
		
		// read line 32 comments about storing player instances in collections
		sessions.put(player, new Session(this, player, results));
		return true;
	}
	
	
	// utilities
	
	
	boolean hasPermission(Player player, String permission, boolean verbose)
	{
		permission = "recipedia."+permission;
		boolean allowed = player.isOp() || !player.isPermissionSet(permission) || player.hasPermission(permission);
		
		if ( !allowed && verbose )
			player.sendMessage(msg("noPermission", permission));
		return allowed;
	}

	
	private String listPermissions(CommandSender sender)
	{
		if ( !(sender instanceof Player) )
			return "Recipedia plugin requires Minecraft 3D Client";
		
		Player player = (Player) sender;
		return
			(hasPermission(player, "search",     false)? '+': '-')+"search "+
			(hasPermission(player, "ingredient", false)? '+': '-')+"ingredient "+
			(hasPermission(player, "hand",       false)? '+': '-')+"hand "+
			(hasPermission(player, "target",     false)? '+': '-')+"target "+
			(hasPermission(player, "recipe",     false)? '+': '-')+"recipe";
	}
	
	
	private boolean die(CommandSender sender, String key, Object... args)
	{
		sender.sendMessage(msg(key, args));
		return true;
	}
	
	
	String msg(String key, Object... args)
	{
		if ( conf.contains(key) )
			return String.format("\u00A7e"+conf.getString(key), args)
				.replaceAll("[\r\n]+$", "")
				.replaceAll("(?m):(.*?)$", ":\u00A7b$1\u00A7e");
		else
			return "\u00A7dMissing conf: \u00A7e"+key;
	}
	
	
	@SuppressWarnings("deprecation")
	static boolean isEmptyMaterial(Material material)
	{
		// true if null or any "air", LEGACY_AIR for servers updated prior "the flattering"
		return material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR || material == Material.LEGACY_AIR;
	}
	
	
	static boolean isEmptyItem(ItemStack item)
	{
		return item == null || isEmptyMaterial(item.getType());
	}
	
	
	static String stripAccents(String text)
	{
		// https://www.drillio.com/en/2011/java-remove-accent-diacritic/
		// https://memorynotfound.com/remove-accents-diacritics-from-string/
	    return text == null ? null :
	        Normalizer.normalize(text, Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
	}
	
}
