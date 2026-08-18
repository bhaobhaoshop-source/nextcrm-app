/**
 * THE SCRIPTS
 * ------------------------------------------------------------------
 * Edit the text in this file to change what your callers say.
 * Placeholders get filled in automatically:
 *   {NAME}    -> the lead's name        {AREA}   -> their area/society
 *   {CALLER}  -> your caller's name     {AGENCY} -> your agency name
 *   {PROPERTY}-> property type + size
 */

const AGENCY_NAME = "Skyline Estates"; // <-- CHANGE THIS to your agency name

const SCRIPTS = {
  ur: [
    {
      tag: "1. Salaam + permission (5 seconds)",
      text: "Assalam-o-Alaikum, {NAME} sahab? Main <em>{CALLER}</em> baat kar raha hoon <em>{AGENCY}</em> se. Aap ko sirf <em>ek minute</em> lunga — abhi baat kar sakte hain?",
      note: "Rukiye. Unka jawab sunne ka intezaar karein. Agar 'nahi' kahein to poochein: 'Bilkul, main kis waqt call karoon?'",
    },
    {
      tag: "2. Kyun call kiya (reason)",
      text: "Shukriya. Main aap ko is liye call kar raha hoon ke hum <em>{AREA}</em> mein kaam kar rahe hain, aur is waqt hamare paas is ilaaqe ke liye <em>serious clients</em> maujood hain.",
    },
    {
      tag: "3. Sabse ahem sawaal — POOCHNA ZAROORI HAI",
      text: "Bas ye confirm karna tha — <em>{PROPERTY}</em> jo aap ke naam par hai, us ke baare mein… agar koi acha offer aaye, to aap bechne pe consider karenge?",
      note: "Ab CHUP HO JAYEIN. Jo bhi kahein, sun-na hai. Pehla bolne wala haar jaata hai.",
    },
    {
      tag: "4a. Agar wo kahein HAAN / SHAYAD",
      them: "\"Haan, sahi rate mile to sochenge…\"",
      text: "Bohat acha. Do chhoti baatein poochta hoon: <em>aap ke zehen mein rate kya hai?</em> Aur property abhi <em>khaali hai ya kiraye par</em>?",
      note: "Rate aur availability ✍️ Notes mein likhein. Phir seedha STEP 6 (appointment) par jayein.",
    },
    {
      tag: "4b. Agar wo kahein NAHI bechna",
      them: "\"Nahi, filhaal bechne ka iraada nahi.\"",
      text: "Bilkul samajh sakta hoon, koi masla nahi. Ek aakhri baat — kya aap ya aap ke family mein koi <em>kharidne</em> ya <em>investment</em> ke liye dekh raha hai? Hamare paas {AREA} mein kuch acha inventory hai.",
      note: "Yahan aksar deal nikalti hai. Poochna mat bhoolein!",
    },
    {
      tag: "5. Agar kharidne mein interest ho (BUY)",
      text: "Zabardast. Aap ka <em>budget range</em> kya hoga, aur kis <em>ilaaqe</em> mein dekh rahe hain? Main aap ko sirf wohi options bhejunga jo aap ke budget mein fit hon — faltu messages nahi karunga.",
      note: "Budget + area + timeline (kab tak) — teenon Notes mein likhein.",
    },
    {
      tag: "6. Close — appointment / WhatsApp",
      text: "Theek hai {NAME} sahab. Main aap ko WhatsApp par details bhej deta hoon. Aur agar <em>15 minute</em> ki mulaqaat ho jaye — main aap ko is waqt ke actual rates dikha dunga. <em>Kal shaam theek rahegi, ya parso subah?</em>",
      note: "Do options dein — 'haan ya na' nahi. Waqt Notes mein likhein aur 'Appointment Booked' dabayein.",
    },
    {
      tag: "7. Khatam karna",
      text: "Bohat shukriya {NAME} sahab, aap ka waqt dene ka. Mera number aap ke paas save ho jayega — kabhi bhi zaroorat ho to bila jhijhak call karein. Allah Hafiz.",
    },
  ],

  en: [
    {
      tag: "1. Greeting + permission (5 seconds)",
      text: "Hello, am I speaking with <em>{NAME}</em>? This is <em>{CALLER}</em> calling from <em>{AGENCY}</em>. I'll only take <em>one minute</em> of your time — is now an okay moment?",
      note: "Then STOP and wait for their answer. If they say no, ask: 'No problem — what time works better?'",
    },
    {
      tag: "2. Reason for the call",
      text: "Thank you. The reason I'm calling is that we're actively working in <em>{AREA}</em> right now, and we currently have <em>serious clients</em> looking specifically in that area.",
    },
    {
      tag: "3. The one question that matters — ALWAYS ASK IT",
      text: "I just wanted to check with you directly — regarding the <em>{PROPERTY}</em> under your name… if the right offer came along, would you consider selling?",
      note: "Now BE QUIET. Let them answer. Whoever speaks first, loses.",
    },
    {
      tag: "4a. If they say YES / MAYBE",
      them: "\"Yes, if the price is right…\"",
      text: "That's great. Two quick things: <em>what price do you have in mind</em>, and is the property currently <em>vacant or rented out</em>?",
      note: "Write the price + availability in ✍️ Notes. Then go straight to STEP 6 (appointment).",
    },
    {
      tag: "4b. If they say NO",
      them: "\"No, I'm not looking to sell.\"",
      text: "Completely understood, no problem at all. One last thing — are you or anyone in your family looking to <em>buy</em> or <em>invest</em> at the moment? We have some strong inventory in {AREA}.",
      note: "This flip is where a lot of deals come from. Never skip it.",
    },
    {
      tag: "5. If they're interested in BUYING",
      text: "Excellent. What <em>budget range</em> are you working with, and which <em>areas</em> are you considering? I'll only send you options that actually fit — I won't spam you.",
      note: "Capture budget + area + timeline in Notes.",
    },
    {
      tag: "6. Close — appointment / WhatsApp",
      text: "Perfect, {NAME}. I'll send the details to you on WhatsApp. And if we can find <em>15 minutes</em> to meet, I'll show you what things are actually selling for right now. <em>Would tomorrow evening work, or is the morning after better?</em>",
      note: "Give two options, never a yes/no. Put the time in Notes and hit 'Appointment Booked'.",
    },
    {
      tag: "7. Wrap up",
      text: "Thank you so much for your time, {NAME}. You'll have my number saved now — please reach out any time. Have a great day.",
    },
  ],
};

