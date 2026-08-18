/* Cold Call Command Center — front-end */

const DISPO_UI = {
  INTERESTED:     { emo: "🔥", bg: "#ea580c", help: "They want to hear more. Big win — log it." },
  APPOINTMENT:    { emo: "🤝", bg: "#16a34a", help: "They agreed to meet or a serious next step. Best outcome." },
  CALLBACK:       { emo: "⏰", bg: "#2563eb", help: "They said call me later. Pick when — it comes back automatically." },
  NO_ANSWER:      { emo: "📵", bg: "#475569", help: "Rang out / voicemail. We auto-retry in 3 hours." },
  BUSY:           { emo: "✂️", bg: "#64748b", help: "They picked up and cut it. We'll try again later." },
  NOT_INTERESTED: { emo: "🚫", bg: "#7f1d1d", help: "A clear no. Closed — we won't call again." },
  WRONG_NUMBER:   { emo: "❓", bg: "#78350f", help: "Not the person we wanted. Closed." },
  NUMBER_DEAD:    { emo: "☠️", bg: "#3f3f46", help: "Number off, doesn't exist, or invalid. Closed." },
  DNC:            { emo: "⛔", bg: "#450a0a", help: "They demanded no more calls. NEVER dial again — legal." },
};
const DISPO_ORDER = [
  "APPOINTMENT","INTERESTED","CALLBACK","NO_ANSWER",
  "BUSY","NOT_INTERESTED","WRONG_NUMBER","NUMBER_DEAD","DNC",
];
const NEEDS_CALLBACK = new Set(["CALLBACK", "BUSY"]);
const NEEDS_INTENT   = new Set(["APPOINTMENT", "INTERESTED", "CALLBACK"]);

const $  = (s) => document.querySelector(s);
const $$ = (s) => Array.from(document.querySelectorAll(s));
const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;" }[c]));

let CALLER = localStorage.getItem("ccc_caller") || "";
let BOOT = null;
let LANG = localStorage.getItem("ccc_lang") || "ur";

function toast(msg, err) {
  const t = $("#toast");
  t.textContent = msg;
  t.className = "toast show" + (err ? " err" : "");
  clearTimeout(t._t);
  t._t = setTimeout(() => (t.className = "toast"), 2400);
}
const api = (u, o) => fetch(u, o).then((r) => r.json());

/* ================= GATE ================= */
async function boot() {
  BOOT = await api("/api/bootstrap");
  renderChips();
  renderHelpDispo();
  if (BOOT.meta && BOOT.meta.demo) {
    $("#demoBanner").classList.remove("hidden");
    $("#gateUpload").classList.remove("hidden");
  }
  if (CALLER) enterApp();
}

function renderChips() {
  const box = $("#callerChips");
  box.innerHTML = (BOOT.callers || [])
    .map((c) => `<button class="chip" data-name="${esc(c)}">${esc(c)}</button>`).join("");
  $$("#callerChips .chip").forEach((b) =>
    b.onclick = () => { $("#callerInput").value = b.dataset.name; start(); });
}

