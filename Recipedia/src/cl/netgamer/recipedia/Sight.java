
package cl.netgamer.recipedia;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;


class Sight
{
	
	private Set<Material> lavas = new HashSet<Material>();
	private Player player;
	private Location eyeLocation;
	private Location hitLocation;
	private double hitDistance = 50.00; // squared, just for comparisons

	Sight(Player player)
	{
		this.player = player;
		eyeLocation = player.getEyeLocation();
		lavas.add(Material.LAVA);
		lavas.add(Material.LEGACY_LAVA);
		lavas.add(Material.LEGACY_STATIONARY_LAVA);
	}
	
	
	ItemStack getTargetItem()
	{
		// get target entity first, then try to find a closest target block
		Entity targetEntity = getTargetEntity();
		Block targetBlock = getTargetBlock();
		
		// find if lava is blocking the view
		if ( blockedByLava() )
			return null;
		
		// return block item if found, otherwise return entity item
		if ( targetBlock != null )
			return toItem(targetBlock);
		return toItem(targetEntity);
	}
	
	
	private Entity getTargetEntity()
	{
		Entity targetEntity = null;
		for (Entity entity : player.getNearbyEntities(5, 5, 5))
		{
			// get hit location if exists
			RayTraceResult hitStatus = entity.getBoundingBox().rayTrace(eyeLocation.toVector(), eyeLocation.getDirection(), 5);
			if ( hitStatus == null )
				continue;
			
			// check hit distance
			Location entityHitLocation = hitStatus.getHitPosition().toLocation(entity.getWorld());
			double entityHitDistance = eyeLocation.distanceSquared(entityHitLocation);
			if ( entityHitDistance > hitDistance )
				continue;
			
			targetEntity = entity;
			hitLocation = entityHitLocation;
			hitDistance = entityHitDistance;
		}
		return targetEntity;
	}
	
	
	private Block getTargetBlock()
	{
		// get target block (if any) with precise collision shape
		// but has a flaw: lava is considered transparent (ignored) when is actually opaque
		Block targetBlock = player.getTargetBlockExact(5);
		if ( targetBlock == null )
			return null;
		
		RayTraceResult hitStatus = targetBlock.rayTrace(eyeLocation, eyeLocation.getDirection(), 6, FluidCollisionMode.NEVER);
		double blockHitDistance = eyeLocation.distanceSquared(hitStatus.getHitPosition().toLocation(targetBlock.getWorld()));
		if ( blockHitDistance > hitDistance )
			return null;
		
		hitDistance = blockHitDistance;
		return targetBlock;
	}
	
	
	private boolean blockedByLava()
	{
		// try to fix the transparent lava block flaw
		Set<Material> transparent = new HashSet<Material>();
		Block testBlock;
		Material testMaterial;
		
		// advance blocks throught line of sight adding them to transparent set
		// repeat until lava were reached
		do
		{
			testBlock = player.getTargetBlock(transparent, 6);
			testMaterial = testBlock.getType();
			if ( testBlock == null || transparent.contains(testMaterial) )
				return false;
			transparent.add(testMaterial);
		}
		while ( !lavas.contains(testMaterial) );
		
		// get hit distance and compare with previous, hitbox used to surpass fluid collision flaw
		RayTraceResult hitStatus = BoundingBox.of(testBlock).rayTrace(eyeLocation.toVector(), eyeLocation.getDirection(), 6);
		if ( hitStatus == null )
			return false;
		if ( eyeLocation.distanceSquared(hitStatus.getHitPosition().toLocation(testBlock.getWorld())) > hitDistance )
			return false;
		return true;
	}
	
	
	private ItemStack toItem(Block block)
	{
		switch (block.getType())
		{
		case BEETROOTS:
			return new ItemStack(Material.BEETROOT);
		case CARROTS:
			return new ItemStack(Material.CARROT);
		case COCOA:
			return new ItemStack(Material.COCOA_BEANS);
		case KELP_PLANT:
			return new ItemStack(Material.KELP);
		case POTATOES:
			return new ItemStack(Material.POTATO);
		case REDSTONE_WIRE:
			return new ItemStack(Material.REDSTONE);
		case TALL_SEAGRASS:
			return new ItemStack(Material.SEAGRASS);
		case TRIPWIRE:
			return new ItemStack(Material.TRIPWIRE_HOOK);
		// BUBBLE_COLUMN
		// FROSTED_ICE
		default:
			String materialName = block.getType().toString();
			materialName = materialName.replace("WALL_", "").replace("POTTED_", "").replace("ATTACHED_", "").replace("_STEM", "");
			return new ItemStack(Material.getMaterial(materialName));
		}
	}
	
	
	private ItemStack toItem(Entity entity)
	{
		if ( entity == null )
			return null;
		
		switch (entity.getType())
		{
		case ARROW:
		case EGG:
		case ENDER_PEARL:
		case LINGERING_POTION:
		case PAINTING:
		case MINECART:
		case SNOWBALL:
		case SPECTRAL_ARROW:
		case SPLASH_POTION:
		case TIPPED_ARROW:
		case TRIDENT:
			return new ItemStack(Material.getMaterial(entity.getType().name()));
		case DRAGON_FIREBALL:
		case FIREBALL:
		case SMALL_FIREBALL:
			return new ItemStack(Material.FIRE_CHARGE);
		case MINECART_COMMAND:
			return new ItemStack(Material.COMMAND_BLOCK_MINECART);
		case ENDER_CRYSTAL:
			return new ItemStack(Material.END_CRYSTAL);
		case ENDER_SIGNAL:
			return new ItemStack(Material.ENDER_EYE);
		case MINECART_TNT:
			return new ItemStack(Material.TNT_MINECART);
		case FIREWORK:
			return new ItemStack(Material.FIREWORK_ROCKET);
		case FISHING_HOOK:
			return new ItemStack(Material.FISHING_ROD);
		case MINECART_HOPPER:
			return new ItemStack(Material.HOPPER_MINECART);
		case LEASH_HITCH:
			return new ItemStack(Material.LEAD);
		case MINECART_FURNACE:
			return new ItemStack(Material.FURNACE_MINECART);
		case MINECART_CHEST:
			return new ItemStack(Material.CHEST_MINECART);
		case THROWN_EXP_BOTTLE:
			return new ItemStack(Material.EXPERIENCE_BOTTLE);
		case PRIMED_TNT:
			return new ItemStack(Material.TNT);
		case WITHER_SKULL:
			return new ItemStack(Material.WITHER_SKELETON_SKULL);
		case BOAT:
			String woodName = ((Boat)entity).getWoodType().name();
			woodName = woodName.replace("GENERIC", "OAK").replace("REDWOOD", "SPRUCE");
			return new ItemStack(Material.getMaterial(woodName+"_BOAT"));
		case FALLING_BLOCK:
			return new ItemStack(((FallingBlock)entity).getBlockData().getMaterial());
		case DROPPED_ITEM:
			return ((Item) entity).getItemStack();
		case ITEM_FRAME:
			ItemStack framedItem = ((ItemFrame) entity).getItem();
			if ( Main.isEmptyItem(framedItem) )
				return new ItemStack(Material.ITEM_FRAME);
			return framedItem;
		case ARMOR_STAND:
			// special case: a single armor stand can hold 4 items
			// use the height of the hit location to select it
			ArmorStand stand = (ArmorStand) entity;
			ItemStack standItem;
			
			double hitHeight = hitLocation.getY() - entity.getLocation().getY();
			if ( hitHeight < 0.50 )
				standItem = stand.getBoots();
			else if ( hitHeight < 1.00 )
				standItem = stand.getLeggings();
			else if ( hitHeight < 1.50 )
				standItem = stand.getChestplate();
			else
				standItem = stand.getHelmet();
			
			if ( Main.isEmptyItem(standItem) )
				return new ItemStack(Material.ARMOR_STAND);
			return standItem;
		default:
			return null;
		}
	}

}
