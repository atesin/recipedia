
package cl.netgamer.recipedia;

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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;


class Sight
{
	
	private Player player;
	private Location eyeLocation;
	private Location hitLocation;
	private double hitDistance = 50.00; // squared, just for comparisons

	Sight(Player player)
	{
		this.player = player;
		eyeLocation = player.getEyeLocation();
	}
	
	
	ItemStack getTargetItem()
	{
		// get target entity first, then try to find a closer target block
		Entity targetEntity = getTargetEntity();
		Block targetBlock = getTargetBlock();
		
		// return block item if found, otherwise return entity item
		if ( targetBlock != null )
			return blockToItem(targetBlock);

		Material entityMaterial = getEntityMaterial(targetEntity);
		return entityMaterial == null? null: new ItemStack(entityMaterial);
	}
	
	
	/**
	get player target entity, closer than hitDistance
	@return target entity (it updates hitDistance also), or null if not found
	*/
	private Entity getTargetEntity()
	{
		Entity targetEntity = null;
		for (Entity entity : player.getNearbyEntities(5, 5, 5))
		{
			// on hit, get hit point location
			RayTraceResult hitStatus = entity.getBoundingBox().rayTrace(eyeLocation.toVector(), eyeLocation.getDirection(), 5);
			if ( hitStatus == null )
				continue;
			
			// check distance to hit point
			Location entityHitLocation = hitStatus.getHitPosition().toLocation(entity.getWorld());
			double entityHitDistance = eyeLocation.distanceSquared(entityHitLocation);
			if ( entityHitDistance > hitDistance )
				continue;
			
			// closer candidate found, update data
			targetEntity = entity;
			hitDistance = entityHitDistance;
		}
		return targetEntity;
	}
	
	
	/**
	get player target block, closest than hitDistance
	@return target clock (it updates hitDistance also), or null if not found
	*/
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
		
		// transparent lava fix: walk blocks in player line of sight until target or lava were reached
		for (Block block : player.getLineOfSight(null, 6))
		{
			if ( block == targetBlock ||  isLavaBlock(block) )
				return block;
		}
		
		return targetBlock;
	}
	
	
	@SuppressWarnings("deprecation")
	private boolean isLavaBlock(Block block)
	{
		// legacy materials for servers updated prior "the flattening"
		switch (block.getType())
		{
		case LAVA:
		case LEGACY_LAVA:
		case LEGACY_STATIONARY_LAVA:
			return true;
		default:
			return false;
		}
	}
	
	
	private ItemStack blockToItem(Block block)
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
		case SWEET_BERRY_BUSH:
			return new ItemStack(Material.SWEET_BERRIES);
		case REDSTONE_WIRE:
			return new ItemStack(Material.REDSTONE);
		case TRIPWIRE:
			return new ItemStack(Material.STRING);
		// BUBBLE_COLUMN
		// FROSTED_ICE
		default:
			return new ItemStack(Material.getMaterial(block.getType().toString()
				.replace("WALL_", "")
				.replace("POTTED_", "")
				.replace("ATTACHED_", "")
				.replace("TALL_", "")
				.replace("_STEM", "")
			));
		}
	}
	
	
	private Material getEntityMaterial(Entity entity)
	{
		if ( entity == null )
			return null;
		
		// armor stands are the only *not living* living entities
		// a single armor stand can hold up to 4 items
		// use the height of the hit location to get it, or the stand itself if empty
		if ( entity instanceof ArmorStand )
		{
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
				return Material.ARMOR_STAND;
			return standItem.getType();
		}
		
		// living entities has no matching material (except armor stands)
		if ( entity instanceof LivingEntity )
			return null;
		
		// try to get the entity material
		switch (entity.getType())
		{
			case DROPPED_ITEM:
				return ((Item) entity).getItemStack().getType();
				
			case FALLING_BLOCK:
				return ((FallingBlock)entity).getBlockData().getMaterial();
				
			case ITEM_FRAME:
				// get contained item, or item frame itself if empty
				ItemStack framedItem = ((ItemFrame) entity).getItem();
				if ( Main.isEmptyItem(framedItem) )
					return Material.ITEM_FRAME;
				return framedItem.getType();
				
			case BOAT:
				switch (((Boat) entity).getWoodType())
				{
					case ACACIA:
						return Material.ACACIA_BOAT;
					case BIRCH:
						return Material.BIRCH_BOAT;
					case DARK_OAK:
						return Material.DARK_OAK_BOAT;
					case GENERIC:
						return Material.OAK_BOAT;
					case JUNGLE:
						return Material.JUNGLE_BOAT;
					case REDWOOD:
						return Material.SPRUCE_BOAT;
					default:
						return null;
				}
			
			case MINECART_CHEST:
				return Material.CHEST_MINECART;
			case MINECART_COMMAND:
				return Material.COMMAND_BLOCK_MINECART;
			case MINECART_FURNACE:
				return Material.FURNACE_MINECART;
			case MINECART_HOPPER:
				return Material.HOPPER_MINECART;
			case MINECART_TNT:
				return Material.TNT_MINECART;
			// MINECART_MOB_SPAWNER has no matching material
				
			// fix some mismatches beetween entity and material names
			case ENDER_CRYSTAL:
				return Material.END_CRYSTAL;
			case FIREBALL:
				return Material.FIRE_CHARGE;
			case FIREWORK:
				return Material.FIREWORK_ROCKET;
			case FISHING_HOOK:
				return Material.FISHING_ROD;
			case LEASH_HITCH:
				return Material.LEAD;
			case PRIMED_TNT:
				return Material.TNT;
			case THROWN_EXP_BOTTLE:
				return Material.EXPERIENCE_BOTTLE;
			case WITHER_SKULL:
				return Material.WITHER_SKELETON_SKULL;
			
			// finally try to get material from literal entity name
			default:
				return Material.getMaterial(entity.getType().toString());
		}
	}

}
