# Emulator & E2E Testing Workflow (/emulator)
- Boot Android Emulator (API 34/35 x86_64)
- Run local proxy server on host (e.g. Shadowsocks/VLESS server)
- Connect app to `10.0.2.2:<port>`
- Verify tunnel establishment and packet flow via `adb shell curl -v`
- Test kill-switch by halting upstream proxy and checking packet drop
- Test reconnect and split tunneling
