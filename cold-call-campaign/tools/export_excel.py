#!/usr/bin/env python3
"""
EXCEL BUILDER — turns the live campaign into one formatted workbook.

  python3 tools/export_excel.py

Produces exports/Cold-Call-Campaign.xlsx with 4 sheets:
  1. START HERE   – instructions in plain English
  2. Call Sheet   – printable, one row per lead, tick-box columns
  3. Call Log     – every single dial, who made it, what happened
  4. Summary      – counts per outcome + per caller
"""

import json, os, sys
from datetime import datetime, timezone, timedelta

try:
    from openpyxl import Workbook
    from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
    from openpyxl.utils import get_column_letter
    from openpyxl.worksheet.datavalidation import DataValidation
except ImportError:
    sys.exit("pip install openpyxl")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PKT = timezone(timedelta(hours=5))  # Pakistan Standard Time

DISPO_LABEL = {
    "APPOINTMENT": "Appointment Booked", "INTERESTED": "Interested / Hot",
    "CALLBACK": "Call Back Later", "NO_ANSWER": "No Answer",
    "BUSY": "Busy / Cut the call", "NOT_INTERESTED": "Not Interested",
    "WRONG_NUMBER": "Wrong Number", "NUMBER_DEAD": "Number Off / Invalid",
    "DNC": "Do Not Call Again",
}
DISPO_FILL = {
    "APPOINTMENT": "C6EFCE", "INTERESTED": "FFE0B2", "CALLBACK": "BBDEFB",
    "NO_ANSWER": "E0E0E0", "BUSY": "ECEFF1", "NOT_INTERESTED": "FFCDD2",
    "WRONG_NUMBER": "FFE0B2", "NUMBER_DEAD": "E0E0E0", "DNC": "EF9A9A",
}

HEAD_FILL = PatternFill("solid", fgColor="1F3864")
HEAD_FONT = Font(bold=True, color="FFFFFF", size=11)
TITLE_FONT = Font(bold=True, size=16, color="1F3864")
THIN = Side(style="thin", color="BFBFBF")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)


