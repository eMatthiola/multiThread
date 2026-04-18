package multiThreadMicroService.step1;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 压测客户端：100个线程同时打两个实例
 *
 * 运行前准备：
 *   1. 启动实例1：直接运行 MultiThreadApplication（端口 8080）
 *   2. 启动实例2：Edit Configurations → 复制配置 → VM options 加 -Dserver.port=8081
 *   3. 先访问 POST http://localhost:8080/reset 重置库存
 *   4. 运行这个 main()
 */
public class LoadTestClient {

    static final HttpClient http = HttpClient.newHttpClient();
    static final AtomicInteger successCount = new AtomicInteger(0);
    static final AtomicInteger failCount = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 无锁版本（复现超卖）===");
        runTest("/buy");

        // 重置库存
        try { sendPostRequest(8080, "/reset"); } catch (Exception e) { Thread.currentThread().interrupt(); } //NOSONAR
        successCount.set(0);
        failCount.set(0);

        System.out.println("\n=== Redis 分布式锁版本 ===");
        runTest("/buy/redis");
    }

    static void runTest(String path) throws InterruptedException {
        System.out.println("开始压测：100个线程同时打两个实例（8080 和 8081）");

        int threadCount = 100;
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            int port = i % 2 == 0 ? 8080 : 8081;
            new Thread(() -> {
                try {
                    startGun.await();
                    String result = sendPostRequest(port, path);
                    if (result.contains("购买成功")) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startGun.countDown();
        endLatch.await();

        System.out.println("购买成功次数: " + successCount.get());
        System.out.println("购买失败次数: " + failCount.get());
        try {
            System.out.println("最终库存:     " + sendGetRequest(8080, "/stock")); //NOSONAR
        } catch (Exception e) { //NOSONAR
            System.out.println("查询失败: " + e.getMessage());
        }
    }

    static String sendPostRequest(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    static String sendGetRequest(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}
