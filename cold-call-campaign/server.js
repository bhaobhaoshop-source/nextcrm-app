/**
 * COLD CALL COMMAND CENTER — server
 * -------------------------------------------------------------
 * Zero-dependency Node HTTP server.
 * - Serves the caller UI + manager dashboard
 * - Stores every lead, every call attempt, every note in ONE json file
 * - Hands each caller their own leads so two people never call the same person
 *
 * Run it:   node server.js
 * Then open the URL it prints.
 */

const http = require("http");
const fs = require("fs");
const path = require("path");

const ROOT = __dirname;
const PUBLIC_DIR = path.join(ROOT, "public");
const DATA_DIR = path.join(ROOT, "data");
const LEADS_FILE = path.join(DATA_DIR, "leads.json");
const DB_FILE = path.join(DATA_DIR, "campaign-db.json");
const PORT = process.env.PORT || 3100;

/* ------------------------------------------------------------------ */
/* Storage                                                             */
/* ------------------------------------------------------------------ */

const DISPOSITIONS = {
  INTERESTED: { label: "Interested / Hot", terminal: false, hot: true },
  APPOINTMENT: { label: "Appointment Booked", terminal: true, hot: true },
  CALLBACK: { label: "Call Back Later", terminal: false, hot: false },
  NO_ANSWER: { label: "No Answer", terminal: false, hot: false },
  BUSY: { label: "Busy / Cut the call", terminal: false, hot: false },
  NOT_INTERESTED: { label: "Not Interested", terminal: true, hot: false },
  WRONG_NUMBER: { label: "Wrong Number", terminal: true, hot: false },
  NUMBER_DEAD: { label: "Number Off / Invalid", terminal: true, hot: false },
  DNC: { label: "Do Not Call Again", terminal: true, hot: false },
};

const MAX_ATTEMPTS = 5; // after this many no-answers we stop showing the lead

function ensureDirs() {
  for (const d of [DATA_DIR, path.join(ROOT, "exports")]) {
    if (!fs.existsSync(d)) fs.mkdirSync(d, { recursive: true });
  }
}

function loadJSON(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    return fallback;
  }
}

let db = null;

function defaultDB() {
  return {
    version: 1,
    createdAt: new Date().toISOString(),
    callers: [],
    leadState: {}, // leadId -> state
    calls: [], // append-only log of every dial
  };
}

function loadDB() {
  ensureDirs();
  db = loadJSON(DB_FILE, null) || defaultDB();
  if (!db.callers) db.callers = [];
  if (!db.leadState) db.leadState = {};
  if (!db.calls) db.calls = [];
  return db;
}

let saveTimer = null;
let savePending = false;
function saveDB() {
  savePending = true;
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    if (!savePending) return;
    savePending = false;
    const tmp = DB_FILE + ".tmp";
    fs.writeFileSync(tmp, JSON.stringify(db, null, 2));
    fs.renameSync(tmp, DB_FILE); // atomic-ish: never leaves a half-written db
  }, 120);
}
function saveNow() {
  savePending = false;
  if (saveTimer) {
    clearTimeout(saveTimer);
    saveTimer = null;
  }
  const tmp = DB_FILE + ".tmp";
  fs.writeFileSync(tmp, JSON.stringify(db, null, 2));
  fs.renameSync(tmp, DB_FILE);
}

/* ------------------------------------------------------------------ */
/* Leads                                                               */
/* ------------------------------------------------------------------ */

let LEADS = [];
let LEADS_BY_ID = new Map();
let LEADS_META = {};

function loadLeads() {
  const raw = loadJSON(LEADS_FILE, null);
  if (!raw) {
    LEADS = [];
    LEADS_META = { empty: true };
  } else if (Array.isArray(raw)) {
    LEADS = raw;
    LEADS_META = {};
  } else {
    LEADS = raw.leads || [];
    LEADS_META = raw.meta || {};
  }
  LEADS_BY_ID = new Map(LEADS.map((l) => [String(l.id), l]));
  return LEADS;
}

function stateFor(id) {
  const key = String(id);
  if (!db.leadState[key]) {
    db.leadState[key] = {
      status: "NEW",
      disposition: null,
      attempts: 0,
      assignedTo: null,
      claimedAt: null,
      lastCalledAt: null,
      callbackAt: null,
      intent: null, // BUY | SELL | BOTH | NONE
      notes: "",
      tags: [],
    };
  }
  return db.leadState[key];
}

