/*
 * Requiem
 * Copyright (C) 2017-2020 Ladysnake
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
package ladysnake.requiem.common.entity;

import nerdhub.cardinal.components.api.component.Component;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

public final class HorologistManager implements Component {
    private static final UUID HOROLOGIST_UUID = UUID.fromString("578c808d-b9ef-46c3-b628-1246c8b54e65");

    private boolean busy = false;

    public Optional<HorologistEntity> trySpawnHorologistAround(ServerWorld world, BlockPos pos) {

        if (isHorologistBusy()) {
            return Optional.empty();
        }

        int xCenter = pos.getX();
        int yCenter = pos.getY();
        int zCenter = pos.getZ();

        int xMin = xCenter - 2;
        int zMin = zCenter - 2;
        int xMax = xMin + 2;
        int zMax = zMin + 2;

        try (BlockPos.PooledMutable mut = BlockPos.PooledMutable.get()) {
            for (int x = xMin; x <= xMax; ++x) {
                for (int z = zMin; z <= zMax; ++z) {
                    mut.set(x, yCenter, z);
                    if ((x - xCenter) * (x - xCenter) + (z - zCenter) * (z - zCenter) > 1) {
                        Optional<HorologistEntity> optional = trySpawnHorologistAt(world, mut);
                        if (optional.isPresent()) {
                            return optional;
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    public Optional<HorologistEntity> trySpawnHorologistAt(ServerWorld world, BlockPos.Mutable pos) {
        if (isHorologistBusy()) {
            return Optional.empty();
        }

        VoxelShape voxelShape = world.getBlockState(pos).getCollisionShape(world, pos);
        if (!(voxelShape.getMaximum(Direction.Axis.Y) > 0.4375D)) {

            double y = pos.getY();
            while (pos.getY() >= 0 && y - pos.getY() <= 2 && world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
                pos.setOffset(Direction.DOWN);
            }

            VoxelShape blockShape = world.getBlockState(pos).getCollisionShape(world, pos);
            if (!blockShape.isEmpty()) {
                double d = (double) pos.getY() + blockShape.getMaximum(Direction.Axis.Y) + 2.0E-7D;
                if (!(y - d > 2.0D)) {
                    EntityType<HorologistEntity> type = RequiemEntities.HOROLOGIST;
                    float radius = type.getWidth() / 2.0F;
                    Vec3d vec3d = new Vec3d((double) pos.getX() + 0.5D, d, (double) pos.getZ() + 0.5D);
                    if (world.doesNotCollide(new Box(
                        vec3d.x - (double) radius,
                        vec3d.y,
                        vec3d.z - (double) radius,
                        vec3d.x + (double) radius,
                        vec3d.y + (double) type.getHeight(),
                        vec3d.z + (double) radius
                    ))) {
                        return retrieveHorologistEntity(world, vec3d);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public boolean isHorologistBusy() {
        return this.busy;
    }

    public void freeHorologist() {
        this.busy = false;
    }

    @Nonnull
    private Optional<HorologistEntity> retrieveHorologistEntity(ServerWorld world, Vec3d vec3d) {
        Entity horologist = world.getEntity(HOROLOGIST_UUID);
        if (!(horologist instanceof HorologistEntity)) {
            if (horologist != null) {
                world.removeEntity(horologist);
            }
            horologist = new HorologistEntity(RequiemEntities.HOROLOGIST, world);
            horologist.setUuid(HOROLOGIST_UUID);
        }
        horologist.updatePosition(vec3d.x, vec3d.y, vec3d.z);
        if (!world.spawnEntity(horologist)) {
            return Optional.empty();
        }
        world.spawnParticles(ParticleTypes.PORTAL, vec3d.x, vec3d.y, vec3d.z, 100, 0.1, 0.1, 0.1, 0.1);
        return Optional.of((HorologistEntity) horologist);
    }

    @Override
    public void fromTag(CompoundTag tag) {
        this.busy = tag.getBoolean("busy");
    }

    @Override
    public CompoundTag toTag(CompoundTag tag) {
        tag.putBoolean("busy", this.busy);
        return tag;
    }
}
