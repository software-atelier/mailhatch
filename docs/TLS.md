# TLS and Let's Encrypt

## Modes

- **STARTTLS**: SMTP starts in plaintext and upgrades after `STARTTLS`. Usually ports 25
  or 587. MailHatch requires the upgrade by default.
- **Implicit TLS (SMTPS)**: TLS starts immediately. Usually port 465.
- **Disabled**: intended for tests or a trusted connection behind a terminating proxy.

The certificate must cover the hostname clients use to connect.

## Obtain a certificate

For a dedicated SMTP hostname, create an A/AAAA record first. HTTP validation is convenient
when port 80 can reach Certbot:

```bash
sudo certbot certonly --standalone -d mail.example.com
```

DNS validation is suitable when HTTP validation is unavailable:

```bash
sudo certbot certonly --manual --preferred-challenges dns -d mail.example.com
```

Configure MailHatch with:

```text
/etc/letsencrypt/live/mail.example.com/fullchain.pem
/etc/letsencrypt/live/mail.example.com/privkey.pem
```

`fullchain.pem`, not only `cert.pem`, ensures clients receive intermediate certificates.

## Permissions

The service account needs read access to both files and traversal access to their parent
directories. Do not make the private key world-readable. A common approach is a dedicated
group plus a Certbot deploy hook that copies the files atomically into a group-readable
service directory:

```bash
install -o root -g mailhatch -m 0640 "$RENEWED_LINEAGE/fullchain.pem" /etc/mailhatch/fullchain.pem
install -o root -g mailhatch -m 0640 "$RENEWED_LINEAGE/privkey.pem" /etc/mailhatch/privkey.pem
```

Use temporary files plus `mv` if your deployment mechanism does not replace them atomically.

## Renewal

Test renewal:

```bash
sudo certbot renew --dry-run
```

MailHatch detects changed modification times on the next TLS connection. No signal or
restart is required. If a deploy hook copies files while preserving timestamps, explicitly
update their timestamps or replace them atomically so reload detection sees the change.

## Network checklist

- Publish an A/AAAA record for the SMTP hostname.
- Open the chosen TCP port in host and provider firewalls.
- If accepting internet mail directly, publish an MX record and listen on port 25.
- Ensure the process can bind privileged port 25 without running the full JVM as root,
  for example with systemd socket forwarding or a firewall redirect.
- Test externally with `openssl s_client -starttls smtp -connect mail.example.com:25`.
