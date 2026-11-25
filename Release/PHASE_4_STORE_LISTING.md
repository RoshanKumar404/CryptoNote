# Phase 4: Google Play Store Listing & Creative Assets

This document contains the finalized, humanized store listing copy, visual asset links, and metadata ready to be copy-pasted directly into the **Google Play Console**.

---

## 1. App Details & Listing Copy

### 1.1 App Name (28 / 30 characters)
```text
CryptoNote: Encrypted Notes
```

### 1.2 Short Description (76 / 80 characters)
```text
Zero-knowledge, offline-first encrypted notes and private vault for Android.
```

### 1.3 Full Description (Ready to paste into Play Console)
```text
Your thoughts, private ideas, and passwords belong to you — not advertisers, not cloud providers, and not us.

CryptoNote is an offline-first, end-to-end encrypted notes app and personal vault for Android. Built with zero-knowledge cryptography, your notes are encrypted directly on your phone before they ever touch storage or cloud sync. Even if our servers were intercepted, your data remains mathematically unbreakable ciphertext.

🔒 ZERO-KNOWLEDGE ENCRYPTION
• Authenticated AEAD: Every note is sealed inside an individual Envelope V1 container using industry-standard AES-256-GCM.
• Hardware-Backed Keys: Your master encryption key is derived using Argon2id (64MB memory cost) and stored inside Android's hardware security chip (TEE / StrongBox Keystore).
• Anti-Forensic Memory Hygiene: Passphrase character arrays and key buffers in RAM are actively zeroized immediately after cryptographic use.

🛡️ DUAL-ZONE ARCHITECTURE
• Everyday Notes: Keep quick meeting notes, recipes, and ideas easily accessible in your standard encrypted notebook.
• Biometric Security Vault: Lock sensitive passwords, recovery phrases, and confidential journals inside a high-security vault that requires biometric fingerprint/face authentication or your master passphrase to reveal.
• Screenshot Protection: Vault mode activates Android's FLAG_SECURE window protection, blocking unauthorized screen captures and task snapshots in the recent apps switcher.

⚡ OFFLINE-FIRST, REAL-TIME SYNC
• Work Anywhere: Create, edit, and organize notes completely offline.
• Deterministic Conflict Resolution: When you reconnect to Wi-Fi or cellular, changes sync smoothly across your authorized devices using monotonic sequence clocks and automatic envelope re-sealing.
• Bandwidth Efficient: Sync payloads are GZIP compressed and transferred over strict TLS 1.3.

📝 CLEAN MARKDOWN EDITOR
• Distraction-free writing experience with live Markdown syntax highlighting.
• Interactive task checklists, bullet lists, code blocks, blockquotes, and dividers.
• Fast search that queries your decrypted local database instantly without sending search queries to any server.

🎨 ADAPTIVE APPEARANCE
• Beautiful luxury color palettes: Espresso, Teal, Emerald, Amber, Blue, Purple, and Rose.
• Match your home screen with dynamic adaptive launcher icons.

🌿 HONEST PRIVACY
• Zero Advertisements: No ads, no sponsored content, and no popups.
• Zero Trackers: No Google Analytics, no Facebook SDK, no advertising identifiers, and no telemetry tracking.
• 100% Open-Source: Inspected, verified, and licensed under Apache 2.0 on GitHub (github.com/RoshanKumar404/CryptoNote).

Take back control of your private notes with CryptoNote.
```

---

## 2. Visual Assets Manifest

All visual assets have been generated, verified, and placed in the project directory ready for upload:

| Asset Type | Specifications | File Path | Status |
| :--- | :--- | :--- | :--- |
| **App Icon** | 512 × 512 px, 32-bit PNG | [`Release/store_icon_512x512.png`](file:///d:/KotlinAps/CryptoNote/Release/store_icon_512x512.png) | ✅ Generated (49 KB) |
| **Feature Graphic** | 1024 × 500 px, 24/32-bit PNG | [`Release/store_feature_graphic_1024x500.png`](file:///d:/KotlinAps/CryptoNote/Release/store_feature_graphic_1024x500.png) | ✅ Generated (80 KB) |
| **Phone Screenshot 1** | 1536 × 2752 px, 9:16 PNG | [`website/assets/showcase-notes-vault.png`](file:///d:/KotlinAps/CryptoNote/website/assets/showcase-notes-vault.png) | ✅ Verified |
| **Phone Screenshot 2** | 768 × 1376 px, 9:16 PNG | [`website/assets/showcase-vault-locked.png`](file:///d:/KotlinAps/CryptoNote/website/assets/showcase-vault-locked.png) | ✅ Verified |
| **Phone Screenshot 3** | 768 × 1376 px, 9:16 PNG | [`website/assets/showcase-editor-markdown.png`](file:///d:/KotlinAps/CryptoNote/website/assets/showcase-editor-markdown.png) | ✅ Verified |
| **Phone Screenshot 4** | 1536 × 2752 px, 9:16 PNG | [`website/assets/showcase-vault-biometric.png`](file:///d:/KotlinAps/CryptoNote/website/assets/showcase-vault-biometric.png) | ✅ Verified |
| **Phone Screenshot 5** | 1536 × 2752 px, 9:16 PNG | [`website/assets/showcase-appearance-themes.png`](file:///d:/KotlinAps/CryptoNote/website/assets/showcase-appearance-themes.png) | ✅ Verified |
| **Phone Screenshot 6** | 1536 × 2752 px, 9:16 PNG | [`website/assets/showcase-profile-audit.png`](file:///d:/KotlinAps/CryptoNote/website/assets/showcase-profile-audit.png) | ✅ Verified |
| **Phone Screenshot 7** | 1536 × 2752 px, 9:16 PNG | [`website/assets/showcase-auth-register.png`](file:///d:/KotlinAps/CryptoNote/website/assets/showcase-auth-register.png) | ✅ Verified |

---

## 3. Store Categorization & Tags

* **Application Category:** `Productivity` (Alternative: `Tools`)
* **Tags (Select up to 5 in Console):**
  1. `Productivity`
  2. `Notes`
  3. `Security`
  4. `Tools`
  5. `Personalization`

---

## 4. Content Rating (IARC Questionnaire Guide)

When completing the International Age Rating Coalition (IARC) questionnaire in Play Console:

1. **Category Selection:** Select **Utility, Productivity, Communication, or Other**.
2. **Violence:** Select **No** (Does not contain violent materials).
3. **Sexuality:** Select **No** (Does not contain nudity or sexual themes).
4. **Language:** Select **No** (No profanity or crude humor).
5. **Controlled Substances:** Select **No** (No references to alcohol, tobacco, or drugs).
6. **Miscellaneous:**
   * Does the app allow users to interact or exchange messages with other users?  
     👉 Select **No** *(CryptoNote is a personal notebook; sync only replicates notes between the user's own logged-in devices).*
   * Does the app share the user's physical location?  
     👉 Select **No**.
   * Does the app allow users to purchase digital goods?  
     👉 Select **No**.
7. **Expected Result:**  
   * **PEGI 3** (Europe)
   * **ESRB Everyone** (North America)
   * **USK 0** (Germany)
   * **ACB G** (Australia)
