package com.lumina.rpc.core.spi;

import java.net.InetSocketAddress;

/**
 * 负载均衡选择结果
 *
 * 包含选中的地址和请求完成回调
 * 负载均衡器内部管理状态（如活跃计数），通过回调机制清理
 *
 * @author Lumina-RPC Team
 * @since 1.3.0
 */
public class SelectionResult {

    /** 选中的服务地址 */
    private final InetSocketAddress address;

    /** 请求完成回调（用于清理内部状态，如活跃计数） */
    private final Runnable onComplete;

    /**
     * 构造选择结果
     *
     * @param address 选中的地址
     * @param onComplete 请求完成回调（可为空）
     */
    public SelectionResult(InetSocketAddress address, Runnable onComplete) {
        this.address = address;
        this.onComplete = onComplete != null ? onComplete : () -> {};
    }

    /**
     * 创建无回调的结果（用于不需要状态管理的负载均衡器）
     */
    public static SelectionResult simple(InetSocketAddress address) {
        return new SelectionResult(address, null);
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public Runnable getOnComplete() {
        return onComplete;
    }
}