# Room Display Worker — Setup

## 1. Get Zoho OAuth credentials (self-client, EU data center)

1. Go to https://api-console.zoho.eu → **Add Client** → **Self Client**.
2. Copy the **Client ID** and **Client Secret**.
3. In the same console, under **Generate Code**, request scope:
   `ZohoCalendar.calendar.READ,ZohoCalendar.event.READ`
   Set duration to 10 minutes, generate the code.
4. Exchange that code for a **refresh token** (one-time, from your own machine):

   ```
   curl -X POST "https://accounts.zoho.eu/oauth/v2/token" \
     -d "grant_type=authorization_code" \
     -d "client_id=YOUR_CLIENT_ID" \
     -d "client_secret=YOUR_CLIENT_SECRET" \
     -d "redirect_uri=https://www.zoho.eu" \
     -d "code=THE_CODE_FROM_STEP_3"
   ```

   The response includes `refresh_token` — save it, it does not expire unless revoked.

## 2. Find the room's Calendar UID

In Zoho Calendar → the room's calendar → **Settings** → the UID is in the calendar's info/embed section (or via the `GET /api/v1/calendars` endpoint using the same access token).

## 3. Deploy

```
cd worker
npm install -g wrangler   # if not already installed
wrangler login
wrangler secret put ZOHO_CLIENT_ID
wrangler secret put ZOHO_CLIENT_SECRET
wrangler secret put ZOHO_REFRESH_TOKEN
wrangler secret put ZOHO_CALENDAR_UID
wrangler deploy
```

Test it:
```
curl https://room-display-worker.<your-subdomain>.workers.dev/api/status
```

## Notes

- Zoho's exact "list events" request shape has shifted between API versions before. If step 3's test returns an error instead of JSON, the fix is almost always in `getTodayEvents()` in `src/index.js` — the URL or the `range` parameter — not in the OAuth or status logic.
- No database, no caching layer: the Worker fetches a fresh Zoho access token on every request. Fine at one request per 30 seconds; if you ever poll much faster, add Cloudflare KV caching for the token.
