package multithreadSingleService.step2;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Step 2：JMH 压测三种锁的吞吐量
 * 运行方式：直接跑 main()，等待约 2 分钟出结果
 *
 * 测什么：16 个线程并发自增同一个计数器，看谁每毫秒能完成更多次操作
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(16)
public class LockBenchmark {

    private int syncCounter;

    private final ReentrantLock lock = new ReentrantLock();
    private int lockCounter;

    private final AtomicInteger atomicCounter = new AtomicInteger(0);

    @Benchmark
    public void synchronizedIncrement() {
        synchronized (this) {
            syncCounter++;
        }
    }

    @Benchmark
    public void reentrantLockIncrement() {
        lock.lock();
        try {
            lockCounter++;
        } finally {
            lock.unlock();
        }
    }

    @Benchmark
    public void atomicIncrement() {
        atomicCounter.incrementAndGet();
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(LockBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