async function start() {
  const name = $("#callerInput").value.trim();
  if (!name) return toast("Please type your name first", true);
  const r = await api("/api/caller", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
  if (r.error) return toast(r.error, true);
  CALLER = name;
  localStorage.setItem("ccc_caller", name);
  BOOT.callers = r.callers;
  enterApp();
}

function enterApp() {
  $("#gate").classList.add("hidden");
  $("#app").classList.remove("hidden");
  $("#whoName").textContent = CALLER;
  Dialer.loadNext();
}

/* ================= TABS ================= */
function showView(v) {
  $$(".tab").forEach((t) => t.classList.toggle("active", t.dataset.view === v));
  ["call","list","dash","help"].forEach((k) =>
    $("#view-" + k).classList.toggle("hidden", k !== v));
  if (v === "list") List.load();
  if (v === "dash") Dash.load();
}

/* ================= PROGRESS ================= */
function paintStats(st) {
  if (!st) return;
  const mine = (st.perCaller || []).find((p) => p.caller === CALLER);
  $("#psToday").textContent     = mine ? mine.today : 0;
  $("#psRemaining").textContent = st.remaining;
  $("#psHot").textContent       = st.hot;
  $("#psCallbacks").textContent = st.callbacksDue;
  const pct = st.total ? Math.round(((st.total - st.remaining) / st.total) * 100) : 0;
  $("#psBarFill").style.width = pct + "%";
}

/* ================= DIALER ================= */
const Dialer = {
  lead: null, dispo: null, intent: null, callbackAt: null,

  async loadNext() {
    this.reset();
    const r = await api("/api/next?caller=" + encodeURIComponent(CALLER));
    paintStats(r.stats);
    if (!r.lead) {
      $("#callCard").classList.add("hidden");
      $("#emptyState").classList.remove("hidden");
      $("#emptyMsg").textContent = r.stats.total === 0
        ? "No leads have been imported yet. Ask your manager to load the list."
        : "Every lead has been worked or is scheduled for later. Check the Dashboard.";
      return;
    }
    $("#emptyState").classList.add("hidden");
    $("#callCard").classList.remove("hidden");
    this.lead = r.lead;
    this.render();
  },

  reset() {
    this.dispo = null; this.intent = null; this.callbackAt = null;
    $("#notes").value = "";
    $("#cbExact").value = "";
    $("#followup").classList.add("hidden");
    $("#saveBtn").disabled = true;
    $$(".dispo,.intent,.cb").forEach((b) => b.classList.remove("sel"));
  },

  render() {
    const l = this.lead, s = l.state;

    $("#leadName").textContent = l.owner_name || "(name not in the data)";
    $("#leadSub").textContent  = [l.property_type, l.area, l.city].filter(Boolean).join(" · ") || "—";
    $("#leadId").textContent   = l.id;

    /* flags */
    const flags = [];
    if (s.attempts > 0) flags.push(["flag-warn", `ATTEMPT #${s.attempts + 1}`]);
    else flags.push(["flag-info", "NEVER CALLED"]);
    if (s.disposition === "INTERESTED") flags.push(["flag-hot", "🔥 WAS INTERESTED"]);
    if (s.callbackAt) flags.push(["flag-warn", "⏰ CALLBACK DUE"]);
    if (s.intent) flags.push(["flag-good", "WANTS TO " + s.intent]);
    (l.flags || []).forEach((f) => flags.push(["flag-info", f]));
    if ((l.phones || []).length > 1) flags.push(["flag-info", `${l.phones.length} NUMBERS`]);
    $("#leadFlags").innerHTML = flags
      .map(([c, t]) => `<span class="flag ${c}">${esc(t)}</span>`).join("");

    /* phones */
    const phones = l.phones || [];
    const main = phones[0] || "";
    $("#phoneMain").textContent = main || "NO NUMBER";
    $("#phoneMain").href = "tel:" + main.replace(/\s/g, "");
    $("#callBtn").href   = "tel:" + main.replace(/\s/g, "");
    $("#waBtn").href     = "https://wa.me/" + main.replace(/[^\d]/g, "");
    $("#otherPhones").innerHTML = phones.slice(1)
      .map((p) => `<a class="other-phone" href="tel:${esc(p.replace(/\s/g,""))}">☎ ${esc(p)} (backup)</a>`)
      .join("");

    /* details — show whatever the data actually has */
    const rows = [
      ["Property", l.property_type], ["Size", l.size], ["Asking price", l.price],
      ["Area / Society", l.area], ["City", l.city], ["Source", l.source],
    ].filter(([, v]) => v);
    if (l.address) rows.push(["Address", l.address, true]);
    Object.entries(l.extra || {}).forEach(([k, v]) => v && rows.push([k, v]));
    if (l.note_from_data) rows.push(["Note from the list", l.note_from_data, true]);
    $("#detailGrid").innerHTML = rows.map(([k, v, wide]) =>
      `<div class="detail${wide ? " wide" : ""}"><div class="detail-k">${esc(k)}</div>
       <div class="detail-v">${esc(v)}</div></div>`).join("") ||
      `<div class="detail wide"><div class="detail-v">No extra details in the data.</div></div>`;

    /* history */
    if (s.attempts > 0 || s.notes) {
      $("#historyBox").classList.remove("hidden");
      const bits = [];
      if (s.lastCalledAt) bits.push(`<div class="hist-row">Last called: <b>${new Date(s.lastCalledAt).toLocaleString()}</b></div>`);
      if (s.disposition) bits.push(`<div class="hist-row">Result: <b>${esc(BOOT.dispositions[s.disposition]?.label || s.disposition)}</b></div>`);
      if (s.notes) bits.push(`<div class="hist-row">Notes: <b>${esc(s.notes)}</b></div>`);
      $("#historyList").innerHTML = bits.join("");
    } else $("#historyBox").classList.add("hidden");

    this.renderScript();
  },

  renderScript() {
    const l = this.lead;
    const fill = (t) => t
      .replace(/{NAME}/g, esc(l.owner_name || "sir"))
      .replace(/{AREA}/g, esc(l.area || l.city || "your area"))
      .replace(/{CALLER}/g, esc(CALLER))
      .replace(/{AGENCY}/g, esc(AGENCY_NAME))
      .replace(/{PROPERTY}/g, esc([l.size, l.property_type].filter(Boolean).join(" ") || "property"));

    $("#scriptBody").innerHTML = SCRIPTS[LANG].map((s) => `
      <div class="sline">
        <span class="stag">${esc(s.tag)}</span>
        ${s.them ? `<div class="sthem">They say: ${esc(s.them)}</div>` : ""}
        <div class="stext">${fill(s.text)}</div>
        ${s.note ? `<div class="snote">👉 ${fill(s.note)}</div>` : ""}
      </div>`).join("");

    $("#objectionBody").innerHTML = OBJECTIONS[LANG].map((o) => `
      <div class="obj"><div class="obj-q">${esc(o.q)}</div>
      <div class="obj-a">${fill(o.a)}</div></div>`).join("");

    $$(".stog").forEach((b) => b.classList.toggle("active", b.dataset.lang === LANG));
  },

  pick(code) {
    this.dispo = code;
    $$(".dispo").forEach((b) => b.classList.toggle("sel", b.dataset.code === code));
    const needCb = NEEDS_CALLBACK.has(code);
    const needIn = NEEDS_INTENT.has(code);
    $("#followup").classList.toggle("hidden", !(needCb || needIn));
    $("#intentRow").classList.toggle("hidden", !needIn);
    $("#cbRow").classList.toggle("hidden", !needCb);
    $("#cbExact").classList.toggle("hidden", !needCb);
    $("#cbLabel").classList.toggle("hidden", !needCb);
    $("#saveBtn").disabled = false;
    $("#saveBtn").textContent = code === "DNC"
      ? "SAVE — NEVER CALL AGAIN →" : "SAVE & NEXT LEAD →";
  },

  async save() {
    if (!this.dispo) return toast("Pick what happened first", true);
    let cb = this.callbackAt;
    if ($("#cbExact").value && NEEDS_CALLBACK.has(this.dispo))
      cb = new Date($("#cbExact").value).toISOString();
    $("#saveBtn").disabled = true;
    const r = await api("/api/log", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        leadId: this.lead.id, caller: CALLER, disposition: this.dispo,
        notes: $("#notes").value.trim(), intent: this.intent,
        callbackAt: cb, phone: (this.lead.phones || [])[0] || "",
      }),
    });
    if (r.error) { $("#saveBtn").disabled = false; return toast(r.error, true); }
    toast("✅ Saved — loading next lead");
    paintStats(r.stats);
    this.loadNext();
  },

  async skip() {
    await api("/api/skip", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ leadId: this.lead.id, caller: CALLER }),
    });
    toast("Skipped");
    this.loadNext();
  },
};

