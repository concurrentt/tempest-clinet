package dev.tempest.modules;

import dev.tempest.module.BoolSetting;
import dev.tempest.module.Category;
import dev.tempest.module.Module;
import dev.tempest.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Nametags: the Tempest logo in front of your name (and your Tempest friends'), your own nametag in third person,
 * and the totem pop count after a player's name. Applied in mixin/EntityRendererNametagMixin.
 */
public final class NametagsModule extends Module {
    private final BoolSetting ownTag = add(new BoolSetting("own_tag", "Show Own Nametag (F5)", true));
    private final BoolSetting logo = add(new BoolSetting("logo", "Tempest Logo", true));
    private final BoolSetting logoFriends = add(new BoolSetting("logo_friends", "Logo On Tempest Friends", true));
    private final BoolSetting showOnline = add(new BoolSetting("show_online", "Show Tempest Users", true));
    private final BoolSetting shareOnline = add(new BoolSetting("share_online", "Let Others See I Use Tempest", true));

    /** U+E000 in the tempest:icons font is the Tempest logo (textures/font/logo.png). */
    public static final Text LOGO = Text.literal("").styled(s -> s.withFont(new StyleSpriteSource.Font(Identifier.of("tempest", "icons"))));

    /** Invisible marker on a decorated name so it is never decorated twice. */
    private static final String MARK = "tempest:nametag";

    /** Decorates a render state's label, looking the player up by entity id (fallback for renderers that rebuild the name). */
    public static void decorateState(Object state) {
        if (!(state instanceof net.minecraft.client.render.entity.state.PlayerEntityRenderState ps) || ps.displayName == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        Entity e = mc.world.getEntityById(ps.id);
        if (e != null) ps.displayName = decorate(e, ps.displayName);
    }

    public NametagsModule() {
        super("nametags", "Nametags", "Tempest logo on Tempest users' nametags, your own nametag in third person, and totem pops after names.",
                Category.RENDER, true);
    }

    private static NametagsModule on() {
        NametagsModule m = ModuleManager.get(NametagsModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    /** Ask the online service which nearby players use Tempest. */
    public static boolean showOnline() { NametagsModule m = on(); return m != null && m.logo.get() && m.showOnline.get(); }

    /** Tell the online service you're playing with Tempest (so others see your logo). */
    public static boolean shareOnline() { NametagsModule m = on(); return m != null && m.shareOnline.get(); }

    /** Your own nametag in third person (vanilla never shows it). */
    public static boolean showOwnTag() {
        NametagsModule m = on();
        MinecraftClient mc = MinecraftClient.getInstance();
        return m != null && m.ownTag.get() && mc.options != null && !mc.options.getPerspective().isFirstPerson();
    }

    /** The name to show above this entity: logo + name + pop count. Returns the original when nothing changes. */
    public static Text decorate(Entity entity, Text name) {
        if (name == null || !(entity instanceof PlayerEntity p)) return name;
        if (MARK.equals(name.getStyle().getInsertion())) return name;     // already decorated (several hooks may run)
        MinecraftClient mc = MinecraftClient.getInstance();
        NametagsModule m = on();
        boolean self = mc.player != null && p == mc.player;

        boolean addLogo = m != null && m.logo.get()
                && (self || (m.logoFriends.get() && FriendsModule.isFriend(p.getName().getString()))
                    || (m.showOnline.get() && dev.tempest.util.TempestNet.isTempestUser(p.getUuid())));
        int pops = TotemPopModule.onNametag() ? TotemPopModule.getPops(p.getUuid()) : 0;
        Text shield = ShieldStatusModule.badge(p);
        if (!addLogo && pops <= 0 && shield == null) return name;

        MutableText out = Text.empty().styled(s -> s.withInsertion(MARK));
        if (addLogo) out.append(LOGO).append(Text.literal(" "));
        out.append(name);
        if (pops > 0) out.append(Text.literal(" -" + pops).formatted(Formatting.RED, Formatting.BOLD));
        if (shield != null) out.append(shield);
        return out;
    }
}
