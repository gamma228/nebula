# Performance Workflow (/perf)
- Audit Compose recompositions and remember/derivedStateOf usage
- Verify heavy IO and crypto work runs strictly on Dispatchers.IO / default worker pool
- Audit bitmap and image loading memory bounds (downsampling, Coil memory cache limits)
- Check LeakCanary traces in debug runs
- Profile memory allocations during VPN connection lifecycle
