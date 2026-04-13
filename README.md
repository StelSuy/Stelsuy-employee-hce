<p align="center">
  <img src="https://img.shields.io/badge/Android-API_26+-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white"/>
  <img src="https://img.shields.io/badge/NFC-HCE-00BCD4?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Android_Keystore-RSA--2048-FF5722?style=for-the-badge"/>
</p>

<h1 align="center">🪪 Stelsuy Employee HCE</h1>
<h3 align="center">Android NFC Badge · Android NFC Бейдж</h3>

<p align="center">
  Part of the <strong>Stelsuy</strong> employee attendance system.<br/>
  Turns an employee's Android phone into a cryptographically secure NFC smart card.
</p>

<p align="center">
  <a href="#english">🇬🇧 English</a> &nbsp;|&nbsp;
  <a href="#ukrainian">🇺🇦 Українська</a>
</p>

---

## 🇬🇧 English <a name="english"></a>

### Overview

**Stelsuy Employee HCE** is an Android application that uses **Host-based Card Emulation (HCE)** to make the employee's phone behave exactly like a contactless NFC smart card. When the phone is tapped against the [Stelsuy Terminal](https://github.com/StelSuy/Stelsuy-terminal-android), it responds to APDU commands — providing the employee's unique ID, public key, and cryptographic signatures — all without any internet connection on the employee's side.

The private RSA key **never leaves the device**. It is generated and stored inside the **Android Keystore**, which is hardware-backed on devices with a secure element (TEE / StrongBox).

### How It Works

```
┌────────────────────────────────────────────────────────────┐
│               HCE Badge — Tap to Authenticate              │
│                                                            │
│  Employee Phone (this app)    NFC Terminal (scanner app)   │
│  ─────────────────────────    ──────────────────────────── │
│                                                            │
│  [App runs in background]                                  │
│       │                                                    │
│       │◄──── NFC field detected ──────────────────────────│
│       │                                                    │
│       │◄──── SELECT AID (F0010203040506) ────────────────│
│       │──── 90 00 (OK) ──────────────────────────────────►│
│       │                                                    │
│       │◄──── GET_EMP (00 CA 00 00 00) ───────────────────│
│       │──── "EMP:<uid>" + 90 00 ────────────────────────►│
│       │                                                    │
│  [Registration only]                                       │
│       │◄──── GET_PUB (00 CC 00 00 00) ───────────────────│
│       │──── "PUB:<base64_public_key>" + 90 00 ──────────►│
│       │                                                    │
│  [Every secure scan]                                       │
│       │◄──── SIGN (00 CB <challenge_bytes>) ─────────────│
│       │  Android Keystore signs with private RSA key      │
│       │──── "<signature_base64>" + 90 00 ───────────────►│
│       │                                                    │
│  📳 Vibrates on successful sign                            │
└────────────────────────────────────────────────────────────┘
```

### Key Features

| Feature | Description |
|---|---|
| 📱 **HCE Smart Card** | Phone emulates a contactless ISO 14443-4 card — no physical card needed |
| 🔑 **Android Keystore** | RSA-2048 private key is generated on-device, hardware-backed, never exported |
| ✍️ **On-device Signing** | Signs server challenges with `SHA256withRSA` — private key never leaves the device |
| 🆔 **Persistent Employee ID** | UUID generated once on first launch and stored in SharedPreferences |
| 📴 **Works in Background** | HCE service responds to NFC taps even when the app is minimized or the screen is off |
| 📳 **Vibration Feedback** | Short haptic pulse when a challenge is successfully signed |
| 🔒 **Enable / Disable Badge** | User can deactivate HCE responses — terminal receives `DISABLED` and denies access |
| 📋 **Copy Employee Code** | One-tap copy of the employee UUID for manual registration by an admin |
| ⚠️ **NFC Status Warning** | Detects if NFC is disabled on the device and shows a direct link to NFC settings |

### Security Design

```
┌──────────────────────────────────────────────────────┐
│                  Security Properties                 │
│                                                      │
│  ✅ Private key hardware-backed (TEE / StrongBox)    │
│  ✅ Private key never exported or transmitted        │
│  ✅ Each scan uses a unique server-issued nonce      │
│  ✅ Replay attacks impossible (nonce consumed once)  │
│  ✅ No internet connection required on employee side │
│  ✅ Badge can be disabled remotely via admin panel   │
│  ✅ "DISABLED" response denies access instantly      │
└──────────────────────────────────────────────────────┘
```

### APDU Command Reference

The app responds to the following ISO 7816-4 APDU commands:

| Command | Bytes | Condition | Response |
|---|---|---|---|
| SELECT AID | `00 A4 04 00 07 F001...` | Always | `90 00` |
| GET\_EMP | `00 CA 00 00 00` | Enabled | `EMP:<uid> 90 00` |
| GET\_PUB | `00 CC 00 00 00` | Enabled | `PUB:<base64> 90 00` |
| SIGN | `00 CB <challenge>` | Enabled | `<signature_b64> 90 00` |
| Any (disabled) | — | Badge disabled | `DISABLED 90 00` |
| Unknown | — | — | `6A 82` (not found) |

**AID:** `F0010203040506` (proprietary, registered in `AndroidManifest.xml`)

