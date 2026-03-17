package com.lumina.rpc.core.transport;

import com.lumina.rpc.protocol.codec.RpcDecoder;
import com.lumina.rpc.protocol.codec.RpcEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty RPC 服务器
 *
 * 防御性编程特性：
 * 1. 优雅停机：实现 @PreDestroy，确保 Spring 关闭时正确释放资源
 * 2. Boss/Worker 线程组优雅关闭
 * 3. 关闭超时控制
 */
public class NettyServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);

    // 优雅关闭等待时间（秒）
    private static final int GRACEFUL_SHUTDOWN_QUIET_PERIOD = 3;
    private static final int GRACEFUL_SHUTDOWN_TIMEOUT = 10;

    // Boss EventLoopGroup，用于处理连接请求
    private final EventLoopGroup bossGroup;

    // Worker EventLoopGroup，用于处理 I/O 操作
    private final EventLoopGroup workerGroup;

    // ServerBootstrap
    private final ServerBootstrap serverBootstrap;

    // 请求处理器
    private final RpcRequestHandler requestHandler;

    // 绑定的端口
    private int port;

    // 运行状态
    private volatile boolean running = false;

    // 关闭标志
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public NettyServer(RpcRequestHandler requestHandler) {
        this.requestHandler = requestHandler;
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
        this.serverBootstrap = new ServerBootstrap();
        initBootstrap();
    }

    /**
     * 初始化 ServerBootstrap
     */
    private void initBootstrap() {
        serverBootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        // 心跳检测（60秒读超时，30秒写超时）
                        pipeline.addLast(new IdleStateHandler(60, 30, 0, TimeUnit.SECONDS));

                        // 解码器（解决粘包/半包）
                        pipeline.addLast(new RpcDecoder());

                        // 编码器（动态选择序列化器）
                        pipeline.addLast(new RpcEncoder());

                        // 服务器处理器
                        pipeline.addLast(new NettyServerHandler(requestHandler));
                    }
                });
    }

    /**
     * 启动服务器
     *
     * @param port 端口号
     *
     * 💡 [虚拟线程预告] 💡
     * ============================================================
     * 此处未来可引入 Java 21 虚拟线程进一步提升高并发吞吐量：
     *
     *   // 替换传统的线程池
     *   ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
     *
     * 虚拟线程 (Virtual Threads) 优势：
     * - 百万级并发连接，内存占用极低
     * - 无需手动管理线程池
     * - 与现有代码完全兼容
     *
     * 适用场景：高吞吐量的 RPC 服务、即时通讯网关
     * ============================================================
     */
    public void start(int port) {
        if (running) {
            logger.warn("Server is already running on port {}", this.port);
            return;
        }

        this.port = port;

        try {
            // 显式绑定到 0.0.0.0，确保容器内外都能访问（IPv4）
            ChannelFuture future = serverBootstrap.bind(new InetSocketAddress("0.0.0.0", port)).sync();
            this.running = true;
            logger.info("Netty RPC server started on 0.0.0.0:{}", port);

            // 等待服务器关闭
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error("Server interrupted", e);
            Thread.currentThread().interrupt();
        } finally {
            shutdown();
        }
    }

    /**
     * 异步启动服务器
     *
     * @param port 端口号
     * @return ChannelFuture
     */
    public ChannelFuture startAsync(int port) {
        if (running) {
            logger.warn("Server is already running on port {}", this.port);
            throw new IllegalStateException("Server is already running");
        }

        this.port = port;

        // 显式绑定到 0.0.0.0，确保容器内外都能访问（IPv4）
        ChannelFuture future = serverBootstrap.bind(new InetSocketAddress("0.0.0.0", port));
        future.addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                this.running = true;
                logger.info("Netty RPC server started on 0.0.0.0:{}", port);
            } else {
                logger.error("Failed to start server on port {}", port, f.cause());
            }
        });

        return future;
    }

    /**
     * 关闭服务器
     *
     * 防御性编程：实现优雅停机
     * 1. 防止重复关闭
     * 2. 先停止接收新连接
     * 3. 等待现有请求处理完成
     * 4. 关闭 Boss 和 Worker 线程组
     */
    @PreDestroy
    public void shutdown() {
        // 防止重复关闭
        if (!shutdown.compareAndSet(false, true)) {
            logger.info("NettyServer already shut down");
            return;
        }

        if (!running) {
            return;
        }

        logger.info("🛑 [Graceful Shutdown] Shutting down Netty RPC server on port {}...", port);

        // 1. 先标记为非运行状态，停止接受新连接
        this.running = false;

        // 2. 优雅关闭 WorkerGroup（处理 I/O 的线程组）
        if (workerGroup != null && !workerGroup.isShutdown()) {
            try {
                Future<?> workerFuture = workerGroup.shutdownGracefully(
                        GRACEFUL_SHUTDOWN_QUIET_PERIOD,
                        GRACEFUL_SHUTDOWN_TIMEOUT,
                        TimeUnit.SECONDS
                );
                workerFuture.await(15, TimeUnit.SECONDS);
                logger.info("📡 [Graceful Shutdown] WorkerGroup terminated");
            } catch (Exception e) {
                logger.warn("Error during WorkerGroup shutdown", e);
            }
        }

        // 3. 优雅关闭 BossGroup（接受连接的线程组）
        if (bossGroup != null && !bossGroup.isShutdown()) {
            try {
                Future<?> bossFuture = bossGroup.shutdownGracefully(
                        GRACEFUL_SHUTDOWN_QUIET_PERIOD,
                        GRACEFUL_SHUTDOWN_TIMEOUT,
                        TimeUnit.SECONDS
                );
                bossFuture.await(15, TimeUnit.SECONDS);
                logger.info("⚡ [Graceful Shutdown] BossGroup terminated");
            } catch (Exception e) {
                logger.warn("Error during BossGroup shutdown", e);
            }
        }

        logger.info("✅ [Graceful Shutdown] Netty RPC server shutdown complete on port {}", port);
    }

    /**
     * 获取端口号
     *
     * @return 端口号
     */
    public int getPort() {
        return port;
    }

    /**
     * 是否正在运行
     *
     * @return true 如果正在运行
     */
    public boolean isRunning() {
        return running;
    }
}
