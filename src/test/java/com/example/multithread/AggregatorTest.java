package com.example.multithread;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class AggregatorTest {

    public static void main(String[] args) throws Exception {
        testPerformance();
        System.out.println("----------------------------");
        testConcurrentSafety();
        System.out.println("----------------------------");
        testPerformanceComparison();
    }

    /**
     * 测试1：性能测试
     * 预期：同步版本耗时约 1100ms（各服务耗时之和）
     */
    static void testPerformance() throws Exception {
        System.out.println("=== 性能测试 ===");

        long start = System.currentTimeMillis();
        List<Map<String, Object>> result = DataAggregator.aggregateData("ABCDE");
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("同步耗时: " + elapsed + "ms");
        System.out.println("返回结果数: " + result.size());
        // 预期输出：同步耗时: ~1100ms，结果数: 5
    }

    /**
     * 测试2：线程安全测试
     *
     * 真正的问题场景：多个线程同时往【同一个 ArrayList】里写数据
     * ArrayList 的 add() 不是原子操作，并发写会导致：
     *   1. 数据丢失（size 比预期小）
     *   2. 数组越界异常（ArrayIndexOutOfBoundsException）
     *
     * 预期：100 个线程各写 1 条，最终 size 应为 100，但实际往往小于 100 甚至报错
     */
    static void testConcurrentSafety() throws InterruptedException {
        System.out.println("=== 线程安全测试（共享 ArrayList）===");

        int threadCount = 1000;
        // startGun：让所有线程在同一时刻冲出去，制造最激烈的竞争
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        List<String> sharedList = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    startGun.await();              // 所有线程在这里等待
                    sharedList.add("data-" + id); // 同时冲，竞争最激烈
                } catch (Exception e) {
                    System.out.println("线程 " + id + " 异常: " + e.getClass().getSimpleName());
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startGun.countDown(); // 一声令下，所有线程同时开始
        endLatch.await();

        System.out.println("期望 size: " + threadCount);
        System.out.println("实际 size: " + sharedList.size());

        if (sharedList.size() < threadCount) {
            System.out.println("数据丢失了 " + (threadCount - sharedList.size()) + " 条 → 证明 ArrayList 线程不安全");
        } else {
            System.out.println("本次未复现，可多跑几次");
        }
    }

    /**
     * 测试3：三个版本性能对比
     */
    static void testPerformanceComparison() throws Exception {
        System.out.println("=== 三版本性能对比 ===");

        // 原始同步版本
        long start = System.currentTimeMillis();
        DataAggregator.aggregateData("ABCDE");
        System.out.println("同步版本:          " + (System.currentTimeMillis() - start) + "ms  ← 各服务耗时之和");

        // 方向A：并发 + 线程安全容器
        start = System.currentTimeMillis();
        DataAggregatorV2.aggregateWithSyncList("ABCDE");
        System.out.println("方向A (加锁容器):  " + (System.currentTimeMillis() - start) + "ms  ← 最慢服务耗时");

        // 方向B：并发 + 消灭共享状态
        start = System.currentTimeMillis();
        DataAggregatorV2.aggregateWithNoSharedState("ABCDE");
        System.out.println("方向B (无共享状态):" + (System.currentTimeMillis() - start) + "ms  ← 最慢服务耗时");
    }
}
