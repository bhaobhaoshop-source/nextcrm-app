#!/usr/bin/env python3
"""
LEAD CLEANER  —  crude data in, call-ready leads.json out
=========================================================
Handles: PDF, CSV, XLSX, or plain text.

  python3 tools/clean_leads.py <input-file> [--out data/leads.json]

What it does
------------
1. Pulls every row/line of text out of the file.
2. Finds Pakistani phone numbers anywhere in the mess and normalises them
   to +92 3XX XXXXXXX.
3. Guesses which bit is a name, area, price, size, property type.
4. Merges duplicates (same phone = same person).
5. Scores each lead so the best ones get called first.
6. Writes data/leads.json for the calling app.
"""

import argparse, json, os, re, sys, unicodedata
from collections import OrderedDict

# --------------------------------------------------------------------------
# Pakistan phone handling
# --------------------------------------------------------------------------

PK_MOBILE_PREFIXES = {
    "300","301","302","303","304","305","306","307","308","309",  # Jazz/Mobilink
    "310","311","312","313","314","315","316","317","318","319",  # Zong
    "320","321","322","323","324","325","326","327","328","329",  # Warid/Jazz
    "330","331","332","333","334","335","336","337","338","339",  # Ufone
    "340","341","342","343","344","345","346","347","348","349",  # Telenor
    "355",
}
CITY_CODES = {
    "51":"Islamabad/Rawalpindi","42":"Lahore","21":"Karachi","41":"Faisalabad",
    "61":"Multan","91":"Peshawar","81":"Quetta","55":"Gujranwala","62":"Bahawalpur",
    "48":"Sargodha","52":"Sialkot","46":"Sahiwal","57":"Attock","53":"Gujrat",
}

def digits(s):
    return re.sub(r"\D", "", str(s or ""))

def normalise_pk(raw):
    """Return (pretty, kind) or (None, reason)."""
    d = digits(raw)
    if not d:
        return None, "empty"
    # strip international / trunk prefixes down to national significant number
    if d.startswith("0092"):  d = d[4:]
    elif d.startswith("92") and len(d) >= 12: d = d[2:]
    elif d.startswith("92") and len(d) == 12: d = d[2:]
    d = d.lstrip("0") if len(d) in (11, 12) and d.startswith("0") else d
    if d.startswith("0"): d = d.lstrip("0")

    # mobile: 3XXXXXXXXX (10 digits)
    if len(d) == 10 and d[0] == "3":
        if d[:3] in PK_MOBILE_PREFIXES:
            return f"+92 {d[:3]} {d[3:]}", "mobile"
        return f"+92 {d[:3]} {d[3:]}", "mobile?"   # unknown prefix, still plausible

    # landline: area code (2-3) + 6-8 digits, total 9-10
    if 9 <= len(d) <= 10:
        for code_len in (2, 3):
            code = d[:code_len]
            if code in CITY_CODES:
                return f"+92 {code} {d[code_len:]}", "landline"
        return f"+92 {d}", "landline?"

    if len(d) < 9:
        return None, "too-short"
    return None, "unrecognised"

PHONE_RE = re.compile(r"(?:(?:\+|00)?92[\s\-.]?|0)?3\d{2}[\s\-.]?\d{7}|(?:\+?92[\s\-.]?)?0?\d{2,3}[\s\-.]?\d{6,8}")

def find_phones(text):
    out = []
    for m in PHONE_RE.finditer(str(text or "")):
        p, kind = normalise_pk(m.group(0))
        if p and kind in ("mobile", "mobile?", "landline", "landline?"):
            out.append((p, kind))
    # dedupe, mobiles first (you want a mobile for cold calling)
    seen, ranked = set(), []
    for p, k in sorted(out, key=lambda x: 0 if x[1].startswith("mobile") else 1):
        if p not in seen:
            seen.add(p); ranked.append(p)
    return ranked

# --------------------------------------------------------------------------
# Field guessing
# --------------------------------------------------------------------------

