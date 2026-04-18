package multithreadSingleService.step2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Step 2：三种方案修复超卖
 * 每次测试前重置库存为 10，100 线程同时冲，验证最终库存 >= 0 且成功数 <= 10
 */
public class OversellFix {

    // ─────────────────────────────────────────────
    // 方案 A：synchronized
    // ─────────────────────────────────────────────
    static int stockA = 10;
    static int successA = 0;

    static synchronized void buySync() {
        if (stockA > 0) {
            stockA--;
            successA++;
        }
    }

    // ─────────────────────────────────────────────
    // 方案 B：ReentrantLock
    // ─────────────────────────────────────────────
    static int stockB = 10;
    static int successB = 0;
    static final ReentrantLock lock = new ReentrantLock();

    static void buyReentrant() {
        lock.lock();
        try {
            if (stockB > 0) {
                stockB--;
                successB++;
            }
        } finally {
            lock.unlock();  // finally 保证锁一定被释放
        }
    }

    // ─────────────────────────────────────────────
    // 方案 C：AtomicInteger CAS
    // ─────────────────────────────────────────────
    static final AtomicInteger stockC = new AtomicInteger(10);
    static final AtomicInteger successC = new AtomicInteger(0);

    static void buyAtomic() {
        int current;
        do {
            current = stockC.get();
            if (current <= 0) return;
            // CAS：期望值是 current，改成 current-1；失败说明被别人抢先改了，重试
        } while (!stockC.compareAndSet(current, current - 1));
        successC.incrementAndGet();
    }

    // ─────────────────────────────────────────────
    // 运行三种方案并打印结果
    // ─────────────────────────────────────────────
    public static void main(String[] args) throws InterruptedException {
        runTest("synchronized",   OversellFix::buySync);
        runTest("ReentrantLock",  OversellFix::buyReentrant);
        runTest("AtomicInteger",  OversellFix::buyAtomic);
    }

    static void runTest(String name, Runnable buyFn) throws InterruptedException {
        int threadCount = 100;
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startGun.await();
                    buyFn.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startGun.countDown();
        endLatch.await();

        int finalStock = name.equals("AtomicInteger") ? stockC.get()
                       : name.equals("ReentrantLock") ? stockB : stockA;
        int success    = name.equals("AtomicInteger") ? successC.get()
                       : name.equals("ReentrantLock") ? successB : successA;

        System.out.printf("%-16s  最终库存: %3d  购买成功: %2d  %s%n",
                name, finalStock, success,
                finalStock >= 0 && success <= 10 ? "✓ 正确" : "✗ 超卖");
    }
}
