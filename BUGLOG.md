# Nebula Bug & Hardening Log (BUGLOG.md)

This log records every reported problem, its reproduction, root cause analysis, invariant fix, and automated proof.

---

## Index of Defects
- [D1: The VPN does NOT turn off when the button is pressed](#d1-reliable-connectdisconnect)
- [D2: "Check all servers" (latency test) hangs forever](#d2-server-latency-tests-that-never-hang)
- [D3: Connecting does not change anything (blocked sites stay blocked)](#d3-connecting-does-not-change-anything)
- [D4: Connection is slow/unstable ("works badly")](#d4-performance-measure-then-tune)
- [D5: Routing rules screen UX (long scrolling, controls at bottom)](#d5-routing-ui-redesign)
- [D6: Systematic bug sweep & hardening](#d6-systematic-bug-sweep)

---

## D1: Reliable Connect/Disconnect (State Machine)

### Problem Description
The VPN does not disconnect reliably when the disconnect button is pressed: the button may stay in an active/intermediate state, core stops may hang, or rapid toggling causes deadlocks.

### Root Cause Analysis
1. **No Absolute Priority for Disconnect**: If a connect attempt was in flight (waiting on DNS resolution, network handshake, or core setup), calling disconnect queued behind or raced with the connect job, allowing connection to complete *after* the user tapped disconnect.
2. **Unbounded Core Stop**: `proxyCore.stop()` previously waited indefinitely on native Go `closeService()` without a hard timeout. If Go sockets blocked or hung, the disconnect operation never completed.
3. **No Centralized State Machine**: State management was fragmented across `ProxyVpnService`, `VpnRepository`, and various ViewModels/Tile services without a strict single-actor serializer.

### Invariant Fix
1. Implemented `DefaultConnectionManager` as a Hilt singleton owning a strict state machine (`Disconnected`, `Connecting`, `Connected`, `Reconnecting`, `Disconnecting`, `Failed`).
2. Added `_userRequestedDisconnect` flag that permanently suppresses auto-reconnects, kill-switch re-arms, and callbacks until the next explicit connect.
3. Disconnect cancels in-flight jobs immediately with `withTimeoutOrNull(3000L)` on service shutdown, guaranteeing completion within 3 seconds even if native core hangs.
4. `ProxyVpnService.onStartCommand` returns `START_NOT_STICKY`.

### Automated Verification Proof
- `ConnectionManagerTest`:
  - `initial state is Disconnected` -> PASS
  - `disconnect while Connecting cancels in-flight job and finishes in Disconnected` -> PASS
  - `disconnect has 3 second timeout and forces termination if service hangs` -> PASS
  - `rapid 100 connect-disconnect cycles do not deadlock` -> PASS
