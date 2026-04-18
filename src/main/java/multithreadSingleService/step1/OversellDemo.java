package multithreadSingleService.step1;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Step 1：复现超卖
 * 目的：亲眼看到"判断 + 扣减"不是原子操作时会发生什么
 */
public class OversellDemo {

    static int stock = 10;
    static AtomicInteger successCount = new AtomicInteger(0);

    static void buy(int userId) {
        if (stock > 0) {
            // 模拟判断通过后的业务处理耗时（真实场景：RPC、DB查询等）
            // sleep 期间其他线程也会通过 if 判断 → 必然超卖
            sleep(10);
            stock--;
            successCount.incrementAndGet();
        }
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 100;
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int userId = i;
            new Thread(() -> {
                try {
                    startGun.await();   // 所有线程在此等待，一声令下同时冲出
                    buy(userId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startGun.countDown();   // 发令
        endLatch.await();       // 等所有线程跑完

        System.out.println("初始库存:   10");
        System.out.println("购买成功数: " + successCount.get());
        System.out.println("最终库存:   " + stock);
        System.out.println();
        if (stock < 0) {
            System.out.println("超卖了！库存为负数 → 证明「判断+扣减」不是原子操作");
        } else if (successCount.get() < 10) {
            System.out.println("数据丢失！成功数 < 10 → 有线程被覆盖写入");
        } else {
            System.out.println("本次未复现，多跑几次（JIT 预热后更容易触发）");
        }
    }
}
