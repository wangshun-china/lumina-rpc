package com.lumina.rpc.protocol.spi;

import com.lumina.rpc.protocol.RpcRequest;

/**
 * 序列化性能对比测试
 *
 * 运行方式：直接运行 main 方法
 */
public class SerializerBenchmark {

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("        Serializer Performance Benchmark");
        System.out.println("============================================================\n");

        // 创建测试数据
        RpcRequest request = createTestRequest();

        // 初始化序列化器
        JsonSerializer jsonSerializer = new JsonSerializer();
        KryoSerializer kryoSerializer = new KryoSerializer();

        // 预热
        System.out.println("[Warmup] Running warmup iterations...\n");
        for (int i = 0; i < 1000; i++) {
            jsonSerializer.serialize(request);
            kryoSerializer.serialize(request);
        }

        // 测试参数
        int iterations = 10000;

        // 测试 JSON
        System.out.println("[Test 1] JSON Serializer");
        System.out.println("-".repeat(50));
        long jsonStart = System.nanoTime();
        int jsonTotalBytes = 0;
        for (int i = 0; i < iterations; i++) {
            byte[] bytes = jsonSerializer.serialize(request);
            jsonTotalBytes += bytes.length;
            jsonSerializer.deserialize(bytes, RpcRequest.class);
        }
        long jsonEnd = System.nanoTime();
        double jsonMs = (jsonEnd - jsonStart) / 1_000_000.0;
        int jsonAvgBytes = jsonTotalBytes / iterations;

        System.out.printf("  Iterations:    %,d%n", iterations);
        System.out.printf("  Total Time:    %.2f ms%n", jsonMs);
        System.out.printf("  Avg Latency:   %.4f ms%n", jsonMs / iterations);
        System.out.printf("  Throughput:    %,.0f ops/s%n", iterations / jsonMs * 1000);
        System.out.printf("  Avg Size:      %d bytes%n%n", jsonAvgBytes);

        // 测试 KRYO
        System.out.println("[Test 2] KRYO Serializer");
        System.out.println("-".repeat(50));
        long kryoStart = System.nanoTime();
        int kryoTotalBytes = 0;
        for (int i = 0; i < iterations; i++) {
            byte[] bytes = kryoSerializer.serialize(request);
            kryoTotalBytes += bytes.length;
            kryoSerializer.deserialize(bytes, RpcRequest.class);
        }
        long kryoEnd = System.nanoTime();
        double kryoMs = (kryoEnd - kryoStart) / 1_000_000.0;
        int kryoAvgBytes = kryoTotalBytes / iterations;

        System.out.printf("  Iterations:    %,d%n", iterations);
        System.out.printf("  Total Time:    %.2f ms%n", kryoMs);
        System.out.printf("  Avg Latency:   %.4f ms%n", kryoMs / iterations);
        System.out.printf("  Throughput:    %,.0f ops/s%n", iterations / kryoMs * 1000);
        System.out.printf("  Avg Size:      %d bytes%n%n", kryoAvgBytes);

        // 对比结果
        System.out.println("============================================================");
        System.out.println("                    Comparison");
        System.out.println("============================================================");
        double speedup = jsonMs / kryoMs;
        double sizeReduction = 100.0 * (jsonAvgBytes - kryoAvgBytes) / jsonAvgBytes;

        System.out.printf("  Speed Up:      %.1fx faster%n", speedup);
        System.out.printf("  Size Reduction: %.1f%% smaller%n%n", sizeReduction);

        System.out.printf("  JSON: %,.0f ops/s, %d bytes%n", iterations / jsonMs * 1000, jsonAvgBytes);
        System.out.printf("  KRYO: %,.0f ops/s, %d bytes%n", iterations / kryoMs * 1000, kryoAvgBytes);
        System.out.println("============================================================");
    }

    private static RpcRequest createTestRequest() {
        RpcRequest request = new RpcRequest();
        request.setRequestId(12345L);
        request.setInterfaceName("com.lumina.example.UserService");
        request.setMethodName("getUserById");
        request.setParameterTypes(new Class<?>[]{Long.class, String.class});
        request.setParameters(new Object[]{10001L, "detail"});
        request.setVersion("1.0.0");
        return request;
    }
}