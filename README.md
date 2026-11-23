## 0.21.1 - 3D cosmetics
- Cosmetics are 3D: wings, ears, horns, antlers and crowns are solid pieces with real thickness and pixel-shaped
  sides (like held items), the halo has a solid ring, hat brims have depth, and everything is shaded by the way
  each face points instead of looking like flat paper.

## 0.21.0 - In-game account switcher
- Kill Effects: "Preview" opens a small window that plays the chosen effect on a loop (works from the menus too),
  with < > to switch effects, Replay, and "In world" to see it for real.
- Cosmetics fit around armor: hats, halos, ears and horns sit on top of a helmet (or worn block / skull), crowns are
  bands just outside the head so nothing pokes through, wings start behind a chestplate and a cape, and wings are
  hidden while you wear an elytra instead of overlapping it.
- Fix: the Tier Tagger and Custom Sky keys showed raw names in Controls.
- Tier Tagger (PvP): players' PvP tiers in front of their names on nametags and in the tab list, like the
  TierTagger mod - from MCTiers, NovaTiers (novatiers.com) or both. Pick a game mode per list or "Highest"; optional
  peak tier, mode name, and a key to cycle the mode.
- Armor HUD: in "Number" durability mode each piece gets a durability bar under it.
- Mods menu reorganised: combat HUDs (Reach, Combo, Target Health, Cooldowns, Shield Status) are in PvP; looks
  (Fullbright, Time Changer, Block Outline, Item Physics, Motion Blur, Particles, Low Fire, Kill Effects) in Visuals;
  camera & world (FOV, Fake Chunks) in Render; Capes, Cosmetics, Emotes, Tempest Editor and Social in Client; Auto GG
  in Chat; Toggle Sprint, Mouse Tweaks, Armor Swap, Shulker Preview, Tooltip Info in QOL.
- Chat Macros (Chat): keys that send a message or a /command in game. Edit them from the mod's settings; each
  macro has a cooldown so it can't spam.
- Accounts (title screen: "Switch account" on your profile card, or the name chip top-right): add Microsoft
  accounts with Microsoft's own code sign-in (microsoft.com/link) and switch between them and your launcher account
  without restarting. Saved logins are encrypted on your PC (Windows data protection) and never sent to Tempest.
  Needs a one-time Microsoft app ID (the screen explains how).
- Tempest sign-in works for accounts Mojang gives no chat key: it falls back to the normal server-join check with
  Mojang (like joining any server). Your access token still only goes to Mojang. Needs the updated worker (deploy.bat).
- Social / chat: when this account isn't signed in to Tempest, buttons now say why (e.g. Mojang gave the account no
  chat key) instead of "Couldn't reach the Tempest service".
- Title screen: the favourite servers card sits in the very top-left corner.
- Mods menu: "always on" mods are listed first in every category, then starred mods.
- Tempest Editor: polished look pages - header tinted in the section's colour, status chip, sidebar with an accent
  bar on the selected category, gradient cards that lift on hover and glow when selected.

## 0.20.1 - Inventory looks fix
- Tempest Editor: an Inventory GUI look taken from one of your packs could come out see-through (some PvP packs ship
  "clear" inventories). Inventory looks are now always solid, and one you already applied is fixed the next time you
  open the editor. Pack previews of high-resolution packs now show the right part of the texture.

## 0.20.0 - Tempest Editor icons
- Tempest Editor's main page has its own hand-drawn, full-colour icons for PvP, Blocks, Gear, HUD, Misc, Effects and
  Sounds and for the Configs / My Packs / Cosmetics / Painter buttons, and each tile glows in its own colour on hover
  (tools/make_editor_icons.py).

## 0.19.9 - Spotify HUD, Tempest chat, Fake Chunks
- Spotify HUD: cover, title, artist, album, progress and synced lyrics (lrclib.net); the card takes the album's colours.
- Chat Tabs is Tempest chat: Private messages with friends, Party, and your own group chats.
- Fake Chunks (singleplayer): generates the land around your spawn in the background, like Chunky.
- Fix: the world-loading overlay no longer covers the pause menu.

## 0.19.8 - Hitboxes, emote camera
- Combat Hitboxes: a hitbox only turns red while your crosshair is on it and it's within your reach (a block in the
  way doesn't count).
- Emotes switch to the normal third-person view (behind you) instead of the front view, so the controls aren't reversed.
- Reach renamed to Reach Display.

## 0.19.7 - Performance
- HUD boxes (Modern style) are pre-rendered once per size at your screen resolution and drawn as one texture, instead
  of ~25 separate shapes per box every frame. Looks identical, far fewer draw calls.
- Module on/off state is cached (it was looked up in the config map on every check; some checks run for every block
  while chunks are built, others for every entity every frame).
- HUD text layout (label/value split and text widths) is only worked out again when the text changes.
- Xray remembers which blocks it shows (no registry lookups per block while chunks rebuild).
- Totem counter counts your inventory once per tick instead of every frame; small allocation fixes.

## 0.19.6 - Tempest Editor
- Tempest Editor (Right Shift > Editor, or Utility > Tempest Editor): a pack editor. Groups PvP, Blocks, Gear, HUD,
  Misc, Effects and Sounds, each with categories (End Crystals, Respawn Anchors, Totems, Pearls, Golden Apples,
  Shields, Bows, Obsidian, Glowstone, Cobwebs, Fire, Bedrock, Ores, Netherite / Diamond Armor, Elytra, weapons,
  Crosshair, Inventory GUI, Hearts, Hunger, Hotbar, XP Bar, Enchant Glint, Arrows, XP Orbs, crit / totem / explosion /
  sweep particles, effect icons).
