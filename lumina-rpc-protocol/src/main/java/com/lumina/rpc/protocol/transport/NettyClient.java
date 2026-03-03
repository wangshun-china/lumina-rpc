package com.lumina.rpc.protocol.transport;

import com.lumina.rpc.protocol.codec.RpcDecoder;
import com.lumina.rpc.protocol.codec.RpcEncoder;
import com.lumina.rpc.protocol.RpcMessage;
import com.lumina.rpc.protocol.spi.Serializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty RPC 客户端
 * 支持连接池管理多个服务端连接
 *
 * 防御性编程特性：
 * 1. 断线自动重连：Channel 失效时自动剔除并重连
 * 2. 优雅停机：提供 shutdown() 方法供外部调用
 * 3. 连接健康检查：获取连接时验证 isActive() 状态
 *
 * 注意：此类不依赖任何 Spring 组件，可独立使用
 */
public class NettyClient {

    private static final Logger logger = LoggerFactory.getLogger(NettyClient.class);

    // 重连最大重试次数
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    // 重连间隔（毫秒）
    private static final long RECONNECT_INTERVAL_MS = 1000;

    // EventLoopGroup
    private final EventLoopGroup eventLoopGroup;

    // Bootstrap
    private final Bootstrap bootstrap;

    // 序列化器
    private final Serializer serializer;

    // 连接池: addressKey(host:port) -> Channel
    private final ConcurrentHashMap<String, Channel> channelPool;

    // 连接状态: addressKey -> 是否已连接
    private final ConcurrentHashMap<String, Boolean> connectionStatus;

    // 关闭标志
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public NettyClient(Serializer serializer) {
        this.serializer = serializer;
        this.eventLoopGroup = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap();
        this.channelPool = new ConcurrentHashMap<>();
        this.connectionStatus = new ConcurrentHashMap<>();
        initBootstrap();
    }

    /**
     * 初始化 Bootstrap
     */
    private void initBootstrap() {
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        // 心跳检测
                        pipeline.addLast(new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS));

                        // 解码器（解决粘包/半包）
                        pipeline.addLast(new RpcDecoder());

                        // 编码器
                        pipeline.addLast(new RpcEncoder(serializer));

