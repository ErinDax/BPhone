package cn.erindax.bphone.block;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.block.entity.HomePhoneBlockEntity;
import cn.erindax.bphone.item.HomePhoneHandsetItem;
import cn.erindax.bphone.phone.PhoneCallManager;
import cn.erindax.bphone.phone.PhoneNetworking;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.lang.reflect.Method;

public final class HomePhoneBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<HomePhoneBlock> CODEC = Block.simpleCodec(HomePhoneBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty HAS_HANDSET = BooleanProperty.create("has_handset");
    private static final double[][] BASE_BOXES = new double[][]{
            {2.0D, 0.0D, 4.0D, 14.0D, 2.6D, 6.0D},
            {2.0D, 0.0D, 6.0D, 14.0D, 3.8D, 8.0D},
            {2.0D, 0.0D, 8.0D, 14.0D, 4.9D, 10.0D},
            {2.0D, 0.0D, 10.0D, 14.0D, 6.0D, 12.0D},
            {2.0D, 0.0D, 12.0D, 14.0D, 6.2D, 13.5D}
    };
    private static final double[][] HANDSET_BOXES = new double[][]{
            {10.0D, 0.3D, 4.0D, 12.4D, 4.5D, 7.0D},
            {10.0D, 3.4D, 7.0D, 12.4D, 6.0D, 10.0D},
            {10.0D, 4.3D, 10.0D, 12.4D, 7.1D, 13.6D}
    };
    private static final VoxelShape[] SHAPES = buildShapes(false);
    private static final VoxelShape[] SHAPES_WITH_HANDSET = buildShapes(true);

    public HomePhoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HAS_HANDSET, true));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new HomePhoneBlockEntity(blockPos, blockState);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HAS_HANDSET, true);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_HANDSET);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        InteractionHand handsetHand = resolveHeldBoundHandsetHand(player, level, pos);
        if (handsetHand != null) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (player.isShiftKeyDown()) {
                    PhoneCallManager.putDownHomePhoneHandset(serverPlayer, level.dimension(), pos);
                } else if (PhoneCallManager.hasActiveHomePhoneCall(serverPlayer, pos)) {
                    PhoneNetworking.openHomePhoneScreen(serverPlayer, pos);
                } else {
                    PhoneCallManager.putDownHomePhoneHandset(serverPlayer, level.dimension(), pos);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (hand != InteractionHand.MAIN_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                PhoneCallManager.pickUpHomePhoneHandset(serverPlayer, pos, hand);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (level.isClientSide) {
            openHomePhoneScreenClient(pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide) {
            openHomePhoneScreenClient(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level instanceof ServerLevel serverLevel) {
            PhoneCallManager.handleHomePhoneRemoved(serverLevel, pos);
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static VoxelShape shapeFor(BlockState state) {
        VoxelShape[] shapes = state.getValue(HAS_HANDSET) ? SHAPES_WITH_HANDSET : SHAPES;
        return shapes[state.getValue(FACING).get2DDataValue()];
    }

    private static VoxelShape[] buildShapes(boolean withHandset) {
        VoxelShape[] shapes = new VoxelShape[4];
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] box : BASE_BOXES) {
                shape = Shapes.or(shape, rotateBox(direction, box));
            }
            if (withHandset) {
                for (double[] box : HANDSET_BOXES) {
                    shape = Shapes.or(shape, rotateBox(direction, box));
                }
            }
            shapes[direction.get2DDataValue()] = shape;
        }
        return shapes;
    }

    private static VoxelShape rotateBox(Direction direction, double[] box) {
        double minX = box[0];
        double minY = box[1];
        double minZ = box[2];
        double maxX = box[3];
        double maxY = box[4];
        double maxZ = box[5];
        return switch (direction) {
            case EAST -> Block.box(16.0D - maxZ, minY, minX, 16.0D - minZ, maxY, maxX);
            case SOUTH -> Block.box(16.0D - maxX, minY, 16.0D - maxZ, 16.0D - minX, maxY, 16.0D - minZ);
            case WEST -> Block.box(minZ, minY, 16.0D - maxX, maxZ, maxY, 16.0D - minX);
            default -> Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        };
    }

    private static void openHomePhoneScreenClient(BlockPos blockPos) {
        try {
            Class<?> hookClass = Class.forName("cn.erindax.bphone.client.phone.PhoneClientHooks");
            Method openMethod = hookClass.getDeclaredMethod("openHomePhoneScreen", BlockPos.class);
            openMethod.invoke(null, blockPos);
        } catch (ReflectiveOperationException exception) {
            BPhone.LOGGER.debug("Failed to open home phone screen", exception);
        }
    }

    private static InteractionHand resolveHeldBoundHandsetHand(Player player, Level level, BlockPos blockPos) {
        if (player == null || level == null || blockPos == null) {
            return null;
        }

        for (InteractionHand candidateHand : InteractionHand.values()) {
            var boundAddress = HomePhoneHandsetItem.getBoundAddress(player.getItemInHand(candidateHand));
            if (boundAddress != null
                    && boundAddress.dimension().equals(level.dimension())
                    && boundAddress.blockPos().equals(blockPos)) {
                return candidateHand;
            }
        }

        return null;
    }
}
