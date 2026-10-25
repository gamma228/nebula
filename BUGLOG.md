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


---

## D2: Server Latency Tests That Never Hang

### Problem Description
When the user tapped "Check latency" or "Ping all", the entire application froze and stopped responding. Additionally, tapping the ping button on an individual subscription was pinging all servers across all subscriptions simultaneously, locking up the UI until all socket connections completed or timed out (often taking minutes). There was no cancellation button, no live progress reporting, and no graceful handling of offline/hanging endpoints.

### Root Cause Analysis
1. **Unbounded Blocking Network Calls**: Previous ping implementations performed synchronous socket handshakes on IO without strict worker pooling, exhausting thread pools and starving UI coroutines when multiple endpoints were offline.
2. **Subscription Isolation Defect**: The subscription-level ping trigger mistakenly fell through to full-catalog ping or broadcasted global ping state to all subscriptions.
3. **Absence of Cancellation Token & Dual Timeouts**: Once started, a test could not be cancelled. Sockets relied on default operating system TCP timeouts (often 30+ seconds per attempt) rather than short bounded timeouts.
4. **Room Transaction Locking on UI Flow**: Database writes for ping results were performed per-item during the ping loop rather than batched at completion, inducing database contention.

### Invariant Fix
1. **`DefaultLatencyTester` with Bounded Concurrency**: Enforced `Semaphore(6)` concurrency limit, preventing socket pool exhaustion and ensuring smooth non-blocking execution.
2. **Dual Timeout Enforcement**: Every probe is wrapped in both a coroutine-level `withTimeout(5000L)` and socket-level `soTimeout` / `connectTimeout = 5000ms`. In addition, a global batch timeout (`withTimeoutOrNull(30000L)`) bounds total execution.
3. **Structured Cancellation**: Implemented `cancel()` which immediately aborts active coroutine jobs, cleans up sockets, and safely sets all in-flight probes to `ServerTestState.Timeout`.
4. **Reactive Live Progress & Strict Subscription Scope**:
   - Exposed `StateFlow<BatchTestProgress>` streaming completed/total counts and individual server states (`Pending`, `Testing`, `Ok(ms)`, `Timeout`, `Error`).
   - `pingSubscriptionProfiles(subscriptionId)` strictly filters and tests only servers belonging to the target subscription.
   - Live UI displays progress bar, server spinners, and a one-tap Cancel (`X`) button.
5. **Selectable Probing Strategies**: Added `ProbeMethod` support for `TCP_HANDSHAKE`, `TLS_HANDSHAKE` (with SNI negotiation), and `HTTP_GET` (generate_204).
6. **Batched Persistence**: Results are collected in-memory during testing and committed in a single batch transaction (`profileRepository.updatePingsBatch`) once the run finishes.

### Automated Verification Proof
- `LatencyTesterTest`:
  - `hung servers trigger timeout without stalling queue` -> PASS
  - `cancel aborts in-flight batch and leaves no server in Testing state` -> PASS
