/**
 * Meeting Room Display - Cloudflare Worker
 *
 * One endpoint: GET /api/status
 * Reads a Zoho Calendar (one room = one calendar) and returns:
 * { isOccupied, currentMeeting: {title, endTime} | null, nextMeeting: {title, startTime} | null }
 *
 * Required secrets (set with `wrangler secret put <NAME>`):
 *   ZOHO_CLIENT_ID
 *   ZOHO_CLIENT_SECRET
 *   ZOHO_REFRESH_TOKEN
 *   ZOHO_CALENDAR_UID   (the room's calendar UID from Zoho Calendar settings)
 *
 * Zoho EU data center is used everywhere (accounts.zoho.eu, calendar.zoho.eu).
 */

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname !== "/api/status") {
      return new Response("Not found", { status: 404 });
    }

    try {
      const accessToken = await getAccessToken(env);
      const events = await getTodayEvents(accessToken, env.ZOHO_CALENDAR_UID);
      const status = computeStatus(events);

      return new Response(JSON.stringify(status), {
        headers: {
          "Content-Type": "application/json",
          "Access-Control-Allow-Origin": "*",
          "Cache-Control": "no-store",
        },
      });
    } catch (err) {
      return new Response(JSON.stringify({ error: String(err.message || err) }), {
        status: 500,
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
      });
    }
  },
};

// --- Zoho OAuth ---------------------------------------------------------

async function getAccessToken(env) {
  const params = new URLSearchParams({
    grant_type: "refresh_token",
    client_id: env.ZOHO_CLIENT_ID,
    client_secret: env.ZOHO_CLIENT_SECRET,
    refresh_token: env.ZOHO_REFRESH_TOKEN,
  });

  const resp = await fetch(`https://accounts.zoho.eu/oauth/v2/token?${params.toString()}`, {
    method: "POST",
  });

  const data = await resp.json();

  if (!data.access_token) {
    throw new Error("Zoho token refresh failed: " + JSON.stringify(data));
  }

  return data.access_token;
}

// --- Zoho Calendar -------------------------------------------------------

// NOTE: Zoho Calendar's exact event-list request shape has changed between
// API versions in the past. If this call returns an error, check the
// current "list events" endpoint in Zoho Calendar API docs for your DC
// and adjust the URL / range param below — the OAuth + status logic
// underneath does not need to change.
async function getTodayEvents(accessToken, calendarUid) {
  const now = new Date();
  const start = new Date(now);
  start.setHours(0, 0, 0, 0);
  const end = new Date(now);
  end.setHours(23, 59, 59, 999);

  const zFmt = (d) => d.toISOString().replace(/[-:]/g, "").split(".")[0] + "Z";

  const range = JSON.stringify({ start: zFmt(start), end: zFmt(end) });
  const eventsUrl = `https://calendar.zoho.eu/api/v1/calendars/${calendarUid}/events?range=${encodeURIComponent(range)}`;

  const resp = await fetch(eventsUrl, {
    headers: { Authorization: `Zoho-oauthtoken ${accessToken}` },
  });

  const data = await resp.json();
  return data.events || [];
}

// --- Status logic ---------------------------------------------------------

function computeStatus(events) {
  const now = new Date();

  const parsed = events
    .map((e) => ({
      title: e.title,
      start: new Date(e.dateandtime.start),
      end: new Date(e.dateandtime.end),
    }))
    .sort((a, b) => a.start - b.start);

  let current = null;
  let next = null;

  for (const ev of parsed) {
    if (ev.start <= now && now < ev.end) {
      current = ev;
    } else if (ev.start > now && !next) {
      next = ev;
    }
  }

  const hhmm = (d) => d.toISOString().substring(11, 16);

  return {
    isOccupied: !!current,
    currentMeeting: current ? { title: current.title, endTime: hhmm(current.end) } : null,
    nextMeeting: next ? { title: next.title, startTime: hhmm(next.start) } : null,
  };
}
