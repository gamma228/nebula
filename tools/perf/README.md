# Nebula Android Performance & ANR Measurement Framework

This directory contains automated scripts and methodologies for benchmarking performance, reproducing "App Not Responding" (ANR) hangs, and measuring battery/memory footprints across diverse Android hardware.

## Tools & Scripts

1. **`measure_metrics.ps1`**:
   - Measures cold startup latency (`am start -W`), extracting `ThisTime` and `TotalTime`.
   - Samples foreground and minimized PSS memory consumption via `dumpsys meminfo`.
   - Samples CPU utilization via `top -b -n 1`.
   - Captures UI framing jank and 90th percentile frame latencies via `dumpsys gfxinfo`.
   - Inspects `MainThreadWatchdog` stall files in `/data/data/app.nebula.vpn/files/stability/`.

2. **`stress_scenarios.ps1`**:
   - **Low-Memory Injection:** Fires `am send-trim-memory` (`RUNNING_CRITICAL`, `COMPLETE`) to simulate aggressive Android LMK/OEM memory pressure.
   - **CPU Contention Burner:** Spawns high-intensity arithmetic threads to simulate weak/overheated low-end CPUs.
   - **Screen Off / Doze Transitions:** Forces power transitions to ensure foreground notifications and timer loops do not freeze.
   - **Rapid Input Storm:** Stresses Compose navigation and tab state machines under rapid user interactions.
   - **Exit Reason Inspection:** Queries `dumpsys activity exit-info` (Android 11+ `ApplicationExitInfo`) for historical crashes, ANRs, or LMK terminations.

## Hardware Profile Approximations (AVD Settings)

To model real-world devices in the field (especially aggressive OEM skins like HyperOS/MIUI, One UI, ColorOS, and budget chipsets):

| Profile | Target Specs | AVD `config.ini` Parameters |
| :--- | :--- | :--- |
| **Low-End Budget (e.g. Redmi 9A / Galaxy A03)** | 2 GB RAM, 2 Cores, API 29/31 | `hw.ramSize = 2048M`, `hw.cpu.ncore = 2`, `vm.heapSize = 128M` |
| **Mid-Range Typical (e.g. Galaxy A52 / Redmi Note 11)** | 4 GB RAM, 4 Cores, API 33/34 | `hw.ramSize = 4096M`, `hw.cpu.ncore = 4`, `vm.heapSize = 256M` |
| **Aggressive OEM Memory Killer** | Trim-memory frequency: 15s | Scripted `am send-trim-memory app.nebula.vpn COMPLETE` |

## Debug Watchdog

In debug builds, `app.nebula.vpn.core.watchdog.MainThreadWatchdog` monitors the Android main looper. If the main thread fails to process the message queue for >= 1500 ms (1.5 seconds), it dumps complete stack traces for all active threads to `/data/data/app.nebula.vpn/files/stability/anr_stall_<timestamp>.txt`.
