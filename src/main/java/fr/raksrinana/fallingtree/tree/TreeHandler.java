package fr.raksrinana.fallingtree.tree;

import fr.raksrinana.fallingtree.config.Config;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import static fr.raksrinana.fallingtree.FallingTreeUtils.isLeafBlock;
import static fr.raksrinana.fallingtree.FallingTreeUtils.isTreeBlock;

public class TreeHandler{
	@Nonnull
	public static Optional<Tree> getTree(@Nonnull World world, @Nonnull BlockPos blockPos){
		Block logBlock = world.getBlockState(blockPos).getBlock();
		if(!isTreeBlock(logBlock)){
			return Optional.empty();
		}
		Queue<BlockPos> toAnalyzePos = new LinkedList<>();
		Set<BlockPos> analyzedPos = new HashSet<>();
		Tree tree = new Tree(world, blockPos);
		toAnalyzePos.add(blockPos);
		while(!toAnalyzePos.isEmpty()){
			BlockPos analyzingPos = toAnalyzePos.remove();
			tree.addLog(analyzingPos);
			analyzedPos.add(analyzingPos);
			Collection<BlockPos> nearbyPos = neighborLogs(world, logBlock, analyzingPos, analyzedPos);
			nearbyPos.removeAll(analyzedPos);
			toAnalyzePos.addAll(nearbyPos.stream().filter(pos -> !toAnalyzePos.contains(pos)).collect(Collectors.toList()));
		}
		return Optional.of(tree);
	}
	
	@Nonnull
	private static Collection<BlockPos> neighborLogs(@Nonnull IWorld world, @Nonnull Block logBlock, @Nonnull BlockPos blockPos, @Nonnull Collection<BlockPos> analyzedPos){
		List<BlockPos> neighborLogs = new LinkedList<>();
		final BlockPos.Mutable checkPos = new BlockPos.Mutable();
		for(int x = -1; x <= 1; x++){
			for(int z = -1; z <= 1; z++){
				for(int y = -1; y <= 1; y++){
					checkPos.setPos(blockPos.getX() + x, blockPos.getY() + y, blockPos.getZ() + z);
					if(!analyzedPos.contains(checkPos) && isSameLog(world, checkPos, logBlock)){
						neighborLogs.add(checkPos.toImmutable());
					}
				}
			}
		}
		neighborLogs.addAll(analyzedPos);
		return neighborLogs;
	}
	
	private static boolean isSameLog(@Nonnull IWorld world, @Nonnull BlockPos blockPos, @Nullable Block logBlock){
		return world.getBlockState(blockPos).getBlock().equals(logBlock);
	}
	
	public static boolean destroy(@Nonnull Tree tree, @Nonnull PlayerEntity player, @Nonnull ItemStack tool){
		final World world = tree.getWorld();
		final boolean noToolLoss = (!tool.isDamageable() || Config.COMMON.getToolsConfiguration().isIgnoreDurabilityLoss());
		final int damageMultiplicand = Config.COMMON.getToolsConfiguration().getDamageMultiplicand();
		int toolUsesLeft = noToolLoss ? Integer.MAX_VALUE : ((tool.getMaxDamage() - tool.getDamage()) / damageMultiplicand);
		if(Config.COMMON.getToolsConfiguration().isPreserve()){
			toolUsesLeft--;
		}
		if(toolUsesLeft < 1){
			return false;
		}
		final boolean isTreeFullyBroken = noToolLoss || toolUsesLeft >= tree.getLogCount();
		tree.getLogs().stream().limit(toolUsesLeft).forEachOrdered(logBlock -> {
			final BlockState logState = world.getBlockState(logBlock);
			if(!Config.COMMON.getToolsConfiguration().isIgnoreDurabilityLoss()){
				tool.damageItem(damageMultiplicand, player, (entity) -> {});
			}
			player.addStat(Stats.ITEM_USED.get(logState.getBlock().asItem()));
			logState.getBlock().harvestBlock(world, player, logBlock, logState, world.getTileEntity(logBlock), tool);
			world.destroyBlock(logBlock, false);
		});
		if(isTreeFullyBroken){
			final int radius = Config.COMMON.getTreesConfiguration().getLavesBreakingForceRadius();
			if(radius > 0){
				tree.getLogs().stream().max(Comparator.comparingInt(BlockPos::getY)).ifPresent(topLog -> {
					BlockPos.Mutable checkPos = new BlockPos.Mutable();
					for(int dx = -radius; dx < radius; dx++){
						for(int dy = -radius; dy < radius; dy++){
							for(int dz = -radius; dz < radius; dz++){
								checkPos.setPos(topLog.getX() + dx, topLog.getY() + dy, topLog.getZ() + dz);
								final BlockState checkState = world.getBlockState(checkPos);
								final Block checkBlock = checkState.getBlock();
								if(isLeafBlock(checkBlock)){
									Block.spawnDrops(checkState, world, checkPos);
									world.removeBlock(checkPos, false);
								}
							}
						}
					}
				});
			}
		}
		return true;
	}
}
