## 0.21.20 - Discord emoji in chat, player card fixes
- Discord emoji in chat: :sob:, :skull:, :joy:, :fire: and 1,900 other Discord / GitHub names - or a real emoji
  someone pastes - show as the emoji picture, in full colour.
- Player Card: every MCTiers mode shows its icon (unranked ones with a plain ring and "-"), and the name tag no
  longer floats above the player model.

## 0.21.19 - Tempest-style accounts screen, roomier Tempest Editor
- Switch account now opens a Tempest-style account list: player heads, Log in / Edit / Remove, search and
  + Add account, in the same glass panel as the rest of Tempest. In-Game Account Switcher still does the sign-in.
- Tempest Editor: roomier main page - one panel with the groups as big cards (simple line icons that light up in
  your accent colour), and Configs / My Packs / Cosmetics / Painter as an even row underneath.
- Player Card (middle click a player): bigger card with the player standing on the left (turns to follow your
  mouse) and their tiers like the tier sites - a round icon per MCTiers mode with its tier underneath (unranked
  modes dimmed, hover for the mode and peak), plus their ranked NovaTiers modes.

## 0.21.18 - Xray and Fullbright fixed, account switcher, realistic menus and sky
- Fixed: Xray hid nothing. While chunks are built, every block that isn't on the list now reads as air, so ores
  keep all their faces and buried ores aren't culled; everything left is fully lit. This works alongside Fabric's
  Indigo renderer (which blocked the earlier hook) and no longer changes block opacity game-wide. With Sodium
  installed it says so in chat.
- Fixed: two Xray mixins and the XP orb clumping mixin failed to load on 1.21.11 (outdated method signatures).
- Account switching is back: "Switch account" on the title screen opens In-Game Account Switcher, which now comes
  inside the Tempest jar - nothing extra to install. Add Microsoft or offline accounts and switch without restarting.
- Fullbright uses gamma again: the lightmap gets the Strength value (15 = 1500% brightness) while your Brightness
  slider and options.txt keep your real setting. No more Night Vision tint.
- Realistic main menu themes: all six backgrounds re-rendered like photographs. Nebula is a deep-space hydrogen
  cloud with dust pillars; Galaxy is the Milky Way's core over layered mountains; Aurora has folded curtains over
  snowy peaks and a lake; Black Hole is ray traced (real light bending, a Doppler-bright disk, the lensed Milky
  Way around it); Ringed Planet has lit bands, ring shadows and moons; Blood Moon is a copper total eclipse.
- Realistic Custom Sky: a real-looking Moon (seas, craters, rays), a Milky Way with star clouds and dust lanes,
  shaded planets with ring shadows, a turbulent accretion disk and lensed arc, softer nebulae, aurora with a bright
  lower edge and rays. Stars have true colours, twinkle gently (more near the horizon) and fade low in the sky,
  as does the Milky Way. Scene colours are now natural (no neon purple).
- New art tools: tools/make_menu_real.py and tools/make_sky_real.py (replace make_menu_hq.py / make_sky_textures.py).

## 0.21.17 - Player cards, armor alerts, per-server setups, auto reconnect
- Middle click a player for their card: face, whether they use Tempest, MCTiers / NovaTiers, and Add Friend / Message.
- New: Armor Break Alert (PvP) - a warning and a sound when any armor piece you wear drops below a set durability.
- New: Per-Server Setups (Client) - pick a saved config for a server and it loads every time you join that server.
- Reconnect: when a server is full (or restarting / queueing) Tempest retries every few seconds until you get in,
  with a countdown and a Cancel button. "Auto Reconnect On Any Kick" retries for any reason.
- The game window is now called "Tempest Client 1.21.11".

## 0.21.16 - Color Saturation
- New: Color Saturation (Visuals) - make the game more vivid (up to 200%) or wash it out to black and white (0%).
  A full-screen effect on your screen only; it steps aside while the game uses its own (spectating a creeper etc.).

## 0.21.15 - Messages, snappier Clientside Crystals
- Clientside Crystals: hitting a crystal right after placing it no longer feels delayed - the hit goes to the server's
  real crystal (straight away, or the instant it appears) instead of being lost on the stand-in.
