# CooBS Client Testing Guide: Mobile & Web Frontend

Comprehensive end-to-end testing guide for the **Flutter Customer Mobile App** (`app/`) and the **React Teller Web Portal** (`frontend/`), connected to the **CooBS Microservices Backend** via Docker.

---

## 1. Prerequisites & Test Accounts

### 1.1 Backend Services
Ensure Docker containers are running and healthy before launching clients:
```powershell
docker compose ps
```
The Spring Cloud Gateway must be listening on port **8080** (`http://localhost:8080`). You can verify its health via:
```powershell
curl http://localhost:8080/actuator/health
```

### 1.2 Pre-Seeded Test Credentials
| Role | Username | Password | Account Number | Initial Balance | Intended Client |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Retail Customer** | `john_doe` | `Password123!` | `ACC_10000001` | ₱50,000.00 | **Mobile App** (`app/`) |
| **Retail Customer** | `jane_smith` | `Password123!` | `ACC_10000002` | ₱25,000.00 | Transfer Recipient |
| **Branch Teller / Staff** | `teller_alice` | `Password123!` | N/A | N/A | **Web Portal** (`frontend/`) |

---

## 2. Web Frontend Testing (React / Vite)

The web frontend is dedicated to **Branch Tellers and Operations Staff** for account lifecycle management, KYC approval, and transaction tracking.

