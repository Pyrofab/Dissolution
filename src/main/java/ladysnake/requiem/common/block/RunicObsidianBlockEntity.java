/*
 * Requiem
 * Copyright (C) 2017-2021 Ladysnake
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses>.
 *
 * Linking this mod statically or dynamically with other
 * modules is making a combined work based on this mod.
 * Thus, the terms and conditions of the GNU General Public License cover the whole combination.
 *
 * In addition, as a special exception, the copyright holders of
 * this mod give you permission to combine this mod
 * with free software programs or libraries that are released under the GNU LGPL
 * and with code included in the standard release of Minecraft under All Rights Reserved (or
 * modified versions of such code, with unchanged license).
 * You may copy and distribute such a system following the terms of the GNU GPL for this mod
 * and the licenses of the other code concerned.
 *
 * Note that people who make modified versions of this mod are not obligated to grant
 * this special exception for their modified versions; it is their choice whether to do so.
 * The GNU General Public License gives permission to release a modified version without this exception;
 * this exception also makes it possible to release a modified version which carries forward this exception.
 */
package ladysnake.requiem.common.block;

import com.google.common.base.Preconditions;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import ladysnake.requiem.api.v1.block.ObeliskRune;
import ladysnake.requiem.api.v1.remnant.RemnantComponent;
import ladysnake.requiem.common.tag.RequiemBlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.StairShape;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.Tag;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class RunicObsidianBlockEntity extends BlockEntity {
    public static final Direction[] OBELISK_SIDES = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
    public static final int POWER_ATTEMPTS = 1;
    public static final int MAX_OBELISK_WIDTH = 5;
    public static final DataResult<ObeliskMatch> INVALID_BASE = DataResult.error("Structure does not have a matching base");
    public static final DataResult<ObeliskMatch> INVALID_CORE = DataResult.error("Structure does not have a matching runic core");
    public static final DataResult<ObeliskMatch> INVALID_CAP = DataResult.error("Structure does not have a matching cap");

    private final Object2IntMap<ObeliskRune> levels = new Object2IntOpenHashMap<>();
    private boolean requiresInit = true;
    private @Nullable BlockPos delegate;
    private int obeliskWidth = 0;
    private int obeliskHeight = 0;

    public RunicObsidianBlockEntity(BlockPos pos, BlockState state) {
        super(RequiemBlockEntities.RUNIC_OBSIDIAN, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, RunicObsidianBlockEntity be) {
        if (world.isClient) return;

        // Salt the time to avoid checking every potential obelisk on the same tick
        if ((world.getTime() + pos.hashCode()) % 80L == 0L) {
            RunicObsidianBlock.tryActivateObelisk((ServerWorld) world, pos);

            if (!state.get(RunicObsidianBlock.ACTIVATED)) {
                world.removeBlockEntity(pos);
                be.markRemoved();
                return;
            }

            if (be.requiresInit) {
                be.init(state);
            }

            int obeliskWidth = be.obeliskWidth;
            Vec3d obeliskCenter = getObeliskCenter(pos, obeliskWidth);

            if (!be.levels.isEmpty() && be.findPowerSource((ServerWorld) world, obeliskCenter, obeliskWidth * 5)) {
                be.applyPlayerEffects(world, pos);
            }
        }
    }

    @NotNull
    private static Vec3d getObeliskCenter(BlockPos pos, int obeliskWidth) {
        return new Vec3d(
            MathHelper.lerp(0.5, pos.getX(), pos.getX() + obeliskWidth - 1),
            pos.getY() - 2,
            MathHelper.lerp(0.5, pos.getZ(), pos.getZ() + obeliskWidth - 1)
        );
    }

    public static Optional<BlockPos> findObeliskOrigin(World world, BlockPos pos) {
        while (true) {
            ObeliskOriginMatch match = tryMatchObeliskOrigin(world, pos);
            if (match.origin != null) return Optional.of(match.origin);
            else if (match.delegate == null) return Optional.empty();
            else pos = match.delegate;
        }
    }

    private void applyPlayerEffects(World world, BlockPos pos) {
        Preconditions.checkState(this.obeliskWidth > 0);
        Preconditions.checkState(this.obeliskHeight > 0);

        double range = this.obeliskWidth * 10 + 10;
        Box box = (new Box(pos, pos.add(this.obeliskWidth - 1, obeliskHeight - 1, this.obeliskWidth - 1))).expand(range);

        List<PlayerEntity> players = world.getNonSpectatingEntities(PlayerEntity.class, box);

        for (PlayerEntity player : players) {
            if (RemnantComponent.get(player).getRemnantType().isDemon()) {
                for (Object2IntMap.Entry<ObeliskRune> effect : this.levels.object2IntEntrySet()) {
                    effect.getKey().applyEffect((ServerPlayerEntity) player, effect.getIntValue(), this.obeliskWidth);
                }
            }
        }
    }

    private boolean findPowerSource(ServerWorld world, Vec3d center, double range) {
        BlockPos.Mutable checked = new BlockPos.Mutable();

        for (int attempt = 0; attempt < RunicObsidianBlockEntity.POWER_ATTEMPTS; attempt++) {
            // https://stackoverflow.com/questions/5837572/generate-a-random-point-within-a-circle-uniformly/50746409#50746409
            double r = range * Math.sqrt(world.random.nextDouble());
            double theta = world.random.nextDouble() * 2 * Math.PI;
            double x = center.x + r * Math.cos(theta);
            double z = center.z + r * Math.sin(theta);
            checked.set(Math.round(x), center.y, Math.round(z));
            BlockState state = world.getBlockState(checked);

            while (!state.isSolidBlock(world, checked)) {
                checked.move(Direction.DOWN);
                state = world.getBlockState(checked);
            }

            if (state.isIn(BlockTags.SOUL_SPEED_BLOCKS)) {
                Vec3d particleSrc = new Vec3d(checked.getX() + 0.5, checked.getY() + 0.9, checked.getZ() + 0.5);
                Vec3d toObelisk = center.subtract(particleSrc).normalize();
                world.spawnParticles(ParticleTypes.SOUL, particleSrc.x, particleSrc.y, particleSrc.z, 0, toObelisk.x, 1, toObelisk.z, 0.1);
                return true;
            }
        }

        return false;
    }

    private RunicObsidianBlockEntity getDelegate() {
        if (this.world != null && this.delegate != null) {
            BlockEntity blockEntity = this.world.getBlockEntity(this.delegate);
            if (blockEntity instanceof RunicObsidianBlockEntity) {
                return ((RunicObsidianBlockEntity) blockEntity).getDelegate();
            }
        }
        return this;
    }

    public int getRangeLevel() {
        return this.getDelegate().obeliskWidth;
    }

    public int getPowerLevel() {
        return this.getDelegate().obeliskHeight;
    }

    private void init(BlockState state) {
        assert this.world != null;
        this.delegate = null;
        this.levels.clear();
        this.obeliskWidth = 0;
        this.obeliskHeight = 0;

        tryMatchObeliskOrigin(this.world, this.pos).apply(
            obeliskOrigin -> matchObelisk(world, obeliskOrigin).result().ifPresent(match -> {
                this.obeliskWidth = match.coreWidth();
                this.obeliskHeight = match.coreHeight();
                this.levels.putAll(match.collectRunes());
            }),
            delegate -> this.delegate = delegate,
            () -> this.world.getBlockTickScheduler().schedule(this.pos, state.getBlock(), 0)
        );
    }

    /**
     * Finds the bottommost northwest corner of an obelisk.
     *
     * <p>If a {@link RunicObsidianBlockEntity} is found along the way, the search will be aborted
     * and {@link #delegate} will be set.
     * This method does not check that there is a valid obelisk, however it will abort and return
     * {@code null} if the core below this block entity does not conform
     * to expectations.
     *
     * @param world the world in which to search for the origin of an obelisk
     * @param pos   the position from which to initiate the search
     * @return an {@link Either} describing either a potential origin for an obelisk, or the position of a better-suited delegate
     */
    public static ObeliskOriginMatch tryMatchObeliskOrigin(World world, BlockPos pos) {
        // getBlockEntity is faster than getBlockState, and we know that adjacent blocks must be of the same type for the structure to be valid
        if (!(world.getBlockState(pos.west()).isIn(RequiemBlockTags.OBELISK_CORE))) {
            if (!(world.getBlockState(pos.north()).isIn(RequiemBlockTags.OBELISK_CORE))) {
                // we are at the northwest corner, now we go down until we find either the floor or a better candidate
                // this should not go on for too many blocks, so mutable blockpos should not be necessary
                BlockPos obeliskOrigin = pos;
                BlockPos down = obeliskOrigin.down();
                BlockState downState = world.getBlockState(down);

                while (!downState.isIn(RequiemBlockTags.OBELISK_FRAME)) {
                    if (!downState.isIn(RequiemBlockTags.OBELISK_CORE)) {
                        return ObeliskOriginMatch.failure();
                    }
                    obeliskOrigin = down;
                    down = obeliskOrigin.down();

                    if (world.getBlockEntity(down) instanceof RunicObsidianBlockEntity) {
                        // Found a better candidate in a lower core layer
                        return ObeliskOriginMatch.partial(down);
                    }

                    downState = world.getBlockState(down);
                }

                return ObeliskOriginMatch.success(obeliskOrigin);
            } else {
                // Found a better candidate in the same layer
                return ObeliskOriginMatch.partial(pos.north());
            }
        } else {
            // Found a better candidate in the same layer
            return ObeliskOriginMatch.partial(pos.west());
        }
    }

    public static DataResult<ObeliskMatch> matchObelisk(World world, BlockPos origin) {
        int tentativeWidth = matchObeliskBase(world, origin);
        if (tentativeWidth > 0) {
            ObeliskMatch attempt = matchObeliskCore(world, origin, tentativeWidth);
            if (attempt.coreHeight() > 0) {
                if (matchObeliskCap(world, origin, tentativeWidth, attempt.coreHeight())) {
                    return DataResult.success(attempt);
                }
                return INVALID_CAP;
            }
            return INVALID_CORE;
        }
        return INVALID_BASE;
    }

    private static ObeliskMatch matchObeliskCore(World world, BlockPos origin, int width) {
        // start at north-west corner
        BlockPos start = origin.add(-1, 0, -1);
        BlockPos.Mutable pos = start.mutableCopy();
        List<RuneSearchResult> layers = new ArrayList<>();
        int height;

        for (height = 0; testCoreLayerFrame(world, start, pos, width, height); height++) {
            RuneSearchResult result = findRune(world, origin, width, height);
            if (!result.valid()) break;
            layers.add(result);
        }

        return new ObeliskMatch(origin, width, height, layers);
    }

    private static boolean testCoreLayerFrame(World world, BlockPos start, BlockPos.Mutable pos, int width, int height) {
        return world.getBlockState(pos.set(start.getX(), start.getY() + height, start.getZ())).isIn(RequiemBlockTags.OBELISK_FRAME)
            && world.getBlockState(pos.set(start.getX() + width + 1, start.getY() + height, start.getZ())).isIn(RequiemBlockTags.OBELISK_FRAME)
            && world.getBlockState(pos.set(start.getX() + width + 1, start.getY() + height, start.getZ() + width + 1)).isIn(RequiemBlockTags.OBELISK_FRAME)
            && world.getBlockState(pos.set(start.getX(), start.getY() + height, start.getZ() + width + 1)).isIn(RequiemBlockTags.OBELISK_FRAME);
    }

    private static RuneSearchResult findRune(World world, BlockPos origin, int width, int height) {
        ObeliskRune rune = null;
        boolean first = true;
        for (BlockPos corePos : iterateCoreBlocks(origin, width, height)) {
            BlockState state = world.getBlockState(corePos);
            if (state.isIn(RequiemBlockTags.OBELISK_CORE)) {
                @Nullable ObeliskRune foundRune = ObeliskRune.LOOKUP.find(world, corePos, state, null, null);
                if (first) {
                    rune = foundRune;
                    first = false;
                } else if (foundRune != rune) {
                    return RuneSearchResult.FAILED;
                }
            } else {
                return RuneSearchResult.FAILED;
            }
        }
        return RuneSearchResult.of(rune);
    }

    private static int matchObeliskBase(BlockView world, BlockPos origin) {
        int width = checkObeliskExtremity(world, origin.add(-1, -1, -1), RequiemBlockTags.OBELISK_BASE_EDGES, state -> state.isIn(RequiemBlockTags.OBELISK_BASE_CORNERS));

        if (width > 0) {
            for (BlockPos corePos : iterateCoreBlocks(origin, width, -1)) {
                if (!world.getBlockState(corePos).isIn(RequiemBlockTags.OBELISK_BASE)) {
                    // Not a valid base
                    return 0;
                }
            }
        }

        return width;
    }

    private static boolean matchObeliskCap(BlockView world, BlockPos origin, int width, int height) {
        if (checkObeliskExtremity(world, origin.add(-1, height, -1), RequiemBlockTags.OBELISK_CAP_EDGES, state -> {
            if (state.isIn(RequiemBlockTags.OBELISK_CAP_CORNERS)) {
                // Someone added full blocks to this list
                if (!(state.getBlock() instanceof StairsBlock)) return true;

                StairShape stairShape = state.get(StairsBlock.SHAPE);
                return stairShape == StairShape.OUTER_LEFT || stairShape == StairShape.OUTER_RIGHT;
            }
            return false;
        }) != width) {
            return false;
        }
        for (BlockPos blockPos : iterateCoreBlocks(origin, width, height + 1)) {
            if (!world.getBlockState(blockPos).isIn(RequiemBlockTags.OBELISK_CAP_TOP)) {
                return false;
            }
        }
        return true;
    }

    static Iterable<BlockPos> iterateCoreBlocks(BlockPos origin, int width, int height) {
        return BlockPos.iterate(origin.up(height), origin.add(width - 1, height, width - 1));
    }

    private static int checkObeliskExtremity(BlockView world, BlockPos origin, Tag<Block> edgeTag, Predicate<BlockState> corner) {
        // start at bottom north-west corner
        BlockPos.Mutable pos = origin.mutableCopy();
        int lastSideLength = -1;

        for (Direction direction : OBELISK_SIDES) {
            int sideLength = 0;
            while (true) {
                BlockState state = world.getBlockState(pos);
                if (corner.test(state)) {
                    // Corner block can be start or end of side
                    if (sideLength > 0) {
                        break;
                    }
                } else if (state.isIn(edgeTag)) {
                    sideLength++;
                    if (sideLength > MAX_OBELISK_WIDTH) {
                        // Too large: not a valid base
                        return 0;
                    }
                } else {
                    // Unexpected block: not a valid base
                    return 0;
                }
                pos.move(direction);
            }
            if (lastSideLength < 0) {
                lastSideLength = sideLength;
            } else if (lastSideLength != sideLength) {
                // Not a square base: not a valid base
                return 0;
            }
        }

        return lastSideLength;
    }

    record RuneSearchResult(boolean valid, @Nullable ObeliskRune rune) {
        static final RuneSearchResult FAILED = new RuneSearchResult(false, null);
        static final RuneSearchResult INERT = new RuneSearchResult(true, null);

        static RuneSearchResult of(@Nullable ObeliskRune rune) {
            return rune == null ? INERT : new RuneSearchResult(true, rune);
        }
    }

    private static final class ObeliskOriginMatch {
        private static final ObeliskOriginMatch FAIL = new ObeliskOriginMatch(null, null);

        private final @Nullable BlockPos origin;
        private final @Nullable BlockPos delegate;

        public static ObeliskOriginMatch success(BlockPos origin) {
            return new ObeliskOriginMatch(origin, null);
        }

        public static ObeliskOriginMatch partial(BlockPos delegate) {
            return new ObeliskOriginMatch(null, delegate);
        }

        public static ObeliskOriginMatch failure() {
            return FAIL;
        }

        private ObeliskOriginMatch(@Nullable BlockPos origin, @Nullable BlockPos delegate) {
            this.origin = origin;
            this.delegate = delegate;
        }

        public void apply(Consumer<BlockPos> originConsumer, Consumer<BlockPos> delegateConsumer, Runnable failure) {
            if (this.origin != null) originConsumer.accept(this.origin);
            else if (this.delegate != null) delegateConsumer.accept(this.delegate);
        }
    }
}
