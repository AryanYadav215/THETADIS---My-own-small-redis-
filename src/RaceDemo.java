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

    public static void main(String[] args) throws Exception {
        int expected = THREADS * INCREMENTS;

        // 1) WITHOUT synchronized
        UnsafeCounter unsafe = new UnsafeCounter();
        runThreads(() -> unsafe.incr());
        System.out.println("Expected:                " + expected);
        System.out.println("Without synchronized:    " + unsafe.get() + "   <-- lost updates (race condition)");

        // 2) WITH synchronized (our Database)
        Database db = new Database();
        runThreads(() -> {
            try {
                db.incr("counter");
            } catch (InvalidCommandException e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("With synchronized (DB):  " + db.get("counter") + "   <-- always exact");
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
