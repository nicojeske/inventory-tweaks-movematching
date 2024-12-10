package invtweaks;

import java.util.*;
import java.util.concurrent.TimeoutException;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Slot;

import org.apache.logging.log4j.Logger;

import invtweaks.api.container.ContainerSection;

/**
 * Button that opens the inventory & chest settings screen.
 *
 * @author Jimeo Wan
 */
public class InvTweaksGuiMoveMatchingButton extends InvTweaksGuiIconButton {

    private static final Logger log = InvTweaks.log;

    public InvTweaksGuiMoveMatchingButton(InvTweaksConfigManager cfgManager, int id, int x, int y, int w, int h,
            String displayString, String tooltip, boolean useCustomTexture) {
        super(cfgManager, id, x, y, w, h, displayString, tooltip, useCustomTexture);
    }

    @Override
    public void drawButton(Minecraft minecraft, int i, int j) {
        super.drawButton(minecraft, i, j);

        // Display string
        InvTweaksObfuscation obf = new InvTweaksObfuscation(minecraft);
        drawCenteredString(obf.getFontRenderer(), displayString, xPosition + 5, yPosition - 1, getTextColor(i, j));
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, int i, int j) {
        InvTweaksObfuscation obf = new InvTweaksObfuscation(minecraft);

        if (super.mousePressed(minecraft, i, j)) {
            try {
                InvTweaksContainerManager containerManager = new InvTweaksContainerManager(minecraft);

                // Ensure no item is being held
                if (obf.getHeldStack() != null) {
                    tryReleaseHeldItem();
                }

                moveMatchingItemsToChest(containerManager);

                return true;
            } catch (Exception e) {
                log.error("Error in mousePressed", e);
                return false;
            }
        }
        return false;
    }

    private void tryReleaseHeldItem() throws Exception {
        InvTweaksContainerSectionManager invManager = new InvTweaksContainerSectionManager(
                Minecraft.getMinecraft(),
                ContainerSection.INVENTORY);

        // Try to place held item in first empty inventory slot
        for (int k = 0; k < invManager.getSize(); k++) {
            if (invManager.getItemStack(k) == null) {
                invManager.leftClick(k);
                break;
            }
        }
    }

    private void moveMatchingItemsToChest(InvTweaksContainerManager containerManager) throws TimeoutException {
        Map<String, ItemInfo> inventoryItems = scanSection(containerManager, ContainerSection.INVENTORY);
        Map<String, ItemInfo> chestItems = scanSection(containerManager, ContainerSection.CHEST);

        for (Map.Entry<String, ItemInfo> invEntry : inventoryItems.entrySet()) {
            String itemName = invEntry.getKey();
            ItemInfo chestItemInfo = chestItems.get(itemName);

            // Skip if item doesn't exist in chest
            if (chestItemInfo == null) continue;

            moveAllMatchingItems(containerManager, invEntry.getValue(), chestItemInfo);
        }
    }

    private void moveAllMatchingItems(InvTweaksContainerManager containerManager, ItemInfo invItemInfo,
            ItemInfo chestItemInfo) throws TimeoutException {
        List<Integer> remainingInvSlots = new ArrayList<>(invItemInfo.slotIndices);

        // First, fill existing partially filled chest stacks
        for (int chestSlotIndex : chestItemInfo.slotIndices) {
            Slot chestSlot = containerManager.getSlot(ContainerSection.CHEST, chestSlotIndex);
            int currentStackSize = chestSlot.getStack().stackSize;
            int remainingSpace = invItemInfo.stackLimit - currentStackSize;

            // Skip if chest slot is already full
            if (remainingSpace <= 0) continue;

            // Try to fill this chest slot
            Iterator<Integer> invSlotIterator = remainingInvSlots.iterator();
            while (invSlotIterator.hasNext()) {
                int invSlot = invSlotIterator.next();
                Slot invSlotObj = containerManager.getSlot(ContainerSection.INVENTORY, invSlot);

                // Calculate amount to move
                int invStackSize = invSlotObj.getStack().stackSize;
                int amountToMove = Math.min(invStackSize, remainingSpace);

                if (amountToMove > 0) {
                    // Move items
                    containerManager.move(ContainerSection.INVENTORY, invSlot, ContainerSection.CHEST, chestSlotIndex);

                    // Update remaining space and remove slot if emptied
                    remainingSpace -= amountToMove;
                    if (amountToMove == invStackSize) {
                        invSlotIterator.remove();
                    }

                    // Stop if no more space in this chest slot
                    if (remainingSpace == 0) break;
                }
            }
        }

        // Move remaining items to empty chest slots
        int emptyChestSlot;
        while (!remainingInvSlots.isEmpty()
                && (emptyChestSlot = containerManager.getFirstEmptyIndex(ContainerSection.CHEST)) != -1) {

            // Move from first remaining inventory slot
            int invSlot = remainingInvSlots.get(0);
            containerManager.move(ContainerSection.INVENTORY, invSlot, ContainerSection.CHEST, emptyChestSlot);

            // Remove slot if fully moved
            remainingInvSlots.remove(0);
        }
    }

    private Map<String, ItemInfo> scanSection(InvTweaksContainerManager containerManager, ContainerSection section) {
        Map<String, ItemInfo> itemMap = new HashMap<>();
        int slotIndex = 0;

        while (true) {
            Slot slot = containerManager.getSlot(section, slotIndex);
            if (slot == null) break;

            if (slot.getHasStack()) {
                String itemName = slot.getStack().getItem().getUnlocalizedName();
                int stackLimit = slot.getStack().getItem().getItemStackLimit();

                ItemInfo itemInfo = itemMap.computeIfAbsent(itemName, k -> new ItemInfo(stackLimit));

                itemInfo.slotIndices.add(slotIndex);
            }

            slotIndex++;
        }

        return itemMap;
    }

    private static class ItemInfo {

        public int stackLimit;
        public List<Integer> slotIndices;

        public ItemInfo(int stackLimit) {
            this.stackLimit = stackLimit;
            this.slotIndices = new ArrayList<>();
        }
    }

}