/* ================= LIST ================= */
const List = {
  filter: "all", q: "",
  async load() {
    const r = await api(`/api/leads?filter=${this.filter}&q=${encodeURIComponent(this.q)}`);
    $("#listCount").textContent =
      `Showing ${r.shown} of ${r.matched} matching leads` + (r.matched > 500 ? " (refine your search to see more)" : "");
    $("#leadTable").innerHTML = `
      <thead><tr><th>#</th><th>Name</th><th>Phone</th><th>Property</th><th>Area</th>
      <th>Status</th><th>Tries</th><th>Caller</th><th>Notes</th></tr></thead>
      <tbody>${r.leads.map((l) => {
        const s = l.state, d = s.disposition;
        const ui = DISPO_UI[d];
        return `<tr>
          <td>${esc(l.id)}</td>
          <td><b>${esc(l.owner_name || "—")}</b></td>
          <td>${(l.phones||[]).map(p=>`<a class="tnum" href="tel:${esc(p)}">${esc(p)}</a>`).join("<br>")||"—"}</td>
          <td>${esc([l.size,l.property_type].filter(Boolean).join(" ")||"—")}</td>
          <td>${esc(l.area || l.city || "—")}</td>
          <td>${d ? `<span class="pill" style="background:${ui?.bg||"#334155"};color:#fff">${ui?.emo||""} ${esc(BOOT.dispositions[d]?.label||d)}</span>`
                  : `<span class="pill" style="background:#1e3a5f;color:#93c5fd">Not called</span>`}</td>
          <td>${s.attempts}</td>
          <td>${esc(s.assignedTo || "—")}</td>
          <td style="max-width:280px;font-size:13px;color:#93a3c0">${esc(s.notes || "")}</td>
        </tr>`;
      }).join("")}</tbody>`;
  },
};