- Minecraft's own menus (Options, Video, Controls...): flat rectangle buttons and sliders on the see-through background.
- Updates: "Check for updates" button on the main menu (bottom left), and "Check" in Client Settings works in game.
  A new version shows a pop-up (and a chat line in game); a manual check also tells you when you're up to date.
- Zoom: new "Zoom Sensitivity %" setting for mouse speed while zoomed.
- Totem Pop Counter: "Reset Pop Counts Key" - bind a key (in its settings or Controls) to clear pop counts.
- Social menu: new Messages tab - chat with Tempest friends right in the menu, even when they're on another server.
  Friends who are offline get your message when they come back. Messages are deleted after one hour.
- Fixed: Tempest couldn't sign in on some setups ("no MinecraftSessionService field"), so those players never showed
  the Tempest logo and their Social menu stayed on "Connecting".
- Fullbright works again (lights the world like Night Vision); Chat Mentions highlights just your name and no longer
  pings on your own messages; Combat Timer's end sound is off by default.

## 0.21.11 - Join and invite friends, friend pop-ups, Combat Timer
- Social menu: "Invite" sends an online friend a "come play on my server" invite - they get a pop-up, a chat line
  with a [Join] button and a Join row at the top of their Social menu. Nothing joins until they click.
- Social menu: "Message" opens chat on the Private tab with that friend (Tempest DMs work across different servers).
- Pop-ups when a friend comes online or joins your server (and, if you turn it on, goes offline) - Social settings.
- New: Combat Timer (PvP) - counts down until you're out of combat. Starts when you hit a player or get hurt near one,
  follows the server's own "Combat: 12s" / "no longer in combat" messages, and plays a sound when it's over.
- Social menu: friends who are on a server get a "Join" button - one click takes you to the same server (it leaves
  the world you're in first). Shows only when they share their server ("Show Friends My Server") and you're not
  already there.

## 0.21.10 - FPS Saver, Light Levels, Tempest logo for everyone
- New: FPS Saver (Render) - skips drawing mobs hidden behind walls (players too, if you turn it on), entities further
  than a set distance, far chests/signs/banners, and far particles. Only changes what you see.
- New: Light Levels (Utility) - press F7 to mark where monsters can spawn: red any time, yellow at night.
- Everyone on Tempest now always shows as a Tempest user (the logo above their name) to other Tempest users - the
  "Let Others See I Use Tempest" setting is gone, and turning off the Nametags module no longer hides you.
- Someone who has just started Tempest gets their logo within about 30 seconds instead of up to 2 minutes.

## 0.21.9 - Simpler Tempest Editor
- Tempest Editor: a cleaner home page - a smaller title, plain flat tiles (no glows or underlines), the shortcuts
  (Configs, My Packs, Cosmetics, Painter) as one row of small buttons, and the status as a single line of text.

## 0.21.8 - Friend request badge, lighter friend glow
- Menu: the Social button shows a red "+N" badge when someone has sent you a friend request.
- Glow Friends costs much less FPS: a new "Light Glow (more FPS)" option (on by default, in Social) draws a sharp
  outline in 2 screen passes instead of vanilla's soft blurred one (4 passes). Turn it off for the soft look.
- Mods menu: the search box just says "Search mods" (the filter hints are gone; the filters still work).

## 0.21.7 - Fly Speed, armor bars under each piece
- New: Fly Speed (Utility) - choose how fast you fly in creative, from 0.5x to 10x the normal speed.
- Armor HUD: the durability bars (Number mode) now sit right under the armor piece they belong to.
- Mods menu: the shortcut buttons along the bottom (Edit HUD, Custom Sky, ...) are sized to fit their labels.
- AppleSkin: the food preview is back to how it was in 0.21.5 (no gold glow or "+N" label).
- Server icons: removed the website-icon fallback - servers show only their own icon again.

## 0.21.6 - Spotify covers, clearer AppleSkin, slimmer armor bars
- Spotify: covers now load for every song Spotify shows one for. Tempest reads the cover straight from Windows
  properly (it could come back empty before and never retry), waits a moment after a song change so it doesn't
  show the previous song's cover, and if Windows has none it tries Deezer as well as iTunes.
- AppleSkin: holding food shows a gold glow over the shanks it will fill and "+N" above them.
- Servers with no icon: Tempest also looks for an icon on the server's store and www sites.
- Armor HUD: the durability bars (Number mode) are thinner and shorter.

## 0.21.5 - Window icon, menu fixes
- Servers that send no icon (like crystalbox.org) show their website's icon instead of the globe.
- Mods menu: your name and face show in the corner card from the main menu too (not "Player").
- The Right Shift menu blurs the game behind it as well (GUI & Configs -> Menu Blur).
- Totem Pop Counter: a Reset button to clear the pop counts by hand.
- The game window uses the Tempest icon (taskbar, title bar, Alt-Tab) instead of the grass block.
- Discord Status: a Check button that says in chat whether it's connected to Discord (and why not); every
  connection step and any error from Discord is also written to the game log as "[Tempest] Discord: ...".

## 0.21.4 - Nicer mods menu, background blur
- Mods menu (Right Shift): the game behind it is blurred (GUI & Configs -> Menu Blur: Off / Light / Medium /
  Strong), with a lighter wash and a soft vignette. Cards have a top-lit surface, lift on hover and glow green when
  enabled; disabled mods get a quiet dark bar instead of a red one.
- Discord Status (Client): "Playing Tempest Client" on your Discord profile - where you are (server, singleplayer or
  menus), time played, the Tempest logo, your skin's face and a "Get Tempest" button. Server address, username
  and button can each be hidden. Needs the Discord desktop app.

## 0.21.3 - New font, real sky lyrics, shield fix
- Tempest's menus and HUD use Inter, a clean Discord-like font (chat, signs and vanilla screens keep Minecraft's
  font). GUI & Configs -> Font switches back to the Minecraft font.