### 2.1 Starting the Dev Server
From the repository root:
```powershell
cd frontend
npm install
npm run dev
```
Open **[http://localhost:3000](http://localhost:3000)** in your browser.

> **Network Flow:** The Vite dev server proxies requests from `/api/*` and `/actuator/*` directly to `http://localhost:8080` (Spring Cloud Gateway), avoiding CORS issues in development.

### 2.2 Test Scenarios to Verify
1. **Teller Login:** Sign in with `teller_alice` / `Password123!`.
2. **Account Inquiries:** Search and view accounts `ACC_10000001` and `ACC_10000002`.
3. **Audit & Saga Stepper:** Submit a transfer and verify the 11-stage technical saga timeline.
4. **Session Inactivity Timeout:** Leave the portal inactive for 15 minutes; observe auto-logout and session purge.
5. **Secure Logout:** Click logout and inspect Network tab to confirm `POST /api/v1/auth/token/revoke` is called and `sessionStorage` is cleared.

### 2.3 Running Web Frontend Checks
```powershell
cd frontend
npm run lint    # Oxlint static analysis
npm run build   # Production TypeScript compilation
```

---

## 3. Mobile App Testing (Flutter)

The mobile app is built for **Retail Bank Members** self-service (dashboard composite, 3-stage milestone timeline, P2P remittances).

### 3.1 Choosing Your Testing Setup

| Setup Mode | Best Used For | Requirement | Gateway URL Passed |
| :--- | :--- | :--- | :--- |
| **Mode A: True Wi-Fi LAN** *(Recommended)* | Physical Android Phone/Tablet | PC and Phone on same Wi-Fi | `http://<PC_WIFI_IP>:8080` |
| **Mode B: USB / ADB Reverse** | Physical Device with USB cable | ADB connected via USB | `http://localhost:8088` *(after reverse)* |
| **Mode C: Android Emulator** | Development without physical device | Android Studio Emulator | Default (`http://10.0.2.2:8080`) |
| **Mode D: Air-Gapped Fixture** | Offline UI review, zero backend | None | Offline mock mode (`USE_FIXTURES=true`) |

---

### Mode A: True Wi-Fi LAN Testing (Recommended for Physical Devices)

This tests real wireless communication without depending on USB cables or ADB tunnels.

#### Step 1: Find your PC's Wi-Fi IP Address
In PowerShell on your host PC:
```powershell
ipconfig
```
Look for your active **Wireless LAN adapter Wi-Fi** `IPv4 Address` (e.g., `192.168.254.126`).

#### Step 2: Ensure Phone and PC are on the Same Wi-Fi Network
Both devices must be connected to the same wireless router or mobile hotspot.

#### Step 3: Run the App
From the `CooBS/app` directory:
```powershell
cd app
flutter run --dart-define=GATEWAY_URL=http://<YOUR_PC_IP>:8080
```
*Example:*
```powershell
flutter run --dart-define=GATEWAY_URL=http://192.168.254.126:8080
```

> **Why this works:** The app's `android/app/src/main/res/xml/network_security_config.xml` permits cleartext HTTP traffic to private local networks (`192.168.x.x`, `10.x.x.x`, `172.16-31.x.x`).

---

### Mode B: USB / ADB Reverse Testing

Use this if your Wi-Fi router blocks client-to-client communication (AP Isolation) or if you are tethered via USB.

#### ⚠️ Critical Samsung / Android Gotcha (Port 8080 Conflict)
On many physical Android devices (especially Samsung One UI), port `8080` is internally bound by a system daemon. Running `adb reverse tcp:8080 tcp:8080` fails with `cannot bind listener: Address already in use`.  
**Solution:** We map device port **`8088`** to host port **`8080`**.

#### Step 1: Connect Device via USB and Enable USB Debugging
Confirm ADB detects your device:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```

#### Step 2: Set Up ADB Reverse Port Forwarding
Run:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8088 tcp:8080
```
*(Verify by running `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse --list` — it should output `(reverse) tcp:8088 tcp:8080`).*

> **Notice:** If you unplug the USB cable or restart ADB, the reverse tunnel is reset and you must re-run this command.

#### Step 3: Run the App Targeting Port 8088
```powershell
cd app
flutter run --dart-define=GATEWAY_URL=http://localhost:8088
```

---

### Mode C: Android Emulator Testing
If using the Android Studio Emulator:
```powershell
cd app
flutter run
```
No `--dart-define` is needed; `AppConfig` automatically routes Android emulators to `http://10.0.2.2:8080`.

---

### Mode D: Air-Gapped Fixture Mode (Offline / Zero Backend)
To test UI layouts and flows without running Docker microservices:
```powershell
cd app
flutter run --dart-define=USE_FIXTURES=true
```

---

## 4. Security Guardrails Verification Checklist

The following banking security controls are enforced on both client and backend (`mobile-bff`):

| Test Case | Steps to Test | Expected Behavior |
| :--- | :--- | :--- |
| **₱500,000 Transfer Ceiling** | Attempt to transfer `₱500,001.00` | UI blocks submission; backend rejects with `400 Bad Request` ("Transfer amount exceeds regulatory ceiling"). |
| **Self-Transfer Prevention** | Set source `ACC_10000001` and destination `ACC_10000001` | Mobile UI disables submit; backend rejects with `400 Bad Request` ("Source and destination accounts must be distinct"). |
| **Brute-Force Lockout** | Enter wrong password 5 consecutive times | App locks out login attempts for 30 seconds with an active countdown timer. |
| **Remarks Sanitization** | Enter `<script>alert('xss')</script>` or multi-line CRLF in remittance remarks | Script tags and illegal characters are stripped; maximum 150 characters enforced. |
| **Session Inactivity Timeout** | Leave app or web portal idle for 15 minutes | Inactivity listener invalidates token on backend (`POST /auth/token/revoke`), purges secure storage, and returns user to login screen. |

---

## 5. Troubleshooting Common Issues

### Issue 1: `ClientException: SocketException: Connection refused (errno = 111)`
- **Cause A (Using `localhost:8088`):** The `adb reverse` tunnel was cleared when the device was disconnected or ADB cycled.
  - **Fix:** Re-run `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8088 tcp:8080`.
- **Cause B (Using Wi-Fi LAN IP):** Your PC's Wi-Fi IP changed (DHCP lease renewed) or Windows Firewall is blocking inbound connections to port 8080.
  - **Fix:** Run `ipconfig` to verify current IP. Test from device using:
    ```powershell
    & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell curl -m 3 http://<YOUR_PC_IP>:8080/actuator/health
    ```
- **Cause C:** Docker containers are not running. Run `docker compose ps` and verify `gateway` is UP.

### Issue 2: PowerShell `Unexpected token 'connect' in expression or statement`
- **Cause:** Quoted path without the PowerShell invocation call operator `&`.
  - **Fix:** Always prepend `&` when running quoted executable paths:
    ```powershell
    & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" connect 192.168.254.181:44283
    ```

### Issue 3: `cannot bind listener: Address already in use` during `adb reverse`
- **Cause:** Android device's internal system service is using port 8080.
  - **Fix:** Forward to port 8088 instead:
    ```powershell
    & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8088 tcp:8080
    flutter run --dart-define=GATEWAY_URL=http://localhost:8088
    ```

---

## 6. Verification & Automated Test Commands

### Mobile App Unit & Widget Tests:
```powershell
cd app
flutter test
flutter analyze
```
*(All 28 unit and widget tests must pass with 0 analyze warnings).*

### Mobile-BFF Backend Tests:
```powershell
cd backend/mobile-bff
mvn test
```
*(All 17 integration and security unit tests must pass).*

### Web Frontend Linter & Build:
```powershell
cd frontend
npm run lint
npm run build
```
