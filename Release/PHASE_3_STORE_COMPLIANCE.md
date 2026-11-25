# Phase 3: Google Play Store Policy & Compliance Guide

This guide contains the exact URLs, policies, and questionnaire answers required to pass Google Play Console app review without rejections.

---

## 1. Mandatory Public URLs

| Requirement | Public Live URL |
| :--- | :--- |
| **Privacy Policy URL** | `https://roshankumar404.github.io/CryptoNote/privacy.html` |
| **Account & Data Deletion URL** | `https://roshankumar404.github.io/CryptoNote/privacy.html#data-deletion` |

*Both URLs are served over HTTPS and hosted on your live GitHub Pages showcase domain.*

---

## 2. Google Play Data Safety Form Answers

When completing the **Data Safety** form under *Policy and programs > App content > Data safety* in the Google Play Console, use the exact answers below:

### 2.1 Overview & Security Practices
* **Does your app collect or share any of the required user data types?**  
  👉 Select: **Yes**
* **Is all of the user data collected by your app encrypted in transit?**  
  👉 Select: **Yes** *(Enforced via TLS 1.3 / HTTPS)*
* **Do you provide a way for users to request that their data be deleted?**  
  👉 Select: **Yes**
* **Add a link that users can use to request their data be deleted:**  
  👉 Enter: `https://roshankumar404.github.io/CryptoNote/privacy.html#data-deletion`

---

### 2.2 Data Types Declaration

#### A. Personal Info → Email Address
* **Collected?** 👉 **Yes**
* **Shared?** 👉 **No** *(Never shared with third parties)*
* **Is this data processed ephemerally?** 👉 **No** *(Stored in database for login)*
* **Is this data required for your app, or can users choose whether it's collected?**  
  👉 Select: **Data collection is required** *(Users cannot create a cloud-sync account without an email)*
* **Why is this user data collected?**  
  👉 Select: **Account management**

#### B. Personal Info → Other Personal Info
* 👉 **No** *(No names, phone numbers, addresses, or IDs collected)*

#### C. Messages / User Content → Other User-Generated Content (Notes)
* **Collected?** 👉 **Yes** *(Stored exclusively as zero-knowledge opaque encrypted binary envelopes)*
* **Shared?** 👉 **No**
* **Ephemeral?** 👉 **No**
* **Required or optional?** 👉 **Data collection is required**
* **Why is this user data collected?**  
  👉 Select: **App functionality**

#### D. Financial Info, Location, Health, Photos, Contacts
* 👉 Select: **No** for all categories.

---

## 3. App Content & Policy Declarations

### 3.1 Advertising
* **Does your app contain ads?**  
  👉 Select: **No, my app does not contain ads**

### 3.2 App Access (Reviewer Credentials)
Google reviewers require login credentials to verify authentication and sync:
* **Access instructions:** Select *All or some functionality is restricted*.
* **Credentials to provide in Play Console:**
  - Instructions: *"Create an account or use test credentials: Email: reviewer@example.com, Passphrase: ReviewerPass123!"*

### 3.3 Target Audience and Content
* **Target age groups:**  
  👉 Select: **18 and over** (or 13–17, 18 and over)
* **Could your store listing unintentionally appeal to children?**  
  👉 Select: **No**

### 3.4 Government Apps
* **Is your app developed by or on behalf of a government?**  
  👉 Select: **No**

### 3.5 Financial Features
* **Does your app provide financial features?**  
  👉 Select: **My app doesn't provide any financial features**

---

## 4. Account Deletion Mechanism Verification

Google Play policy requires two distinct deletion mechanisms:

1. **In-App Deletion (Verified):**
   Users can navigate to:  
   *Profile Screen > Tap "Erase & Reset Account"*  
   This immediately executes `authRepository.logout()`, deletes local SQLite databases, zeroizes memory, and triggers an account wipe on the server.
2. **Web-Based Deletion (Verified):**
   Documented publicly on [privacy.html#data-deletion](https://roshankumar404.github.io/CryptoNote/privacy.html#data-deletion). Users can request deletion via email to `roshankuar951@gmail.com`.
