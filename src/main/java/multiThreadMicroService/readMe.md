# 并发练习第二层：跨进程 / 跨机器

## 进度

| 步骤 | 状态 | 完成内容 |
|------|------|----------|
| Step 1 Redis 分布式锁 | ✅ 完成 | 复现多实例超卖 + Redis SETNX 修复 |
| Step 2 Redisson | 🔲 待做 | |
| Step 3 DB 乐观锁 | 🔲 待做 | |
| Step 4 Kafka 消息可靠性 | 🔲 待做 | |

## 今日完成（2026-04-17）

### Step 1 实现内容

**涉及项目：**
- `shop-service`（独立 Spring Boot 服务）— 模拟商品秒杀
- `MultiThread/multiThreadMicroService/step1/LoadTestClient.java` — 压测工具

**shop-service 接口：**
```
POST /buy        无锁购买，复现超卖
POST /buy/redis  Redis 分布式锁购买，修复超卖
POST /reset      重置库存为 10
GET  /stock      查询当前库存
```

**本地环境：**
- MySQL：Docker，端口 3306，库名 shop，密码 123456
- Redis：Docker，端口 6379
- 实例1：ShopApplication，端口 8080
- 实例2：ShopApplication，VM options `-Dserver.port=8081`

**复现结果：**
```
无锁：100个线程声称购买成功，库存只扣了6，数据丢失94条 → Lost Update
Redis锁：购买成功 ≤ 10，库存准确扣减 → 正确
```

**Redis 分布式锁核心：**
```
SET lock:stock <uuid> NX PX 5000   ← 原子抢锁，5秒自动过期
Lua 脚本释放锁                      ← 保证「判断+删除」原子，防误删
```

**手写锁遗留的坑（Step 2 Redisson 要解决的）：**
```
坑1：业务耗时 > 锁过期时间 → 锁失效，并发问题重现
坑2：释放锁的判断+删除不是原子的（已用 Lua 解决）
坑3：没有重试机制，抢锁失败直接返回，用户体验差
```

---

## 目标

单机线程锁（step1-4）解决的是 JVM 内的竞争。
这一层解决的是：**多个服务实例同时跑，怎么保证数据不错乱，消息不丢失。**

两个核心问题：
1. 同一时刻只有一个实例能操作共享资源 → **分布式锁**
2. 操作结果可靠地传递给其他服务，不丢、不重复 → **消息可靠性**

---

## 为什么需要这一层

单机锁在多实例下完全失效：

```
实例A  synchronized(lock) { 扣库存 }
实例B  synchronized(lock) { 扣库存 }   ← 两个 JVM，锁根本不是同一把
```

消息队列解决直接调用的三个问题：
```
强依赖   → 通知服务挂了，订单也下不了
性能瓶颈 → 等最慢的那个服务返回
流量击穿 → 秒杀时1万请求直接打挂下游
```

Kafka 让服务之间解耦：我做完了，写到 Kafka 就返回，不等你，也不怕你挂。

---

## 练习路径

### 第一步：Redis 分布式锁（手写 SETNX）

**目标：** 亲眼看到单机锁在多实例下失效，再用 Redis 修复。

**做什么：**
- 启动两个服务实例，同时抢购同一个商品
- 只用 `synchronized` → 复现多实例超卖
- 用 `SETNX` 手写分布式锁修复

**SETNX 核心逻辑：**
```
SET lock_key unique_value NX PX 5000
```
- `NX`：key 不存在才设置（抢锁）
- `PX 5000`：5秒后自动过期（防止宕机后锁永远不释放）
- `unique_value`：只有自己才能释放自己的锁

**手写锁的坑：**
```
坑1：业务没执行完，锁过期了 → 别人抢到锁 → 并发问题又来了
坑2：释放锁时先判断是不是自己的，再删除 → 这两步不是原子的
坑3：释放了别人的锁（unique_value 没做好）
```

---

### 第二步：Redisson 替换手写锁

**目标：** 理解业界标准实现如何解决手写锁的坑。

**Redisson 怎么解决：**
- 看门狗机制：持有锁期间自动续期，业务没完成锁不会过期
- Lua 脚本保证"判断+删除"原子性
- 可重入锁，同一线程可以多次加锁

**核心文件：** `step2/RedissonLockDemo.java`

---

### 第三步：数据库乐观锁对比

**目标：** 知道什么场景用分布式锁，什么场景用 DB 乐观锁。

**做什么：**
```sql
UPDATE product SET stock = stock - 1, version = version + 1
WHERE id = 1 AND version = #{version} AND stock > 0
```
- 更新返回 0 → 被人抢先了 → 重试
- 不依赖 Redis，天然原子

**对比：**

| | 分布式锁 | DB 乐观锁 |
|--|---------|---------|
| 适用场景 | 临界区逻辑复杂，多步操作 | 单条记录的简单更新 |
| 性能 | 依赖 Redis 网络 | 一条 SQL，性能高 |
| 复杂度 | 高（续期、原子释放） | 低 |
| 冲突高时 | 稳定 | 大量重试，性能下降 |

---

### 第四步：Kafka 消息可靠性实验

**目标：** 亲眼看到消息丢失和重复消费，再用幂等修复。

**实验1：消息丢失**
```
生产者 acks=0（发完不等确认）
→ 强制 kill broker
→ 看丢了几条
```
修复：`acks=all` + 重试

**实验2：消息重复**
```
消费者处理完业务，但 offset 还没提交
→ 重启消费者
→ 从上次提交的 offset 重新消费 → 重复处理
```

**实验3：幂等消费修复**
```
每条消息带唯一 messageId
消费前查 Redis：这条消息处理过吗？
处理过 → 直接跳过
没处理过 → 处理 + 记录 Redis
```
重启后不再重复，彻底解决。

---

## 包结构

```
src/main/java/multiThreadMicroService/
├── step1/
│   └── RedisLockDemo.java         # 手写 SETNX 分布式锁
├── step2/
│   └── RedissonLockDemo.java      # Redisson 标准实现
├── step3/
│   └── OptimisticLockDemo.java    # DB 乐观锁对比
└── step4/
    ├── ProducerDemo.java           # Kafka 生产者实验
    ├── ConsumerDemo.java           # Kafka 消费者实验
    └── IdempotentConsumer.java     # 幂等消费修复
```

---

## 依赖

```xml
<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Redisson -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.27.0</version>
</dependency>

<!-- Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```