/** A lead is "workable" if nobody finished it and it isn't parked in the future. */
function isWorkable(lead, now) {
  const s = stateFor(lead.id);
  if (s.status === "DONE") return false;
  if (s.disposition && DISPOSITIONS[s.disposition]?.terminal) return false;
  if (lead.doNotCall) return false;
  if (!lead.phones || lead.phones.length === 0) return false;
  if (s.attempts >= MAX_ATTEMPTS) return false;
  if (s.callbackAt && new Date(s.callbackAt).getTime() > now) return false;
  return true;
}

/** Priority: callbacks due first, then hot, then never-touched, then fewest attempts. */
function priorityScore(lead, now) {
  const s = stateFor(lead.id);
  let score = 0;
  if (s.callbackAt && new Date(s.callbackAt).getTime() <= now) score -= 10000;
  if (s.disposition === "INTERESTED") score -= 5000;
  score += s.attempts * 100;
  if (s.attempts === 0) score -= 50;
  score -= lead.score || 0; // data-quality / value score from the cleaner
  return score;
}

const CLAIM_TTL_MS = 45 * 60 * 1000; // a claim expires if a caller walks away

function nextLeadFor(caller) {
  const now = Date.now();
  // already holding one? give it back so a refresh doesn't lose their place
  const held = LEADS.filter((l) => {
    const s = stateFor(l.id);
    return (
      s.assignedTo === caller &&
      s.status === "IN_PROGRESS" &&
      isWorkable(l, now)
    );
  });
  if (held.length) return held[0];

  const pool = LEADS.filter((l) => {
    const s = stateFor(l.id);
    if (!isWorkable(l, now)) return false;
    if (
      s.assignedTo &&
      s.assignedTo !== caller &&
      s.claimedAt &&
      now - new Date(s.claimedAt).getTime() < CLAIM_TTL_MS
    )
      return false; // someone else is on it right now
    return true;
  });
  if (!pool.length) return null;
  pool.sort((a, b) => priorityScore(a, now) - priorityScore(b, now));
  const lead = pool[0];
  const s = stateFor(lead.id);
  s.assignedTo = caller;
  s.claimedAt = new Date().toISOString();
  s.status = "IN_PROGRESS";
  saveDB();
  return lead;
}

/* ------------------------------------------------------------------ */
/* Stats                                                               */
/* ------------------------------------------------------------------ */

function todayKey(d = new Date()) {
  return d.toISOString().slice(0, 10);
}

function computeStats() {
  const now = Date.now();
  const total = LEADS.length;
  const counts = {};
  let touched = 0,
    remaining = 0,
    hot = 0,
    callbacksDue = 0;

  for (const l of LEADS) {
    const s = stateFor(l.id);
    if (s.disposition) {
      counts[s.disposition] = (counts[s.disposition] || 0) + 1;
      touched++;
    }
    if (isWorkable(l, now)) remaining++;
    if (s.disposition && DISPOSITIONS[s.disposition]?.hot) hot++;
    if (s.callbackAt && new Date(s.callbackAt).getTime() <= now) callbacksDue++;
  }

  const today = todayKey();
  const perCaller = {};
  for (const c of db.calls) {
    const day = c.at.slice(0, 10);
    if (!perCaller[c.caller])
      perCaller[c.caller] = { caller: c.caller, today: 0, total: 0, hot: 0 };
    perCaller[c.caller].total++;
    if (day === today) perCaller[c.caller].today++;
    if (DISPOSITIONS[c.disposition]?.hot) perCaller[c.caller].hot++;
  }

  const callsToday = db.calls.filter((c) => c.at.slice(0, 10) === today).length;

  return {
    total,
    touched,
    remaining,
    hot,
    callbacksDue,
    callsToday,
    totalCalls: db.calls.length,
    counts,
    perCaller: Object.values(perCaller).sort((a, b) => b.today - a.today),
    connectRate: db.calls.length
      ? Math.round(
          (db.calls.filter((c) =>
            ["INTERESTED", "APPOINTMENT", "NOT_INTERESTED", "CALLBACK"].includes(
              c.disposition
            )
          ).length /
            db.calls.length) *
            100
        )
      : 0,
  };
}

/* ------------------------------------------------------------------ */
/* CSV export                                                          */
/* ------------------------------------------------------------------ */

