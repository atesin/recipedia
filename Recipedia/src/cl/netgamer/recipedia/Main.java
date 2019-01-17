
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
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;


public final class Main extends JavaPlugin implements Listener
{
	
	private FileConfiguration conf;
	RecipeBook recipes;
	private Dictionary dictionary;
	private Map<Player, Session> sessions = new HashMap<Player, Session>();
	//private Debug debug;
	
	
	public void onEnable()
	{
		saveDefaultConfig();
		conf = getConfig();
		
		recipes = new RecipeBook(this);
		dictionary = new Dictionary(this);
		
		getServer().getPluginManager().registerEvents(this, this);
		getLogger().info("Plugin ready to work");
		
		//debug = new Debug(this);
	}

	
	public void onDisable()
	{
		// can't close player inventory because must be scheduled to next tick when plugin will be disabled
		// not so neat to leave player inventory opened on reloads but at least it keeps integrity
		for (Session session : sessions.values())
			session.fakeClose();
	}
	
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent e)
	{
		Session session = sessions.get((Player) e.getPlayer());
		if ( session != null && !session.fakeClose() )
			sessions.remove((Player) e.getPlayer());
	}
	
	
	@EventHandler
	public void onInventoryClose(InventoryCloseEvent e)
	{
		Session session = sessions.get((Player) e.getPlayer());
		if ( session != null && !session.fakeClose() )
			sessions.remove((Player) e.getPlayer());
	}
	
	
	@EventHandler
	public void onInventoryClick(InventoryClickEvent e)
	{
		// skip if plugin is not being used
		if ( !sessions.containsKey(e.getWhoClicked()) )
			return;
		
		// plugin is being used, set some vars
		e.setCancelled(true);
		ItemStack item = e.getCurrentItem();
		SlotType slot = e.getSlotType();
		Player player = (Player) e.getWhoClicked();
		Session session = sessions.get(player);
		
		/*
		- click outside
		  - left click = replay command
		  - other click (right, double, etc.) = close recipe browsing session
		- click on empty slot = do nothing
		- click on hotbar = browse result pages
		- non-left click = show inverse recipe
		- click on container while "show recipe" mode = display recipe in crafting grid
		- other click = display recipe products in container + first recipe in grid
		*/
		
		// replay or close
		if ( slot == SlotType.OUTSIDE )
		{
			if ( e.getClick() == ClickType.LEFT )
				session.replay();
			else
			{
				// close inventory here should be scheduled, see InventoryClickEvent javadoc
				new BukkitRunnable()
				{
					@Override
					public void run()
					{
						player.closeInventory();
					}
				}.runTask(this);
			}
		}
		
		// do nothing
		else if ( isEmpty(item) );
		
		// navigate page
		else if ( slot == SlotType.QUICKBAR )
			session.showPage(e.getSlot());
		
		// list inverse recipes
		else if ( e.getClick() != ClickType.LEFT )
		{
			if ( hasPermission(player, "ingredient") )
				session.updateProducts(recipes.getInverseRecipeProducts(item));
		}
		
		// show recipe in grid
		else if ( slot == SlotType.CONTAINER && session.isShowingRecipe() )
			session.showRecipe(item, e.getSlot());
		
		// list item recipes (products)
		else
			session.updateRecipes(item);
	}
	
	
	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
	{
		if ( command.getName().matches("(?i)recipe(dia|hand|target)") )
			return new ArrayList<String>();
		return super.onTabComplete(sender, command, alias, args);
	}

	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String alias, String[] args)
	{
		// skip unrelated commands
		if ( !cmd.getName().matches("(?i)recipe(dia|hand|target)") )
			return true;
		String cmdName = cmd.getName().toLowerCase();
		
		/* /// debug
		if ( debug.check(sender, args) )
			return true;
		// */
		
		// the only useful info for console admins may be installed languages
		if ( !(sender instanceof Player) )
		{
			sender.sendMessage("\u00A7eRecipedia plugin requires the Minecraft graphic client");
			sender.sendMessage("\u00A7eRead 'config.yml' to know how to configure and use it");
			sender.sendMessage("\u00A7eInstalled locales: "+dictionary.getLocales());
			return true;
		}
		Player player = (Player) sender;
		
		// proccess primary command
		if ( cmdName.equals("recipedia") )
		{
			// print help page on [no] number entered
			if ( args.length < 1 || args[0].equals("1") )
				return help(player, 1);
			try
			{
				int page = Integer.parseInt(args[0]);
				if ( args.length == 1 )
					return help(player, page);
				else
					return die(player, "invalidArguments");
			}
			catch(NumberFormatException e){}
			
			// list search results on word[s] entered
			if ( hasPermission(player, "search") && showResults(player, dictionary.searchItemsByName(args)) );
			return true;
		}
		
		// try to get queried item
		ItemStack item;
		if ( cmdName.equals("recipehand") && hasPermission(player, "hand") )
			item = player.getInventory().getItemInMainHand();
		else if ( cmdName.equals("recipetarget") && hasPermission(player, "target") )
			item = new Sight(player).getTargetItem();
		else
			return true;
		
		// show [inverse] recipes for item
		if ( args.length < 1 )
			return showRecipes(player, item);
		else if ( args[0].equalsIgnoreCase("i") )
		{
			if ( hasPermission(player, "ingredient") && showResults(player, recipes.getInverseRecipeProducts(item)) );
			return true;
		}
		else
			return die(player, "invalidArguments");
	}
	
	
	private boolean showRecipes(Player player, ItemStack item)
	{
		if ( !recipes.isCraftable(item) )
			return die(player, "noRecipesFound");
		sessions.put(player, new Session(this, player, item));
		return true;
	}
	
	
	private boolean showResults(Player player, List<ItemStack> results)
	{
		if ( results.size() < 1 )
			return die(player, "noItemsFound");
		if ( results.size() > 243 )
			return die(player, "tooManyResults");
		// read WWW about storing player instances in collections
		sessions.put(player, new Session(this, player, results));
		return true;
	}
	
	
	// utilities
	
	
	boolean hasPermission(Player player, String permission)
	{
		boolean granted = hasPermissionQuiet(player, permission);
		if ( !granted )
			player.sendMessage(msg("noPermission"));
		return granted;
	}
	
	
	private boolean hasPermissionQuiet(Player player, String permission)
	{
		if ( player.isOp() || !player.isPermissionSet("recipedia."+permission) )
			return true;
		return player.hasPermission("recipedia."+permission);
	}
	
	
	private String listPermissions(Player player)
	{
		return ""
			+(hasPermissionQuiet(player, "search")?     '+': '-')+"search "
			+(hasPermissionQuiet(player, "hand")?       '+': '-')+"hand "
			+(hasPermissionQuiet(player, "target") ?    '+': '-')+"target "
			+(hasPermissionQuiet(player, "ingredient")? '+': '-')+"ingredient "
			+(hasPermissionQuiet(player, "recipe")?     '+': '-')+"recipe";
	}
	
	
	private boolean die(CommandSender sender, String key)
	{
		sender.sendMessage(msg(key));
		return true;
	}
	
	
	String msg(String key, Object... args)
	{
		if ( conf.contains(key) )
			return String.format("\u00A7e"+conf.getString(key), args);
		else
			return "\u00A7dMissing string in conf: \u00A7e"+key;
	}
	
	
	private boolean help(Player player, int pageIndex)
	{
		// chat format codes must be lowercase
		List<String> helpPages = conf.getStringList("helpPages");
		pageIndex = Math.max(1, Math.min(pageIndex, helpPages.size()));
		
		player.sendMessage("\u00A7e"+helpPages.get(pageIndex - 1)
			.replaceAll("[\r\n]+$", "")
			.replaceAll("(?m):(.*?)$", ":\u00A7b$1\u00A7e")
			/* .replaceAll("([^%])%c", "$1"+pageIndex)
			.replaceAll("([^%])%t", "$1"+helpPages.size())
			.replaceAll("([^%])%n", "$1"+dictionary.getLocales())
			.replaceAll("([^%])%p", "$1"+listPermissions(player))
			.replaceAll("([^%])%l", "$1"+player.getName())
			.replaceAll("%%([ctlpn])", "%$1");
			// */
			.replace("%c", String.valueOf(pageIndex))
			.replace("%t", String.valueOf(helpPages.size()))
			.replace("%n", dictionary.getLocales())
			.replace("%p", listPermissions(player))
			.replace("%l", player.getName())
		);
		return true;
	}
	
	
	static boolean isEmpty(ItemStack item)
	{
		if ( item == null )
			return true;
		return item.getType() == Material.AIR || item.getType() == Material.LEGACY_AIR;
	}
	
}
