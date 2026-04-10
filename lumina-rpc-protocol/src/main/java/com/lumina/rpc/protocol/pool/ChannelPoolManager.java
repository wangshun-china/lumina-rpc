package com.lumina.rpc.protocol.pool;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Channel 池管理器
 *
 * 管理所有服务地址的 Channel 池，提供统一的连接获取/归还接口
 *
 * 特性：
 * 1. 多连接池：每个地址维护多个 Channel
 * 2. 动态扩容：按需创建新连接
 * 3. 连接复用：借用-归还机制
 * 4. 健康检查：自动清理失效连接
 *
 * @author Lumina-RPC Team
 * @since 1.1.0
 */
public class ChannelPoolManager {

    private static final Logger logger = LoggerFactory.getLogger(ChannelPoolManager.class);

    /** 单例实例 */
    private static volatile ChannelPoolManager instance;

    /** 默认最小连接数 */
    private static final int DEFAULT_MIN_CHANNELS = 2;

    /** 默认最大连接数 */
    private static final int DEFAULT_MAX_CHANNELS = 10;

    /** 获取连接超时时间（毫秒） */
    private static final long BORROW_TIMEOUT_MS = 3000;

    /** 等待获取连接的间隔（毫秒） */
    private static final long WAIT_INTERVAL_MS = 50;

    /** 地址 -> ChannelPool 映射 */
    private final ConcurrentHashMap<String, ChannelPool> poolMap;

    /** 创建连接的工厂 */
    private ChannelFactory channelFactory;

    /** 全局最大连接数 */
    private final int globalMaxChannels;

    /** 全局当前连接数 */
    private final java.util.concurrent.atomic.AtomicInteger globalChannelCount;

    /** 锁，用于创建新池时的同步 */
    private final ReentrantLock createLock;

    /**
     * Channel 创建工厂接口
     */
    @FunctionalInterface
    public interface ChannelFactory {
        /**
         * 创建新的 Channel
         *
         * @param address 目标地址
         * @return 新创建的 Channel
         */
        Channel createChannel(InetSocketAddress address);
    }

    private ChannelPoolManager() {
        this.poolMap = new ConcurrentHashMap<>();
        this.globalMaxChannels = 100;
        this.globalChannelCount = new java.util.concurrent.atomic.AtomicInteger(0);
        this.createLock = new ReentrantLock();
    }

    /**
     * 获取单例实例
     */
    public static ChannelPoolManager getInstance() {
        if (instance == null) {
            synchronized (ChannelPoolManager.class) {
                if (instance == null) {
                    instance = new ChannelPoolManager();
                }
            }
        }
        return instance;
    }

    /**
     * 设置 Channel 创建工厂
     */
    public void setChannelFactory(ChannelFactory factory) {
        this.channelFactory = factory;
    }

    /**
     * 获取或创建 Channel 池
     */
    private ChannelPool getOrCreatePool(InetSocketAddress address) {
        String addressKey = address.getHostString() + ":" + address.getPort();
        ChannelPool pool = poolMap.get(addressKey);

        if (pool == null) {
            createLock.lock();
            try {
                pool = poolMap.get(addressKey);
                if (pool == null) {
                    pool = new ChannelPool(address, DEFAULT_MIN_CHANNELS, DEFAULT_MAX_CHANNELS);
                    poolMap.put(addressKey, pool);
                    logger.info("📦 Created new ChannelPool for {}", addressKey);
                }
            } finally {
                createLock.unlock();
            }
        }

        return pool;
    }

    /**
     * 获取一个 Channel（阻塞直到获取成功或超时）
     *
     * @param address 目标地址
     * @return Channel
     * @throws RuntimeException 获取失败
     */
    public Channel acquire(InetSocketAddress address) {
        return acquire(address, BORROW_TIMEOUT_MS);
    }

    /**
     * 获取一个 Channel
     *
     * @param address 目标地址
     * @param timeoutMs 超时时间（毫秒）
     * @return Channel
     */
    public Channel acquire(InetSocketAddress address, long timeoutMs) {
        ChannelPool pool = getOrCreatePool(address);

        long startTime = System.currentTimeMillis();

        while (true) {
            // 1. 尝试从池中借用
            Channel channel = pool.borrowChannel();
            if (channel != null && channel.isActive()) {
                return channel;
            }

            // 2. 如果可以创建新连接
            if (channelFactory != null && pool.canCreate() && globalChannelCount.get() < globalMaxChannels) {
                try {
                    Channel newChannel = channelFactory.createChannel(address);
                    if (newChannel != null && newChannel.isActive()) {
                        pool.addChannel(newChannel);
                        globalChannelCount.incrementAndGet();

                        // 添加关闭监听器
                        final ChannelPool finalPool = pool;
                        newChannel.closeFuture().addListener(f -> {
                            finalPool.removeChannel(newChannel);
                            globalChannelCount.decrementAndGet();
                        });

                        return newChannel;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to create channel for {}", pool.getAddressKey(), e);
                }
            }

            // 3. 检查超时
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                throw new RuntimeException("Timeout acquiring channel for " + pool.getAddressKey());
            }

            // 4. 等待重试
            try {
                TimeUnit.MILLISECONDS.sleep(WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for channel", e);
            }
        }
    }

    /**
     * 归还 Channel
     *
     * @param address 目标地址
     * @param channel 使用完毕的 Channel
     */
    public void release(InetSocketAddress address, Channel channel) {
        if (channel == null) {
            return;
        }

        String addressKey = address.getHostString() + ":" + address.getPort();
        ChannelPool pool = poolMap.get(addressKey);

        if (pool != null) {
            pool.returnChannel(channel);
        } else {
            // 池不存在，直接关闭
            if (channel.isActive()) {
                channel.close();
            }
        }
    }

    }