/* ================= DASHBOARD ================= */
const Dash = {
  async load() {
    const r = await api("/api/stats");
    const st = r.stats;
    paintStats(st);

    $("#dashCards").innerHTML = [
      ["Total leads", st.total, "#eaf0fb"],
      ["Calls made", st.totalCalls, "#3b82f6"],
      ["Calls today", st.callsToday, "#22c55e"],
      ["🔥 Hot / booked", st.hot, "#f97316"],
      ["Still to call", st.remaining, "#fcd34d"],
      ["Callbacks due", st.callbacksDue, "#a78bfa"],
      ["Contact rate", st.connectRate + "%", "#22c55e"],
    ].map(([k, v, c]) => `<div class="dcard"><b style="color:${c}">${v}</b><span>${k}</span></div>`).join("");

    $("#teamTable").innerHTML = `
      <thead><tr><th>Caller</th><th>Today</th><th>All time</th><th>🔥 Hot</th></tr></thead>
      <tbody>${(st.perCaller.length ? st.perCaller : [{caller:"— no calls yet —",today:0,total:0,hot:0}])
        .map(p => `<tr><td><b>${esc(p.caller)}</b></td><td>${p.today}</td><td>${p.total}</td>
        <td style="color:#f97316;font-weight:800">${p.hot}</td></tr>`).join("")}</tbody>`;

    const max = Math.max(1, ...Object.values(st.counts));
    const untouched = st.total - st.touched;
    $("#dispoBars").innerHTML =
      [["Not called yet", untouched, "#1e3a5f"]]
        .concat(DISPO_ORDER.filter(c => st.counts[c])
          .map(c => [BOOT.dispositions[c].label, st.counts[c], DISPO_UI[c].bg]))
        .map(([k, v, c]) => `<div class="bar-row">
          <div class="bar-top"><span>${esc(k)}</span><b>${v}</b></div>
          <div class="bar"><i style="width:${Math.round((v/Math.max(max,untouched))*100)}%;background:${c}"></i></div>
        </div>`).join("");

    $("#recentTable").innerHTML = `
      <thead><tr><th>When</th><th>Caller</th><th>Lead</th><th>Result</th><th>Notes</th></tr></thead>
      <tbody>${r.calls.map(c => `<tr>
        <td style="white-space:nowrap">${new Date(c.at).toLocaleString()}</td>
        <td>${esc(c.caller)}</td><td>${esc(c.leadId)}</td>
        <td><span class="pill" style="background:${DISPO_UI[c.disposition]?.bg||"#334155"};color:#fff">
          ${esc(BOOT.dispositions[c.disposition]?.label || c.disposition)}</span></td>
        <td style="font-size:13px;color:#93a3c0">${esc(c.notes||"")}</td></tr>`).join("")
        || `<tr><td colspan="5" style="color:#6b7c9c">No calls logged yet.</td></tr>`}</tbody>`;
  },
};

