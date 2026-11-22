# Tempest online service on Cloudflare Workers (free)

Same API as `tempest-backend` (the Railway version), so the mod needs no code changes besides the address.

- Sign-in is checked with Mojang exactly like before. Your Minecraft access token only ever goes to Mojang.
- Login codes and tokens are signed with a secret (`TOKEN_SECRET`), so nothing is stored in a database.
- "Who is online" lives in memory in one Durable Object. If Cloudflare restarts it, it refills from heartbeats within a minute.

## One-time setup (Windows)
1. Install **Node.js LTS** from nodejs.org (it includes `npx`).
2. Make a free account at dash.cloudflare.com.
3. In this folder (`tempest-worker`):
   ```
   npx wrangler login
   node -e "console.log(require('crypto').randomBytes(48).toString('base64url'))"
   npx wrangler secret put TOKEN_SECRET
   npx wrangler deploy
   ```
   - `wrangler login` opens your browser - click Allow.
   - The `node -e` line prints a random secret. Copy it, then paste it when `secret put` asks.
     Keep it private; anyone with it could fake Tempest logins. Never put it in the code or on GitHub.
   - `deploy` prints your address, like `https://tempest-backend.<you>.workers.dev`.
4. Open `https://tempest-backend.<you>.workers.dev/health` - you should see `{"ok":true,"service":"tempest","online":0}`.

## Updating later
Edit `src/index.js`, run `npm test`, then `npx wrangler deploy`.

## Free plan limits
About 100,000 requests a day (resets at midnight UTC). A player sends at most a few hundred an hour, so this covers
a few hundred hours of play per day. If it runs out, Tempest badges just stop showing until the reset; the game is unaffected.
