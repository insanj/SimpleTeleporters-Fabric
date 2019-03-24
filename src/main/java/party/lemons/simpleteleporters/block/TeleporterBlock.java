package party.lemons.simpleteleporters.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.network.packet.PlayerPositionLookS2CPacket;
import net.minecraft.client.network.packet.PlayerPositionLookS2CPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.VerticalEntityPosition;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.sortme.ItemScatterer;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateFactory;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.inventory.Inventories;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BoundingBox;
import net.minecraft.util.math.Direction;
import net.minecraft.block.FacingBlock;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.BlockView;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.loot.context.LootContextParameters;
import party.lemons.simpleteleporters.block.entity.TeleporterBlockEntity;
import party.lemons.simpleteleporters.init.SimpleTeleportersItems;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

public class TeleporterBlock extends BlockWithEntity
{
	public static BooleanProperty ON = BooleanProperty.create("on");
	public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
	protected static final VoxelShape TELE_AABB = VoxelShapes.cube(0D, 0.0D, 0D, 1D, 0.3D, 1D); // originally instance of VoxelShapeContainer

	public TeleporterBlock(Settings settings)
	{
		super(settings);
		this.setDefaultState(this.stateFactory.getDefaultState().with(ON, false).with(WATERLOGGED, false));
	}

	@Override
	public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity)
	{
		if(entity.isSneaking() && entity instanceof PlayerEntity)
		{
			TeleporterBlockEntity teleporter = (TeleporterBlockEntity) world.getBlockEntity(pos);
			if(teleporter.hasCrystal() && teleporter.isInDimension(entity))
			{
				entity.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
				if(entity instanceof ServerPlayerEntity && world.getServer() != null && !world.getServer().isRemote())
				{
						BlockPos teleporterPos = teleporter.getTeleportPosition();
						ServerPlayerEntity splayer = (ServerPlayerEntity) entity;

						splayer.velocityModified = true;

						splayer.networkHandler.teleportRequest(teleporterPos.getX() + 0.5, teleporterPos.getY(), teleporterPos.getZ() + 0.5, entity.yaw, entity.pitch, EnumSet.noneOf(net.minecraft.client.network.packet.PlayerPositionLookS2CPacket.Flag.class));

						splayer.setVelocity(0, 0.5, 0); // originally just a velocityY =
						splayer.velocityDirty = true; // maybe scheduleVelocityUpdate ?

						splayer.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
				} else if (entity instanceof PlayerEntity) {
					BlockPos teleporterPos = teleporter.getTeleportPosition();
					PlayerEntity cplayer = (PlayerEntity) entity;

					cplayer.velocityModified = true;
					cplayer.setLocationAndAngles(teleporterPos.getX() + 0.5, teleporterPos.getY(), teleporterPos.getZ() + 0.5, entity.yaw, entity.pitch);
					cplayer.setPositionAnglesAndUpdate(teleporterPos.getX() + 0.5, teleporterPos.getY(), teleporterPos.getZ() + 0.5, entity.yaw, entity.pitch);
					cplayer.setVelocity(0, 0.5, 0);
					cplayer.velocityDirty = true;

					cplayer.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
				}
			}
		}
	}

	@Override
	public boolean activate(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hitResult) //float v, float v1, float v2)
	{
		TeleporterBlockEntity tele = (TeleporterBlockEntity) world.getBlockEntity(pos);
		if(tele.hasCrystal())
		{
			ItemStack crystalStack = tele.getCrystal();
			player.inventory.insertStack(crystalStack);
			player.playSound(SoundEvents.ENTITY_ARROW_SHOOT, 0.5F, 0.4F / (world.random.nextFloat() * 0.4F + 0.8F));

			world.setBlockState(pos, state.with(ON, false));
			tele.setCrystal(ItemStack.EMPTY);

			return true;
		}
		else
		{
			ItemStack stack = player.getStackInHand(hand);
			if(!stack.isEmpty())
			{
				if(stack.getItem() == SimpleTeleportersItems.TELE_CRYSTAL && stack.getTag() != null)
				{
					player.playSound(SoundEvents.ENTITY_ARROW_SHOOT, 0.5F, 0.4F / (world.random.nextFloat() * 0.4F + 0.8F));
					world.setBlockState(pos, state.with(ON, true));
					ItemStack setstack = stack.copy();
					setstack.setAmount(1);
					tele.setCrystal(setstack);
					stack.subtractAmount(1);
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public void onBreak(World world, BlockPos blockPos, BlockState blockState, PlayerEntity playerEntity)
	{
		TeleporterBlockEntity tele = (TeleporterBlockEntity) world.getBlockEntity(blockPos);
		ItemScatterer.spawn(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), tele.getCrystal());
		super.onBreak(world, blockPos, blockState, playerEntity);
	}


	@Override
	public FluidState getFluidState(BlockState var1)
	{
		return var1.get(WATERLOGGED) ? Fluids.WATER.getState(false) : super.getFluidState(var1);
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction facing, BlockState neighborState, IWorld world, BlockPos pos, BlockPos neighborPos)
	{
		if (state.get(WATERLOGGED)) {
			world.getFluidTickScheduler().schedule(pos, Fluids.WATER, Fluids.WATER.getTickRate(world)); // getTickRate == method_15789?
		}

		return super.getStateForNeighborUpdate(state, facing, neighborState, world, pos, neighborPos);
	}

	@Override
	public VoxelShape getRayTraceShape(BlockState state, BlockView world, BlockPos pos)
	{
		return TELE_AABB;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView view, BlockPos pos, VerticalEntityPosition ePos)
	{
		return TELE_AABB;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, VerticalEntityPosition ePos)
	{
		return TELE_AABB;
 	}

	@Override
	protected void appendProperties(StateFactory.Builder<Block, BlockState> st)
	{
		st.with(ON).with(WATERLOGGED);
	}

	@Override
	public BlockEntity createBlockEntity(BlockView blockView)
	{
		return new TeleporterBlockEntity();
	}

	public BlockRenderType getRenderType(BlockState var1)
	{
		return BlockRenderType.MODEL;
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random)
	{
		if(state.get(ON))
		{
			for(int i = 0; i < 15; i++)
			{
				world.addParticle(ParticleTypes.PORTAL, pos.getX() + 0.2F + (random.nextFloat()/2), pos.getY() + 0.4F, pos.getZ() + 0.2F + (random.nextFloat()/2), 0, random.nextFloat(), 0); 	// originally method_8406
			}
		}
	}

	public BlockState getPlacementState(ItemPlacementContext ctx)
	{
		FluidState fs = ctx.getWorld().getFluidState(ctx.getBlockPos());
		boolean isWater = fs.getFluid().equals(Fluids.WATER);
		return this.getDefaultState().with(WATERLOGGED, isWater);
	}
}
