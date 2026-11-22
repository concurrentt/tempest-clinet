# Tempest online service

Lets Tempest clients see which players also use Tempest (they get the Tempest logo on their nametag).
Tiny Node.js app with no dependencies. Everything is kept in memory; nothing is written to disk.

## How it works
1. **Sign in (once a day):** the client asks for a one-time code, "joins" it through Mojang's session server
   (the same step Minecraft does when you join a server), and the service asks Mojang to confirm. The player's
   Minecraft access token only ever goes to Mojang, never here.
2. **Heartbeat (every 60 s):** "I'm online with Tempest". Players disappear 2.5 minutes after their last one.
3. **Lookup (every 10 s, batched, cached 2 min):** "which of these players use Tempest?"

Endpoints: `GET /health`, `POST /v1/auth/start`, `POST /v1/auth/finish`, `POST /v1/heartbeat`,
`POST /v1/lookup`, `POST /v1/logout`. Limits: 120 requests/min per IP, 200 players per lookup, 16 KB bodies.

## Deploy on Railway (CLI - easiest)
1. Make a Railway account at railway.com and install the Railway CLI (see Railway's docs), then log in:
   `railway login`
2. In **this folder** (`tempest-backend`), run:
   ```
   railway init        # name the project, e.g. tempest-backend
   railway up          # uploads and deploys this folder
   railway domain      # gives you a public https address
   ```
3. Open `https://<your-domain>/health` in a browser. You should see `{"ok":true,"service":"tempest","online":0}`.

## Deploy on Railway (GitHub)
1. Push the project to GitHub.
2. On railway.com/new choose **Deploy from GitHub repo** and pick it.
3. Because this service lives in a sub-folder, open the service's **Settings** and set the **Root Directory**
   to `/tempest-backend`.
4. In **Settings -> Networking**, click **Generate Domain**.

Railway detects Node.js automatically, runs `npm start`, and provides `PORT`. No variables are needed.
**Never** set `TEMPEST_DEV_SKIP_MOJANG` on Railway: it turns off account verification (local testing only).

## Point the mod at it
In `src/main/java/dev/tempest/util/TempestNet.java` set
`DEFAULT_URL = "https://<your-domain>";` and rebuild the mod. (Players can also override it in
`config/tempest.json` with `"backendUrl"`.)

## Test locally
`npm test` starts a local copy with the Mojang check skipped and runs the whole flow.

## Cost
A service this small stays well inside Railway's free monthly usage credit for a modest player base; the Hobby
plan ($5/month incl. $5 usage) covers a lot more. Check railway.com/pricing for the current plans.
