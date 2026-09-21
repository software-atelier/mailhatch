package ch.softwareatelier.mailhatch;

import ch.softwareatelier.mailhatch.internal.ReloadingSslContextProvider;
import ch.softwareatelier.mailhatch.internal.SmtpSessionHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Embeddable SMTP server. Create, start, and close one instance per listener. */
public final class MailHatchServer implements AutoCloseable {
    private final MailHatchConfig config;
    private final MailHandler handler;
    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1,
            new DefaultThreadFactory("mailhatch-acceptor", true));
    private final EventLoopGroup workerGroup = new NioEventLoopGroup(0,
            new DefaultThreadFactory("mailhatch-worker", true));
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile Channel serverChannel;

    /** @param config listener configuration @param handler application callback */
    public MailHatchServer(MailHatchConfig config, MailHandler handler) {
        this.config = Objects.requireNonNull(config, "config");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /** Starts listening. Port 0 may be used to select a free port for tests. @return this server */
    public synchronized MailHatchServer start() {
        if (!started.compareAndSet(false, true)) throw new IllegalStateException("Server already started");
        ReloadingSslContextProvider sslContexts = config.tls().mode() == TlsMode.DISABLED
                ? null : new ReloadingSslContextProvider(config.tls());
        try {
            var bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) throws Exception {
                            boolean implicitTls = config.tls().mode() == TlsMode.IMPLICIT;
                            if (implicitTls) {
                                channel.pipeline().addLast("ssl", sslContexts.current().newHandler(channel.alloc()));
                            }
                            channel.pipeline().addLast("idle", new IdleStateHandler(
                                    config.idleTimeout().toSeconds(), 0, 0, TimeUnit.SECONDS));
                            channel.pipeline().addLast("lines", new LineBasedFrameDecoder(1_048_576, true, true));
                            channel.pipeline().addLast("smtp", new SmtpSessionHandler(
                                    config, handler, sslContexts, implicitTls));
                        }
                    });
            serverChannel = bootstrap.bind(config.bindAddress(), config.port()).syncUninterruptibly().channel();
            return this;
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    /** Returns the actual bound port; useful when configured with port 0. @return listener port */
    public int port() {
        Channel channel = serverChannel;
        if (channel == null) throw new IllegalStateException("Server is not started");
        return ((InetSocketAddress) channel.localAddress()).getPort();
    }

    /** @return whether the listener channel is active */
    public boolean isRunning() {
        Channel channel = serverChannel;
        return channel != null && channel.isActive();
    }

    @Override
    public synchronized void close() {
        Channel channel = serverChannel;
        serverChannel = null;
        if (channel != null) channel.close().syncUninterruptibly();
        bossGroup.shutdownGracefully().syncUninterruptibly();
        workerGroup.shutdownGracefully().syncUninterruptibly();
    }
}
