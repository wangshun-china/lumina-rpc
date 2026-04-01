package com.lumina.rpc.core.spi;

import com.lumina.rpc.core.discovery.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一致性哈希负载均衡器
 *
 * 基于一致性哈希算法选择实例，相同参数的请求总是路由到同一实例
 * 适用于需要会话保持的场景
 *
 * 注意：一致性哈希场景下，排除失败节点可能导致相同 key 路由到不同实例
 *
 * @author Lumina-RPC Team
 * @since 1.3.0
 */
public class ConsistentHashLoadBalancer implements LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(ConsistentHashLoadBalancer.class);

    // 虚拟节点数量（每个实例创建的虚拟节点数）
    private static final int VIRTUAL_NODES = 160;

    // 每个服务的一致性哈希环，key: serviceName
    private final ConcurrentHashMap<String, ConsistentHashRing> ringMap = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "consistent-hash";
    }

    @Override
    public SelectionResult selectWithExclusion(
            List<ServiceInstance> instances,
            List<InetSocketAddress> excluded,
            String serviceName,
            Object context) {

        // Step 1: 过滤已失败的节点
        List<ServiceInstance> available = filterExcluded(instances, excluded);

        if (available.isEmpty()) {
            logger.warn("[ConsistentHash] No available instances for service: {}", serviceName);
            return null;
        }

        if (available.size() == 1) {
            ServiceInstance instance = available.get(0);
            InetSocketAddress address = new InetSocketAddress(instance.getHost(), instance.getPort());
            return SelectionResult.simple(address);
        }

        // Step 2: 使用服务名作为默认的 hash key
        // 如果 context 是字符串，使用它作为 hash key
        String hashKey = (context instanceof String) ? (String) context : serviceName;

        ConsistentHashRing ring = getOrCreateRing(available, serviceName);
        String addressStr = ring.getNode(hashKey);

        if (addressStr == null) {
            logger.warn("[ConsistentHash] No node found for key: {}", hashKey);
            return null;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("[ConsistentHash] Selected {} for service {} (key={})",
                    addressStr, serviceName, hashKey);
        }

        String[] parts = addressStr.split(":");
        InetSocketAddress address = new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
        return SelectionResult.simple(address);
    }

    /**
     * 根据指定的 key 选择实例（带排除列表）
     *
     * @param instances   服务实例列表
     * @param excluded    排除的地址
     * @param serviceName 服务名
     * @param key         哈希 key
     * @return 选中的地址
     */
    public SelectionResult selectWithKey(
            List<ServiceInstance> instances,
            List<InetSocketAddress> excluded,
            String serviceName,
            String key) {
        return selectWithExclusion(instances, excluded, serviceName, key);
    }

    /**
     * 获取或创建哈希环
     */
    private ConsistentHashRing getOrCreateRing(List<ServiceInstance> instances, String serviceName) {
        return ringMap.compute(serviceName, (k, existingRing) -> {
            // 检查实例列表是否变化
            if (existingRing == null || existingRing.instanceCount() != instances.size()) {
                ConsistentHashRing newRing = new ConsistentHashRing();
                for (ServiceInstance instance : instances) {
                    // 根据权重调整虚拟节点数
                    int virtualNodes = VIRTUAL_NODES * instance.getWeight() / 100;
                    virtualNodes = Math.max(1, virtualNodes); // 至少 1 个虚拟节点
                    newRing.addNode(instance.getAddress(), virtualNodes);
                }
                return newRing;
            }
            return existingRing;
        });
    }

    /**
     * 一致性哈希环
     */
    private static class ConsistentHashRing {
        // 哈希环，使用 TreeMap 存储，按哈希值排序
        private final TreeMap<Long, String> ring = new TreeMap<>();

        /**
         * 添加节点
         *
         * @param node         节点地址
         * @param virtualNodes 虚拟节点数量
         */
        public void addNode(String node, int virtualNodes) {
            for (int i = 0; i < virtualNodes; i++) {
                String virtualNodeName = node + "&&VN" + i;
                long hash = hash(virtualNodeName);
                ring.put(hash, node);
            }
        }

        /**
         * 根据 key 获取节点
         *
         * @param key 哈希 key
         * @return 节点地址
         */
        public String getNode(String key) {
            if (ring.isEmpty()) {
                return null;
            }

            long hash = hash(key);

            // 查找大于等于该哈希值的第一个节点
            SortedMap<Long, String> tailMap = ring.tailMap(hash);
            Long nodeHash = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();

            return ring.get(nodeHash);
        }

        /**
         * 获取实例数量
         */
        public int instanceCount() {
            // 统计唯一的节点数量
            return (int) ring.values().stream().distinct().count();
        }

        /**
         * 计算 MD5 哈希值
         */
        private long hash(String key) {
            try {
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                byte[] digest = md5.digest(key.getBytes(StandardCharsets.UTF_8));

                // 取前 8 字节作为 long 值
                long hash = 0;
                for (int i = 0; i < 8; i++) {
                    hash = (hash << 8) | (digest[i] & 0xFF);
                }
                return hash;
            } catch (NoSuchAlgorithmException e) {
                // MD5 算法一定存在，这里不应该发生
                return key.hashCode();
            }
        }
    }
}