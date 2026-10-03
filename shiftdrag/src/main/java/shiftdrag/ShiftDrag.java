package shiftdrag;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

@Mod(modid = "shiftdrag", name = "ShiftDrag", version = "1.0", clientSideOnly = true)
public class ShiftDrag {
    private Slot lastSlot;

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (!(e.gui instanceof GuiContainer)) return;
        boolean held = Mouse.isButtonDown(0) || Mouse.isButtonDown(1);
        if (!held || !GuiScreen.isShiftKeyDown()) { lastSlot = null; return; }
        GuiContainer gui = (GuiContainer) e.gui;
        Slot slot = ObfuscationReflectionHelper.getPrivateValue(GuiContainer.class, gui, "theSlot", "field_147006_u");
        if (slot == null || slot == lastSlot || !slot.getHasStack()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer.inventory.getItemStack() != null) return;
        lastSlot = slot;
        mc.playerController.windowClick(gui.inventorySlots.windowId, slot.slotNumber, 0, 1, mc.thePlayer);
    }
}