function csvCell(v) {
  if (v === null || v === undefined) return "";
  const s = String(v);
  return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
}

function exportLeadsCSV() {
  const cols = [
    "id",
    "owner_name",
    "phone_primary",
    "phone_all",
    "property_type",
    "address",
    "area",
    "city",
    "size",
    "price",
    "source",
    "status",
    "disposition",
    "intent",
    "attempts",
    "last_called_at",
    "callback_at",
    "assigned_to",
    "notes",
  ];
  const rows = [cols.join(",")];
  for (const l of LEADS) {
    const s = stateFor(l.id);
    rows.push(
      [
        l.id,
        l.owner_name,
        (l.phones || [])[0] || "",
        (l.phones || []).join(" | "),
        l.property_type,
        l.address,
        l.area,
        l.city,
        l.size,
        l.price,
        l.source,
        s.status,
        s.disposition ? DISPOSITIONS[s.disposition]?.label : "",
        s.intent || "",
        s.attempts,
        s.lastCalledAt || "",
        s.callbackAt || "",
        s.assignedTo || "",
        s.notes || "",
      ]
        .map(csvCell)
        .join(",")
    );
  }
  return rows.join("\n");
}

function exportCallsCSV() {
  const cols = [
    "call_id",
    "at",
    "caller",
    "lead_id",
    "owner_name",
    "phone_dialled",
    "disposition",
    "intent",
    "callback_at",
    "notes",
  ];
  const rows = [cols.join(",")];
  for (const c of db.calls) {
    const l = LEADS_BY_ID.get(String(c.leadId)) || {};
    rows.push(
      [
        c.id,
        c.at,
        c.caller,
        c.leadId,
        l.owner_name || "",
        c.phone || "",
        DISPOSITIONS[c.disposition]?.label || c.disposition,
        c.intent || "",
        c.callbackAt || "",
        c.notes || "",
      ]
        .map(csvCell)
        .join(",")
    );
  }
  return rows.join("\n");
}

/* ------------------------------------------------------------------ */
/* HTTP                                                                */
/* ------------------------------------------------------------------ */

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".png": "image/png",
};

function send(res, code, body, headers = {}) {
  res.writeHead(code, {
    "Cache-Control": "no-store",
    "Access-Control-Allow-Origin": "*",
    ...headers,
  });
  res.end(body);
}
function sendJSON(res, code, obj) {
  send(res, code, JSON.stringify(obj), {
    "Content-Type": "application/json; charset=utf-8",
  });
}

function readBody(req) {
  return new Promise((resolve) => {
    let b = "";
    req.on("data", (c) => {
      b += c;
      if (b.length > 2e6) req.destroy();
    });
    req.on("end", () => {
      try {
        resolve(JSON.parse(b || "{}"));
      } catch {
        resolve({});
      }
    });
  });
}

/** Read a raw binary upload straight to disk (no multipart, no dependencies). */
function readRawToFile(req, dest, limit = 120e6) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (c) => {
      size += c.length;
      if (size > limit) {
        req.destroy();
        reject(new Error("File too large (max 120 MB)"));
        return;
      }
      chunks.push(c);
    });
    req.on("error", reject);
    req.on("end", () => {
      try {
        fs.mkdirSync(path.dirname(dest), { recursive: true });
        fs.writeFileSync(dest, Buffer.concat(chunks));
        resolve(size);
      } catch (e) {
        reject(e);
      }
    });
  });
}

const ALLOWED_UPLOAD_EXT = new Set([".pdf", ".csv", ".tsv", ".xlsx", ".xlsm", ".txt"]);

