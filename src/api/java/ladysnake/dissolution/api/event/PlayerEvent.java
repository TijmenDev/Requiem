package ladysnake.dissolution.api.event;

import net.fabricmc.fabric.util.HandlerArray;
import net.fabricmc.fabric.util.HandlerRegistry;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;

public class PlayerEvent {
    @FunctionalInterface
    public interface ItemPickup {
        ActionResult onItemPickup(PlayerEntity player, ItemEntity pickedUp);
    }

    public static final HandlerRegistry<ItemPickup> PICKUP_ITEM = new HandlerArray<>(ItemPickup.class);
}