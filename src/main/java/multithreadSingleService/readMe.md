# 并发练习计划：从问题到原理

## 目标

不是背 API，是亲眼看到问题、亲手修复、用数字对比差异。
四步走完之后，面试能讲清楚，生产能看懂死锁，源码能对上号。

---

## Step 1 — 复现超卖问题

**目标：** 亲眼看到数据错乱，理解为什么会超卖。

**核心文件：** `step1/OversellDemo.java`

**做什么：**
- 库存 `int stock = 10`
- 100 个线程同时执行"判断库存 > 0 → 扣减"
- 用 `CountDownLatch startGun` 让所有线程同一时刻冲出去，制造最激烈的竞争

**为什么会超卖：**
```
线程A: 读到 stock=1，判断 > 0，通过 ✓
线程B: 读到 stock=1，判断 > 0，通过 ✓   ← 还没等A写回去
线程A: stock-- → stock=0
线程B: stock-- → stock=-1  ← 超卖
```
"判断 + 扣减"不是原子操作，这就是根源。

**预期输出：**
```
最终库存: -7  ← 负数，证明超卖
```

---

## Step 2 — 三种方案修复 + JMH 压测

**目标：** 同一个问题，三种解法，用数字感受差异。

**核心文件：**
- `step2/OversellFix.java` — 三种修复方案
- `step2/LockBenchmark.java` — JMH 压测

### 三种方案

| 方案 | 机制 | 关键点 |
|------|------|--------|
| `synchronized` | JVM 内置互斥锁 | 最简单，重量级 |
| `ReentrantLock` | AQS 实现的显式锁 | 可中断、可超时、可公平 |
| `AtomicInteger` (CAS) | CPU 指令级原子操作 | 无锁，高并发下吞吐最高 |

**AtomicInteger CAS 循环的关键写法：**
```java
int current;
do {
    current = stock.get();
    if (current <= 0) return; // 已无库存，直接返回
} while (!stock.compareAndSet(current, current - 1)); // CAS 失败则重试
```
这把"判断 + 扣减"变成了一个原子操作。

### JMH 压测

测的不是"能不能买到"，测的是**锁的原始吞吐量**：
- 16 个线程并发自增同一个计数器
- 看每毫秒能完成多少次操作（ops/ms）

**预期结论（数量级参考）：**
```
AtomicInteger   >> ReentrantLock ≈ synchronized
```
`synchronized` 和 `ReentrantLock` 在现代 JVM 下差距不大，
`AtomicInteger` 无锁操作在高竞争下吞吐量领先明显。

**运行方式：** 直接在 IDE 里跑 `LockBenchmark.main()`，无需额外 Maven 配置。

---

## Step 3 — 主动制造死锁

**目标：** 写出必然死锁的程序，用 `jstack` 把它抓出来。

**核心文件：** `step3/DeadlockDemo.java`

**死锁四个必要条件（缺一不成）：**
1. 互斥：锁只能被一个线程持有
2. 占有且等待：持有一把锁的同时等另一把
3. 不可剥夺：锁只能主动释放，不能被抢
4. 循环等待：T1 等 T2 的锁，T2 等 T1 的锁

**复现手法：**
```
Thread-T1: 先锁 A，sleep 100ms，再锁 B
Thread-T2: 先锁 B，sleep 100ms，再锁 A
```
`sleep` 是为了确保两个线程都能先拿到第一把锁，然后互相等待，必然死锁。

**用 jstack 抓死锁：**
```bash
# 1. 运行程序（程序会卡住不退出）
# 2. 另开终端
jps                        # 找到进程 PID
jstack <PID>               # 打印所有线程状态

# jstack 输出里会看到：
# Found one Java-level deadlock:
# "Thread-T1" waiting to lock <0x...> held by "Thread-T2"
# "Thread-T2" waiting to lock <0x...> held by "Thread-T1"
```

---

## Step 4 — 手写 AQS 锁，再看源码

**目标：** 先知道 AQS 解决什么问题，再去读源码，事半功倍。

**核心文件：** `step4/SimpleAQSLock.java`

**做什么：**
- 继承 `AbstractQueuedSynchronizer`，只实现三个方法
- 用这把自制锁再跑一次超卖场景，验证正确性

**AQS 的核心模型（三个概念）：**

```
state         → 锁的状态。0=无锁，1=已锁（ReentrantLock 里是重入次数）
tryAcquire()  → 你来定义"怎样算抢锁成功"，用 CAS 修改 state
tryRelease()  → 你来定义"怎样算释放锁"，把 state 改回 0
```

AQS 帮你搞定的部分：
- 抢锁失败 → 自动把线程放进 CLH 队列排队
- 锁释放 → 自动唤醒队列头部线程（`LockSupport.unpark`）
- 你只需要写业务逻辑（state 怎么变），排队和唤醒 AQS 全包了

**读完这步再去看 `ReentrantLock.lock()` 源码，会直接对上号。**

---

## 包结构

```
src/main/java/com/example/multithreadSingleService/
├── step1/
│   └── OversellDemo.java          # 超卖复现
├── step2/
│   ├── OversellFix.java           # synchronized / ReentrantLock / CAS 三种修复
│   └── LockBenchmark.java         # JMH 吞吐量对比
├── step3/
│   └── DeadlockDemo.java          # 必然死锁 + jstack 使用说明
└── step4/
    └── SimpleAQSLock.java         # 手写 AQS 锁 + 验证
```

---

## 依赖

pom.xml 需要加 JMH（Step 2 压测用）：

```xml
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.37</version>
</dependency>
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>1.37</version>
    <scope>provided</scope>
</dependency>
```
