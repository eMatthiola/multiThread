package multithreadSingleService.step4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Step 4：手写 AQS 锁
 *
 * AQS 把"排队 + 唤醒"全包了，你只需要定义：
 *   state=0  无锁
 *   state=1  已锁
 *   tryAcquire：用 CAS 把 state 从 0 改成 1
 *   tryRelease：把 state 改回 0
 *
 * 读完这段代码再去看 ReentrantLock 源码，会直接对上号。
 */
public class SimpleAQSLock {

    private final Sync sync = new Sync();

    private static class Sync extends AbstractQueuedSynchronizer {

        @Override
        protected boolean tryAcquire(int arg) {
            // CAS：state 0→1，成功说明抢到锁；失败说明已被占用，AQS 会把当前线程排进队列
            return compareAndSetState(0, 1);
        }

        @Override
        protected boolean tryRelease(int arg) {
            // 直接把 state 置回 0，AQS 随后会唤醒队列里的下一个线程
            setState(0);
            return true;
        }

        @Override
        protected boolean isHeldExclusively() {
            return getState() == 1;
        }
    }

    public void lock()   { sync.acquire(1); }
    public void unlock() { sync.release(1); }

    // ─────────────────────────────────────────────
    // 用这把自制锁重跑超卖场景，验证正确性
    // ─────────────────────────────────────────────
    static int stock = 10;
    static final AtomicInteger successCount = new AtomicInteger(0);
    static final SimpleAQSLock myLock = new SimpleAQSLock();

    static void buy() {
        myLock.lock();
        try {
            if (stock > 0) {
                stock--;
                successCount.incrementAndGet();
            }
        } finally {
            myLock.unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 100;
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startGun.await();
                    buy();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startGun.countDown();
        endLatch.await();

        System.out.println("使用 SimpleAQSLock（手写 AQS）：");
        System.out.println("  最终库存:   " + stock);
        System.out.println("  购买成功数: " + successCount.get());
        System.out.println("  " + (stock >= 0 && successCount.get() <= 10 ? "✓ 正确，无超卖" : "✗ 有问题"));
        System.out.println();
        System.out.println("对应 ReentrantLock 源码路径：");
        System.out.println("  lock()   → AbstractQueuedSynchronizer.acquire(1)");
        System.out.println("  unlock() → AbstractQueuedSynchronizer.release(1)");
        System.out.println("  排队逻辑  → AQS.addWaiter() + LockSupport.park()");
        System.out.println("  唤醒逻辑  → AQS.unparkSuccessor() + LockSupport.unpark()");
    }
}
