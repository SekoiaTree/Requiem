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
package ladysnake.pandemonium.mixin.common.entity.player;

import ladysnake.pandemonium.common.entity.fakeplayer.FakeServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {

    @Shadow
    public abstract ServerChunkManager getChunkManager();
    @Shadow
    public abstract void checkEntityChunkPos(Entity entity);

    @Shadow
    @Final
    private List<ServerPlayerEntity> players;

    private boolean requiem$fakePlayerSleeping = false;

    @ModifyVariable(method = "updateSleepingPlayers", at = @At(value = "STORE"))
    private ServerPlayerEntity captureSleepingPlayer(ServerPlayerEntity player) {
        requiem$fakePlayerSleeping = player instanceof FakeServerPlayerEntity;
        return player;
    }

    @ModifyVariable(method = "updateSleepingPlayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;isSleeping()Z"), ordinal = 0)
    private int discountFakePlayers(int spectatorPlayers) {
        if (requiem$fakePlayerSleeping) {
            requiem$fakePlayerSleeping = false;
            return spectatorPlayers + 1;
        }
        return spectatorPlayers;
    }

    @Inject(method = "loadEntityUnchecked", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerChunkManager;loadEntity(Lnet/minecraft/entity/Entity;)V"))
    private void addFakePlayer(Entity entity, CallbackInfo ci) {
        if (entity instanceof FakeServerPlayerEntity) {
            this.players.add((ServerPlayerEntity) entity);
        }
    }

    /**
     * When an entity ticks, it keeps its chunk loaded with an UNKNOWN ticket.
     * Minecraft allows chunk unloading by preventing regular entities from ticking in chunks that should otherwise unload.
     * Players are naturally exempted from this, so we have to do it ourselves for fake players.
     */
    @Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
    private void freeChunk(Entity entity, CallbackInfo ci) {
        if (entity instanceof FakeServerPlayerEntity && !this.getChunkManager().shouldTickEntity(entity)) {
            this.checkEntityChunkPos(entity);
            ci.cancel();
        }
    }
}