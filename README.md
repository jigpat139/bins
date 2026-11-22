# What's in your Storage?

Track what's inside your storage bins. Stick an NFC tag or a QR label on each bin; tap or scan it and that bin's
contents open on your phone, where you can add, edit, count and remove items. Keep your own storage, share one with
your household, or both; everything syncs across phones.

- **Android:** a real app with built-in NFC tag writing and reading, barcode/QR scanner, works offline.
- **iPhone:** the same app as a website you add to the home screen. Tapping a tag or scanning a QR label opens it.

## One-time setup

### 1. Let GitHub build everything (2 minutes)
1. In this repo: **Settings → Pages → Build and deployment → Source: GitHub Actions**.
2. **Actions** tab → "Build app and website" → **Run workflow** (or just push any change).
3. After ~5 minutes you get:
   - the website: `https://YOUR-USERNAME.github.io/bins/`
   - the Android app: **Releases → latest → whats-in-your-bin.apk**

### 2. Turn on syncing and sharing (Supabase, free, ~10 minutes)
Bins live in **storages**. Each person can have their own storage, share one with the household, or both
(switch in ⋯). People sign in with their email and a 6-digit code; no passwords. Whoever creates a storage gets a
**join code** to share. When two phones change the same bin at once, the changes are merged item by item.

1. Create a project at supabase.com.
2. **SQL Editor → New query** → paste `web/setup.sql` → **Run**. (Run it again after app updates; it's safe.)
3. **Authentication → Emails → Templates**: in both **Confirm signup** and **Magic Link**, add the line
   `Your code: {{ .Token }}` so the email shows the 6-digit code the app asks for.
4. **Authentication → URL Configuration → Site URL**: your website, e.g. `https://YOUR-USERNAME.github.io/bins/`.
5. **Project Settings → API**: copy the **Project URL** and the **anon public** key, then either
   - **recommended:** in this GitHub repo, **Settings → Secrets and variables → Actions → Variables** add
     `SUPABASE_URL` and `SUPABASE_ANON_KEY` (and `GOOGLE_CLIENT_ID` from step 3 below), then re-run the build.
     Every phone is then set up automatically; people just enter their email and the join code. Or
   - paste them into each phone: ⋯ → *Share with family or your other phones* → *Set up syncing*.

Supabase's built-in email sender only sends a few emails an hour, which is fine for a family. Each phone stays
signed in, so it only matters on day one.

### 3. Photos in Google Drive (optional, ~15 minutes, one time)
Every bin and item can have photos (📷 buttons). They're always saved on the phone first; connect Google Drive to
back them up to a **"What's in your Bin — photos"** folder and show them on your other phones. Each file is named
like `Tent — in Garage camping (Garage, top shelf).jpg`, so searching Drive also tells you where things are.

1. Go to console.cloud.google.com → create a project (e.g. "Whats in your Storage").
2. **APIs & Services → Library** → search **Google Drive API** → **Enable**.
3. **APIs & Services → OAuth consent screen** (Google Auth Platform) → Get started → app name "What's in your Storage?",
   your email → Audience **External** → finish. Under **Audience → Test users** add your Gmail (and family members').
4. **Clients → Create client**:
   - **Android**: package name `app.bins.tracker`, SHA-1 `7C:47:DD:08:27:56:67:1C:C2:F0:C8:CA:26:6E:F9:38:B5:9B:9B:83`.
     That's all the Android app needs.
   - **Web application** (for iPhone/website): Authorized JavaScript origins `https://jigpat139.github.io`.
     Copy its **Client ID** and paste it in the website's ⋯ → sync settings → "Google client ID"
     (or put it in `web/config.js` as `googleClientId`).
5. In the app: ⋯ → **Connect Google Drive**.

Photos are uploaded as "anyone with the link can view" so phones signed in to other Google accounts can show them.
The links are long random ids and aren't listed anywhere public.

### 4. Phones
- **Android:** open the website on the phone and tap ⋯ → *Get the Android app* (or download the APK from Releases),
  allow "install unknown apps" for your browser, install, open, and follow the welcome screen.
- **iPhone:** open the website in Safari → Share → *Add to Home Screen*, open it, and follow the welcome screen.
- **Invite someone:** ⋯ → *Invite* sends the join code and app links.

## Using it
- **New bin:** ＋ New bin. Add items; type `screws x20` to add 20 at once.
- **NFC tag:** open the bin → *Write to NFC tag* → hold the tag to the back of the phone (Android app).
  On iPhone use *Copy link* and write it with the free NFC Tools app.
- **QR label:** open the bin → *Show / print QR label* (print from a computer: ⋯ → print labels for all bins).
- **Existing barcode or tag:** scan it with 📷 (or tap an old tag in the Android app) and link it to a bin.
- **Find something:** type in the search box on the home screen.
- **Add lots at once:** `tent, headlamp x2, 4 batteries`, or tap 🎤 and say them (where the phone supports it).
- **Take it out / put back:** open an item → *Take it out* (who has it?). Home shows everything that's out.
- **Expiry, low stock, value:** set them on an item; home shows *expiring soon* and *running low* alerts.
- **Check contents:** More → *Check contents* to tick off what's really there and deal with what's missing.
- **Organise:** bin icons and colours, ★ favourites, filter by place, sort, categories, history of changes.
- **Spreadsheet:** ⋯ → *Spreadsheet (CSV)* → Export or Import. Import matches columns by name (Bin, Location, Item, Qty, Category…), adds new bins/items and updates existing ones; *Download a blank template* to start.
- **Share / print:** More → *Share the list* or *Print contents sheet*; ⋯ → *Export spreadsheet (CSV)*; tap *Stats* on home.

## Changing the app
- Everything the app does is in `web/index.html` (plain HTML/JS, no build step); the sync merge rules are in `web/merge.js`. Edit it, push, and both the
  website and the Android app rebuild automatically.
- Android-only NFC code: `android/app/src/main/java/app/bins/tracker/NfcBinPlugin.java`.
- The Android app is signed with `android/app/bins.keystore` (kept in the repo on purpose, so new builds install
  over old ones). It's a personal side-loaded app, not a Play Store key.

Libraries: Capacitor 8 (MIT), supabase-js 2.117.3 (MIT), html5-qrcode 2.3.8 (Apache-2.0), qrcode-generator 1.4.4 (MIT).