/* ================= HELP ================= */
function renderHelpDispo() {
  $("#helpDispo").innerHTML = DISPO_ORDER.map((c) => `
    <div class="hd" style="border-color:${DISPO_UI[c].bg}">
      <b>${DISPO_UI[c].emo} ${esc(BOOT.dispositions[c].label)}</b>
      <span>${esc(DISPO_UI[c].help)}</span></div>`).join("");
}

/* ================= WIRE UP ================= */
function buildDispoButtons() {
  $("#dispoGrid").innerHTML = DISPO_ORDER.map((c, i) => `
    <button class="dispo${c === "DNC" ? " wide" : ""}" data-code="${c}"
      style="background:${DISPO_UI[c].bg}" title="${esc(DISPO_UI[c].help)}">
      <span class="emo">${DISPO_UI[c].emo}</span>${esc(DISPO_UI[c].label || "")}
    </button>`).join("");
}

document.addEventListener("DOMContentLoaded", async () => {
  $("#startBtn").onclick = start;
  $("#callerInput").addEventListener("keydown", (e) => { if (e.key === "Enter") start(); });
  $("#switchUser").onclick = () => {
    localStorage.removeItem("ccc_caller"); CALLER = "";
    $("#app").classList.add("hidden"); $("#gate").classList.remove("hidden");
    $("#callerInput").value = "";
  };
  $$(".tab").forEach((t) => t.onclick = () => showView(t.dataset.view));
  Uploader.wire();

  await boot();

  // labels need BOOT
  Object.keys(DISPO_UI).forEach((c) => DISPO_UI[c].label = BOOT.dispositions[c].label);
  buildDispoButtons();
  renderHelpDispo();

  $$(".dispo").forEach((b) => b.onclick = () => Dialer.pick(b.dataset.code));
  $("#saveBtn").onclick = () => Dialer.save();
  $("#skipBtn").onclick = () => Dialer.skip();
  $("#copyBtn").onclick = () => {
    navigator.clipboard?.writeText($("#phoneMain").textContent);
    toast("Number copied");
  };
  $$(".intent").forEach((b) => b.onclick = () => {
    Dialer.intent = b.dataset.intent;
    $$(".intent").forEach((x) => x.classList.toggle("sel", x === b));
  });
  $$(".cb").forEach((b) => b.onclick = () => {
    Dialer.callbackAt = new Date(Date.now() + (+b.dataset.mins) * 60000).toISOString();
    $$(".cb").forEach((x) => x.classList.toggle("sel", x === b));
    $("#cbExact").value = "";
  });
  $$(".stog").forEach((b) => b.onclick = () => {
    LANG = b.dataset.lang; localStorage.setItem("ccc_lang", LANG);
    if (Dialer.lead) Dialer.renderScript();
  });
  $$(".filt").forEach((b) => b.onclick = () => {
    List.filter = b.dataset.filter;
    $$(".filt").forEach((x) => x.classList.toggle("active", x === b));
    List.load();
  });
  let sT; $("#searchBox").addEventListener("input", (e) => {
    clearTimeout(sT); List.q = e.target.value;
    sT = setTimeout(() => List.load(), 250);
  });
});


