# 📞 Cold Call Command Center

A dead-simple calling system for a real estate team. No training required.
One person on screen at a time, the exact words to say, one button to log what
happened. Everything is tracked automatically.

---

## ⚡ The 3 commands you will ever need

### Easiest: drag the file into the browser

Start the app (`node server.js`), open it, and **drop your PDF onto the box on
the first screen**. It is parsed, cleaned and loaded in place — no terminal, no
file paths. Use the **"Load a new list"** button on the Dashboard tab to do it
again later. Your call history is backed up automatically before the list is
swapped.

### Or from the terminal

```bash
# 1. Load your list — ANY of these work
python3 tools/clean_leads.py /path/to/your-list.pdf     # a file (PDF / XLSX / CSV / TXT)
python3 tools/clean_leads.py "https://drive.google.com/file/d/..."   # a shared link
pbpaste | python3 tools/clean_leads.py -                # text pasted from your clipboard

# 2. Start the calling app  (then open the link it prints)
node server.js

# 3. Get the manager's Excel report, any time
python3 tools/export_excel.py
```

Google Drive, Google Sheets and Dropbox share links are converted to
direct downloads automatically — just paste the link you'd normally share.

That's it. There is nothing to install and no database to set up.

---

## What your callers do

1. Open the link. **Type their name.** Hit START CALLING.
2. One person appears on screen with a big green **CALL NOW** button.
3. The middle panel is the **script, word for word** — Roman Urdu or English,
   with the lead's real name and area already filled in.
4. Call ends → tap **one** coloured button for what happened.
5. Tap **SAVE & NEXT LEAD**. The next person loads. Repeat.

They cannot get lost, and they cannot call the same person twice.

---

## What it handles for you

| Problem | What the system does |
|---|---|
| Two callers ringing the same person | Each lead is locked to one caller while they work it |
| "I forgot to call them back" | Callbacks re-appear automatically at the right time |
| Nobody picked up | Auto-retries in 3 hours, up to 5 attempts, then stops |
| Someone said never call again | Marked DNC and permanently removed from the queue |
| Messy phone numbers | `0301-4455667`, `+923014455667`, `92 301 4455667` → all become `+92 301 4455667` |
| The same person listed 4 times | Merged into one lead, flagged "APPEARED 4x" |
| Rows with no usable phone number | Dropped, and listed in `data/clean-report.json` so you can check |
| "Which leads are worth calling first?" | Every lead is graded A/B/C and the list is sorted best-first |
| Manager wants a report | `tools/export_excel.py` → a formatted 4-tab workbook |

---

## The buttons, explained

| Button | Use it when | What happens next |
|---|---|---|
| 🤝 Appointment Booked | They agreed to meet | Closed — best outcome |
| 🔥 Interested / Hot | They want to hear more | Stays in the list, jumps to the top |
| ⏰ Call Back Later | "Call me at 6" | Comes back at the time you pick |
| 📵 No Answer | Rang out / voicemail | Auto-retry in 3 hours |
| ✂️ Busy / Cut the call | Picked up and hung up | Retry later |
| 🚫 Not Interested | A clear no | Closed |
| ❓ Wrong Number | Not the right person | Closed |
| ☠️ Number Off / Invalid | Dead number | Closed |
| ⛔ Do Not Call Again | They demanded it | **Never dialled again** |

---

## Changing the script

Open `public/scripts.js`. It's plain text — edit and refresh the page.

At the top, set your agency name:

```js
const AGENCY_NAME = "Skyline Estates";   // <-- change this
```

These get filled in automatically wherever they appear:

`{NAME}` `{AREA}` `{CALLER}` `{AGENCY}` `{PROPERTY}`

---

## Files

```
cold-call-campaign/
├── server.js                  the app (zero dependencies)
├── public/
│   ├── index.html             the screens
│   ├── app.js                 the logic
│   ├── scripts.js             ← THE CALL SCRIPTS. Edit this one.
│   └── styles.css
├── tools/
│   ├── clean_leads.py         crude PDF/Excel/CSV  →  clean leads
│   └── export_excel.py        live campaign  →  formatted .xlsx
├── data/
│   ├── leads.json             your cleaned list
│   ├── campaign-db.json       every call ever logged  ← BACK THIS UP
│   └── clean-report.json      rows that were dropped, and why
└── exports/                   generated spreadsheets
```

**`data/campaign-db.json` is your entire call history.** Copy it somewhere safe
at the end of each day. Nothing else is precious — everything else regenerates.

---

## Running it for a whole team

`node server.js` binds to `0.0.0.0:3100`, so anyone on the same office Wi-Fi can
use it at `http://<your-computer-ip>:3100`. One machine runs the server,
everyone else just opens the link on their phone or laptop. All calls land in the
same database.

Change the port with `PORT=8080 node server.js`.

---

## Calling rules (tell your team)

- **Call 11:00–20:00 only.** Never before 10 AM, never after 9 PM.
- If they say *don't call me again* → hit **Do Not Call Again**. This matters legally.
- Never argue, never lie about a property, never promise a price.
- A "no" is a good outcome. It costs you nothing and clears the list.
