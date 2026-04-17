package com.example.multithread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 原始同步版本（有问题的代码）
 * 问题1：ArrayList 线程不安全
 * 问题2：顺序调用各服务，总耗时 = 各服务耗时之和
 */
public class DataAggregator {

    /**
     * 业务场景：电商下单前的数据聚合
     * 根据前端传入的服务代码组合，依次查询各微服务并汇总结果
     *
     * input 示例："ACE" → 需要查询 用户服务、库存服务、物流服务
     */
    public static List<Map<String, Object>> aggregateData(String input) throws InterruptedException {

        Map<String, String> serviceRegistry = new HashMap<>();
        serviceRegistry.put("A", "用户服务");
        serviceRegistry.put("B", "订单服务");
        serviceRegistry.put("C", "库存服务");
        serviceRegistry.put("D", "支付服务");
        serviceRegistry.put("E", "物流服务");

        // 问题：ArrayList 不是线程安全的
        List<Map<String, Object>> results = new ArrayList<>();

        // 问题：同步顺序调用，每个服务都要等上一个完成才能执行
        //优化点，服务之前并无依赖关系，完全可以并发执行
        for (char c : input.toCharArray()) {
            String key = String.valueOf(c);
            if (serviceRegistry.containsKey(key)) {
                if (key.equals("A")) {
                    results.addAll(MockServices.userService());       // 200ms
                } else if (key.equals("B")) {
                    results.addAll(MockServices.orderService());      // 300ms
                } else if (key.equals("C")) {
                    results.addAll(MockServices.inventoryService());  // 150ms
                } else if (key.equals("D")) {
                    results.addAll(MockServices.paymentService());    // 250ms
                } else if (key.equals("E")) {
                    results.addAll(MockServices.logisticsService());  // 200ms
                }
            }
        }

        return results; // 总耗时 ≈ 各服务耗时之和 ~1100ms
    }
}
