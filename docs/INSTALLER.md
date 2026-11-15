# Nebula Windows Installer Architecture & Specification

## 1. Toolchain Decision: Inno Setup 6 (Modern UI)

For the Windows desktop installer, **Inno Setup 6** was chosen over WiX/MSI and NSIS based on the following engineering criteria:

1. **Lightweight & Self-Contained:** Produces a single, self-extracting, highly compressed executable (`Nebula-Setup-1.2.1.exe`) using LZMA2/ultra64 solid compression. Reduces the 234 MB raw application footprint down to ~110 MB.
2. **Dual-Privilege Support (`PrivilegesRequired=lowest`):**
   - Automatically supports **administrative / per-machine install** to `C:\Program Files\Nebula VPN` when launched with elevation or `/ALLUSERS`.
   - Supports **per-user install** to `%LOCALAPPDATA%\Programs\Nebula VPN` with zero UAC prompts when launched without admin rights or via `/CURRENTUSER`.
3. **High-DPI & Modern Styling:** Employs `WizardStyle=modern` with custom 200% high-DPI bitmaps (`wizard_side.bmp` and `wizard_small.bmp`) and multi-resolution icons (`app.ico` supporting 16, 24, 32, 48, 64, 128, and 256 px).
4. **Active Instance Mutex Protection:** Native `AppMutex=NebulaVPNAppMutex` hook prevents running installations over an active process and enables graceful shutdown.
5. **Clean Uninstallation:** Completely removes application binaries, shortcuts, deep-link protocol registrations, and safely resets Windows system proxy (`ProxyEnable = 0`) to prevent leaving the user without internet.

---

## 2. Installer Options & Tasks

During interactive installation, the user is presented with the following options:

| Task ID | Description | Default | Registry / Action |
| :--- | :--- | :--- | :--- |
| `desktopicon` | Create a desktop shortcut | **Checked** | Creates `{autodesktop}\Nebula VPN.lnk` pointing to `Nebula.exe` with embedded icon. |
| `startmenuicon` | Create Start Menu shortcut | **Checked** | Creates `{autoprograms}\Nebula VPN\Nebula VPN.lnk` and Uninstaller. |
| `autostart` | Start with Windows (minimized to tray) | Unchecked | Writes `HKCU\Software\Microsoft\Windows\CurrentVersion\Run\NebulaVPN` -> `"{app}\Nebula.exe" --minimized`. |
| `protocolassoc` | Deep link associations (`vless://`, `vmess://`) | **Checked** | Registers URL handlers in `HKA\Software\Classes\vless` and `vmess`. |

---

## 3. Silent & Enterprise Deployment Flags

The installer supports standard silent flags for enterprise, MDM, or automated deployment:

```cmd
:: Silent per-user install with desktop shortcut and protocol association
Nebula-Setup-1.2.1.exe /CURRENTUSER /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /TASKS="desktopicon,startmenuicon,protocolassoc"

:: Silent administrative all-users install
Nebula-Setup-1.2.1.exe /ALLUSERS /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /TASKS="desktopicon,startmenuicon,autostart,protocolassoc"
```

---

## 4. Code Signing & Antivirus Guidance

- **SmartScreen Notice:** Unsigned community builds will display Windows SmartScreen ("Windows protected your PC"). Click **More info -> Run anyway**.
- **Antivirus Coexistence:** Proxy cores (`sing-box.exe`) and TUN drivers (`wintun.dll`) perform low-level network routing that heuristics in some third-party AVs may flag. Add an exclusion for `C:\Program Files\Nebula VPN` or `%LOCALAPPDATA%\Programs\Nebula VPN` if blocked.
