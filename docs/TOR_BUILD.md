# Nebula Android: Tor Engine (Arti + Onionmasq) Native Build Specification

## 1. Upstream Artifact Verification

The native engine is bundled as `app/libs/onionmasq.aar`, built from the official Tor Project `onionmasq` repository (release `1.9.1Beta`):
- **Upstream Source:** `https://gitlab.torproject.org/tpo/core/onionmasq`
- **Release Tag:** `1.9.1Beta` (Git commit `8f7a63d91cf3`)
- **License:** BSD 3-Clause (compatible with Nebula's open-source architecture; see `THIRD_PARTY_NOTICES.md`)
- **SHA-256 Checksum:**
  ```
  89c580cc6f462fc2a346263b414d80a9c779e6966a6e4702dddd576d48444087  app/libs/onionmasq.aar
  ```

---

## 2. ABI Architecture Matrix

The native library `libonionmasq.so` is packaged inside `onionmasq.aar` across 4 Android ABIs:

| Architecture | Target Triple | Min API | Native Size | Target Audience |
| :--- | :--- | :--- | :--- | :--- |
| **`arm64-v8a`** | `aarch64-linux-android` | 26 | ~8.4 MB | 95%+ modern Android smartphones & tablets |
| **`armeabi-v7a`** | `armv7-linux-androideabi` | 26 | ~6.9 MB | Legacy 32-bit Android devices |
| **`x86_64`** | `x86_64-linux-android` | 26 | ~9.1 MB | 64-bit Android Studio Emulators & Chromebooks |
| **`x86`** | `i686-linux-android` | 26 | ~7.8 MB | Legacy 32-bit Android Emulators |

---

## 3. Native Build Pipeline (cargo-ndk)

To rebuild `libonionmasq.so` from source:

### Prerequisites
- **Rust Toolchain:** `rustc 1.78.0+` with standard library targets:
  ```bash
  rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
  cargo install cargo-ndk
  ```
- **Android NDK:** Version `r26d` (26.3.11579264)
- **Environment Variables:**
  ```bash
  export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.3.11579264
  ```

### Build Commands
```bash
git clone https://gitlab.torproject.org/tpo/core/onionmasq.git
cd onionmasq
git checkout 1.9.1Beta

# Build for all ABIs with LTO and size optimization
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -t x86 -o app/src/main/jniLibs build --release

# Package into AAR
./gradlew :onionmasq:assembleRelease
```

---

## 4. Security & Hardening Flags

Native compilation enforces the following compiler flags in `Cargo.toml`:
```toml
[profile.release]
opt-level = "s"
lto = true
codegen-units = 1
panic = "abort"
strip = "debuginfo"
```

- **Stack Canaries:** Enforced by clang NDK compiler (`-fstack-protector-strong`).
- **Position Independent Executable (PIE):** Enforced (`-fPIC`).
- **No Text Relocations:** Verified clean on all ABIs via `readelf -d libonionmasq.so | grep TEXTREL` (0 matches).
