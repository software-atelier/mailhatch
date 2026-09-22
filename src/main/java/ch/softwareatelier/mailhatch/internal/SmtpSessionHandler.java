package ch.softwareatelier.mailhatch.internal;

import ch.softwareatelier.mailhatch.MailEnvelope;
import ch.softwareatelier.mailhatch.MailHandler;
import ch.softwareatelier.mailhatch.MailHatchConfig;
import ch.softwareatelier.mailhatch.RecipientContext;
import ch.softwareatelier.mailhatch.RecipientPolicy;
import ch.softwareatelier.mailhatch.SmtpReplyException;
import ch.softwareatelier.mailhatch.TlsMode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SmtpSessionHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger LOG = LoggerFactory.getLogger(SmtpSessionHandler.class);

    private final MailHatchConfig config;
    private final RecipientPolicy recipientPolicy;
    private final MailHandler handler;
    private final ReloadingSslContextProvider sslContexts;
    private final MimeMessageParser parser = new MimeMessageParser();

    private String helo;
    private String mailFrom;
    private final List<String> recipients = new ArrayList<>();
    private ByteArrayOutputStream data;
    private boolean dataTooLarge;
    private boolean tlsActive;

    public SmtpSessionHandler(MailHatchConfig config, RecipientPolicy recipientPolicy, MailHandler handler,
                              ReloadingSslContextProvider sslContexts, boolean tlsActive) {
        this.config = config;
        this.recipientPolicy = recipientPolicy;
        this.handler = handler;
        this.sslContexts = sslContexts;
        this.tlsActive = tlsActive;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) {
        reply(context, "220 " + config.hostname() + " MailHatch ESMTP ready");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, ByteBuf line) {
        if (data != null) {
            acceptDataLine(context, line);
            return;
        }

        String commandLine = line.toString(StandardCharsets.UTF_8);
        int space = commandLine.indexOf(' ');
        String command = (space < 0 ? commandLine : commandLine.substring(0, space)).toUpperCase(Locale.ROOT);
        String argument = space < 0 ? "" : commandLine.substring(space + 1).trim();

        switch (command) {
            case "EHLO" -> greet(context, argument, true);
            case "HELO" -> greet(context, argument, false);
            case "NOOP" -> reply(context, "250 OK");
            case "RSET" -> { resetTransaction(); reply(context, "250 OK"); }
            case "QUIT" -> reply(context, "221 Bye", true);
            case "STARTTLS" -> startTls(context);
            case "MAIL" -> mailFrom(context, argument);
            case "RCPT" -> recipient(context, argument);
            case "DATA" -> beginData(context);
            default -> reply(context, "502 Command not implemented");
        }
    }

    private void greet(ChannelHandlerContext context, String argument, boolean extended) {
        if (argument.isBlank()) {
            reply(context, "501 HELO requires a domain");
            return;
        }
        helo = argument;
        resetTransaction();
        if (!extended) {
            reply(context, "250 " + config.hostname());
            return;
        }
        var response = new StringBuilder("250-").append(config.hostname())
                .append("\r\n250-SIZE ").append(config.maxMessageBytes())
                .append("\r\n250-8BITMIME")
                .append("\r\n250-SMTPUTF8");
        if (config.tls().mode() == TlsMode.STARTTLS && !tlsActive) response.append("\r\n250-STARTTLS");
        response.append("\r\n250 PIPELINING");
        reply(context, response.toString());
    }

    private void startTls(ChannelHandlerContext context) {
        if (config.tls().mode() != TlsMode.STARTTLS) {
            reply(context, "502 STARTTLS not available");
            return;
        }
        if (tlsActive) {
            reply(context, "503 TLS already active");
            return;
        }
        if (mailFrom != null || !recipients.isEmpty()) {
            reply(context, "503 STARTTLS not permitted during a mail transaction");
            return;
        }
        try {
            var sslContext = sslContexts.current();
            write(context, "220 Ready to start TLS").addListener(ignored -> {
                context.pipeline().addFirst("ssl", sslContext.newHandler(context.alloc()));
                tlsActive = true;
                helo = null;
            });
        } catch (Exception exception) {
            LOG.error("Unable to load TLS certificate", exception);
            reply(context, "454 TLS temporarily unavailable");
        }
    }

    private void mailFrom(ChannelHandlerContext context, String argument) {
        if (helo == null) { reply(context, "503 Send HELO/EHLO first"); return; }
        if (config.tls().requireTls() && !tlsActive) { reply(context, "530 Must issue STARTTLS first"); return; }
        if (!argument.regionMatches(true, 0, "FROM:", 0, 5)) {
            reply(context, "501 Syntax: MAIL FROM:<address>");
            return;
        }
        String path = extractPath(argument.substring(5));
        if (path == null) { reply(context, "501 Invalid reverse-path"); return; }
        resetTransaction();
        mailFrom = path;
        reply(context, "250 OK");
    }

    private void recipient(ChannelHandlerContext context, String argument) {
        if (mailFrom == null) { reply(context, "503 Need MAIL before RCPT"); return; }
        if (!argument.regionMatches(true, 0, "TO:", 0, 3)) {
            reply(context, "501 Syntax: RCPT TO:<address>");
            return;
        }
        String path = extractPath(argument.substring(3));
        if (path == null || path.isEmpty()) { reply(context, "501 Invalid forward-path"); return; }
        if (recipients.size() >= config.maxRecipients()) { reply(context, "452 Too many recipients"); return; }
        try {
            var remote = (InetSocketAddress) context.channel().remoteAddress();
            recipientPolicy.validate(new RecipientContext(
                    mailFrom, path, recipients, helo, remote, tlsActive));
        } catch (SmtpReplyException rejection) {
            reply(context, rejection.responseLine());
            return;
        } catch (Exception failure) {
            LOG.warn("Recipient policy failed", failure);
            reply(context, "451 4.3.0 Recipient policy temporarily unavailable");
            return;
        }
        recipients.add(path);
        reply(context, "250 OK");
    }

    private void beginData(ChannelHandlerContext context) {
        if (mailFrom == null || recipients.isEmpty()) {
            reply(context, "503 Need MAIL and RCPT before DATA");
            return;
        }
        data = new ByteArrayOutputStream();
        dataTooLarge = false;
        reply(context, "354 End data with <CR><LF>.<CR><LF>");
    }

    private void acceptDataLine(ChannelHandlerContext context, ByteBuf line) {
        if (line.readableBytes() == 1 && line.getByte(line.readerIndex()) == '.') {
            finishData(context);
            return;
        }
        int start = line.readerIndex();
        int length = line.readableBytes();
        if (length >= 2 && line.getByte(start) == '.' && line.getByte(start + 1) == '.') {
            start++;
            length--;
        }
        if (!dataTooLarge && (long) data.size() + length + 2 <= config.maxMessageBytes()) {
            byte[] bytes = new byte[length];
            line.getBytes(start, bytes);
            data.writeBytes(bytes);
            data.writeBytes(new byte[]{'\r', '\n'});
        } else {
            dataTooLarge = true;
        }
    }

    private void finishData(ChannelHandlerContext context) {
        byte[] raw = data.toByteArray();
        data = null;
        if (dataTooLarge) {
            resetTransaction();
            reply(context, "552 Message exceeds fixed maximum message size");
            return;
        }

        var remote = (InetSocketAddress) context.channel().remoteAddress();
        var envelope = new MailEnvelope(mailFrom, recipients, helo, remote, Instant.now(), tlsActive);
        try {
            var message = parser.parse(raw, envelope);
            context.channel().config().setAutoRead(false);
            try {
                config.handlerExecutor().execute(() -> {
                    try {
                        handler.handle(message);
                        context.executor().execute(() -> completeHandling(context, null));
                    } catch (Exception exception) {
                        context.executor().execute(() -> completeHandling(context, exception));
                    }
                });
            } catch (RuntimeException rejected) {
                context.channel().config().setAutoRead(true);
                LOG.warn("Handler executor rejected inbound message", rejected);
                resetTransaction();
                reply(context, "451 Requested action aborted: processing unavailable");
            }
        } catch (Exception exception) {
            LOG.debug("Rejected malformed message", exception);
            resetTransaction();
            reply(context, "554 Message could not be parsed");
        }
    }

    private void completeHandling(ChannelHandlerContext context, Exception failure) {
        resetTransaction();
        context.channel().config().setAutoRead(true);
        if (failure == null) {
            reply(context, "250 Message accepted for delivery");
        } else if (failure instanceof SmtpReplyException rejection) {
            LOG.debug("Mail handler rejected message: {}", rejection.responseLine());
            reply(context, rejection.responseLine());
        } else {
            LOG.warn("Mail handler failed", failure);
            reply(context, "451 Requested action aborted: local error in processing");
        }
        context.read();
    }

    private static String extractPath(String input) {
        String value = input.trim();
        if (!value.startsWith("<")) return null;
        int end = value.indexOf('>');
        if (end < 0) return null;
        return value.substring(1, end);
    }

    private void resetTransaction() {
        mailFrom = null;
        recipients.clear();
        data = null;
        dataTooLarge = false;
    }

    private static void reply(ChannelHandlerContext context, String message) {
        write(context, message);
    }

    private static void reply(ChannelHandlerContext context, String message, boolean close) {
        var future = write(context, message);
        if (close) future.addListener(ignored -> context.close());
    }

    private static io.netty.channel.ChannelFuture write(ChannelHandlerContext context, String message) {
        return context.writeAndFlush(Unpooled.copiedBuffer(message + "\r\n", StandardCharsets.US_ASCII));
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
        if (event instanceof IdleStateEvent) {
            reply(context, "421 Idle timeout", true);
        } else {
            super.userEventTriggered(context, event);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        LOG.debug("SMTP session failed", cause);
        context.close();
    }
}
