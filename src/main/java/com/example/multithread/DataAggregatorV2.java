package com.example.multithread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataAggregatorV2 {

    /**
     * 方向A：CompletableFuture 并发 + 线程安全容器
     * 多线程写同一个 synchronizedList，靠锁保证安全
     * 问题：锁竞争，性能有损耗，共享状态还在
     */
    public static List<Map<String, Object>> aggregateWithSyncList(String input) {

        // 线程安全的容器，写的时候会加锁排队
        List<Map<String, Object>> sharedList = Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (char c : input.toCharArray()) {
            String key = String.valueOf(c);
            CompletableFuture<Void> future = switch (key) {
                case "A" -> CompletableFuture.runAsync(() -> {
                    try { sharedList.addAll(MockServices.userService()); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
                case "B" -> CompletableFuture.runAsync(() -> {
                    try { sharedList.addAll(MockServices.orderService()); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
                case "C" -> CompletableFuture.runAsync(() -> {
                    try { sharedList.addAll(MockServices.inventoryService()); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
                case "D" -> CompletableFuture.runAsync(() -> {
                    try { sharedList.addAll(MockServices.paymentService()); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
                case "E" -> CompletableFuture.runAsync(() -> {
                    try { sharedList.addAll(MockServices.logisticsService()); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
                default -> CompletableFuture.completedFuture(null);
            };
            futures.add(future);
        }

        // 等所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return sharedList; // 多线程写同一个 list，靠锁保证安全
    }

    /**
     * 方向B：CompletableFuture 并发 + 消灭共享状态（推荐）
     * 每个任务各自返回结果，最后主线程统一合并
     * 无锁，无竞争，性能最优
     */
    public static List<Map<String, Object>> aggregateWithNoSharedState(String input) {

        // 每个 Future 各自持有自己的结果，互不干扰
        List<CompletableFuture<List<Map<String, Object>>>> futures = new ArrayList<>();

        for (char c : input.toCharArray()) {
            String key = String.valueOf(c);
            CompletableFuture<List<Map<String, Object>>> future = switch (key) {
                case "A" -> CompletableFuture.supplyAsync(() -> {
                    try { return MockServices.userService(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return List.of(); }
                });
                case "B" -> CompletableFuture.supplyAsync(() -> {
                    try { return MockServices.orderService(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return List.of(); }
                });
                case "C" -> CompletableFuture.supplyAsync(() -> {
                    try { return MockServices.inventoryService(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return List.of(); }
                });
                case "D" -> CompletableFuture.supplyAsync(() -> {
                    try { return MockServices.paymentService(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return List.of(); }
                });
                case "E" -> CompletableFuture.supplyAsync(() -> {
                    try { return MockServices.logisticsService(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return List.of(); }
                });
                default -> CompletableFuture.completedFuture(List.of());
            };
            futures.add(future);
        }

        // 主线程统一合并，这里只有一个线程在操作，无竞争
        return futures.stream()
                .map(CompletableFuture::join)   // 等待并拿各自结果
                .flatMap(List::stream)           // 展开合并
                .collect(Collectors.toList());
    }
}
