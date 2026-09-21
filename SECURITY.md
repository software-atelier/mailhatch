# Security policy

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability. Use GitHub's private
security advisory feature for this repository, or contact the repository owner privately.
Include affected versions, reproduction steps, and impact if known.

## Supported versions

Until 1.0, only the newest released version is supported with security fixes.

## Scope and deployment notes

MailHatch parses untrusted network input. Keep Java and dependencies current, configure
strict message/recipient/idle limits, and prefer a hardened SMTP proxy for unrestricted
internet exposure. MailHatch does not authenticate the author represented by a message's
`From` header and does not perform SPF, DKIM, DMARC, malware scanning, or content safety
analysis.
