package com.holybuckets.structures.menu;

import com.holybuckets.foundation.HBUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class TemplateChestEntityMenu extends AbstractContainerMenu {

    private final Container container;
    public final int containerRows;
    private static final int CONTAINER_COLUMNS = 9;

    public final static int BUFFER = 4;
    public TemplateChestEntityMenu(int syncId, Inventory playerInventory, Container container)
    {
        super(ModMenus.countingChestMenu.get() , syncId);
        this.container = container;
        this.containerRows = (container.getContainerSize() / 9);

        // Top restricted row (slots 0-8)
        for (int col = 0; col < CONTAINER_COLUMNS; ++col) {
            this.addSlot(new RestrictedSlot(container, col, 8 + col * 18, 17));
        }

        // Normal storage rows (starts from index 9)
        for (int row = 1; row < containerRows; ++row) {
            for (int col = 0; col < CONTAINER_COLUMNS; ++col) {
                int index = row * CONTAINER_COLUMNS + col;
                int x = 8 + col * 18;
                int y = BUFFER + 17 + row * 18;
                this.addSlot(new Slot(container, index, x, y));
            }
        }

        // Player inventory
        int playerInvStartY = BUFFER + 17 + (containerRows) * 18 + 14;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < CONTAINER_COLUMNS; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, playerInvStartY + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < CONTAINER_COLUMNS; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, playerInvStartY + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if(this.container != null && (this.container instanceof  BlockEntity)) {
            //check if player is within 64 blocks
            BlockPos pos = ((BlockEntity) this.container).getBlockPos();
            return HBUtil.BlockUtil.inRange(pos, player.blockPosition(), 64);
        }

        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack = slot.getItem();
            ItemStack original = itemstack.copy();

            int containerSlotCount = container.getContainerSize();
            if (index < containerSlotCount) {
                // From container to player inventory
                if (!this.moveItemStackTo(itemstack, containerSlotCount, this.slots.size(), true))
                    return ItemStack.EMPTY;
            } else {
                int contIndex = 9;
                if(player.isCreative()) contIndex = 0;
                // From player inventory to normal container rows (skip restricted top row)
                if (!this.moveItemStackTo(itemstack, contIndex, containerSlotCount, false))
                    return ItemStack.EMPTY;
            }

            if (itemstack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            return original;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (container instanceof BlockEntity blockEntity) {
            this.container.stopOpen(player);
        }
    }

    // --- RestrictedSlot ---
    private class RestrictedSlot extends Slot {
        public RestrictedSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            Player player = getPlayer();
            return player != null && player.isCreative();
        }

        @Override
        public boolean mayPickup(Player player) {
            return player.isCreative();
        }

        private Player getPlayer() {
            if(container instanceof BlockEntity blockEntity)
            {
                Level level = blockEntity.getLevel();
                BlockPos pos = blockEntity.getBlockPos();
                return level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, false);
            }
            return null;
        }
    }
}