const OBJECTIONS = {
  ur: [
    { q: "\"Mujhe interest nahi hai.\"", a: "Bilkul, main samajhta hoon — aap ne to sochne ka mauqa hi nahi maanga tha. Bas ek cheez bata doon: is mahine {AREA} mein rates kya chal rahe hain? 20 second lagenge." },
    { q: "\"Aap ko mera number kahan se mila?\"", a: "Ji, ye publicly listed property records se hai. Agar aap chahein to main abhi aap ka number apni list se permanently nikaal deta hoon — bataiye?" },
    { q: "\"Main pehle hi kisi agent ke saath hoon.\"", a: "Achi baat hai, aap ne kisi par bharosa kiya hua hai. Main replace karne nahi keh raha — bas itna, agar unke through 60 din mein baat na bane to kya main tab aap se rabta kar sakta hoon?" },
    { q: "\"Rate bohat kam de rahe ho.\"", a: "Main ne abhi koi rate diya hi nahi, sir. Main ye jaanna chahta hoon ke <em>aap</em> ke zehen mein kya figure hai — phir dekhte hain ke market usse match karta hai ya nahi." },
    { q: "\"Abhi busy hoon.\"", a: "Maazrat. Main sirf 40 second loonga — ya agar behtar ho to bataiye kal kis waqt call karoon? Main us waqt hi karunga." },
    { q: "\"WhatsApp par bhej do.\"", a: "Zaroor bhej deta hoon. Bas ye bata dein aap ka interest bechne mein hai ya kharidne mein — taake main faltu cheezein na bhejun." },
    { q: "\"Dobara call mat karna!\"", a: "Bilkul, maazrat. Main abhi aap ka number list se nikaal deta hoon. Allah Hafiz. → phir 'Do Not Call Again' dabayein." },
  ],
  en: [
    { q: "\"I'm not interested.\"", a: "Totally fair — you weren't expecting the question. Let me leave you with one thing: do you know what {AREA} is actually trading at this month? Takes 20 seconds." },
    { q: "\"Where did you get my number?\"", a: "It's from publicly listed property records. And if you'd prefer, I can remove you from our list permanently right now — would you like me to?" },
    { q: "\"I already have an agent.\"", a: "Good — it's smart to have someone you trust. I'm not asking you to replace them. Just this: if it hasn't moved in 60 days, would it be okay if I checked back then?" },
    { q: "\"Your price is too low.\"", a: "I haven't quoted a price yet, sir. I'm asking what number <em>you</em> have in mind — then we can see whether today's market supports it." },
    { q: "\"I'm busy right now.\"", a: "Understood. I'll take 40 seconds — or tell me a better time tomorrow and I'll call exactly then." },
    { q: "\"Just send it on WhatsApp.\"", a: "Happy to. Just tell me first — buying side or selling side? So I don't send you things that waste your time." },
    { q: "\"Do not call me again!\"", a: "Of course, my apologies. I'm removing your number right now. → then hit 'Do Not Call Again'." },
  ],
};