PROP_WORDS = OrderedDict([
    (r"\bfarm\s?house\b", "Farm House"),
    (r"\bupper\s+portion|\blower\s+portion", "Portion"),
    (r"\bplot\b|\bplots\b", "Plot"),
    (r"\bhouse\b|\bkothi\b|\bbungalow\b", "House"),
    (r"\bflat\b|\bapartment\b|\bapt\b", "Apartment"),
    (r"\bshop\b|\bshops\b", "Shop"),
    (r"\boffice\b", "Office"),
    (r"\bfile\b|\bfiles\b", "File"),
    (r"\bcommercial\b", "Commercial"),
    (r"\bfarm\s?house\b", "Farm House"),
    (r"\bagricultur|\bland\b", "Land"),
    (r"\bupper\s+portion|\blower\s+portion", "Portion"),
])
SIZE_RE  = re.compile(r"\b(\d+(?:\.\d+)?)\s*(marla|kanal|sq\.?\s?(?:ft|yd|yards|feet)|square\s+(?:feet|yards)|acres?|bigha)\b", re.I)
PRICE_RE = re.compile(r"(?:rs\.?|pkr|price|demand|rate)?\s*([\d,]+(?:\.\d+)?)\s*(crore|cr\b|lakh|lac\b|million|mn\b|k\b|arab)", re.I)
CITY_WORDS = ["islamabad","rawalpindi","lahore","karachi","peshawar","multan","faisalabad",
              "gujranwala","sialkot","quetta","abbottabad","murree","wah","taxila","gujrat",
              "bahawalpur","sargodha","hyderabad","sukkur","mardan","attock","chakwal","jhelum"]
AREA_HINTS = ["bahria","dha","gulberg","askari","phase","sector","block","town","society",
              "colony","enclave","garden","valley","heights","park view","citi housing",
              "wapda","johar","model town","cantt","scheme","f-","g-","e-","i-","h-","b-17","d-12"]
JUNK_TOKENS = {"n/a","na","none","null","-","--","nil","unknown","xxx",""}

def clean_text(s):
    s = unicodedata.normalize("NFKC", str(s or ""))
    s = s.replace("\u200b", " ").replace("\xa0", " ")
    return re.sub(r"\s+", " ", s).strip()