### Project Structure

```
stelsuy-employee-hce/
├── app/src/main/
│   ├── java/com/stelsuy/employee/hce/
│   │   ├── MainActivity.kt       # UI: show employee code, NFC status
│   │   ├── NfcCardService.kt     # HCE service — APDU command handler
│   │   ├── Crypto.kt             # Android Keystore RSA key management
│   │   └── Prefs.kt              # SharedPreferences: employee UUID, enabled flag
│   ├── res/
│   │   ├── layout/
│   │   │   └── activity_main.xml
│   │   └── xml/
│   │       └── apduservice.xml   # AID registration for HCE
│   └── AndroidManifest.xml       # NFC + HCE service declaration
├── build.gradle
└── settings.gradle
```

### Cryptographic Implementation

Key generation and signing use the **Android Keystore system**:

```kotlin
// Key generation (once, on first launch)
KeyPairGenerator.getInstance(KEY_ALGORITHM_RSA, "AndroidKeyStore")
    .initialize(
        KeyGenParameterSpec.Builder(KEY_ALIAS,
            PURPOSE_SIGN or PURPOSE_VERIFY)
            .setDigests(DIGEST_SHA256)
            .setSignaturePaddings(SIGNATURE_PADDING_RSA_PKCS1)
            .build()
    ).generateKeyPair()

// Signing a challenge (on every NFC tap)
Signature.getInstance("SHA256withRSA")
    .apply { initSign(privateKey); update(challengeBytes) }
    .sign()   // bytes never leave the Keystore
```

The **public key** (DER-encoded, Base64) is shared with the server during registration and stored in the employee record for future signature verification.

### Requirements

- Android **8.0 (API 26)** or higher
- Device with **NFC** hardware and **HCE support**
- NFC must be **enabled** in system settings for the badge to work

### Build & Install

**From Android Studio:**
1. Open the project in Android Studio Hedgehog or newer
2. Sync Gradle
3. Connect a physical device (NFC / HCE not available on emulators)
4. Run → the employee code is shown immediately on screen

**From command line:**
```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

### Employee Onboarding Flow

```
1. Employee installs the app
2. App generates a UUID (employee code) and an RSA key pair
3. Employee shows the code to an admin  ─OR─  taps the terminal in Register Mode
4. Admin assigns the employee to the code in the admin panel
5. On next tap → the terminal performs a full secure scan ✅
```

### Related Repositories

| Repository | Description |
|---|---|
| [stelsuy-backend](https://github.com/StelSuy/diplom_v2) | FastAPI backend — attendance server |
| [stelsuy-terminal](https://github.com/StelSuy/Stelsuy-terminal-android) | Android HCE badge app (employee phone) |

---

## 🇺🇦 Українська <a name="ukrainian"></a>

### Огляд

**Stelsuy Employee HCE** — Android-додаток, що перетворює телефон співробітника на безконтактну NFC смарт-картку за допомогою **Host-based Card Emulation (HCE)**. При піднесенні телефону до терміналу [Stelsuy Terminal](https://github.com/your-org/stelsuy-terminal-android) додаток відповідає на APDU-команди — надає унікальний ідентифікатор, публічний ключ і криптографічні підписи.

Приватний RSA-ключ **ніколи не покидає пристрій** — він зберігається в **Android Keystore** і захищений апаратним безпечним елементом (TEE / StrongBox).

### Ключові можливості

- **📱 HCE смарт-картка** — телефон емулює безконтактну ISO 14443-4 картку без жодної фізичної картки
- **🔑 Android Keystore** — RSA-2048 ключ генерується на пристрої, апаратно захищений, не може бути експортований
- **✍️ Підпис на пристрої** — підписує server challenge алгоритмом `SHA256withRSA` — приватний ключ не передається по мережі
- **📴 Робота у фоні** — HCE-сервіс відповідає на NFC-торкання навіть коли додаток мінімізований або екран вимкнений
- **📳 Вібрація** — короткий haptic-сигнал при успішному підписі
- **🔒 Вмикання/вимикання бейджа** — користувач може деактивувати бейдж; термінал отримає відповідь `DISABLED` і відмовить у доступі
- **📋 Копіювання коду** — одним натисканням скопіювати UUID для ручної реєстрації адміністратором
- **⚠️ Статус NFC** — якщо NFC вимкнено на пристрої — показується попередження з прямим посиланням на налаштування

### Процес реєстрації співробітника

```
1. Співробітник встановлює додаток
2. Додаток генерує UUID та RSA-ключову пару
3. Код показують адміністратору — АБО — підносять телефон до терміналу в режимі реєстрації
4. Адміністратор прив'язує співробітника до коду в адмін-панелі
5. При наступному скануванні — повний захищений скан ✅
```

### Вимоги

- Android **8.0 (API 26)** або вище
- Пристрій з апаратним **NFC** та підтримкою **HCE**
- **NFC** має бути увімкнено в налаштуваннях системи

### Збірка та встановлення

```bash
git clone https://github.com/your-org/stelsuy-employee-hce.git
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

> ⚠️ HCE не підтримується на емуляторах Android — тестування лише на фізичному пристрої з NFC.

---

<p align="center">
  Розроблено як частина дипломного проєкту · 2026
</p>
