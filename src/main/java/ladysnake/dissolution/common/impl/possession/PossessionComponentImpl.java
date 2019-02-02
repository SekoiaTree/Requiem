package ladysnake.dissolution.common.impl.possession;

import ladysnake.dissolution.Dissolution;
import ladysnake.dissolution.api.v1.DissolutionPlayer;
import ladysnake.dissolution.api.v1.possession.Possessable;
import ladysnake.dissolution.api.v1.possession.PossessionComponent;
import ladysnake.dissolution.common.impl.movement.SerializableMovementConfig;
import ladysnake.dissolution.common.tag.DissolutionEntityTags;
import ladysnake.reflectivefabric.reflection.typed.TypedMethod2;
import ladysnake.reflectivefabric.reflection.typed.TypedMethodHandles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.util.UUID;

import static ladysnake.dissolution.common.network.DissolutionNetworking.*;
import static ladysnake.reflectivefabric.reflection.ReflectionHelper.pick;

public class PossessionComponentImpl implements PossessionComponent {
    private static final TypedMethod2<Entity, Float, Float, Void> PLAYER$SET_SIZE = TypedMethodHandles.findVirtual(Entity.class, pick("method_5835", "setSize"), void.class, float.class, float.class);

    private PlayerEntity player;
    @Nullable private UUID possessedUuid;
    private int possessedNetworkId;

    public PossessionComponentImpl(PlayerEntity player) {
        this.player = player;
        this.possessedNetworkId = -1;
    }

    @Override
    public boolean canStartPossessing(final MobEntity mob) {
        DissolutionPlayer dp = (DissolutionPlayer) player;
        return player.world.isClient || (!player.isSpectator() && dp.isRemnant() && dp.getRemnantState().isIncorporeal());
    }

    @Override
    public boolean startPossessing(final MobEntity mob) {
        // 1- check that the player can initiate possession
        if (!canStartPossessing(mob)) {
            return false;
        }
        @Nullable Possessable possessable;
        if (mob instanceof Possessable) {
            possessable = (Possessable) mob;
        } else {
            possessable = Possession.getConversionRegistry().convert(mob, player);
        }
        // 2- check that the mob can be possessed
        if (possessable == null || !possessable.canBePossessedBy(player)) {
            return false;
        }
        MobEntity host = (MobEntity) possessable;
        // 3- transfer inventory
        // TODO data driven item giving
        if (host.getMainHandStack().getItem() instanceof BowItem) {
            player.method_7270(new ItemStack(Items.ARROW, host.world.random.nextInt(10) + 2));
        }
        if (DissolutionEntityTags.ITEM_USER.contains(host.getType())) {
            PossessionComponentImpl.transferEquipment(host, player);
        }
        // 4- Actually set the possessed entity
        this.possessedUuid = host.getUuid();
        this.possessedNetworkId = host.getEntityId();
        possessable.setPossessor(this.player);
        syncPossessed();
        // 5- Update some attributes
        this.player.setPositionAndAngles(host);
        ((DissolutionPlayer)this.player).getMovementAlterer().setConfig(Dissolution.getMovementAltererManager().getEntityMovementConfig(host.getType()));
        PLAYER$SET_SIZE.invoke(player, host.getWidth(), host.getHeight());
        return true;
    }

    private static void transferEquipment(LivingEntity source, LivingEntity dest) {
        for (ItemStack stuff : source.getItemsEquipped()) {
            if (stuff.isEmpty()) {
                continue;
            }
            EquipmentSlot slot = MobEntity.getPreferredEquipmentSlot(stuff);
            if (!dest.getEquippedStack(slot).isEmpty()) {
                dest.dropStack(stuff, 0.5f);
            } else {
                dest.setEquippedStack(slot, stuff);
            }
            source.setEquippedStack(slot, ItemStack.EMPTY);
        }
    }

    @Override
    public void stopPossessing() {
        Possessable possessedEntity = this.getPossessedEntity();
        if (possessedEntity != null) {
            this.possessedUuid = null;
            resetState();
            possessedEntity.setPossessor(null);
            if (DissolutionEntityTags.ITEM_USER.contains(((Entity)possessedEntity).getType())) {
                PossessionComponentImpl.transferEquipment(player, (LivingEntity) possessedEntity);
            }
        }
    }

    private void syncPossessed() {
        if (!this.player.world.isClient) {
            sendTo((ServerPlayerEntity)this.player, createPossessionPacket(this.player.getUuid(), this.possessedNetworkId));
            sendToAllTracking(this.player, createPossessionPacket(this.player.getUuid(), this.possessedNetworkId));
        }
    }

    @CheckForNull
    @Override
    public Possessable getPossessedEntity() {
        if (!isPossessing()) {
            return null;
        }
        // First attempt: use the network id (client & server)
        Entity host = this.player.world.getEntityById(this.possessedNetworkId);
        if (host == null) {
            if (this.player.world instanceof ServerWorld) {
                // Second attempt: use the UUID (server)
                host = this.player.world.getEntityByUuid(this.getPossessedEntityUuid());
            }
            // Set the possessed uuid to null to avoid infinite recursion
            this.possessedUuid = null;
            if (host instanceof MobEntity && host instanceof Possessable) {
                this.startPossessing((MobEntity) host);
            } else {
                if (host != null) {
                    Dissolution.LOGGER.warn("{}: this player's supposedly possessed entity ({}) cannot be possessed!", this.player, host);
                }
                Dissolution.LOGGER.debug("{}: this player's possessed entity is nowhere to be found", this);
                this.resetState();
                host = null;
            }
        }
        return (Possessable) host;
    }

    private void resetState() {
        this.possessedNetworkId = -1;
        ((DissolutionPlayer) this.player).getMovementAlterer().setConfig(SerializableMovementConfig.SOUL);
        syncPossessed();
    }

    @Override
    public boolean isPossessing() {
        return this.possessedUuid != null;
    }

    @CheckForNull
    public UUID getPossessedEntityUuid() {
        return this.possessedUuid;
    }

}