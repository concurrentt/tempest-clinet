package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;

import java.util.Locale;

/**
 * 1.7 / 1.8 style first-person animations. Purely visual: nothing here changes what is sent to the server.
 * <ul>
 *   <li>No Cooldown Dip: the held item stays up after a hit instead of dipping with the attack cooldown.</li>
 *   <li>Sword Blocking: holding a shield in your off hand and right clicking shows the old sword-block pose.</li>
 *   <li>Hide Shield While Blocking: the shield model is hidden while the sword is blocking.</li>
 *   <li>Block-Hitting: left clicking while blocking/eating/drinking plays the swing (client-side only).</li>
 * </ul>
 * Hooks: mixin/OldAnimationsMixin (HeldItemRenderer) and util/InputHooks.
 */
public final class OldAnimationsModule extends Module {
    private final BoolSetting noDip = add(new BoolSetting("no_dip", "No Cooldown Dip", true));
    private final BoolSetting swordBlock = add(new BoolSetting("sword_block", "Sword Blocking (shield in off hand)", true));
    private final BoolSetting hideShield = add(new BoolSetting("hide_shield", "Hide Shield While Blocking", true));
    private final BoolSetting blockHit = add(new BoolSetting("block_hit", "Block-Hitting Swing", true));

    public OldAnimationsModule() {
        super("old_animations", "Old Animations", "1.7 style sword blocking, block-hitting and no cooldown dip.", Category.PVP, false);
    }

    private static OldAnimationsModule on() {
        OldAnimationsModule m = ModuleManager.get(OldAnimationsModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    public static boolean noDip() {
        OldAnimationsModule m = on();
        return m != null && m.noDip.get();
    }

    /** You are right-click blocking with a sword in the main hand and a shield in the off hand. */
    public static boolean swordBlocking() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || !c.player.isUsingItem() || c.player.getActiveHand() != Hand.OFF_HAND) return false;
        return c.player.getOffHandStack().isOf(Items.SHIELD) && c.player.getMainHandStack().isIn(ItemTags.SWORDS);
    }

    private static boolean firstPerson(Object ctx) {
        return ctx != null && String.valueOf(ctx).toUpperCase(Locale.ROOT).contains("FIRST_PERSON");
    }

    private static boolean leftHand(Object ctx) {
        return String.valueOf(ctx).toUpperCase(Locale.ROOT).contains("LEFT");
    }

    /** renderItem HEAD: true = skip drawing this item (the shield while sword-blocking). */
    public static boolean hideItem(LivingEntity entity, ItemStack stack, Object ctx) {
        OldAnimationsModule m = on();
        MinecraftClient c = MinecraftClient.getInstance();
        return m != null && m.swordBlock.get() && m.hideShield.get() && entity == c.player && firstPerson(ctx)
                && stack != null && stack.isOf(Items.SHIELD) && swordBlocking();
    }

    /** renderItem HEAD: pushes the 1.8 block pose for the sword. Returns true if it pushed. */
    public static boolean applyBlockPose(LivingEntity entity, ItemStack stack, Object ctx, MatrixStack matrices) {
        OldAnimationsModule m = on();
        MinecraftClient c = MinecraftClient.getInstance();
        if (m == null || !m.swordBlock.get() || entity != c.player || !firstPerson(ctx) || stack == null || !stack.isIn(ItemTags.SWORDS)
                || !swordBlocking()) return false;
        float k = leftHand(ctx) ? -1f : 1f;
        matrices.push();
        matrices.translate(k * -0.14142136f, 0.08f, 0.14142136f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102.25f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(k * 13.365f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(k * 78.05f));
        return true;
    }

    /** A physical left click (from InputHooks): swing while using an item, like 1.8 block-hitting. */
    public static void onLeftClick(MinecraftClient c) {
        OldAnimationsModule m = on();
        if (m == null || !m.blockHit.get() || c.player == null || c.currentScreen != null || !c.player.isUsingItem()) return;
        c.player.swingHand(Hand.MAIN_HAND, false);   // local animation only, no packet
    }
}
