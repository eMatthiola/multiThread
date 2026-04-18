# 并发通信全景图

## 核心决策树

```
需要结果？
  是 → 跨服务？ → 是 → HTTP/RPC（Feign、gRPC）
               → 否 → CompletableFuture
  否 → 跨服务？ → 是 → Kafka
               → 否 → @Async 或 Spring Event
```

---

## 四个概念详解

场景贯穿：用户下单。

---

### 1. HTTP/RPC — 服务间，需要结果

```
订单服务 → 问库存服务：这个商品还有货吗？
          ← 库存服务回答：有3件
订单服务继续处理...
```

- 必须等回答才能继续，像打电话，对方不接就卡住
- Feign 把 HTTP 调用封装成本地方法调用的形式
- 适用：扣库存、验证支付、校验权限——结果决定下一步怎么走

```java
// Feign 示例
@FeignClient("stock-service")
public interface StockClient {
    @GetMapping("/stock/{productId}")
    int getStock(@PathVariable Long productId);
}
```

---

### 2. Kafka — 服务间，不需要结果

```
订单服务 → 写消息到 Kafka："订单123已创建" → 立即返回

Kafka ↓
通知服务  读到消息 → 发短信（自己的节奏）
积分服务  读到消息 → 加积分
```

- 发完不等回复，像发邮件
- 下游挂了消息积在 Kafka，恢复后继续消费，不影响上游
- 天然削峰：秒杀流量写进 Kafka，下游按自己速度消费，不会被打挂
- 适用：发通知、写日志、加积分、生成报表——失败了可以重试，不影响主流程

```java
// 生产者
kafkaTemplate.send("order-topic", new OrderCreatedEvent(orderId));
// 消费者（另一个服务）
@KafkaListener(topics = "order-topic")
public void onOrder(OrderCreatedEvent event) { 发短信... }
```

---

### 3. CompletableFuture — 服务内，需要并行结果

```
下单页面需要同时展示：用户信息 + 库存状态 + 优惠券

三件事互不依赖，没必要排队
```

- 并行跑，等最慢的那个，总耗时 = max(各任务耗时)
- 串行跑，总耗时 = sum(各任务耗时)
- 适用：聚合多个数据源、调用多个无依赖的接口

```java
CompletableFuture<User>   u = supplyAsync(() -> userService.get(id));
CompletableFuture<Stock>  s = supplyAsync(() -> stockService.get(id));
CompletableFuture<Coupon> c = supplyAsync(() -> couponService.get(id));
CompletableFuture.allOf(u, s, c).join();  // 并行等结果
```

---

### 4. @Async / Spring Event — 服务内，不需要结果

**@Async：直接异步执行，主线程不等**

```java
@Async
public void logOrder(Order order) { 写日志... }

// 主流程：
saveOrder(order);
logOrder(order);   // 另起线程，主线程直接返回给用户
```

适用：写日志、发监控指标——失败无所谓，不能拖慢主流程。

**Spring Event：连调用关系也解掉**

```java
// 订单服务只管发事件，完全不知道谁会处理
eventPublisher.publishEvent(new OrderCreatedEvent(order));

// 通知模块订阅（可以在任何地方）
@EventListener
@Async
public void onOrderCreated(OrderCreatedEvent e) { 发短信... }
```

- `@Async`：异步调用，但订单服务还是知道"我在调用通知服务"
- `Spring Event`：订单服务只知道"我发了个事件"，谁处理、怎么处理完全不关心
- 适用：模块间解耦，一个事件多个监听者

---

## 不能异步的场景

判断标准：**下一步依赖这一步的结果，必须同步。**

```
下单前检查库存    → 同步，库存不够不能下单
支付结果         → 同步，支付成功才算完成
登录验证密码      → 同步，验证通过才能往下走
查数据做计算      → 同步，没数据没法算
```

---

## 对应练习位置

```
multithread/step1-4          → CompletableFuture、线程池（服务内并行）
multiThreadMicroService/     → Redis 分布式锁、Kafka 消息可靠性（服务间）
multithreadSingleService/    → @Async、Spring Event（服务内异步）
```