/* ================= UPLOADER ================= */
const Uploader = {
  busy: false,

  wire() {
    const dz = $("#dropZone"), fi = $("#fileInput");
    if (!dz) return;
    dz.onclick = () => fi.click();
    fi.onchange = () => { if (fi.files[0]) this.send(fi.files[0]); };
    ["dragenter", "dragover"].forEach((e) =>
      dz.addEventListener(e, (ev) => { ev.preventDefault(); dz.classList.add("over"); }));
    ["dragleave", "drop"].forEach((e) =>
      dz.addEventListener(e, (ev) => { ev.preventDefault(); dz.classList.remove("over"); }));
    dz.addEventListener("drop", (ev) => {
      const f = ev.dataTransfer?.files?.[0];
      if (f) this.send(f);
    });
    // let the manager re-open the dropzone from the dashboard
    const btn = $("#dashLoadBtn");
    if (btn) btn.onclick = () => {
      $("#app").classList.add("hidden");
      $("#gate").classList.remove("hidden");
      $("#gateUpload").classList.remove("hidden");
      $("#gateUpload").scrollIntoView?.({ behavior: "smooth" });
    };
  },

  status(msg, cls) {
    const el = $("#dzStatus");
    el.textContent = msg;
    el.className = "dz-status" + (cls ? " " + cls : "");
  },

  async send(file) {
    if (this.busy) return;
    this.busy = true;
    $("#dzResult").classList.add("hidden");
    const mb = (file.size / 1048576).toFixed(1);
    this.status(`Uploading ${file.name} (${mb} MB)… then reading it. Large PDFs can take a minute.`, "busy");
    try {
      const res = await fetch("/api/upload?name=" + encodeURIComponent(file.name),
                              { method: "POST", body: file });
      const r = await res.json();
      if (r.error) {
        this.status("❌ " + r.error, "bad");
        if (r.detail) {
          $("#dzResult").classList.remove("hidden");
          $("#dzResult").innerHTML = `<h4>What went wrong</h4><pre style="white-space:pre-wrap;font-size:11px;color:#fca5a5">${esc(r.detail)}</pre>`;
        }
        return;
      }
      this.status(`✅ ${r.count} callable leads loaded from ${r.file}`, "good");
      this.showResult(r);
      BOOT.meta = r.meta || {};
      $("#demoBanner").classList.add("hidden");
      paintStats(r.stats);
    } catch (e) {
      this.status("❌ Upload failed: " + e.message, "bad");
    } finally {
      this.busy = false;
    }
  },

  showResult(r) {
    const qc = { A: "#166534", B: "#854d0e", C: "#7f1d1d" };
    const box = $("#dzResult");
    box.classList.remove("hidden");
    box.innerHTML = `
      <h4>First ${r.preview.length} leads — check these look right</h4>
      <table class="dz-tbl">
        <tr><th>#</th><th>Name</th><th>Phone</th><th>Property</th><th>Area</th><th>Grade</th></tr>
        ${r.preview.map(p => `<tr>
          <td>${esc(p.id)}</td>
          <td>${esc(p.owner_name || "—")}</td>
          <td>${esc(p.phone)}${p.extraPhones > 0 ? ` <span style="color:#6b7c9c">+${p.extraPhones}</span>` : ""}</td>
          <td>${esc([p.size, p.property_type].filter(Boolean).join(" ") || "—")}</td>
          <td>${esc(p.area || p.city || "—")}</td>
          <td><span class="q" style="background:${qc[p.quality] || "#334155"};color:#fff">${esc(p.quality)}</span></td>
        </tr>`).join("")}
      </table>
      ${r.duplicatesMerged ? `<div class="dz-drop-note" style="color:#93c5fd">🔁 ${r.duplicatesMerged} duplicate row(s) merged — the same person listed more than once is now a single lead.</div>` : ""}
      ${r.dropped ? `<div class="dz-drop-note">⚠️ ${r.dropped} row(s) dropped — no phone number could be found in them (page headers, totals, blank rows). Full list in data/clean-report.json.</div>` : ""}
      <div class="dz-actions">
        <button class="btn btn-green" onclick="location.reload()">Looks right — START CALLING</button>
      </div>`;
  },
};
