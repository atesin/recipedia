
package cl.netgamer.recipedia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
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
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;


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
	RecipeBook recipes;
	private Dictionary dictionary;
	private Map<Player, Session> sessions = new HashMap<Player, Session>();
	
	public void onEnable()
	{
		saveDefaultConfig();
		conf = getConfig();
		
		recipes = new RecipeBook(this);
		dictionary = new Dictionary(this);
		
		getServer().getPluginManager().registerEvents(this, this);
		getLogger().info("Plugin ready to work");
	}

	
	public void onDisable()
	{
		// can't schedule close player inventory because next tick plugin will be disabled,
		// not so neat to leave player inventory opened on reloads but at least it keeps integrity
		for (Session session : sessions.values())
			session.executeClose();
	}
	
	
	@EventHandler
	public void onWorldSave(WorldSaveEvent e)
	{
		// similar to onDisable(), this is for clean sessions eventually to prevent memory leaks
		for (Player player: sessions.keySet())
			if ( !player.isOnline() )
				sessions.remove(player);
	}
	
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent e)
	{
		Session session = sessions.get(e.getPlayer());
		if ( session != null && session.executeClose() )
			sessions.remove(e.getPlayer());
	}
	
	
	@EventHandler
	public void onInventoryClose(InventoryCloseEvent e)
	{
		Session session = sessions.get((Player) e.getPlayer());
		if ( session != null && session.executeClose() )
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
			// left click > replay session
			if ( e.getClick() == ClickType.LEFT )
				session.replay();
			
			// other click (right, double, etc.) > close session (on next tick for safety, see InventoryClickEvent javadoc)
			else
			{
				new BukkitRunnable()
				{
					@Override
					public void run()
					{
						player.closeInventory();
					}
				}.runTask(this);
				return;
			}
		}
		
		// click an empty slot > do nothing
		else if ( isEmptyItem(item) )
			return;
		
		// click an item in...
		
		// hotbar > navigate page
		else if ( slot == SlotType.QUICKBAR )
			session.showResults(e.getSlot());
		
		// non-left click > list inverse recipes (entering in result mode)
		else if ( e.getClick() != ClickType.LEFT )
		{
			if ( hasPermission(player, "ingredient", false) && session.updateResults(recipes.getProductsMadeWith(item)) );
		}
		
		// left click an item in...
		
		// container slot while in recipe mode > show recipe in grid
		else if ( slot == SlotType.CONTAINER && session.inRecipeMode() )
			session.showRecipe(e.getSlot(), item);
		
		// top inventory, or any slot while in result mode > list recipe products + show 1st recipe (entering in recipe mode)
		else // checks pending?
			session.updateRecipes(item);
		
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
		// process /recipedia command
		if ( command.getName().equalsIgnoreCase("recipedia") )
		{
			// with no args, display help page for everyone
			if (args.length < 1)
				return die(sender, "helpPage", dictionary.getLocales(), listPermissions(sender));
			
			if ( !(sender instanceof Player) )
				return true;
			
			Player player = (Player) sender;
			if ( hasPermission(player, "search", true) && displayResults(player, dictionary.searchItemsByName(args)) );
			return true;
		}
		
		Player player = (Player) sender;
		
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
				// read line 28 comments about storing player instances in collections
				sessions.put(player, new Session(this, player, item));
			return true;
		}
		
		// if no "normal" recipes found, try inverse ones
		if ( recipes.isIngredient(item) )
		{
			if ( hasPermission(player, "ingredient", true) )
				displayResults(player, recipes.getProductsMadeWith(item));
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
		
		// read line 28 comments about storing player instances in collections
		sessions.put(player, new Session(this, player, results));
		return true;
	}
	
	///// REVISAR ESTA WEA v
	
	/** check before load recipes for given product */
	List<Recipe> findRecipes(Player player, ItemStack product)
	{
		List<Recipe> lRecipes = recipes.getRecipesFor(product);
		
		// if no "normal" recipes found, try inverse ones before
		if ( lRecipes.isEmpty() )
			sessions.get(player).updateResults(recipes.getProductsMadeWith(product));
		return lRecipes;
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
			return "Recipedia plugin requires Minecraft client";
		
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
			return "\u00A7dMissing string in conf: \u00A7e"+key;
	}
	
	
	static boolean isEmptyItem(Material material)
	{
		return material == null || material == Material.AIR || material == Material.LEGACY_AIR;
	}
	
	
	static boolean isEmptyItem(ItemStack item)
	{
		return item == null || isEmptyItem(item.getType());
	}
	
}
