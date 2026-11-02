/**
 * Proof of thread safety: 5 threads x 1000 increments = 5000 expected.
 * UnsafeCounter has no synchronization and loses updates;
 * Database.incr is synchronized and is always exact.
 */
public class RaceDemo {
    static final int THREADS = 5;
    static final int INCREMENTS = 1000;

    /** Same read-add-write logic as Database.incr but WITHOUT synchronized. */
    static class UnsafeCounter {
        private long value = 0;

        void incr() {
            long current = value;       // read
            Thread.yield();             // give other threads a chance to interleave
            value = current + 1;        // write
        }

        long get() {
            return value;
        }
    }

    /** Runs both experiments. Returns {withoutSynchronized, withSynchronized}. */
    public static long[] compute() throws InterruptedException {
        UnsafeCounter unsafe = new UnsafeCounter();
        runThreads(() -> unsafe.incr());

        Database db = new Database();
        runThreads(() -> {
            try {
                db.incr("counter");
            } catch (InvalidCommandException e) {
                throw new RuntimeException(e);
            }
        });
        long safe;
        try {
            safe = Long.parseLong(db.get("counter"));
        } catch (KeyNotFoundException e) {
            throw new RuntimeException(e);
        }
        return new long[] {unsafe.get(), safe};
    }

    public static void main(String[] args) throws Exception {
        int expected = THREADS * INCREMENTS;
        long[] r = compute();
        System.out.println("Expected:                " + expected);
        System.out.println("Without synchronized:    " + r[0] + "   <-- lost updates (race condition)");
        System.out.println("With synchronized (DB):  " + r[1] + "   <-- always exact");
    }

    private static void runThreads(Runnable task) throws InterruptedException {
        Thread[] t = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            t[i] = new Thread(() -> {
                for (int j = 0; j < INCREMENTS; j++) {
                    task.run();
                }
            });
            t[i].start();
        }
        for (Thread x : t) {
            x.join();   // wait for all threads before reading the result
        }
    }
}
