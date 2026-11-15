# Subscription Inspector Debug Tool

The Subscription Inspector fetches a subscription URL with a designated User-Agent and analyzes:
- HTTP status code and redirects.
- Selected response headers (`content-type`, `content-encoding`, `content-length`, `subscription-userinfo`, `profile-title`, `profile-update-interval`, `support-url`, and any header containing `hwid` or `device`).
- Payload size and content format detection.
- Parsed proxy profile counts per protocol.
- First 200 characters of the payload with all sensitive tokens, IPs, domains, passwords, and UUIDs strictly masked.

## CLI Usage

1. Save the subscription URL into the git-ignored local file `tools/local/sub_url.txt`:
   ```bash
   echo "https://your-panel.example/sub" > tools/local/sub_url.txt
   ```

2. Run with default Nebula User-Agent:
   ```powershell
   powershell.exe -ExecutionPolicy Bypass -File tools/subscription-inspector/inspect.ps1
   ```

3. Run with custom User-Agent:
   ```powershell
   powershell.exe -ExecutionPolicy Bypass -File tools/subscription-inspector/inspect.ps1 -UserAgent "v2rayNG/1.8.5"
   ```