def load(name, fallback):
    p = os.path.join(ROOT, "data", name)
    try:
        with open(p, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return fallback


def pkt(iso):
    if not iso:
        return ""
    try:
        return (datetime.fromisoformat(iso.replace("Z", "+00:00"))
                .astimezone(PKT).strftime("%d %b %Y, %I:%M %p"))
    except Exception:
        return iso


def style_header(ws, row=1):
    for c in ws[row]:
        if c.value is None:
            continue
        c.fill, c.font = HEAD_FILL, HEAD_FONT
        c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
    # NOTE: use the string form — ws.cell() would materialise an empty row
    # and push every appended data row down by one.
    ws.freeze_panes = f"A{row + 1}"


def autosize(ws, maxw=46, minw=8):
    for col in ws.columns:
        letter = get_column_letter(col[0].column)
        if ws.column_dimensions[letter].width:
            continue
        longest = max((len(str(c.value)) for c in col if c.value is not None), default=0)
        ws.column_dimensions[letter].width = max(minw, min(maxw, longest + 3))


def main():
    leads_doc = load("leads.json", {"leads": [], "meta": {}})
    leads = leads_doc.get("leads", leads_doc if isinstance(leads_doc, list) else [])
    db = load("campaign-db.json", {"leadState": {}, "calls": [], "callers": []})
    state, calls = db.get("leadState", {}), db.get("calls", [])

    wb = Workbook()

    # ---------------- 1. START HERE ----------------
    ws = wb.active
    ws.title = "START HERE"
    ws.column_dimensions["A"].width = 4
    ws.column_dimensions["B"].width = 112
    lines = [
        ("t", "COLD CALL CAMPAIGN — READ THIS FIRST"),
        ("", ""),
        ("h", "What is this file?"),
        ("p", "A snapshot of your calling campaign, taken " + datetime.now(PKT).strftime("%d %B %Y at %I:%M %p") + " (Pakistan time)."),
        ("p", "It has 4 tabs at the bottom. Click them to switch."),
        ("", ""),
        ("h", "The tabs"),
        ("p", "• Call Sheet  – every person to call, best leads at the top. Print it or work off the screen."),
        ("p", "• Call Log    – a permanent record of every single call anyone made."),
        ("p", "• Summary     – the scoreboard. How many calls, how many hot leads, who did what."),
        ("", ""),
        ("h", "How to actually make the calls"),
        ("p", "Use the web app — it is far easier and it saves everything automatically."),
        ("p", "Run:  node server.js     then open the link it shows you."),
        ("p", "This spreadsheet is the backup / the manager's view. The app is where the work happens."),
        ("", ""),
        ("h", "If you are working from this sheet on paper"),
        ("p", "1. Go top to bottom. The list is already sorted — best leads first."),
        ("p", "2. Call the number in the 'Phone' column."),
        ("p", "3. In the 'Outcome' column pick from the dropdown."),
        ("p", "4. Write anything useful in 'Notes'. Write the callback time in 'Call Back On'."),
        ("p", "5. Give the sheet back to the manager at the end of the day."),
        ("", ""),
        ("h", "The rules — do not break these"),
        ("p", "• Only call between 11:00 AM and 8:00 PM. Never before 10 AM, never after 9 PM."),
        ("p", "• If someone says 'do not call me again' → mark Do Not Call Again. Never dial them again."),
        ("p", "• Never argue, never lie about a property, never promise a price."),
        ("p", "• A 'No' is fine. Say thank you and move to the next name."),
        ("", ""),
        ("h", "What the outcomes mean"),
    ]
    for code, label in DISPO_LABEL.items():
        lines.append(("p", f"• {label}"))

    r = 1
    for kind, text in lines:
        c = ws.cell(row=r, column=2, value=text)
        if kind == "t":
            c.font = TITLE_FONT
        elif kind == "h":
            c.font = Font(bold=True, size=12, color="C00000")
        else:
            c.font = Font(size=11)
            c.alignment = Alignment(wrap_text=True, vertical="top")
        r += 1

    # ---------------- 2. Call Sheet ----------------
    ws = wb.create_sheet("Call Sheet")
    cols = ["#", "Owner Name", "Phone (dial this)", "Backup Phone", "Property", "Size",
            "Asking Price", "Area / Society", "City", "Grade", "Warnings",
            "Outcome", "Buy / Sell", "Tries", "Last Called", "Call Back On",
            "Called By", "Notes"]
    ws.append(cols)
    style_header(ws)

    for i, l in enumerate(leads, 1):
        s = state.get(str(l.get("id")), {})
        d = s.get("disposition")
        phones = l.get("phones", [])
        ws.append([
            l.get("id", i),
            l.get("owner_name") or "(no name — ask on the call)",
            phones[0] if phones else "",
            " / ".join(phones[1:]),
            l.get("property_type", ""), l.get("size", ""), l.get("price", ""),
            l.get("area", ""), l.get("city", ""), l.get("quality", ""),
            "; ".join(l.get("flags", [])),
            DISPO_LABEL.get(d, ""), s.get("intent", "") or "",
            s.get("attempts", 0), pkt(s.get("lastCalledAt")), pkt(s.get("callbackAt")),
            s.get("assignedTo", "") or "", s.get("notes", "") or "",
        ])
        row = ws.max_row
        if d and d in DISPO_FILL:
            ws.cell(row=row, column=12).fill = PatternFill("solid", fgColor=DISPO_FILL[d])
        # grade colour
        g = l.get("quality")
        ws.cell(row=row, column=10).fill = PatternFill(
            "solid", fgColor={"A": "C6EFCE", "B": "FFEB9C", "C": "FFC7CE"}.get(g, "FFFFFF"))
        ws.cell(row=row, column=3).font = Font(bold=True, size=12, color="006100")
        for c in ws[row]:
            c.border = BORDER
            c.alignment = Alignment(vertical="top", wrap_text=c.column in (11, 18))

    if leads:
        dv = DataValidation(type="list",
                            formula1='"' + ",".join(DISPO_LABEL.values()) + '"',
                            allow_blank=True, showDropDown=False)
        ws.add_data_validation(dv)
        dv.add(f"L2:L{ws.max_row}")
        dv2 = DataValidation(type="list", formula1='"SELL,BUY,BOTH,NONE"', allow_blank=True)
        ws.add_data_validation(dv2)
        dv2.add(f"M2:M{ws.max_row}")
        ws.auto_filter.ref = f"A1:R{ws.max_row}"
    autosize(ws)
    ws.column_dimensions["R"].width = 46
    ws.column_dimensions["K"].width = 26
    ws.print_title_rows = "1:1"

    # ---------------- 3. Call Log ----------------
    ws = wb.create_sheet("Call Log")
    ws.append(["Call #", "When (PKT)", "Caller", "Lead #", "Owner Name",
               "Number Dialled", "Outcome", "Buy / Sell", "Call Back On", "Notes"])
    style_header(ws)
    by_id = {str(l.get("id")): l for l in leads}
    for c in calls:
        l = by_id.get(str(c.get("leadId")), {})
        ws.append([c.get("id"), pkt(c.get("at")), c.get("caller"), c.get("leadId"),
                   l.get("owner_name", ""), c.get("phone", ""),
                   DISPO_LABEL.get(c.get("disposition"), c.get("disposition")),
                   c.get("intent") or "", pkt(c.get("callbackAt")), c.get("notes", "")])
        d = c.get("disposition")
        if d in DISPO_FILL:
            ws.cell(row=ws.max_row, column=7).fill = PatternFill("solid", fgColor=DISPO_FILL[d])
    if calls:
        ws.auto_filter.ref = f"A1:J{ws.max_row}"
    else:
        ws.append(["", "No calls logged yet."])
    autosize(ws)
    ws.column_dimensions["J"].width = 50

    # ---------------- 4. Summary ----------------
    ws = wb.create_sheet("Summary")
    ws.column_dimensions["A"].width = 30
    ws.column_dimensions["B"].width = 16
    ws.column_dimensions["C"].width = 16

    def section(title, r):
        c = ws.cell(row=r, column=1, value=title)
        c.font = Font(bold=True, size=13, color="1F3864")
        return r + 1

    counts = {}
    for sid, s in state.items():
        if s.get("disposition"):
            counts[s["disposition"]] = counts.get(s["disposition"], 0) + 1
    touched = sum(counts.values())
    hot = counts.get("INTERESTED", 0) + counts.get("APPOINTMENT", 0)

    r = section("CAMPAIGN AT A GLANCE", 1)
    for k, v in [("Total leads in list", len(leads)),
                 ("People reached a decision on", touched),
                 ("Never called yet", len(leads) - touched),
                 ("Total dials made", len(calls)),
                 ("HOT leads + appointments", hot),
                 ("Snapshot taken", datetime.now(PKT).strftime("%d %b %Y %I:%M %p"))]:
        ws.cell(row=r, column=1, value=k).font = Font(bold=True)
        ws.cell(row=r, column=2, value=v)
        r += 1

    r = section("OUTCOMES", r + 1)
    ws.cell(row=r, column=1, value="Outcome").font = Font(bold=True)
    ws.cell(row=r, column=2, value="Count").font = Font(bold=True)
    r += 1
    for code, label in DISPO_LABEL.items():
        if not counts.get(code):
            continue
        ws.cell(row=r, column=1, value=label)
        ws.cell(row=r, column=2, value=counts[code])
        ws.cell(row=r, column=1).fill = PatternFill("solid", fgColor=DISPO_FILL[code])
        r += 1
    ws.cell(row=r, column=1, value="Not called yet")
    ws.cell(row=r, column=2, value=len(leads) - touched)
    r += 2

    r = section("TEAM SCOREBOARD", r)
    per = {}
    today = datetime.now(PKT).strftime("%Y-%m-%d")
    for c in calls:
        n = c.get("caller", "?")
        p = per.setdefault(n, {"today": 0, "total": 0, "hot": 0})
        p["total"] += 1
        try:
            if datetime.fromisoformat(c["at"].replace("Z", "+00:00")).astimezone(PKT).strftime("%Y-%m-%d") == today:
                p["today"] += 1
        except Exception:
            pass
        if c.get("disposition") in ("INTERESTED", "APPOINTMENT"):
            p["hot"] += 1
    for h, col in zip(["Caller", "Calls Today", "Calls Total", "Hot Leads"], range(1, 5)):
        ws.cell(row=r, column=col, value=h).font = Font(bold=True)
    ws.column_dimensions["D"].width = 14
    r += 1
    for n, p in sorted(per.items(), key=lambda x: -x[1]["total"]):
        ws.cell(row=r, column=1, value=n)
        ws.cell(row=r, column=2, value=p["today"])
        ws.cell(row=r, column=3, value=p["total"])
        ws.cell(row=r, column=4, value=p["hot"])
        r += 1
    if not per:
        ws.cell(row=r, column=1, value="No calls logged yet.")

    out_dir = os.path.join(ROOT, "exports")
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, "Cold-Call-Campaign.xlsx")
    wb.save(out)
    print(f"✅ Excel written → {out}")
    print(f"   {len(leads)} leads · {len(calls)} calls logged")


if __name__ == "__main__":
    main()
