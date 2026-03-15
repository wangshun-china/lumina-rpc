package com.lumina.rpc.protocol.pool;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单地址的 Channel 池
 *
 * 管理指向同一个服务地址的多个 Channel 连接
 * 支持借用/归还机制，实现真正的连接池复用
 *
 * @author Lumina-RPC Team
 * @since 1.1.0
 */
public class ChannelPool {

    private static final Logger logger = LoggerFactory.getLogger(ChannelPool.class);

    /** 目标地址 */
    private final InetSocketAddress address;

    /** 地址标识 (host:port) */
    private final String addressKey;

    /** 空闲 Channel 队列 */
    private final ConcurrentLinkedQueue<Channel> idleChannels;

    /** 活跃 Channel 数量 */
    private final AtomicInteger activeCount;

    /** 最大 Channel 数量 */
    private final int maxChannels;

    /** 最小 Channel 数量 */
    private final int minChannels;

    /**
     * 创建 Channel 池
     *
     * @param address 目标地址
     * @param minChannels 最小连接数
     * @param maxChannels 最大连接数
     */
    public ChannelPool(InetSocketAddress address, int minChannels, int maxChannels) {
        this.address = address;
        this.addressKey = address.getHostString() + ":" + address.getPort();
        this.idleChannels = new ConcurrentLinkedQueue<>();
        this.activeCount = new AtomicInteger(0);
        this.minChannels = minChannels;
        this.maxChannels = maxChannels;

        logger.info("📊 ChannelPool created for {} (min={}, max={})", addressKey, minChannels, maxChannels);
    }

    /**
     * 借用一个 Channel
     *
     * @return 可用的 Channel，如果没有空闲且未达上限则返回 null
     */
    public Channel borrowChannel() {
        // 优先从空闲队列获取
        Channel channel = idleChannels.poll();
        if (channel != null && channel.isActive()) {
            activeCount.incrementAndGet();
            logger.debug("🔄 Reused idle channel for {}", addressKey);
            return channel;
        }

        // 空闲队列为空或 Channel 失效，检查是否可以创建新连接
        if (activeCount.get() < maxChannels) {
            // 返回 null 表示需要创建新连接
            logger.debug("📥 No idle channel, need to create new for {}", addressKey);
            return null;
        }

        // 已达上限，等待并重试
        logger.debug("⏳ Channel pool exhausted for {}, waiting...", addressKey);
        return null;
    }

    /**
     * 归还一个 Channel
     *
     * @param channel 使用完毕的 Channel
     */
    public void returnChannel(Channel channel) {
        if (channel == null || !channel.isActive()) {
            activeCount.decrementAndGet();
            logger.debug("❌ Returned inactive channel for {}", addressKey);
            return;
        }

        idleChannels.offer(channel);
        activeCount.decrementAndGet();
        logger.debug("📤 Returned channel to pool for {}", addressKey);
    }

    /**
     * 添加新创建的 Channel 到池中
     *
     * @param channel 新创建的 Channel
     */
    public void addChannel(Channel channel) {
        if (channel == null || !channel.isActive()) {
            return;
        }

        activeCount.incrementAndGet();
        logger.debug("✅ Added new channel for {}", addressKey);
    }

    /**
     * 移除失效的 Channel
     *
     * @param channel 失效的 Channel
     */
    public void removeChannel(Channel channel) {
        idleChannels.remove(channel);
        activeCount.decrementAndGet();
        logger.debug("🗑️ Removed inactive channel for {}", addressKey);
    }

    /**
     * 获取当前活跃连接数
     */
    public int getActiveCount() {
        return activeCount.get();
    }

    /**
     * 获取空闲连接数
     */
    public int getIdleCount() {
        return idleChannels.size();
    }

    /**
     * 获取总连接数
     */
    public int getTotalCount() {
        return activeCount.get() + idleChannels.size();
    }

    /**
     * 检查是否需要扩容
     */
    public boolean needExpand() {
        return getTotalCount() < maxChannels && idleChannels.isEmpty();
    }

    /**
     * 检查是否可以创建新连接
     */
    public boolean canCreate() {
        return getTotalCount() < maxChannels;
    }

    /**
     * 获取地址
     */
    public InetSocketAddress getAddress() {
        return address;
    }

    /**
     * 获取地址标识
     */
    public String getAddressKey() {
        return addressKey;
    }

    /**
     * 关闭所有连接
     */
    public void closeAll() {
        Channel channel;
        int closed = 0;
        while ((channel = idleChannels.poll()) != null) {
            try {
                if (channel.isActive()) {
                    channel.close();
                    closed++;
                }
            } catch (Exception e) {
                logger.warn("Error closing channel for {}", addressKey, e);
            }
        }
        activeCount.set(0);
        logger.info("🛑 Closed {} channels for {}", closed, addressKey);
    }

    @Override
    public String toString() {
        return String.format("ChannelPool[%s: active=%d, idle=%d, max=%d]",
                addressKey, activeCount.get(), idleChannels.size(), maxChannels);
    }
}