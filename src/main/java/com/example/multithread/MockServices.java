package com.example.multithread;

import java.util.List;
import java.util.Map;

/**
 * 模拟各微服务，用 Thread.sleep 模拟真实网络延迟
 */
public class MockServices {

    public static List<Map<String, Object>> userService() throws InterruptedException {
        Thread.sleep(200);
        return List.of(Map.of("service", "A", "data", "用户数据"));
    }

    public static List<Map<String, Object>> orderService() throws InterruptedException {
        Thread.sleep(300);
        return List.of(Map.of("service", "B", "data", "订单数据"));
    }

    public static List<Map<String, Object>> inventoryService() throws InterruptedException {
        Thread.sleep(150);
        return List.of(Map.of("service", "C", "data", "库存数据"));
    }

    public static List<Map<String, Object>> paymentService() throws InterruptedException {
        Thread.sleep(250);
        return List.of(Map.of("service", "D", "data", "支付数据"));
    }

    public static List<Map<String, Object>> logisticsService() throws InterruptedException {
        Thread.sleep(200);
        return List.of(Map.of("service", "E", "data", "物流数据"));
    }
}
