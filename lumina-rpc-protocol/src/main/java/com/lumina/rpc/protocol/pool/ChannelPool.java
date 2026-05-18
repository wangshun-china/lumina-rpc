package com.lumina.rpc.protocol.pool;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
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

    /** 当前借出的 Channel */
    private final Set<Channel> borrowedChannels;

    /** 当前池内总 Channel 数量（空闲 + 借出） */
    private final AtomicInteger totalCount;

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
        this.borrowedChannels = ConcurrentHashMap.newKeySet();
        this.totalCount = new AtomicInteger(0);
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
        Channel channel;
        while ((channel = idleChannels.poll()) != null) {
            if (channel.isActive()) {
                borrowedChannels.add(channel);
                logger.debug("🔄 Reused idle channel for {}", addressKey);
                return channel;
            }

            decrementTotalCount();
            logger.debug("❌ Discarded inactive idle channel for {}", addressKey);
        }

        // 空闲队列为空或 Channel 失效，检查是否可以创建新连接
        if (totalCount.get() < maxChannels) {
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
    public boolean returnChannel(Channel channel) {
        if (channel == null) {
            return false;
        }

        boolean wasBorrowed = borrowedChannels.remove(channel);
        if (!wasBorrowed) {
            logger.debug("Ignored duplicate return for {}", addressKey);
            return false;
        }

        if (!channel.isActive()) {
            decrementTotalCount();
            logger.debug("❌ Returned inactive channel for {}", addressKey);
            return true;
        }

        idleChannels.offer(channel);
        logger.debug("📤 Returned channel to pool for {}", addressKey);
        return false;
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

        borrowedChannels.add(channel);
        totalCount.incrementAndGet();
        logger.debug("✅ Added new channel for {}", addressKey);
    }

    /**
     * 移除失效的 Channel
     *
     * @param channel 失效的 Channel
     */
    public boolean removeChannel(Channel channel) {
        boolean removedIdle = idleChannels.remove(channel);
        boolean removedBorrowed = borrowedChannels.remove(channel);

        if (removedIdle || removedBorrowed) {
            decrementTotalCount();
            logger.debug("🗑️ Removed inactive channel for {}", addressKey);
            return true;
        }
        return false;
    }

    /**
     * 检查是否可以创建新连接
     */
    public boolean canCreate() {
        return totalCount.get() < maxChannels;
    }

    /**
     * 关闭池内全部连接。
     */
    public void closeAll() {
        idleChannels.forEach(channel -> {
            if (channel.isOpen()) {
                channel.close();
            }
        });
        borrowedChannels.forEach(channel -> {
            if (channel.isOpen()) {
                channel.close();
            }
        });
        idleChannels.clear();
        borrowedChannels.clear();
        totalCount.set(0);
    }

    /**
     * 获取地址标识
     */
    public String getAddressKey() {
        return addressKey;
    }

    @Override
    public String toString() {
        return String.format("ChannelPool[%s: borrowed=%d, idle=%d, total=%d, max=%d]",
                addressKey, borrowedChannels.size(), idleChannels.size(), totalCount.get(), maxChannels);
    }

    private void decrementTotalCount() {
        totalCount.updateAndGet(current -> Math.max(0, current - 1));
    }
}
