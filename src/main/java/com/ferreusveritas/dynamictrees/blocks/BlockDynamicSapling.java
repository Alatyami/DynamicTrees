package com.ferreusveritas.dynamictrees.blocks;

import java.util.Random;

import javax.annotation.Nullable;

import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.tileentity.TileEntitySpecies;
import com.ferreusveritas.dynamictrees.trees.Species;

import net.minecraft.block.Block;
import net.minecraft.block.IGrowable;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockDynamicSapling extends Block implements ITileEntityProvider, IGrowable {
	
	public BlockDynamicSapling(String name) {
		super(Material.PLANTS);
		setDefaultState(this.blockState.getBaseState());
		setSoundType(SoundType.PLANT);
		setTickRandomly(true);
		setUnlocalizedName(name);
		setRegistryName(name);
		
		hasTileEntity = true;
	}
	
	
	@Override
	protected BlockStateContainer createBlockState() {
		return new ExtendedBlockState(this, new IProperty[]{}, new IUnlistedProperty[] {SpeciesProperty.SPECIES});
	}
	
	@Override
	public IBlockState getExtendedState(IBlockState state, IBlockAccess access, BlockPos pos) {
		return state instanceof IExtendedBlockState ? ((IExtendedBlockState)state).withProperty(SpeciesProperty.SPECIES, getSpecies(access, pos, state)) : state;
	}
	
	///////////////////////////////////////////
	// TREE INFORMATION
	///////////////////////////////////////////
	
	public void setSpecies(World world, BlockPos pos, Species species) {
		world.setBlockState(pos, getDefaultState());
		TileEntity tileEntity = world.getTileEntity(pos);
		if(tileEntity instanceof TileEntitySpecies) {
			TileEntitySpecies speciesTE = (TileEntitySpecies) tileEntity;
			speciesTE.setSpecies(species);
		}
	}
	
	public Species getSpecies(IBlockAccess access, BlockPos pos, IBlockState state) {
		TileEntitySpecies tileEntitySpecies = getTileEntity(access, pos);
		return tileEntitySpecies != null ? tileEntitySpecies.getSpecies() : Species.NULLSPECIES;
	}
	
	
	///////////////////////////////////////////
	// TILE ENTITY STUFF
	///////////////////////////////////////////
	
	@Override
	public TileEntity createNewTileEntity(World worldIn, int meta) {
		return new TileEntitySpecies();
	}
	
	/*
	 * The following is modeled after the harvesting logic flow of flower pots since they too have a
	 * tileEntity that holds items that should be dropped when the block is destroyed.
	 */
	
	public void onBlockHarvested(World worldIn, BlockPos pos, IBlockState state, EntityPlayer player) {
		super.onBlockHarvested(worldIn, pos, state, player);
		
		if (player.capabilities.isCreativeMode) {
			TileEntitySpecies tileentityspecies = getTileEntity(worldIn, pos);
			if(tileentityspecies != null) {
				tileentityspecies.setSpecies(Species.NULLSPECIES);//Prevents dropping a seed in creative mode
			}
		}
	}
	
	@Nullable
	protected TileEntitySpecies getTileEntity(IBlockAccess access, BlockPos pos) {
		TileEntity tileentity = access.getTileEntity(pos);
		return tileentity instanceof TileEntitySpecies ? (TileEntitySpecies)tileentity : null;
	}
	
	@Override
	public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest) {
		if (willHarvest) return true; //If it will harvest, delay deletion of the block until after getDrops
		return super.removedByPlayer(state, world, pos, player, willHarvest);
	}
	
	@Override
	public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state, @Nullable TileEntity te, ItemStack tool) {
		super.harvestBlock(world, player, pos, state, te, tool);
		world.setBlockToAir(pos);
	}
	
	/**
	 * Called on server when World#addBlockEvent is called. If server returns true, then also called on the client. On
	 * the Server, this may perform additional changes to the world, like pistons replacing the block with an extended
	 * base. On the client, the update may involve replacing tile entities or effects such as sounds or particles
	 */
	public boolean eventReceived(IBlockState state, World worldIn, BlockPos pos, int id, int param) {
		TileEntity tileentity = worldIn.getTileEntity(pos);
		return tileentity == null ? false : tileentity.receiveClientEvent(id, param);
	}
	
	///////////////////////////////////////////
	// INTERACTION
	///////////////////////////////////////////
	
	@Override
	public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
		grow(world, rand, pos, state);
	}
	
	public static boolean canSaplingStay(World world, Species species, BlockPos pos) {
		//Ensure there are no adjacent branches or other saplings
		for(EnumFacing dir: EnumFacing.HORIZONTALS) {
			IBlockState blockState = world.getBlockState(pos.offset(dir));
			Block block = blockState.getBlock();
			if(TreeHelper.isBranch(block) || block instanceof BlockDynamicSapling) {
				return false;
			}
		}
		
		//Air above and acceptable soil below
		return world.isAirBlock(pos.up()) && species.isAcceptableSoil(world, pos.down(), world.getBlockState(pos.down()));
	}
	
	public boolean canBlockStay(World world, BlockPos pos, IBlockState state) {
		return canSaplingStay(world, getSpecies(world, pos, state), pos);
	}
	
	@Override
	public void grow(World world, Random rand, BlockPos pos, IBlockState state) {
		Species species = getSpecies(world, pos, state);
		if(canBlockStay(world, pos, state)) {
			species.transitionToTree(world, pos);
		} else {
			dropBlock(world, species, state, pos);
		}
	}
	
	@Override
	public SoundType getSoundType(IBlockState state, World world, BlockPos pos, Entity entity) {
		return getSpecies(world, pos, state).getSaplingSound();
	}
	
	///////////////////////////////////////////
	// DROPS
	///////////////////////////////////////////
	
	@Override
	public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
		if (!this.canBlockStay(world, pos, state)) {
			dropBlock(world, getSpecies(world, pos, state), state, pos);
		}
	}
	
	protected void dropBlock(World world, Species tree, IBlockState state, BlockPos pos) {
		dropBlockAsItem(world, pos, state, 0);
		world.setBlockToAir(pos);
	}
	
	@Override
	public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
		super.getDrops(drops, world, pos, state, fortune);
		Species species = getSpecies(world, pos, state);
		if(species != Species.NULLSPECIES) {
			drops.add(species.getSeedStack(1));
		}
	}
	
	@Override
	public Item getItemDropped(IBlockState state, Random rand, int fortune) {
		return null;//The sapling block itself is not obtainable 
	}
	
	@Override
	public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
		return getSpecies(world, pos, state).getSeedStack(1);
	}
	
	
	///////////////////////////////////////////
	// PHYSICAL BOUNDS
	///////////////////////////////////////////
	
	@Override
	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess access, BlockPos pos) {
		return getSpecies(access, pos, state).getSaplingBoundingBox();
	}
	
	///////////////////////////////////////////
	// RENDERING
	///////////////////////////////////////////
	
	@Override
	public boolean isFullCube(IBlockState state) {
		return false;
	}
	
	@Override
	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}
	
	@Override
	public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face) {
		return BlockFaceShape.UNDEFINED;//This prevents fences and walls from attempting to connect to saplings.
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public BlockRenderLayer getBlockLayer() {
		return BlockRenderLayer.CUTOUT_MIPPED;
	}
	
	@Override
	public boolean canGrow(World world, BlockPos pos, IBlockState state, boolean isClient) {
		return getSpecies(world, pos, state).canGrowWithBoneMeal(world, pos);
	}
	
	@Override
	public boolean canUseBonemeal(World world, Random rand, BlockPos pos, IBlockState state) {
		return getSpecies(world, pos, state).canUseBoneMealNow(world, rand, pos);
	}
	
}
