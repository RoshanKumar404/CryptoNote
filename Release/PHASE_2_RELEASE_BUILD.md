# Phase 2: Gradle & Release Build Configuration

This document provides a comprehensive record of the configuration, security hardening, and packaging procedures executed during **Phase 2** to produce the production-ready Google Play **Android App Bundle (`.aab`)** for CryptoNote.

---

## 1. Executive Summary

| Attribute | Value |
| :--- | :--- |
| **Output Artifact** | `CryptNote/app/build/outputs/bundle/release/app-release.aab` |
| **Artifact Size** | **9.98 MB** |
| **Artifact Format** | Android App Bundle (AAB) |
| **Target SDK** | API 35 (Android 15 — exceeds Play Store minimum API 34 requirement) |
| **Min SDK** | API 26 (Android 8.0 Oreo) |
| **Version Code** | `5` |
| **Version Name** | `1.4.0` |
| **Optimization** | R8 Minification, Tree-Shaking & Resource Shrinking Enabled |
| **Log Stripping** | `android.util.Log` stripped at bytecode level |
| **Signing Status** | Signed with dedicated 2048-bit RSA upload key |

---

## 2. Release Keystore Generation & Security

### 2.1 Keystore Creation
Google Play requires apps to be cryptographically signed by the developer. An upload keystore was generated using the Java Key and Certificate Management Tool (`keytool`):

```bash
keytool -genkeypair -v \
  -keystore cryptnote-upload-key.jks \
  -alias cryptnote-upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=CryptoNote, OU=Mobile, O=CryptoNote, L=Global, ST=Global, C=US"
```

* **Algorithm**: `RSA 2048-bit`
* **Signature Algorithm**: `SHA384withRSA`
* **Validity Period**: `10,000 days` (~27 years)
* **Alias**: `cryptnote-upload`

### 2.2 Credentials Isolation & Git Protection
To strictly prevent secret leakage to GitHub or public repositories, credentials are abstracted into a local properties file:

* **Properties File**: `CryptNote/keystore.properties`
* **Contents**:
  ```properties
  storeFile=cryptnote-upload-key.jks
  storePassword=<SECRET_KEYSTORE_PASSWORD>
  keyAlias=cryptnote-upload
  keyPassword=<SECRET_KEY_PASSWORD>
  ```
* **Git Exclusions**: Both root and module `.gitignore` files were updated:
  ```gitignore
  # Keystores & Security
  *.jks
  *.keystore
  keystore.properties
  **/keystore.properties
  ```
  *Neither the `.jks` file nor `keystore.properties` will ever be tracked or committed.*

---

## 3. Gradle Signing Configuration

In [`CryptNote/app/build.gradle.kts`](file:///d:/KotlinAps/CryptoNote/CryptNote/app/build.gradle.kts), the release signing configuration was implemented to dynamically read from `keystore.properties` when available:

```kotlin
import java.util.Properties
import java.io.FileInputStream

android {
    ...
    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val properties = Properties().apply {
                    load(FileInputStream(keystorePropertiesFile))
                }
                storeFile = rootProject.file(properties.getProperty("storeFile"))
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

---

## 4. ProGuard / R8 Optimization & Security Rules

To ensure release stability while aggressively shrinking code and stripping sensitive information, the following rules are enforced in [`CryptNote/app/proguard-rules.pro`](file:///d:/KotlinAps/CryptoNote/CryptNote/app/proguard-rules.pro):

### 4.1 Bytecode Log Stripping
Prevents passwords, encryption keys, and internal state from leaking to Android Logcat:
```proguard
# Strip android.util.Log calls in release builds to prevent leaking keys/data
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
```

### 4.2 Reflection & Native Library Keep Rules
Prevents R8 from stripping or obfuscating models, serialization adapters, or JNI symbols required at runtime:
* **Room Database**: `-keep class androidx.room.RoomDatabase { *; }`
* **Kotlinx Serialization**: Preserves `@Serializable`, `@SerialName`, and companion serializers.
* **Retrofit / OkHttp**: Preserves network DTO models in `com.example.cryptnote.data.remote.model.**`.
* **Security & Cryptography**:
  * Bouncy Castle: `-keep class org.bouncycastle.** { *; }`
  * Crypto Manager: `-keep class com.example.cryptnote.security.** { *; }`
  * Google Tink & AndroidX Security: `-keep class com.google.crypto.tink.** { *; }`
* **Hilt & WorkManager**: Preserves `ListenableWorker` and `HiltWorkerFactory` constructors.

---

## 5. Build Execution & Verification

### 5.1 Build Command
```powershell
cd CryptNote
.\gradlew bundleRelease
```

### 5.2 Build Output Summary
```
> Task :app:processReleaseResources
> Task :app:compileReleaseKotlin
> Task :app:compileReleaseJavaWithJavac
> Task :app:hiltJavaCompileRelease
> Task :app:minifyReleaseWithR8
> Task :app:shrinkBundleReleaseResources
> Task :app:signReleaseBundle
> Task :app:bundleRelease

BUILD SUCCESSFUL in 5m 10s
53 actionable tasks: 27 executed, 26 up-to-date
```

* **Generated File**: `CryptNote/app/build/outputs/bundle/release/app-release.aab`
* **Verified Size**: `9,982,878 bytes (~9.98 MB)`

---

## 6. How to Build Future Release Bundles

Whenever a new version is ready to be deployed to the Google Play Store:

1. **Increment Version Numbers** in `CryptNote/app/build.gradle.kts`:
   ```kotlin
   defaultConfig {
       versionCode = 6        // Increment by 1 for each Play Store upload
       versionName = "1.4.1"  // Semantic version string
   }
   ```
2. **Execute Build**:
   ```powershell
   cd CryptNote
   .\gradlew bundleRelease
   ```
3. **Locate Signed Bundle**:
   The finalized bundle will always be created at:
   `CryptNote/app/build/outputs/bundle/release/app-release.aab`
4. **Upload to Google Play Console**:
   Navigate to Google Play Console > *Release* > *Production* (or *Closed testing*) > *Create new release* > Upload `app-release.aab`.
