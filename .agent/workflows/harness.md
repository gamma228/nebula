# Local End-to-End Harness Workflow (/harness)

Run the local block simulation and DNS hijack harness:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/block_simulation_harness.ps1
```

Verification scenarios:
1. DNS hijacking (`hijack-dns` on UDP 53)
2. VLESS Reality handshake simulation
3. WebSocket CDN `Host` header preservation
4. Clean connection teardown and TUN fd release
