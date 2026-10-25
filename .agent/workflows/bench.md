# /bench Workflow: Core Benchmark

Run automated core latency, throughput, connection setup, and memory/CPU benchmarks.

## Execution
```powershell
$env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
& .\scripts\bench\run_benchmarks.ps1
```

## Metrics Collected
1. DNS lookup time (ms)
2. TTFB (ms)
3. 1000 short connections completion time
4. Throughput (downlink/uplink MB/s)
5. UDP/QUIC packet success rate
6. Core startup time (ms)
7. Process memory (RSS MB) and CPU load (%)
