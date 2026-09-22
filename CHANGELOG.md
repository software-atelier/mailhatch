# Changelog

All notable changes are documented here. This project follows Semantic Versioning.

## [Unreleased]

## [0.3.0] - 2026-09-22

### Added

- `RecipientPolicy` and immutable `RecipientContext` for validation during `RCPT TO`.
- `SmtpReplyException` for deliberate, validated temporary or permanent SMTP replies.

### Security

- Reject response text containing line breaks to prevent SMTP response injection.

## [0.2.0] - 2026-09-21

### Added

- `MailHatchServer.awaitShutdown()` for an explicit, readable standalone lifecycle.

## [0.1.0] - 2026-09-21

### Added

- Embeddable SMTP listener and handler API.
- Plain SMTP, STARTTLS, and implicit TLS support.
- Automatic PEM certificate reload for Let's Encrypt renewals.
- Structured headers, envelope, bodies, attachments, and raw message access.
- Message-size, recipient-count, and idle-time safety limits.
- End-to-end integration tests and production documentation.
