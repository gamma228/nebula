# libbox Integration Architecture

## Overview
Nebula embeds the official sing-box Go core via `libbox.aar` (Java/JNI bindings produced by `gomobile bind`).

- Pinned Sing-box Tag: `v1.14.4`
- Pinned Sing-box Commit: `c005f35300ce76427dc285481ec5695f5b179cd2`
- libbox.aar SHA-256: `A593A0C4020439F883B20E164C6745375550967E4E25B1B3FA4F66B1543EB996`
- Java Package: `io.nekohasekai.libbox`

## Key Interfaces & Lifecycle

### 1. PlatformInterface
`io.nekohasekai.libbox.PlatformInterface` provides system-level capabilities to the Go runtime:
- `openTun(TunOptions options): Int`: Invoked by sing-box when establishing the TUN device. The Android `VpnService.Builder` MUST be constructed inside this callback using the options provided by libbox (plus application split tunneling rules).
- `autoDetectInterfaceControl(fd: Int)`: Calls `VpnService.protect(fd)` to prevent routing loops.
- `findConnectionOwner(...)`: Resolves the UID for active connections using `ConnectivityManager.getConnectionOwnerUid` (Android 10+).

### 2. CommandServer & Service Lifecycle
Rather than manually creating low-level services, sing-box exposes `CommandServer`:
```kotlin
val server = Libbox.newCommandServer(commandServerHandler, platformInterface)
server.start()
server.startOrReloadService(configJson, overrideOptions)
```
To stop:
```kotlin
server.closeService()
server.close()
```

### 3. CommandClient & Real-Time Stats
The client UI communicates with the core over `CommandClient`:
- Traffic speeds (uplink, downlink, total bytes)
- Log streaming (`writeLogs`)
- Active connection tracking (`writeConnectionEvents`)
- Outbound group latency / health tests (`urlTest`)