- Sky lyrics are now really in the sky: a glowing panel far away above the horizon, part of the world (buildings
  and trees in front hide it, real perspective), drifting slowly after you like the moon; lines cross-fade.
- Shield Fixes: other players' raised shield always shows. When the "using an item" signal arrived before their
  equipment, the game lost the shield and never showed it raised until they blocked again.
- Tempest Editor: new Spears category (every material, inventory and held textures).
- Totem Pop Counter: the totem count can be moved in the HUD editor (starts above the hotbar as before).

## 0.21.2 - Tier Tagger MCTiers fix, new Spotify card
- Tier Tagger: MCTiers tiers now show. It looks players up the same way the TierTagger mod does (the rankings
  endpoint); if a list can't be reached, the reason is written to the game log once.
- Spotify HUD redesigned: darker glass card with a soft wash of the cover's colour, bigger cover with a clean
  pause sign, thin progress bar with time left, lyrics in their own panel (current line bright, the others fading,
  no bold/shadow). Covers load reliably: if Spotify doesn't hand one over, it's looked up online by song + artist.
- Spotify: lyrics can float in the sky (Lyrics Display: Card / Sky / Both). Sky lyrics hang above the horizon
  where you look, drift after you when you turn, glow in the song's colour, show the next line underneath, and fade
  out where you can't see the sky. Height and size are adjustable.

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

## Credits

Tempest bundles [In-Game Account Switcher](https://modrinth.com/mod/in-game-account-switcher) (© The_Fireplace,
VidTu), unmodified, under the GNU LGPL v3 or later. It ships as its own jar nested inside Tempest's; its license and
source are at https://github.com/The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher. To use a different version,
put that IAS jar in your mods folder - Fabric loads the newer one.

Chat emoji are [Twemoji](https://github.com/jdecked/twemoji) graphics (© Twitter, Inc. and other contributors),
licensed under CC-BY 4.0 (https://creativecommons.org/licenses/by/4.0/); they were resized and packed into a font
sheet by tools/make_emoji_font.py. Emoji names come from GitHub's [gemoji](https://github.com/github/gemoji) (MIT).
