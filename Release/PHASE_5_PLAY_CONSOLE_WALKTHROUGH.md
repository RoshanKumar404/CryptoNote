# Phase 5: Google Play Console Step-by-Step Publishing Walkthrough

Follow this step-by-step checklist inside the [Google Play Console](https://play.google.com/console) to submit CryptoNote for review.

---

## Step 1: Create Your App

1. Log in to the [Google Play Console](https://play.google.com/console).
2. On the **All apps** dashboard, click **Create app** (top-right).
3. Fill in the basic details:
   * **App name:** `CryptoNote: Encrypted Notes`
   * **Default language:** `English (United States) - en-US`
   * **App or game:** Select **App**
   * **Free or paid:** Select **Free**
4. Check both declaration checkboxes:
   * *Developer Program Policies*
   * *US export laws*
5. Click **Create app** at the bottom-right.

---

## Step 2: Complete the "Set up your app" Tasks

On your app's **Dashboard**, scroll to the **Set up your app** section and complete each task in order:

### 2.1 Set Privacy Policy
* Enter URL: `https://roshankumar404.github.io/CryptoNote/privacy.html`
* Click **Save**.

### 2.2 App Access
* Select **All or some functionality in my app is restricted**.
* Click **Add instructions**:
  * **Title:** `Reviewer Test Account`
  * **User name / Email:** `reviewer@example.com`
  * **Password / Passphrase:** `ReviewerPass123!`
  * **Explanation:** *"Reviewers can use these credentials or register any new email and passphrase directly from the sign-up screen to test zero-knowledge encryption and cloud sync."*
* Click **Save**.

### 2.3 Ads
* Select **No, my app does not contain ads**.
* Click **Save**.

### 2.4 Content Rating (IARC Questionnaire)
* Click **Start questionnaire**.
* Enter your email address (`roshankuar951@gmail.com`).
* Category: Select **Utility, Productivity, Communication, or Other**.
* Answer **No** to all questions (Violence, Sexuality, Profanity, Controlled Substances, Location sharing).
* Click **Save**, then click **Next** and **Submit**.  
  *(Result: Rated Everyone / PEGI 3).*

### 2.5 Target Audience and Content
* Select age groups: **18 and over** (and **13–17** if desired).
* Could your listing unintentionally appeal to children? Select **No**.
* Click **Save**.

### 2.6 News Apps, COVID-19, Government & Financial Apps
* **News app?** Select **No**.
* **COVID-19 contact tracing?** Select **No**.
* **Government app?** Select **No**.
* **Financial features?** Select **My app doesn't provide any financial features**.

### 2.7 Data Safety Form
* Refer to [`Release/PHASE_3_STORE_COMPLIANCE.md`](file:///d:/KotlinAps/CryptoNote/Release/PHASE_3_STORE_COMPLIANCE.md):
  * **Data encrypted in transit?** Select **Yes**.
  * **Provide deletion URL?** Select **Yes** and paste:  
    `https://roshankumar404.github.io/CryptoNote/privacy.html#data-deletion`
  * **Data types to declare:**
    * *Personal info > Email address:* Collected for **Account management**, not shared, not ephemeral.
    * *Messages / User Content > Other user content (Notes):* Collected for **App functionality**, encrypted on-device, not shared.

---

## Step 3: Set Up Store Listing & Upload Assets

In the left menu, navigate to **Grow > Store presence > Main store listing**:

### 3.1 Text Descriptions
* **App name:** `CryptoNote: Encrypted Notes`
* **Short description:**  
  `Zero-knowledge, offline-first encrypted notes and private vault for Android.`
* **Full description:** Copy and paste the complete text from [`Release/PHASE_4_STORE_LISTING.md`](file:///d:/KotlinAps/CryptoNote/Release/PHASE_4_STORE_LISTING.md).

### 3.2 Graphics Uploads
Upload the prepared files from your workspace:
* **App Icon (512x512):**  
  Upload: `d:\KotlinAps\CryptoNote\Release\store_icon_512x512.png`
* **Feature Graphic (1024x500):**  
  Upload: `d:\KotlinAps\CryptoNote\Release\store_feature_graphic_1024x500.png`
* **Phone Screenshots (Upload in this order):**
  1. `d:\KotlinAps\CryptoNote\website\assets\showcase-notes-vault.png`
  2. `d:\KotlinAps\CryptoNote\website\assets\showcase-vault-locked.png`
  3. `d:\KotlinAps\CryptoNote\website\assets\showcase-editor-markdown.png`
  4. `d:\KotlinAps\CryptoNote\website\assets\showcase-vault-biometric.png`
  5. `d:\KotlinAps\CryptoNote\website\assets\showcase-appearance-themes.png`
  6. `d:\KotlinAps\CryptoNote\website\assets\showcase-profile-audit.png`
  7. `d:\KotlinAps\CryptoNote\website\assets\showcase-auth-register.png`
* Click **Save** at the bottom-right.

---

## Step 4: Upload the Release Bundle (`.aab`)

### Recommended Track: Internal Testing First
Before submitting to production or closed testing, test the signed build via the **Internal testing** track to verify licensing, Play Integrity, and cloud sync:

1. In the left sidebar, navigate to **Release > Internal testing**.
2. Click **Create new release** (top-right).
3. Under **App bundles**, click **Upload** and select:  
   `d:\KotlinAps\CryptoNote\CryptNote\app\build\outputs\bundle\release\app-release.aab`
4. Enter release notes:
   ```text
   Initial production release:
   - Client-side zero-knowledge encryption (AES-256-GCM + Argon2id).
   - Biometric-locked security vault.
   - Offline-first deterministic sync.
   - Markdown syntax highlighting and distraction-free editor.
   ```
5. Click **Next**, review the bundle metrics, and click **Save**.
6. Switch to the **Testers** tab:
   * Create an email list with your own Google account email.
   * Copy the **join-on-Android** testing link to your phone, open it in Chrome, and install CryptoNote directly from the Google Play Store!

---

## Step 5: Closed Testing Requirement (For New Personal Accounts)

> [!IMPORTANT]
> If your Google Play Developer Account was created after **November 13, 2023** as a personal developer account, Google requires you to run a **Closed Test** with at least **20 testers opted-in for 14 continuous days** before the *Apply for Production* button is unlocked.

1. Navigate to **Release > Closed testing**.
2. Create a track (e.g., `Alpha` or `Closed Beta`).
3. Add at least 20 email addresses (friends, colleagues, or developer testing communities like `/r/AndroidClosedTesting`).
4. Ensure testers install the app from Google Play and keep it installed for 14 continuous days.
5. On day 14, complete the final questionnaire in the Console to apply for full **Production Release**.