def titlecase_name(s):
    s = clean_text(s)
    s = re.sub(r"[^\w\s.'\-]", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    if not s: return ""
    small = {"bin","bint","ul","al","e"}
    parts = []
    for w in s.split():
        lw = w.lower()
        parts.append(lw if lw in small else (w.upper() if len(w) <= 2 and w.isupper() else lw.capitalize()))
    return " ".join(parts)

def guess_property(t):
    tl = t.lower()
    for pat, label in PROP_WORDS.items():
        if re.search(pat, tl): return label
    return ""

def guess_size(t):
    m = SIZE_RE.search(t)
    if not m: return ""
    unit = m.group(2).lower()
    unit = {"sq ft":"sq ft","sq.ft":"sq ft","sqft":"sq ft"}.get(unit.replace(".","").replace("  "," "), m.group(2))
    return f"{m.group(1)} {unit.title() if 'marla' in unit or 'kanal' in unit else unit}"

def guess_price(t):
    m = PRICE_RE.search(t)
    if not m: return ""
    n, unit = m.group(1).replace(",", ""), m.group(2).lower()
    unit = {"cr":"Crore","crore":"Crore","lac":"Lakh","lakh":"Lakh","mn":"Million",
            "million":"Million","k":"Thousand","arab":"Arab"}.get(unit, unit.title())
    return f"{n} {unit}"

def guess_city(t):
    tl = t.lower()
    for c in CITY_WORDS:
        if c in tl: return c.title()
    return ""

def guess_area(t):
    """Pull a short, human-readable society/sector name out of messy text."""
    t = clean_text(t)
    # strip things that are definitely not the area
    t = PHONE_RE.sub(" ", t)
    t = SIZE_RE.sub(" ", t)
    t = PRICE_RE.sub(" ", t)
    t = re.sub(r"\brs\.?\b|\bpkr\b", " ", t, flags=re.I)
    for pat in PROP_WORDS:                      # drop "house", "plot", "shop"…
        t = re.sub(pat, " ", t, flags=re.I)
    t = re.sub(r"^[\s.\-–—:;|,]+", " ", t)      # leading punctuation
    best = ""
    for chunk in re.split(r"[|,;/]+", t):
        chunk = clean_text(chunk)
        cl = chunk.lower()
        if not chunk or len(chunk) > 48:
            continue
        if any(h in cl for h in AREA_HINTS):
            # trim leading numbering / stray punctuation
            chunk = re.sub(r"^[\s\d.\-–—:;]+", "", chunk).strip()
            if chunk and (not best or len(chunk) < len(best)):
                best = chunk
    if best:
        return " ".join(w if w.isupper() or "-" in w else w.capitalize()
                        for w in best.split())
    return ""

def looks_like_name(s):
    s = clean_text(s)
    if not s or s.lower() in JUNK_TOKENS: return False
    if len(s) < 3 or len(s) > 45: return False
    if sum(ch.isdigit() for ch in s) > 2: return False
    return bool(re.search(r"[A-Za-z]{3}", s))


# words that are never part of a person's name
NOT_NAME_WORDS = set("""plot plots house kothi bungalow flat apartment apt shop shops office
file files commercial farm land agricultural upper lower portion marla kanal sqft sq ft yd
yards feet acre acres bigha crore cr lakh lac million mn arab rs pkr price demand rate
phase sector block town society colony enclave garden valley heights park citi housing wapda
johar model cantt scheme road street st contact mobile cell phone no number owner name
islamabad rawalpindi lahore karachi peshawar multan faisalabad gujranwala sialkot quetta
bahria dha gulberg askari ghauri centaurus saddar chaklala attock tenant rented vacant
sale sell selling buy buyer urgent serious investor abroad""".split())

HONORIFICS = {"mr","mrs","ms","miss","malik","sheikh","syed","mian","ch","chaudhry","chaudhary",
              "hafiz","haji","dr","engr","raja","khawaja","mirza","agha","sardar","pir","qari"}


def extract_name_from_line(line):
    """For unstructured text: the name is almost always the words BEFORE the phone number."""
    m = PHONE_RE.search(line)
    head = line[:m.start()] if m else line
    head = re.split(r"[|,;:\t]", head)[0]          # stop at the first separator
    head = re.sub(r"^\s*\d+[\.\)]?\s*", "", head)  # drop leading "1." / "12)"
    words, out = head.split(), []
    for w in words:
        bare = re.sub(r"[^\w]", "", w).lower()
        if not bare:
            continue
        if any(ch.isdigit() for ch in bare):
            break
        if bare in NOT_NAME_WORDS:
            break
        out.append(w)
        if len(out) >= 4:
            break
    # a lone honorific isn't a name
    if len(out) == 1 and re.sub(r"[^\w]", "", out[0]).lower() in HONORIFICS:
        return ""
    cand = titlecase_name(" ".join(out))
    return cand if looks_like_name(cand) else ""


def strip_name(line, name):
    """Remove the detected name so it doesn't leak into the area guess."""
    if not name:
        return line
    return re.sub(re.escape(name), " ", line, flags=re.I)

# --------------------------------------------------------------------------
# Readers
# --------------------------------------------------------------------------

def read_pdf(path):
    """Return (rows, pages_text). Tries tables first, falls back to lines."""
    rows, pages = [], []
    try:
        import pdfplumber
    except ImportError:
        sys.exit("pip install pdfplumber")
    with pdfplumber.open(path) as pdf:
        for pno, page in enumerate(pdf.pages, 1):
            txt = page.extract_text() or ""
            pages.append(txt)
            for table in (page.extract_tables() or []):
                for r in table:
                    cells = [clean_text(c) for c in r]
                    if any(cells): rows.append({"cells": cells, "page": pno})
    return rows, pages

def read_csv(path):
    import csv
    rows = []
    with open(path, newline="", encoding="utf-8-sig", errors="replace") as f:
        sample = f.read(8192); f.seek(0)
        try: dialect = csv.Sniffer().sniff(sample, delimiters=",;\t|")
        except Exception: dialect = csv.excel
        for r in csv.reader(f, dialect):
            cells = [clean_text(c) for c in r]
            if any(cells): rows.append({"cells": cells, "page": 1})
    return rows, []

def read_xlsx(path):
    import openpyxl
    rows = []
    wb = openpyxl.load_workbook(path, data_only=True, read_only=True)
    for ws in wb.worksheets:
        for r in ws.iter_rows(values_only=True):
            cells = [clean_text(c) for c in r]
            if any(cells): rows.append({"cells": cells, "page": ws.title})
    return rows, []

def read_txt(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        lines = [clean_text(l) for l in f if clean_text(l)]
    return [{"cells": [l], "page": 1} for l in lines], lines

READERS = {".pdf": read_pdf, ".csv": read_csv, ".tsv": read_csv,
           ".xlsx": read_xlsx, ".xlsm": read_xlsx, ".txt": read_txt}


def fetch_to_temp(url):
    """Download a shared link (Google Drive / Dropbox / any direct URL) to a temp file."""
    import tempfile, urllib.request, urllib.parse

    # rewrite common share links into direct-download form
    if "drive.google.com" in url:
        m = re.search(r"/d/([A-Za-z0-9_-]{10,})", url) or re.search(r"[?&]id=([A-Za-z0-9_-]{10,})", url)
        if m:
            url = f"https://drive.google.com/uc?export=download&id={m.group(1)}"
    elif "dropbox.com" in url:
        url = re.sub(r"[?&]dl=0", "", url) + ("&" if "?" in url else "?") + "dl=1"
    elif "docs.google.com/spreadsheets" in url:
        m = re.search(r"/d/([A-Za-z0-9_-]{10,})", url)
        if m:
            url = f"https://docs.google.com/spreadsheets/d/{m.group(1)}/export?format=xlsx"

    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=90) as r:
        blob = r.read()
        ctype = (r.headers.get("Content-Type") or "").lower()
        disp = r.headers.get("Content-Disposition") or ""

    ext = ""
    m = re.search(r'filename\*?=(?:UTF-8\'\')?"?([^";]+)', disp)
    if m:
        ext = os.path.splitext(m.group(1))[1].lower()
    if ext not in READERS:
        ext = os.path.splitext(urllib.parse.urlparse(url).path)[1].lower()
    if ext not in READERS:
        ext = (".pdf" if "pdf" in ctype else
               ".xlsx" if "sheet" in ctype or "excel" in ctype else
               ".csv" if "csv" in ctype else "")
    if ext not in READERS:
        if blob[:4] == b"%PDF":
            ext = ".pdf"
        elif blob[:2] == b"PK":
            ext = ".xlsx"
        else:
            ext = ".csv"

    fd, path = tempfile.mkstemp(suffix=ext)
    with os.fdopen(fd, "wb") as f:
        f.write(blob)
    print(f"⬇  downloaded {len(blob):,} bytes → {path}")
    return path

# --------------------------------------------------------------------------
# Build
# --------------------------------------------------------------------------

HEADERISH = re.compile(r"^(s\.?\s?no|sr|#|name|owner|contact|phone|mobile|cell|address|"
                       r"area|city|property|size|price|remarks?|status)$", re.I)

def row_is_header(cells):
    hits = sum(1 for c in cells if HEADERISH.match(c.strip()))
    return hits >= 2

def build_leads(rows, pages, source_name):
    header = None
    records, rejected = [], []

    for r in rows:
        cells = r["cells"]
        if header is None and row_is_header(cells):
            header = [c.strip().lower() for c in cells]
            continue
        blob = " | ".join(c for c in cells if c)
        if not blob: continue
        phones = find_phones(blob)
        if not phones:
            rejected.append({"reason": "no phone number found", "text": blob[:200]})
            continue

        # name = first cell that looks like a name and isn't the phone
        name = ""
        for c in cells:
            if find_phones(c): continue
            if looks_like_name(c) and not SIZE_RE.search(c) and not guess_price(c):
                cand = titlecase_name(c)
                if cand and not all(re.sub(r"[^\w]", "", w).lower() in NOT_NAME_WORDS
                                    for w in cand.split()):
                    name = cand
                    break
        if not name:
            name = extract_name_from_line(blob)

        rec = {
            "owner_name": name,
            "phones": phones,
            "property_type": guess_property(blob),
            "size": guess_size(blob),
            "price": guess_price(blob),
            "city": guess_city(blob),
            "area": guess_area(strip_name(blob, name)),
            "address": "",
            "source": source_name,
            "raw": blob[:500],
        }
        # keep any named columns we recognised from a header row
        if header and len(header) == len(cells):
            extra = {}
            for h, c in zip(header, cells):
                if not c or h in ("", "s.no", "sr", "#"): continue
                if find_phones(c): continue
                if h in ("name","owner") and not rec["owner_name"]:
                    rec["owner_name"] = titlecase_name(c); continue
                if h in ("address",): rec["address"] = c; continue
                if h in ("area","location","society"): rec["area"] = rec["area"] or c; continue
                if h in ("city",): rec["city"] = rec["city"] or c; continue
                if h in ("remarks","remark","note","notes","comment"):
                    rec["note_from_data"] = c; continue
                extra[h.title()] = c
            if extra: rec["extra"] = extra
        records.append(rec)

    # ---- fallback: no tables, scan raw page text line by line
    if not records and pages:
        rejected = []   # the table pass produced nothing; don't double-count its rejects
        for page in pages:
            for line in page.splitlines():
                line = clean_text(line)
                if not line:
                    continue
                phones = find_phones(line)
                if not phones:
                    rejected.append({"reason": "no phone number found", "text": line[:200]})
                    continue
                name = extract_name_from_line(line)
                records.append({
                    "owner_name": name, "phones": phones,
                    "property_type": guess_property(line), "size": guess_size(line),
                    "price": guess_price(line), "city": guess_city(line),
                    "area": guess_area(strip_name(line, name)), "address": "",
                    "source": source_name, "raw": line[:500],
                })

    # ---- merge duplicates by phone
    for rec in records:
        # if we got a proper address column, derive the area from it (much cleaner)
        if rec.get("address"):
            better = guess_area(rec["address"])
            if better: rec["area"] = better
            if not rec.get("city"): rec["city"] = guess_city(rec["address"])
        if not rec.get("area") and rec.get("address"):
            rec["area"] = clean_text(rec["address"])[:48]

    by_phone, merged = {}, []
    for rec in records:
        key = rec["phones"][0]
        if key in by_phone:
            tgt = by_phone[key]
            tgt["duplicate_count"] = tgt.get("duplicate_count", 1) + 1
            for f in ("owner_name","property_type","size","price","city","area","address"):
                if not tgt.get(f) and rec.get(f): tgt[f] = rec[f]
            for p in rec["phones"]:
                if p not in tgt["phones"]: tgt["phones"].append(p)
        else:
            by_phone[key] = rec; merged.append(rec)

    # ---- score + flag + id
    for i, rec in enumerate(merged, 1):
        rec["id"] = f"L{i:04d}"
        score, flags = 0, []
        if rec["owner_name"]: score += 30
        else: flags.append("NO NAME — ask who you're speaking to")
        if any(p.startswith("+92 3") for p in rec["phones"]): score += 30
        else: flags.append("LANDLINE ONLY")
        if len(rec["phones"]) > 1: score += 10
        if rec["price"]: score += 15
        if rec["size"]: score += 10
        if rec["area"] or rec["city"]: score += 10
        if rec.get("duplicate_count", 1) > 1:
            flags.append(f"APPEARED {rec['duplicate_count']}x IN THE LIST")
            score += 5
        rec["score"] = score
        rec["flags"] = flags
        rec["quality"] = "A" if score >= 80 else "B" if score >= 50 else "C"

    merged.sort(key=lambda r: -r["score"])
    for i, rec in enumerate(merged, 1):
        rec["id"] = f"L{i:04d}"

    return merged, rejected

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input", help="file path, a URL, or '-' to read pasted text from stdin")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "..", "data", "leads.json"))
    ap.add_argument("--report", default=os.path.join(os.path.dirname(__file__), "..", "data", "clean-report.json"))
    a = ap.parse_args()

    src_name = os.path.basename(a.input)
    inp = a.input

    if inp == "-":
        import tempfile
        text = sys.stdin.read()
        fd, inp = tempfile.mkstemp(suffix=".txt")
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(text)
        src_name = "pasted-text"
    elif inp.startswith(("http://", "https://")):
        src_name = inp
        inp = fetch_to_temp(inp)

    if not os.path.exists(inp):
        sys.exit(f"❌ File not found: {inp}")

    ext = os.path.splitext(inp)[1].lower()
    reader = READERS.get(ext)
    if not reader:
        sys.exit(f"Don't know how to read {ext}. Supported: {', '.join(READERS)}")

    rows, pages = reader(inp)
    leads, rejected = build_leads(rows, pages, src_name)

    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump({"meta": {"source": src_name,
                            "count": len(leads),
                            "quality": {q: sum(1 for l in leads if l["quality"] == q) for q in "ABC"}},
                   "leads": leads}, f, indent=1, ensure_ascii=False)
    merged_dupes = sum(l.get("duplicate_count", 1) - 1 for l in leads)
    with open(a.report, "w", encoding="utf-8") as f:
        json.dump({"source": src_name,
                   "kept": len(leads),
                   "duplicates_merged": merged_dupes,
                   "dropped": len(rejected),
                   "dropped_rows": rejected[:400]},
                  f, indent=1, ensure_ascii=False)

    print(f"✅ {len(leads)} callable leads  →  {a.out}")
    print(f"   A-grade {sum(1 for l in leads if l['quality']=='A')} | "
          f"B {sum(1 for l in leads if l['quality']=='B')} | "
          f"C {sum(1 for l in leads if l['quality']=='C')}")
    if merged_dupes:
        print(f"🔁 {merged_dupes} duplicate row(s) merged into existing leads")
    print(f"⚠️  {len(rejected)} rows dropped (no usable phone) → {a.report}")

if __name__ == "__main__":
    main()