                        // 客户端处理器
                        pipeline.addLast(new NettyClientHandler());
                    }
                });
    }

    /**
     * 获取或创建连接
     *
     * 防御性编程：检查 Channel 健康状态，失效连接自动剔除并重连
     *
     * @param address 服务器地址
     * @return Channel
     */
    public Channel getOrConnect(InetSocketAddress address) {
        if (shutdown.get()) {
            throw new IllegalStateException("NettyClient is shutting down");
        }

        String addressKey = address.getHostString() + ":" + address.getPort();

        // 检查连接池中是否有可用连接
        Channel channel = channelPool.get(addressKey);

        // 防御性检查：验证 Channel 是否真正可用
        if (channel != null && channel.isActive()) {
            logger.debug("Reusing existing connection to {}", addressKey);
            return channel;
        }

        // Channel 失效，从连接池中移除
        if (channel != null) {
            logger.warn("💀 Channel for {} is inactive, removing from pool", addressKey);
            channelPool.remove(addressKey);
            connectionStatus.put(addressKey, false);
        }

        // 创建新连接（带重试机制）
        synchronized (this) {
            // 双重检查
            channel = channelPool.get(addressKey);
            if (channel != null && channel.isActive()) {
                return channel;
            }

            logger.info("🔄 Creating new connection to {} (with reconnect)", addressKey);
            return connectWithRetry(address);
        }
    }

    /**
     * 带重试机制的连接方法
     *
     * @param address 目标地址
     * @return 连接成功的 Channel
     */
    private Channel connectWithRetry(InetSocketAddress address) {
        String addressKey = address.getHostString() + ":" + address.getPort();

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RECONNECT_ATTEMPTS; attempt++) {
            try {
                Channel channel = connect(address);
                if (channel != null && channel.isActive()) {
                    return channel;
                }
            } catch (Exception e) {
                lastException = e;
                logger.warn("⚠️ Connection attempt {}/{} failed for {}: {}",
                        attempt, MAX_RECONNECT_ATTEMPTS, addressKey, e.getMessage());
            }

            // 等待重试
            if (attempt < MAX_RECONNECT_ATTEMPTS) {
                try {
                    Thread.sleep(RECONNECT_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 所有重试失败
        connectionStatus.put(addressKey, false);
        logger.error("❌ Failed to connect to {} after {} attempts", addressKey, MAX_RECONNECT_ATTEMPTS);
        throw new RuntimeException("Failed to connect to RPC server after " + MAX_RECONNECT_ATTEMPTS +
                " attempts: " + addressKey, lastException);
    }

    /**
     * 连接到服务器
     *
     * @param address 服务器地址
     * @return 连接成功的 Channel
     */
    public synchronized Channel connect(InetSocketAddress address) {
        String addressKey = address.getHostString() + ":" + address.getPort();

        // 检查是否已连接
        Channel existingChannel = channelPool.get(addressKey);
        if (existingChannel != null && existingChannel.isActive()) {
            return existingChannel;
        }

        try {
            CompletableFuture<Channel> future = new CompletableFuture<>();

            bootstrap.connect(address).addListener((ChannelFutureListener) channelFuture -> {
                if (channelFuture.isSuccess()) {
                    Channel channel = channelFuture.channel();
                    channelPool.put(addressKey, channel);
                    connectionStatus.put(addressKey, true);

                    // 添加连接关闭监听器，清理连接池
                    channel.closeFuture().addListener(f -> {
                        channelPool.remove(addressKey);
                        connectionStatus.remove(addressKey);
                        logger.info("Connection closed: {}", addressKey);
                    });

                    logger.info("Connected to RPC server: {}", addressKey);
                    future.complete(channel);
                } else {
                    connectionStatus.put(addressKey, false);
                    logger.error("Failed to connect to RPC server: {}", addressKey, channelFuture.cause());
                    future.completeExceptionally(channelFuture.cause());
                }
            });

            return future.get(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            connectionStatus.put(addressKey, false);
            logger.error("Exception while connecting to {}", addressKey, e);
            throw new RuntimeException("Failed to connect to RPC server: " + addressKey, e);
        }
    }

    /**
     * 发送 RPC 消息
     *
     * 防御性编程：发送前检查 Channel 健康状态
     *
     * @param address  服务器地址
     * @param message RPC 消息
     */
    public void sendMessage(InetSocketAddress address, RpcMessage message) {
        if (shutdown.get()) {
            throw new IllegalStateException("NettyClient is shutting down");
        }

        Channel channel = getOrConnect(address);
        if (channel == null || !channel.isActive()) {
            // 尝试重新连接
            String addressKey = address.getHostString() + ":" + address.getPort();
            logger.warn("⚠️ Channel inactive for {}, attempting reconnect...", addressKey);

            // 移除失效连接
            channelPool.remove(addressKey);

            // 重新连接
            channel = connectWithRetry(address);
            if (channel == null || !channel.isActive()) {
                throw new IllegalStateException("Failed to establish connection to RPC server: " + address);
            }
        }

        channel.writeAndFlush(message).addListener(future -> {
            if (!future.isSuccess()) {
                logger.error("Failed to send RPC message to {}", address, future.cause());
            }
        });
    }

    /**
     * 发送 RPC 消息（通过已有 Channel）
     *
     * @param channel Channel
     * @param message RPC 消息
     */
    public void sendMessage(Channel channel, RpcMessage message) {
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("Channel is not active");
        }

        channel.writeAndFlush(message).addListener(future -> {
            if (!future.isSuccess()) {
                logger.error("Failed to send RPC message", future.cause());
            }
        });
    }

    /**
     * 检查是否已连接到指定地址
     *
     * @param address 地址
     * @return 是否已连接
     */
    public boolean isConnected(InetSocketAddress address) {
        String addressKey = address.getHostString() + ":" + address.getPort();
        Channel channel = channelPool.get(addressKey);
        return channel != null && channel.isActive();
    }

    /**
     * 关闭指定连接
     *
     * @param address 服务器地址
     */
    public void close(InetSocketAddress address) {
        String addressKey = address.getHostString() + ":" + address.getPort();
        Channel channel = channelPool.remove(addressKey);
        if (channel != null) {
            channel.close();
        }
        connectionStatus.remove(addressKey);
    }

    /**
     * 关闭所有连接（优雅停机）
     *
     * 供外部手动调用，不依赖 Spring 生命周期
     */
    public void shutdown() {
        // 防止重复关闭
        if (!shutdown.compareAndSet(false, true)) {
            logger.info("NettyClient already shut down");
            return;
        }

        logger.info("🛑 [Graceful Shutdown] Shutting down NettyClient...");

        // 1. 关闭所有活跃的 Channel
        int closedChannels = 0;
        for (Map.Entry<String, Channel> entry : channelPool.entrySet()) {
            try {
                Channel channel = entry.getValue();
                if (channel != null && channel.isActive()) {
                    channel.close().await(5, TimeUnit.SECONDS);
                    closedChannels++;
                }
            } catch (Exception e) {
                logger.warn("Error closing channel: {}", entry.getKey(), e);
            }
        }
        logger.info("📡 [Graceful Shutdown] Closed {} channels", closedChannels);

        // 2. 清空连接池
        channelPool.clear();
        connectionStatus.clear();

        // 3. 优雅关闭 EventLoopGroup
        if (eventLoopGroup != null && !eventLoopGroup.isShutdown()) {
            try {
                eventLoopGroup.shutdownGracefully(100, 300, TimeUnit.MILLISECONDS).await();
                logger.info("⚡ [Graceful Shutdown] EventLoopGroup terminated");
            } catch (Exception e) {
                logger.warn("Error during EventLoopGroup shutdown", e);
            }
        }

        logger.info("✅ [Graceful Shutdown] NettyClient shutdown complete");
    }

    /**
     * 获取连接池大小
     *
     * @return 连接数
     */
    public int getConnectionPoolSize() {
        return channelPool.size();
    }
}