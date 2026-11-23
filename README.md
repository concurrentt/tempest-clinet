Tempest 0.17.4 - Fullbright fixed

- Fullbright only set brightness to 1.0 (vanilla's "Bright"), which does nothing in caves. It now writes a much
  higher brightness straight into the option (past the slider's limit; Strength 2-20, default 15), restores your
  own brightness when switched off, and options.txt always saves your real value (mixin/GameOptionsSaveMixin).

Tempest 0.17.3 - Glowing kill effects

- Kill Effects rewritten: drawn by Tempest in the world with additive (glowing) blending instead of Minecraft
  particles, animated every frame from the time since the kill (smooth at any FPS):
  Soul Spiral (two glowing strands winding up, then a ring of light bursts at the top), Shockwave (white-hot
  pillar, then two glowing rings racing across the ground), Storm Strike (jagged lightning with a white-hot core
  and a branch, a flash and a ground ring), Starburst (glowing streaks bursting from the chest with a centre
  flash), Ascension (a rising column of light, a golden halo floating up, sparks spiralling with it).
  All use your theme colour; "Detail" High/Medium/Low. Hook: mixin/WorldRendererEffectsMixin
  (WorldRenderer.pushEntityRenders).

Tempest 0.17.2 - Auras drawn by Tempest

- Auras no longer use Minecraft particles. They are Tempest's own glowing sprites drawn with your player
  (render/CosmeticsFeature): Sparkles (twinkling stars spiralling up), Hearts (rising and swaying), Flames
  (a flickering ring of flames at your feet), Snow (flakes drifting down around you), Notes (colour-cycling
  music notes), Theme Orbit (three orbs with fading trails, in your theme colour) and the new Rings (two tilted
  rings spinning round your body plus a pulse at your feet). New "Aura Speed" setting. They show in third person
  and in the cosmetics turntable preview. Textures: tools/make_cosmetics.py (aura_*.png).

Tempest 0.17.1 - Totem pops, auras, smooth rounded UI

- Totem pops on nametags (and Auto GG deaths, shield disables): the entity-status hook used TAIL, which only
  catches the method's last return; the status handler returns early in other branches, so totem pops (35)
  were never counted. It now runs at the start of the handler (main thread only, so once per event), where no early return can skip it. Nametag decoration now runs on every return.
- Auras now show in first person by default (kept below eye level so they never sit on the camera).
- Rounded shapes (buttons, panels, rings, toggles, circles) are drawn at real screen-pixel resolution with
  anti-aliased edges, so corners are smooth curves instead of stair-steps at GUI scale 2-4. Buttons are rounder
  (Right Shift menu, main menu, vanilla buttons and sliders); the promo banner is clipped to its rounded card.
- Mod settings: removed the accent bar on the left of a hovered setting.

Tempest 0.17.0 - Key binds search, smarter search, chat animation, shield remakes, bug fixes

Bug fixes
- Ping: the HUD now measures ping live on the game connection (the same small ping packet vanilla's F3 graph
  uses, every 2 s, median of the last 5), falling back to the tab list and then a status ping. Status pings
  (also used by the Multiplayer screen) now turn off Nagle's algorithm, which was adding 40-200 ms, and the
  Multiplayer screen keeps the last ping while refreshing.
- Joining a server from the mod menu's Favorite Servers while already in a world glitched the screen: it
  connected on top of the live world. It now leaves the current world first (like Disconnect does) and
  connects from the server list (util/ServerJoin, also used by the Multiplayer and main-menu buttons).
- Fast Screens "Skip World Loading" no longer leaves a blank screen while the world is still loading.
- Totem pops were appended to every player's real name (getName/getDisplayName). That changed names in chat,
  broke friends and Auto GG matching, doubled the "-N" on nametags, and in singleplayer even renamed players on
  the server. Removed; pops are shown on nametags only.
- Shield blocking state is only changed on the client, never on the singleplayer server.
- ChatHud.refresh is private in 1.21.11; the shadow now matches (stacking and chat tabs depend on it).
- Cosmetics turntable uses the 1.21.11 entity render manager API.

New / remade
- Key Binds (like Controlling): Options -> Controls -> Key Binds opens Tempest's version: search, a
  Conflicts view (with count), a Free Keys view, click a key to rebind (mouse buttons work, Esc unbinds),
  right-click or the reset button for default, Reset All, and Classic for the vanilla screen.
- Search filters with auto-complete (like Searchables), in the key binds and the mod menu: free words plus
  key:r, category:movement, is:conflict / is:unbound / is:changed (key binds), category:pvp, is:on / is:off /
  is:fav / is:hud (mods); -word excludes; Tab completes.
- Chat Animation (Chat, on by default): new messages slide up smoothly (60-500 ms), clipped at the chat's edge.
- Shield Status (remade, like Shield Statuses): a coloured shield badge on the nametag of everyone holding a
  shield - enabled / disabled colours, optional fade back to enabled, seconds left - plus your own HUD
  element with a progress bar. Other players' disable time assumes vanilla's 5 s (configurable).
- Shield Fixes (remade, like Complete Shield Fixes): disable and block sounds for every hit (deduplicated),
  and an accurate blocking state with the real 5-tick raise delay ("Respect 5-Tick Delay", on by default).

Tempest 0.16.1 - More capes and cosmetics

- 8 new capes: Aurora, Galaxy, Sakura, Inferno, Ocean, Void, Royal, Synthwave (tools/make_capes.py; each also
  textures your elytra). 14 capes in total.
- 3 new wings: Phoenix (fire feathers with glowing tips), Crystal (translucent ice shards), Fairy (iridescent).
- 5 new head pieces: Bunny Ears, Antlers, Flower Crown, Witch Hat (cone that bends back), Top Hat.
- Auras (new cosmetics tab): Sparkles, Hearts, Flames, Snow, Notes, Theme Orbit (in your theme colour). A few
  client particles around you every couple of ticks; hidden in first person unless "Aura In First Person" is on.
- New icons for all of them in the cosmetics carousel.

Tempest 0.16.0 - Old animations, kill effects, HUD tools, chat tabs, cosmetics carousel, theme editor

- Old Animations (PvP, new): 1.7-style sword blocking (sword in hand + shield in off hand, right click; the shield
  model is hidden), block-hitting swing (left click while blocking/eating plays the swing, client-side only),
  and No Cooldown Dip (the item no longer dips after every hit). Visual only, nothing extra is sent.
- Kill Effects (PvP, new): Soul Spiral, Shockwave, Storm Strike, Starburst, Ascension (or Random), in your theme
  colour, with a sound. Built from vanilla dust particles on a fixed per-tick budget (no extra render passes),
  0.5-1.5 s each, max 3 at once; Particle Amount High/Medium/Low. "Preview" plays one in front of you.
  Kills are detected from the death of the player you just hit or a kill message naming you (shared with Auto GG,
  which no longer needs to be on for this).
- Server Address and Pack Display HUD elements (new).
- HUD editor: snapping to screen edges/centre and to other elements (edges, centres, side-by-side with a 2px
  gap) with guide lines for every match, an optional 8px grid, Snap/Grid toggles (S/G), Shift to place freely,
  arrow keys to nudge (Shift = 5px).
- Chat Tabs (Chat, new): All / Party / Guild / Private tabs above the chat box with unread counts, and hide
  filters: /tempest filter add <regex>, /tempest filter list, /tempest filter remove <n>, /tempest filter clear.
- Cosmetics (Right Shift -> shirt): a carousel (arrows, click a side card to bring it in, click the middle card or
  Enter to equip) next to a turntable preview of your player wearing the focused item. Browsing tries items on;
  closing the menu puts your saved look back. The menu slides up on small screens so the panel fits.
- Theme editor (Right Shift -> Theme, or Theme on the main menu profile card): preset accents or any colour
  (Custom theme from the hue strip), and the menu background: Nebula, Galaxy, Aurora, Black Hole, Ringed
  Planet, Blood Moon, Animated or Plain. Also in Client -> GUI & Profiles.

Tempest 0.15.0 - Menus, options, totems, combat fixes

- Main menu: night-sky backdrop (drifts slowly, also behind Singleplayer/Multiplayer/Options), a proper TEMPEST
  wordmark, a Custom Sky banner with a rendered black hole, your skin's face on the profile card.
- Multiplayer: server icons of any size are shown (scaled to 64x64), the ping now uses the game's protocol
  number (some proxies sent no icon to the old "-1" handshake), and icons are kept between pings and saved.
- Options and every other vanilla menu: Tempest-style rounded buttons and sliders, and the night sky instead
  of the panorama. Client -> GUI & Profiles -> "Tempest Style Buttons & Options" turns it off.
- Right Shift menu: wordmark, glass buttons with captions, MODS shows the mod count, new shortcut chips (Edit
  HUD, Custom Sky, Settings). The crosshair no longer shows through Tempest menus.
- Totem Tweaks: Pop Size now shrinks the pop animation in place (and goes below 35%). Held Size scales the
  totem in your hand around the item itself.
- Totem pops on nametags: decorated at three points (base renderer, player renderer, right before drawing),
  so servers and renderers that rebuild the name can't drop the "-N".
- CPS: counts real mouse presses (hooked in front of Minecraft's GLFW mouse callback). Reach, Combo and
  Target HUD now get every hit (ClientPlayerInteractionManager.attackEntity) - they were never being fed.
- Auto GG: also triggers from death messages that name you as killer, common kill-feed lines, game-end lines
  (Winner, 1st Killer, Victory, You won ...) and your own death; each trigger has a switch, 10 s cooldown.
- Combat Hitboxes: rewritten. Vanilla hitboxes (F3 + B) turn red while that entity is within your reach
  (uses your real reach, or a custom value). No more extra HUD lines.
- Motion Blur: real frame blending (post effect with a persistent previous-frame buffer), world only, HUD
  stays sharp. Strength 1-9.
- HUD elements (FPS, CPS, Ping ...): new Modern style - soft shadow, smoked-glass surface, hairline edge and
  the mod's icon in the accent colour.
- Low Fire: Ground Fire Height now also works with Fabric's renderer and Sodium. The fire block models are
  wrapped through Fabric's model-loading API and their quads squashed toward the ground while chunks are built
  (render/LowFireModel.java); before, only the vanilla block mesher was hooked. Ground default is now 40%.
- Resource pack loading screen ("Applying textures"): it showed as a blank dark screen. Its fade-in restarted
  on every frame, so the logo, text and bar never became visible. It now restarts only when a new loading
  screen opens (same fix for the start-up screen).
- Main menu after a resource pack reload: the Tempest menu no longer gets replaced by the vanilla one. The
  redirect now also works while the reload overlay is up, and a per-tick check swaps any vanilla title screen
  that slips through back to the Tempest one.
- Mods menu: categories in the sidebar are text only (mods keep their icons).
- Fixed: MinecraftClientMixin was listed in both mixin configs.

Tempest 0.14.0 - Online: see other Tempest users

- New tempest-backend/ folder: a tiny Node.js service (no dependencies) to host on Railway. See
  tempest-backend/README.md for the 3-command deploy. `npm test` runs an end-to-end test locally.
- Client: util/TempestNet.java signs in once with Mojang's session check (your access token only goes to
  Mojang), sends a heartbeat every minute while you play, and asks which nearby players use Tempest (batched,
  cached). Those players get the Tempest logo on their nametag.
- Render -> Nametags: "Show Tempest Users" and "Let Others See I Use Tempest" (both on; switch either off).
- Set TempestNet.DEFAULT_URL to your Railway address before you ship the mod (empty = feature off).

Tempest 0.13.0 - Fixes: totem pops, low fire, ping, block outline, xray; nametag logo

- Totem Pop Counter: the count is now on the player's nametag ("Steve -3") instead of floating above the head.
- Nametags (new, Render): the Tempest logo in front of your name and your Tempest friends' names, and your own
  nametag in third person (F5). The logo is a font glyph: assets/tempest/font/icons.json, U+E000.
- Info Bar: shows the Tempest logo.
- Low Fire: two sliders, Screen Fire Height and Ground Fire Height (100% = vanilla, 0% = gone). Ground fire
  now really changes: fire blocks are squashed (or skipped) while chunks are built, and chunks are rebuilt when
  you move the slider.
- Ping: shows your real ping. Uses the tab-list latency when the server fills it in; many servers and proxies
  leave it at 0, so then Tempest pings the server itself every 5 seconds. Singleplayer shows "local".
- Block Outline: rewritten. It no longer draws lines on the HUD every frame (that was the lag); it changes the
  colour, opacity and thickness of Minecraft's own outline and can add a translucent fill, drawn in the world.
- Xray: rewritten. Blocks that are not on the list are skipped while chunks are built (water hidden, lava
  optional), and what's left is drawn fully lit so ores show up in dark caves. Choose ores, ancient debris,
  chests & barrels, spawners, lava. Note: renderer mods like Sodium build chunks themselves and bypass this.
- Removed: Custom Crosshair; Fake Chunks (it only drew a grid, it never loaded terrain).

Tempest 0.12.1 - AppleSkin outlines, server icons

- AppleSkin: saturation is now a gold outline around each hunger shank (filling in quarters, 2 saturation per
  shank), like the real AppleSkin, instead of a thin bar. The outline is traced from the hunger icon of your
  active resource pack, so it always lines up. Holding food also flashes the saturation it would add.
- Multiplayer: each server shows its own icon (from the live ping, or the one saved in your server list);
  servers without one keep the globe tile. The main-menu favourites card shows the icons too.
  Code: util/ServerIcons.java, util/ServerPinger.java (favicon), modules/AppleSkinModule.java.

Tempest 0.12.0 - New list screens, sky scenes, cosmetics

Singleplayer / Multiplayer:
- Rebuilt in the main-menu style: back button + title, compact rows with a coloured icon, no big empty window.
- Singleplayer: game-mode icon per world (survival, creative, adventure, spectator, hardcore), "Survival . 26 min
  ago", Play appears on hover; search; Play Selected and Create / Edit (vanilla world screen) at the bottom.
- Multiplayer: live ping with signal icon and players; on hover Join, favourite and remove (asks for a second
  click). Header buttons: Favorites, Refresh pings, Classic server list. Bottom: address field + Add + Connect.

Custom Sky:
- One scene at a time instead of stacking toggles: Deep Space, Black Hole, Northern Lights, Galaxy Core,
  Ringed Giant, Nebula, Meteor Shower, Blood Moon. Click "Scene" to cycle, or bind "Next Sky Key" (the name of
  the new sky shows above the hotbar). Settings: Brightness, Turn Scene, Shooting Stars, Show (always / night),
  Hide Clouds, Horizon Blend.
- Better art: glowing horizon band, brightest stars with spikes, two-layer aurora, warm galactic core,
  cratered moons, meteor heads that glow.
- Scenes are data: assets/tempest/sky/scenes.json. tools/preview_sky.py renders every scene offline with the
  same textures and maths, so you can tweak a scene and see it without launching the game.

Cosmetics (new):
- Wings: Angel, Demon, Dragon, Butterfly (flap; size and flap speed settings). Head: Halo (5 colours, floats),
  Devil Horns, Crown, Cat Ears. Pick them in the Right Shift menu -> cosmetics (tabs: Capes / Wings / Head)
  or in Render -> Cosmetics. Client-side: only you see them. They follow your head and body, including sneaking.
- Code: render/CosmeticsFeature.java (player feature, like the cape), modules/CosmeticsModule.java.
  Textures from tools/make_cosmetics.py.

Tempest 0.11.0 - Custom Sky (one mod, drawn in the real sky)

- The separate sky mods (Space Sky, Aurora, Black Hole, Milky Way, Nebula, Ringed Planet, Shooting Stars)
  are now one mod: Visuals -> Custom Sky. Pick a background (Deep Space, Nebula Purple, Midnight Blue,
  Blood Moon, Theme, or Vanilla) and switch on the objects you want; each has its own settings.
- It is now part of the actual sky: drawn inside Minecraft's sky pass right after the sky dome, with the
  game's own camera transform. It no longer slides when you walk or bob, and blocks cover it exactly.
- Much faster: the old version drew thousands of HUD rectangles and cast ~50 rays every frame. Now each
  object is a few textured quads (about a dozen draw calls for the whole sky) and nothing is ray cast.
- Textures in textures/sky/*.png are pre-rendered by tools/make_sky_textures.py (numpy + Pillow).
  Code: modules/CustomSkyModule.java (settings, background, fog), render/SkyRenderer.java (drawing),
  mixin/SkyRenderingMixin.java. If drawing ever fails, Custom Sky turns its drawing off for that session and
  writes one line "[Tempest] Custom Sky drawing turned off: ..." to the log instead of crashing.
- Fireflies and Cinematic Grade stay as their own Visuals mods.

Tempest 0.10.0 - GUI rework

- Icons: an original white line-icon set (75 icons) for every mod, sidebar category and menu button.
  Atlas: textures/gui/icons.png, generated by tools/make_icons.py (edit / add icons there and re-run).
  Code: gui/Icons.java (module -> icon map, drawing).
- Mod menu grid: three cards per row. Each card has a big icon, the mod name, an OPTIONS bar with a gear
  (gear on HUD mods opens the HUD editor), and a full-width green ENABLED / red DISABLED bar.
  Star (top-left, on hover) favourites a mod; favourites are listed first. Sidebar uses the icons too.
- Right Shift menu: Tempest logo and "TEMPEST CLIENT" over the game, with [emotes] [MODS] [cosmetics].
- Main menu: dark rounded buttons with icons; "Mods" (with mod count) and a green "Visuals" button side by
  side; a "SPACE SKY / NEW!" feature card that opens the Visuals tab; Options and Quit underneath.

Tempest 0.9.0 - Space Sky (replaces the sky)

- New Visuals module "Space Sky": replaces the Overworld sky with deep space, day and night.
  The sky dome and the horizon fog are recoloured inside Minecraft's own sky pass, and the sun, moon,
  vanilla stars, sunset glow and clouds are removed, so blocks cover the new sky exactly.
  Styles: Deep Space, Nebula Purple, Midnight Blue, Blood Moon, Theme. Settings: Horizon Blend,
  Hide Sun & Moon, Hide Clouds, Effects in Daytime, Star Field (up to 4000 stars), Twinkle.
- While Space Sky is on, the other sky effects (aurora, black hole, galaxy, nebula, planet, shooting stars)
  show all day. Fireflies still follow the real night.
- Sharper effect edges against terrain: the open-sky map is 4x finer (3.75 x 3 degree cells) and its rays
  reach as far as your render distance (up to 512 blocks).
  Code: modules/SpaceSkyModule.java, mixin/SkyRenderingMixin.java (SkyRendering.renderTopSky /
  renderCelestialBodies / renderGlowingSky), mixin/FogRendererMixin.java (FogRenderer.getFogColor),
  mixin/WorldRendererCloudsMixin.java (WorldRenderer.renderClouds). All hooks are optional (require = 0):
  if a Minecraft update renames one, that part quietly stops instead of crashing.

Tempest 0.8.0 - Visuals tab

New "Visuals" category in the Click GUI (its own sidebar tab), all off by default:
- Aurora Borealis: rippling curtains low in the night sky. Colors (Classic, Polar, Ember, Rainbow, Theme),
  direction, brightness, height, spread, ripple speed, optional daytime.
- Black Hole: shadow, photon ring, lensed light arching over the top, tilted spinning accretion disk and
  infalling matter. Palettes (Interstellar, Quasar Blue, Crimson, Void Purple, Theme), size, position,
  disk tilt / angle, spin speed.
- Shooting Stars: meteors with fading tails; optional Meteor Shower from one radiant point.
- Nebula Clouds: soft glowing gas clouds that slowly breathe (Cosmic, Orion, Teal Dream, Theme; layout seed).
- Milky Way: a galaxy band of up to 2000 twinkling stars with a glowing core; band angle and twinkle.
- Ringed Planet: a shaded, banded gas giant with tilted rings and a Cassini gap (Saturn, Ice Giant, Lava World).
- Fireflies: glowing bugs drifting and blinking in the air around you, hidden behind walls.
- Cinematic Grade: vignette, color mood (Cool Night, Golden Hour, Dreamy, Noir, Theme) and letterbox bars.

How it works: effects are drawn on the HUD layer (under the hotbar, chat and crosshair) by projecting sky
directions with the camera, like Combat Hitboxes. An "open sky" map, refreshed with a few ray casts per frame,
keeps them from showing through terrain, roofs and caves. They fade with day/night and rain, and only appear
in the Overworld (Cinematic Grade works everywhere). They are client-side only.
Code: util/SkyView.java (shared sky math + occlusion), gui/VisualsRenderer.java (draw order),
modules/AuroraModule, BlackHoleModule, ShootingStarsModule, NebulaModule, MilkyWayModule, RingedPlanetModule,
FirefliesModule, CinematicModule.

Tempest 0.7.0 - Custom main menu + glass UI

New screens (replace the vanilla ones; toggle: GUI & Profiles -> "Custom Main Menu"):
- Main menu: animated backdrop (drifting glows + dust), logo with a breathing glow, TEMPEST wordmark, staggered
  button entrance. Singleplayer / Multiplayer / Tempest Mods / Options / Quit, a Favorites quick-join card
  (live ping + player count) and a profile card (Customize, Friends, Edit HUD, Waypoints).
- Singleplayer: searchable world list sorted by last played, game-mode / hardcore chips, keyboard + double-click
  to play. "Create / Manage" opens the vanilla world screen (create, edit, delete, re-create are untouched).
- Multiplayer: saved servers with live ping / players, star to favourite, Join, delete, add-by-address,
  Direct connect, and "Classic List" to open the vanilla screen.
  Code: gui/TempestTitleScreen, TempestSinglePlayerScreen, TempestMultiplayerScreen, MenuKit, TempestScreens,
  mixin/ScreenRedirectMixin (MinecraftClient#setScreen).

Click GUI + every Tempest window:
- Translucent, soft "glass": themes are now see-through (window, sidebar, cards, rows), with a lighter backdrop,
  a faint sheen + hairline on the top edge, rounder corners and a soft halo instead of a hard shadow.
- Panels draw their border as a ring, so transparent fills no longer show the border through them.
- Buttons are soft gradients with a light top edge; toggles glow when on; shared by Friends / Waypoints /
  Favorites / HUD editor automatically (gui/Draw.java).
- Opened from the main menu, windows get the animated backdrop instead of a black screen.

Tempest 0.6.3 - Click GUI polish + "Applying texture pack" screen

Click GUI (Right Shift menu):
- Module cards now have an icon tile (the module's item on a tile tinted by its category), a clearer
  name / description pair, and an accent wash + border when the module is on. Hover brightens the card.
- Sidebar: category-coloured dots, enabled-count pills, a marker bar on the selected row, chevrons on
  Edit HUD / Waypoints / Friends / Servers, and a brand header with the version.
- Window: soft accent glow behind it (follows the theme), a faint top sheen + accent hairline, and the
  contents fade in when it opens. Lists fade out at the top / bottom edge where they can still scroll.
- Header shows "N mods . M on" with a category-coloured bar; the search pill glows when focused and the
  "Ctrl K" hint hides once you type.
- Settings page: bigger header card with the module's icon, taller rows with a hover marker, slider with a
  glowing knob, and consistent button sizes.
- Fixed: the search pill's click area was shifted 8px left of where it was drawn.
- Fixed: on short windows the last sidebar row ("Servers") ran past the bottom of the panel; the rows now
  tighten up to fit.
- Icons added for the 14 modules that had none (Xray, Capes, Shield Status, ...). See gui/ModuleIcons.java.
  Code: gui/ClickGuiScreen.java, gui/Draw.java (hairline, chip helpers).

Resource pack / reload screen:
- Now says "Applying texture pack" (with a small "RELOADING RESOURCES" caption), shows a ring of dots
  circling the logo, and the progress bar has a glowing spark on its tip.
- Text is pre-rendered like the start-up screen, because fonts are part of what a reload replaces.
  New textures: textures/gui/loading/applying.png, reloading.png, and textures/gui/glow_soft.png (a white glow
  that is tinted with the current theme colour).
  Code: gui/TempestLoading.java (renderReload, drawBar, drawOrbit, drawGlow).

Tempest 0.6.2 - Capes

New:
- Five Tempest capes: Tempest (your logo), Stormcloud, Voltage, Ember, Frostbite.
  Pick one in the menu: Render -> Capes -> Cape (or "None" to turn it off).
- Client-side: only you see your cape. Other players are untouched.
- Works on the elytra too (the elytra wears the same design).
- Your own Mojang cape is replaced while a Tempest cape is selected; "None" gives it back.
- Hide it like any cape with Skin Customization -> Cape.
- Textures: assets/tempest/textures/cape/*.png (256x128, normal cape layout, fully opaque).
  Code: modules/CapesModule.java, util/CapeSkins.java, mixin/AbstractClientPlayerEntityMixin.java.
- If a Minecraft version changes how skins are stored, the cape quietly does nothing and one line
  "[Tempest] Could not apply cape" is written to the game log. It never crashes the game.

Tempest 0.6.1 - World / server loading screen

New:
- The "Welcome to Tempest" look now also covers joining and leaving worlds:
  connecting to a server -> logging in -> joining world, opening a singleplayer world,
  loading terrain, and saving / disconnecting on the way out.
- Status lines are put on and taken off with a short fade and slide, and the bar is an animated sweep.
- Minecraft's own screens still run underneath (Cancel, timeouts and errors behave as before);
  Tempest paints over them and puts their buttons back on top. No new mixins.
- Toggle: GUI & Profiles -> "Tempest Loading Screen". Turn it off to get vanilla screens back.
  Code: gui/TempestWorldLoading.java (drawing), gui/WorldLoadingHooks.java (when it shows).
- Disconnect screens (kicked / connection lost) are left alone so the reason stays readable.

Tempest 0.6 - Loading screen + GUI cleanup

New:
- "Welcome to Tempest" loading screen (logo, glow, progress bar) replaces Mojang's red start-up screen.
  Mojang's overlay still controls timing and the fade into the title screen; Tempest only changes how it looks.
  F3+T resource reloads keep the normal overlay.
  Code: gui/TempestLoading.java, mixin/SplashOverlayMixin.java, mixin/DrawContextMixin.java,
  textures in assets/tempest/textures/gui/loading/.
- Logo shown in the menu sidebar and in the Friends / Waypoints / Servers window headers.

GUI cleanup:
- Module cards: name, one-line description, toggle. Keybind chip only when a key is bound; red dot marks
  modules some servers restrict. Hover a trimmed description to see it in full.
- Settings rows are flat (no per-row borders), sliders/toggles/buttons simplified.
- HUD elements share one layout: dim label + bright value, one accent bar, one quiet background.
  FPS no longer reads "FPS 144 FPS". The opacity slider now matches the actual opacity.
- Fixed: sidebar "Servers" overlapping the profile card, scrollbar drawn over card borders.

Previous notes:
Tempest 0.6 — Smooth HUD Metrics Update

Changes:
- FPS display now eases toward the live frame-rate value instead of jumping between integer readings.
- Ping and estimated TPS values use frame-rate-independent easing to reduce visual jitter.
- HUD panel backgrounds ease their width and height when displayed text changes size.
- Shared HUD text is rendered without the soft drop shadow for a crisper appearance.
- Info Bar text is rendered crisply and uses a restrained accent marker.

Build from the project directory with: gradle build



## 0.19.1 - Social redesign, tab list logo, totem icons
- Tempest logo in the tab list for Tempest users and friends (Nametags > Logo In Tab List). The online lookup now covers
  everyone in the tab list, not just nearby players.
- New logo glyph: solid white cloud with a violet bolt and a dark edge, readable on any background.
- Totem pops on nametags show a small totem icon and the count; the hotbar totem counter is a pill that turns amber at 2-3
  and red on your last totem; new totem icon in the mod list.
- Friend glow is off by default (new setting) and can be switched from the Social menu sidebar.
- Social menu redesigned: sidebar with your profile and status, sections with counts, friend search, hover actions,
  empty states, toasts, smooth scrolling.

## 0.18.7 - Social menu
- New Social menu (U, the Friends button on the main menu, or the Social chip in the Right Shift menu): Tempest friends with
  online status and the server they're on, friend requests (accept / decline / cancel), and adding people by name or from
  players near you.
- Friends are stored by account (proven at sign-in); names come from Mojang, so nobody can pose as someone else.
- Social settings: show friends you're online, show friends your server, friend request alerts.
- Glow (and the friend logo) is now only for friends who accepted your request; Middle Click Friend and
  /tempest friend add <name> send a friend request instead of adding to a private list.
- Your status shows "In menus" when you're on the main menu; you go offline when the game closes.

## 0.18.0 — Auto-updater
- Tempest checks GitHub Releases (edrfvgtbhynjm/tempest-clinet) in the background on launch. A newer release shows an "Update available" pill on the main menu.
- The update window shows the release notes, with Update / Later / Skip this version. Nothing downloads until you click Update.
- Downloads only from that repo's release page over HTTPS, checks the SHA-256 against GitHub's digest, then swaps the jar after Minecraft closes. If anything fails, the old jar stays put.
- Settings → GUI & Profiles: "Check For Updates" toggle and a "Check now" button.
- GitHub workflow: no longer needs gradlew; tagged builds are stamped with the tag's version.
