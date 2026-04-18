package multithreadSingleService.step3;

/**
 * Step 3：必然死锁
 *
 * 运行后程序卡住不退出，用以下命令抓死锁：
 *   jps                  → 找到 DeadlockDemo 的 PID
 *   jstack <PID>         → 打印线程快照
 *
 * jstack 输出中会看到：
 *   Found one Java-level deadlock:
 *   "Thread-T1": waiting to lock <0x...> held by "Thread-T2"
 *   "Thread-T2": waiting to lock <0x...> held by "Thread-T1"
 */
public class DeadlockDemo {

    private static final Object LOCK_A = new Object();
    private static final Object LOCK_B = new Object();

    public static void main(String[] args) {

        Thread t1 = new Thread(() -> {
            synchronized (LOCK_A) {
                System.out.println("T1 持有 A，准备获取 B...");
                sleep(100);             // 故意等一下，让 T2 有时间拿到 B
                synchronized (LOCK_B) {
                    System.out.println("T1 同时持有 A 和 B（不会打印）");
                }
            }
        }, "Thread-T1");

        Thread t2 = new Thread(() -> {
            synchronized (LOCK_B) {
                System.out.println("T2 持有 B，准备获取 A...");
                sleep(100);             // 故意等一下，让 T1 有时间拿到 A
                synchronized (LOCK_A) {
                    System.out.println("T2 同时持有 A 和 B（不会打印）");
                }
            }
        }, "Thread-T2");

        t1.start();
        t2.start();

        // 程序在这里永远卡住
        // T1 等 B（B 在 T2 手里），T2 等 A（A 在 T1 手里）→ 循环等待
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
