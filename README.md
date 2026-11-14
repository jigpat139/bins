# What's in your Bin?

Track what's inside your storage bins. Stick an NFC tag or a QR label on each bin; tap or scan it and that bin's
contents open on your phone, where you can add, edit, count and remove items. Bins sync across all your phones.

- **Android:** a real app with built-in NFC tag writing and reading, barcode/QR scanner, works offline.
- **iPhone:** the same app as a website you add to the home screen. Tapping a tag or scanning a QR label opens it.

## One-time setup

### 1. Let GitHub build everything (2 minutes)
1. In this repo: **Settings → Pages → Build and deployment → Source: GitHub Actions**.
2. **Actions** tab → "Build app and website" → **Run workflow** (or just push any change).
3. After ~5 minutes you get:
   - the website: `https://YOUR-USERNAME.github.io/bins/`
   - the Android app: **Releases → latest → whats-in-your-bin.apk**

### 2. Turn on syncing (Supabase, free, ~10 minutes)
1. Create a project at supabase.com.
2. **SQL Editor → New query** → paste `web/setup.sql` → **Run**.
3. **Authentication → Users → Add user** for each person (email + password, tick "Auto confirm").
4. **Authentication → Sign In / Providers** → turn off "Allow new users to sign up".
5. **Project Settings → API**: you'll need the **Project URL** and the **anon public** key.
   Either paste them into each phone (⋯ menu → Share bins across phones), or put them in `web/config.js`
   once so every phone gets them automatically.

### 3. Phones
- **Android:** open the website on the phone and tap ⋯ → *Get the Android app* (or download the APK from Releases),
  allow "install unknown apps" for your browser, install, open, ⋯ → sign in.
- **iPhone:** open the website in Safari → Share → *Add to Home Screen*, open it, ⋯ → sign in.

## Using it
- **New bin:** ＋ New bin. Add items; type `screws x20` to add 20 at once.
- **NFC tag:** open the bin → *Write to NFC tag* → hold the tag to the back of the phone (Android app).
  On iPhone use *Copy link* and write it with the free NFC Tools app.
- **QR label:** open the bin → *Show / print QR label* (print from a computer: ⋯ → print labels for all bins).
- **Existing barcode or tag:** scan it with 📷 (or tap an old tag in the Android app) and link it to a bin.
- **Find something:** type in the search box on the home screen.

## Changing the app
- Everything the app does is in `web/index.html` (plain HTML/JS, no build step). Edit it, push, and both the
  website and the Android app rebuild automatically.
- Android-only NFC code: `android/app/src/main/java/app/bins/tracker/NfcBinPlugin.java`.
- The Android app is signed with `android/app/bins.keystore` (kept in the repo on purpose, so new builds install
  over old ones). It's a personal side-loaded app, not a Play Store key.

Libraries: Capacitor 8 (MIT), supabase-js 2.117.3 (MIT), html5-qrcode 2.3.8 (Apache-2.0), qrcode-generator 1.4.4 (MIT).
