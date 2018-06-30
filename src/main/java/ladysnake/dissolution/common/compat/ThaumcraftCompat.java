package ladysnake.dissolution.common.compat;

import ladysnake.dissolution.common.entity.EntityPlayerShell;
import ladysnake.dissolution.common.entity.PossessableEntityFactory;
import ladysnake.dissolution.common.init.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.internal.CommonInternals;

import java.util.ArrayList;
import java.util.List;

public class ThaumcraftCompat {

    public static void assignAspects() {
        ThaumcraftApi.registerObjectTag(new ItemStack(ModItems.HUMAN_FLESH_RAW), new AspectList()
                .add(Aspect.MAN, 5)
                .add(Aspect.LIFE, 5)
                .add(Aspect.EARTH, 5)
        );
        ThaumcraftApi.registerObjectTag(new ItemStack(ModItems.HUMAN_FLESH_COOKED), new AspectList()
                .add(Aspect.MAN, 5)
                .add(Aspect.LIFE, 5)
                .add(Aspect.CRAFT, 1)
        );
        ThaumcraftApi.registerObjectTag(new ItemStack(ModItems.AETHEREUS), new AspectList()
                .add(Aspect.SOUL, 8)
                .add(Aspect.ALCHEMY, 5)
                .add(Aspect.MAN, 2)
                .add(Aspect.WATER, 5)
        );
        // Add aspects to every procedurally generated entity based on the original
        PossessableEntityFactory.getAllGeneratedPossessables().forEach(e -> {
            AspectList tags;
            String entityName = EntityRegistry.getEntry(e.getKey()).getName();
            ThaumcraftApi.EntityTagsNBT[] nbt = new ThaumcraftApi.EntityTagsNBT[0];
            List<ThaumcraftApi.EntityTags> entityTags = new ArrayList<>();
            for (ThaumcraftApi.EntityTags et : CommonInternals.scanEntities) {
                if (!et.entityName.equals(entityName)) continue;
                if (et.nbts == null || et.nbts.length == 0) {
                    tags = et.aspects;
                } else {
                    tags = et.aspects;
                    nbt = et.nbts;
                }
                entityTags.add(new ThaumcraftApi.EntityTags(entityName, tags, nbt));
            }
            CommonInternals.scanEntities.addAll(entityTags);
        });
        ThaumcraftApi.registerEntityTag(
                EntityRegistry.getEntry(EntityPlayerShell.class).getName(),
                new AspectList().add(Aspect.MAN, 4).add(Aspect.EARTH, 8).add(Aspect.ENTROPY, 4)
        );
    }
}
