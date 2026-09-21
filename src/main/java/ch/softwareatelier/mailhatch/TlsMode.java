package ch.softwareatelier.mailhatch;

/** TLS behavior for the SMTP listener. */
public enum TlsMode {
    /** Plain SMTP only. Suitable for trusted internal networks and tests. */
    DISABLED,
    /** Plain connection upgraded with the SMTP STARTTLS command. */
    STARTTLS,
    /** TLS starts immediately when the TCP connection opens (commonly port 465). */
    IMPLICIT
}