- Each category: Vanilla, Custom colour (picker, or drop a screenshot / Choose... to copy its main colour), 12 styles
  (Crimson, Frost, Toxic, Royal, Gilded, Sakura, Ocean, Midnight, Neon, Pastel, Monochrome, Inverted), and the same
  textures from any pack in your resourcepacks folder. Pencil opens the Texture Painter on that texture.
- Switches: explosion particles (End Crystals), enchant glint on/off, crosshair colour inversion.
- Sounds: replace hit, hurt, explosion, totem pop and bow-hit sounds (Orb, Ding, Pling, Bell, Hat, Pop, Click,
  Level Up, Muted); clicking a card plays it.
- Apply writes everything into resourcepacks/Tempest Custom and reloads once; Reset, Delete (asks twice) and Packs Folder.

## 0.19.5 - Emotes, shared cosmetics, Texture Painter
- Emotes: Wave, Clap, Dance, Dab, Salute, Facepalm, Cheer, Shrug. Hold B for the emote wheel (point and let go, or tap
  and click; 1-8 pick directly). Tempest players near you see them through a live link to the Tempest service.
  Moving cancels an emote; third person while emoting (optional).
- Cosmetics are shared: other Tempest players see your cape, wings and head piece, and you see theirs
  (Cosmetics > Show Mine To Others / See Other Players' Cosmetics).
- Wings are three hinged panels that fold and ripple as they flap, with new 512px textures (tools/make_wings_hq.py).
  Halo has a soft glow; the crown is solid from inside too.
- New Cosmetics menu (Right Shift > Looks / Emotes): big turntable preview (drag to turn, scroll to zoom), tabs for
  Capes, Wings, Head and Emotes, hover to try on, click to wear, wing size / flap and halo colour right there.
- Texture Painter (Right Shift > Textures, or Utility > Texture Painter): pick any item or block texture, paint it with
  brush, eraser, fill and colour picker (mirror, undo/redo, animated frames), and Save & Apply writes it into
  resourcepacks/Tempest Custom and reloads so you see it straight away. "Use original" takes it back out.
- Service (tempest-worker): GET /v1/live WebSocket (hibernating, 30 msgs/10 s limit), POST /v1/cosmetics, and
  /v1/lookup now returns cosmetics. Deploy it before releasing the client.

## 0.19.4 - Shulker icons, HD menu backgrounds
- Shulker Preview (like Simple Shulker Preview): shulker boxes in any slot show an icon of what's inside and a fullness
  bar (green, amber at 75%, red when full). Icon shows the most common item, first or last slot; a shulker named after
  an item ("diamond", "minecraft:tnt") shows that item. Icon size Small/Medium/Large. The hover preview still works.
- Main menu backgrounds re-rendered at 1920x1080 (1.5x supersampled): soft round stars in several colours with sparkles
  on the brightest, wispy detail in nebulae and the Milky Way, layered mountain ridges with haze, bloom, no banding.
  Regenerate with tools/make_menu_hq.py.
- Main menu: more space between the buttons; favourite servers moved to the top-left corner, profile card to the top-right.

## 0.19.3 - Configs, Low Shields, presence fix
- Friends seeing you offline: the online list is now saved by the service (so restarts don't wipe it), heartbeats
  every 45 s, one sent as soon as Social opens, and your status in Social only says Online once the service confirmed it.
- Configs replace profile slots: create named configs, load, save over, rename, copy to share, import, delete
  (GUI & Configs > Configs, or Configs in the mod menu sidebar). Old slots 1-3 become "Profile 1-3".
- Low Shields: the shield sits at the side of the screen and is small while blocking (built-in pack, toggled by the module).
- Server Address shows the server's icon; Pack Display shows each pack's icon.
- Totem pops on nametags are back to "-3". Auras and Fireflies removed.

## 0.19.2 - Kill effects remastered, key bind fix
- Kill effects rebuilt: smooth light fall-off, three theme tones plus white-hot cores, physics sparks with motion
  streaks that land on the ground, a light pool under every kill and lens-flare stars.
  Soul Spiral gets a rising soul wisp and comet-headed strands; Shockwave a shock dome and skidding debris; Storm
  Strike re-strikes with forks and electric arcs over the ground; Starburst twinkles and drops glitter; Ascension adds
  turning god rays, a golden halo and drifting feathers. Detail setting still scales it down.
- Key binds: clicking a bind while the search box was active typed the key into the search instead of binding it.

## 0.19.1 - Social redesign, tab list logo, totem icons
- Tempest logo in the tab list for Tempest users and friends (Nametags > Logo In Tab List). The online lookup now covers
  everyone in the tab list, not just nearby players.
- New logo glyph: solid white cloud with a violet bolt and a dark edge, readable on any background.
- Totem pops on nametags show a small totem icon and the count; the hotbar totem counter is a pill that turns amber at 2-3
  and red on your last totem; new totem icon in the mod list.
- Friend glow is off by default (new setting) and can be switched from the Social menu sidebar.
- Social menu redesigned: sidebar with your profile and status, sections with counts, friend search, hover actions,
  empty states, toasts, smooth scrolling.
- The update window shows the release notes, with Update / Later / Skip this version. Nothing downloads until you click Update.
- Downloads only from that repo's release page over HTTPS, checks the SHA-256 against GitHub's digest, then swaps the jar after Minecraft closes. If anything fails, the old jar stays put.
- Settings → GUI & Profiles: "Check For Updates" toggle and a "Check now" button.
- GitHub workflow: no longer needs gradlew; tagged builds are stamped with the tag's version.
