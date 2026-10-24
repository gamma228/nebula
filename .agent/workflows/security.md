# Security Audit Workflow (/security)
Perform OWASP MASVS aligned security audit:
- Verify `allowBackup="false"` in AndroidManifest.xml
- Verify `networkSecurityConfig` enforces HTTPS and blocks cleartext traffic
- Verify Android Keystore backed SecretBox implementation (AES-GCM-256)
- Check that intent filters, broadcast receivers, and services are non-exported unless strictly required
- Check that sensitive proxy credentials and server addresses are masked in logs
- Verify absence of analytics, tracking, telemetry, or Google Play Services SDKs