function publicLead(lead) {
  if (!lead) return null;
  return { ...lead, state: stateFor(lead.id) };
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || "localhost"}`);
  const p = url.pathname;

  if (req.method === "OPTIONS")
    return send(res, 204, "", {
      "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
    });

  try {
    /* ---------------- API ---------------- */
    if (p === "/api/bootstrap") {
      return sendJSON(res, 200, {
        meta: LEADS_META,
        dispositions: DISPOSITIONS,
        callers: db.callers,
        stats: computeStats(),
        maxAttempts: MAX_ATTEMPTS,
      });
    }

    if (p === "/api/caller" && req.method === "POST") {
      const { name } = await readBody(req);
      const clean = String(name || "").trim().slice(0, 40);
      if (!clean) return sendJSON(res, 400, { error: "Name required" });
      if (!db.callers.includes(clean)) {
        db.callers.push(clean);
        saveDB();
      }
      return sendJSON(res, 200, { ok: true, callers: db.callers });
    }

    if (p === "/api/next") {
      const caller = url.searchParams.get("caller") || "";
      if (!caller) return sendJSON(res, 400, { error: "caller required" });
      const lead = nextLeadFor(caller);
      return sendJSON(res, 200, {
        lead: publicLead(lead),
        stats: computeStats(),
      });
    }

    if (p === "/api/skip" && req.method === "POST") {
      const { leadId, caller } = await readBody(req);
      const s = stateFor(leadId);
      if (s.assignedTo === caller) {
        s.assignedTo = null;
        s.claimedAt = null;
        s.status = s.attempts > 0 ? "ATTEMPTED" : "NEW";
        // push it to the back of the queue for a bit
        s.callbackAt = new Date(Date.now() + 6 * 60 * 60 * 1000).toISOString();
      }
      saveDB();
      return sendJSON(res, 200, { ok: true });
    }

    if (p === "/api/log" && req.method === "POST") {
      const body = await readBody(req);
      const { leadId, caller, disposition, notes, intent, callbackAt, phone } =
        body;
      if (!LEADS_BY_ID.has(String(leadId)))
        return sendJSON(res, 404, { error: "Unknown lead" });
      if (!DISPOSITIONS[disposition])
        return sendJSON(res, 400, { error: "Unknown disposition" });

      const s = stateFor(leadId);
      s.attempts += 1;
      s.disposition = disposition;
      s.lastCalledAt = new Date().toISOString();
      s.assignedTo = null;
      s.claimedAt = null;
      if (intent) s.intent = intent;
      if (notes) s.notes = (s.notes ? s.notes + "\n" : "") + notes;
      s.callbackAt = callbackAt || null;
      s.status = DISPOSITIONS[disposition].terminal
        ? "DONE"
        : callbackAt
        ? "CALLBACK"
        : "ATTEMPTED";
      if (disposition === "NO_ANSWER" && !callbackAt) {
        // auto-retry in 3 hours, up to MAX_ATTEMPTS
        s.callbackAt = new Date(Date.now() + 3 * 3600 * 1000).toISOString();
        s.status = "CALLBACK";
      }

      db.calls.push({
        id: "c" + (db.calls.length + 1).toString().padStart(6, "0"),
        leadId: String(leadId),
        caller: caller || "unknown",
        at: new Date().toISOString(),
        disposition,
        intent: intent || null,
        notes: notes || "",
        callbackAt: s.callbackAt,
        phone: phone || (LEADS_BY_ID.get(String(leadId)).phones || [])[0] || "",
      });
      saveDB();
      return sendJSON(res, 200, { ok: true, stats: computeStats() });
    }

    if (p === "/api/upload" && req.method === "POST") {
      const rawName = url.searchParams.get("name") || "upload.pdf";
      const safe = path.basename(rawName).replace(/[^\w.\-() ]/g, "_").slice(-120);
      const ext = path.extname(safe).toLowerCase();
      if (!ALLOWED_UPLOAD_EXT.has(ext)) {
        return sendJSON(res, 400, {
          error: `Can't read "${ext || "that file type"}". Send a PDF, XLSX, CSV or TXT.`,
        });
      }
      const incoming = path.join(DATA_DIR, "incoming");
      const dest = path.join(incoming, safe);
      let bytes;
      try {
        bytes = await readRawToFile(req, dest);
      } catch (e) {
        return sendJSON(res, 400, { error: e.message });
      }
      if (!bytes) return sendJSON(res, 400, { error: "Empty file received." });

      // keep a copy of the current campaign before we swap the list out
      if (fs.existsSync(DB_FILE)) {
        fs.copyFileSync(DB_FILE, DB_FILE + "." + Date.now() + ".bak");
      }

      const { execFile } = require("child_process");
      const script = path.join(ROOT, "tools", "clean_leads.py");
      const out = await new Promise((resolve) =>
        execFile("python3", [script, dest, "--out", LEADS_FILE,
                             "--report", path.join(DATA_DIR, "clean-report.json")],
          { cwd: ROOT, timeout: 300000, maxBuffer: 20e6 },
          (err, stdout, stderr) => resolve({ err, stdout, stderr }))
      );
      if (out.err) {
        return sendJSON(res, 500, {
          error: "Could not read that file.",
          detail: (out.stderr || out.stdout || String(out.err)).slice(-1500),
        });
      }

      loadLeads();
      let report = {};
      try {
        report = JSON.parse(fs.readFileSync(path.join(DATA_DIR, "clean-report.json"), "utf8"));
      } catch {}

      return sendJSON(res, 200, {
        ok: true,
        file: safe,
        bytes,
        log: out.stdout.trim(),
        count: LEADS.length,
        meta: LEADS_META,
        dropped: report.dropped || 0,
        duplicatesMerged: report.duplicates_merged || 0,
        droppedSample: (report.dropped_rows || []).slice(0, 15),
        preview: LEADS.slice(0, 12).map((l) => ({
          id: l.id, owner_name: l.owner_name, phone: (l.phones || [])[0] || "",
          extraPhones: (l.phones || []).length - 1,
          property_type: l.property_type, size: l.size, price: l.price,
          area: l.area, city: l.city, quality: l.quality, flags: l.flags,
        })),
        stats: computeStats(),
      });
    }

    if (p === "/api/leads") {
      const q = (url.searchParams.get("q") || "").toLowerCase();
      const filter = url.searchParams.get("filter") || "all";
      const now = Date.now();
      let out = LEADS.map(publicLead);
      if (filter === "hot")
        out = out.filter((l) => DISPOSITIONS[l.state.disposition]?.hot);
      else if (filter === "callback")
        out = out.filter((l) => l.state.callbackAt && l.state.status === "CALLBACK");
      else if (filter === "untouched")
        out = out.filter((l) => l.state.attempts === 0);
      else if (filter === "done")
        out = out.filter(
          (l) => DISPOSITIONS[l.state.disposition]?.terminal
        );
      if (q)
        out = out.filter((l) =>
          JSON.stringify(l).toLowerCase().includes(q)
        );
      return sendJSON(res, 200, {
        leads: out.slice(0, 500),
        shown: Math.min(out.length, 500),
        matched: out.length,
      });
    }

    if (p === "/api/lead") {
      const lead = LEADS_BY_ID.get(String(url.searchParams.get("id")));
      if (!lead) return sendJSON(res, 404, { error: "not found" });
      const calls = db.calls.filter((c) => c.leadId === String(lead.id));
      return sendJSON(res, 200, { lead: publicLead(lead), calls });
    }

    if (p === "/api/stats")
      return sendJSON(res, 200, {
        stats: computeStats(),
        calls: db.calls.slice(-100).reverse(),
      });

    if (p === "/api/export/leads.csv")
      return send(res, 200, "\uFEFF" + exportLeadsCSV(), {
        "Content-Type": "text/csv; charset=utf-8",
        "Content-Disposition": 'attachment; filename="leads-progress.csv"',
      });

    if (p === "/api/export/calls.csv")
      return send(res, 200, "\uFEFF" + exportCallsCSV(), {
        "Content-Type": "text/csv; charset=utf-8",
        "Content-Disposition": 'attachment; filename="call-log.csv"',
      });

    /* ---------------- static ---------------- */
    let rel = p === "/" ? "/index.html" : p;
    const file = path.join(PUBLIC_DIR, path.normalize(rel).replace(/^(\.\.[/\\])+/, ""));
    if (!file.startsWith(PUBLIC_DIR)) return send(res, 403, "forbidden");
    if (fs.existsSync(file) && fs.statSync(file).isFile()) {
      return send(res, 200, fs.readFileSync(file), {
        "Content-Type": MIME[path.extname(file)] || "application/octet-stream",
      });
    }
    return send(res, 404, "Not found");
  } catch (err) {
    console.error(err);
    return sendJSON(res, 500, { error: String(err && err.message) });
  }
});

loadDB();
loadLeads();

process.on("SIGTERM", () => {
  saveNow();
  process.exit(0);
});
process.on("SIGINT", () => {
  saveNow();
  process.exit(0);
});

server.listen(PORT, "0.0.0.0", () => {
  console.log("=".repeat(60));
  console.log("  COLD CALL COMMAND CENTER");
  console.log("=".repeat(60));
  console.log(`  Leads loaded : ${LEADS.length}`);
  console.log(`  Calls logged : ${db.calls.length}`);
  console.log(`  Listening on : http://0.0.0.0:${PORT}`);
  console.log("=".repeat(60));
});
