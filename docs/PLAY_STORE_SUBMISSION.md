# Google Play Store Submission Guide

## Prerequisites

- [x] Google Play Developer account ($25 one-time at https://play.google.com/console)
- [x] Signed APK at `android/output/circleone-release.apk`
- [x] Keystore: `TheGeekAlias.keystore`
- [x] Privacy policy URL (host `android/store-listing/privacy-policy.md` publicly)
- [x] Store description (`android/store-listing/description-en.txt`)
- [x] Content rating answers (`android/store-listing/content-rating.md`)

## Assets Needed (Create These)

| Asset | Size | Notes |
|-------|------|-------|
| App icon | 512x512 PNG | isiBheqe triangle symbol on brand colour |
| Feature graphic | 1024x500 PNG | Banner shown at top of store listing |
| Phone screenshots | 1080x1920 (min 2) | Show keyboard in use, isiBheqe glyphs |
| Tablet screenshots | 1920x1200 (optional) | 7" and 10" if supporting tablets |

## Step-by-Step Submission

### 1. Create App in Play Console

1. Go to https://play.google.com/console
2. Click **Create app**
3. Fill in:
   - App name: **CircleOne**
   - Default language: **English (United Kingdom)**
   - App or game: **App**
   - Free or paid: **Free**
4. Accept declarations, click **Create app**

### 2. Store Listing

1. Go to **Main store listing**
2. Fill in:
   - Short description: contents of `short-description-en.txt`
   - Full description: contents of `description-en.txt`
3. Upload graphics:
   - App icon (512x512)
   - Feature graphic (1024x500)
   - Phone screenshots (min 2)
4. Click **Save**

### 3. Content Rating

1. Go to **Content rating**
2. Click **Start questionnaire**
3. Category: **Utility / Productivity**
4. Answer all questions **No** (see `content-rating.md` for details)
5. Submit — should receive **IARC 3+ / Everyone** rating

### 4. Privacy & Data Safety

1. Go to **Data safety**
2. Answer:
   - Does your app collect or share user data? **No**
   - Does your app handle any of these data types? **No** for all
   - Does your app use encryption? **No** (keyboard doesn't do network)
3. Privacy policy URL: point to hosted version of `privacy-policy.md`
   - Option: host on GitHub Pages at `https://bhengubv.github.io/circleone/privacy`
   - Option: host at `https://thegeek.co.za/circleone/privacy`

### 5. App Access

1. Go to **App access**
2. Select **All functionality is available without special access**

### 6. Ads Declaration

1. Go to **Ads**
2. Select **No, my app does not contain ads**

### 7. Target Audience

1. Go to **Target audience and content**
2. Target age group: **18 and over** (avoids COPPA/children's policy requirements)
3. This is a utility app, not designed for children

### 8. Upload APK

1. Go to **Production** → **Create new release**
2. Upload `android/output/circleone-release.apk`
3. Release name: **0.1.0**
4. Release notes:
   ```
   Initial release of CircleOne keyboard.
   - isiBheqe soHlamvu featural syllabary input (471 glyphs)
   - Latin transliteration to isiBheqe glyph conversion
   - Click consonant support with long-press popups
   - Word prediction for Southern African languages
   - No ads, no tracking, no network access
   ```
5. Click **Review release** → **Start rollout to Production**

### 9. Review

- Google typically reviews new apps in **1-3 business days**
- Keyboard apps may get extra scrutiny (input method = sensitive permission)
- If rejected: check the rejection reason email, fix, resubmit

## After Approval

- App will be live at: `https://play.google.com/store/apps/details?id=za.co.thegeek.circleone`
- Update the circleone README with the Play Store link
- Add Play Store badge to marketing materials

## Updating the App

1. Increment version in `build.gradle.kts`
2. Rebuild APK: `cd android && bash build-apk.sh`
3. Upload new APK in Play Console → Production → Create new release
4. Add release notes
5. Roll out
