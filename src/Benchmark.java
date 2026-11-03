import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Measures how fast MiniRedis really is, so we can back up speed claims with numbers.
 *
 *   java -cp out Benchmark
 *
 * Numbers depend on the computer, so run it on the demo laptop and quote THOSE.
 */
public class Benchmark {
    static final int N = 500_000;          // operations per direct test
    static final int PIPELINE_N = 100_000; // commands through queue + parser + workers
    static final int DISK_N = 20_000;      // file reads (slower, so fewer)

    public static void main(String[] args) throws Exception {
        System.out.println("MiniRedis benchmark   (Java " + System.getProperty("java.version")
                + ", " + Runtime.getRuntime().availableProcessors() + " CPU threads)");
        System.out.println("------------------------------------------------------------");

        // Warm-up: lets the JVM compile hot code so the real run is fair.
        Database warm = new Database();
        for (int i = 0; i < 200_000; i++) {
            warm.set("k" + (i % 1000), "v", 0);
            try { warm.get("k" + (i % 1000)); } catch (KeyNotFoundException e) { /* ignore */ }
        }

        // 1) Direct database calls, one thread
        Database db = new Database();
        long t0 = System.nanoTime();
        for (int i = 0; i < N; i++) {
            db.set("key" + (i % 10_000), "value" + i, 0);
        }
        double setSec = (System.nanoTime() - t0) / 1e9;

        t0 = System.nanoTime();
        for (int i = 0; i < N; i++) {
            db.get("key" + (i % 10_000));
        }
        double getSec = (System.nanoTime() - t0) / 1e9;
        report("1. SET, 1 thread, direct call", N, setSec);
        report("2. GET, 1 thread, direct call", N, getSec);

        // 2) Several threads hitting INCR at once (checks speed AND correctness)
        final int threads = 5, each = 100_000;
        final Database shared = new Database();
        Thread[] t = new Thread[threads];
        t0 = System.nanoTime();
        for (int i = 0; i < threads; i++) {
            t[i] = new Thread(() -> {
                for (int j = 0; j < each; j++) {
                    try { shared.incr("counter"); } catch (InvalidCommandException e) { throw new RuntimeException(e); }
                }
            });
            t[i].start();
        }
        for (Thread x : t) x.join();
        double incrSec = (System.nanoTime() - t0) / 1e9;
        String result = shared.get("counter");
        report("3. INCR, 5 threads at once", threads * each, incrSec);
        System.out.println("     final counter = " + result + "  (expected " + (threads * each) + ")  "
                + (result.equals(String.valueOf(threads * each)) ? "CORRECT" : "WRONG"));

        // 3) Whole pipeline: queue -> 3 workers -> parser -> command -> database
        Database pdb = new Database();
        FileStore store = new FileStore("bench.db");
        Parser parser = new Parser(store);
        CommandQueue queue = new CommandQueue();
        Worker.ResultListener quiet = (name, req, reply) -> { /* no printing, we only measure speed */ };
        Thread[] workers = new Thread[3];
        for (int i = 0; i < 3; i++) {
            workers[i] = new Thread(new Worker(queue, pdb, parser, quiet), "worker-" + (i + 1));
            workers[i].start();
        }
        t0 = System.nanoTime();
        for (int i = 0; i < PIPELINE_N; i++) {
            queue.put(new CommandQueue.Request("bench", (i % 2 == 0) ? "SET key" + (i % 1000) + " v" : "INCR counter"));
        }
        while (pdb.getCommandsRun() < PIPELINE_N) {
            Thread.sleep(1);
        }
        double pipeSec = (System.nanoTime() - t0) / 1e9;
        queue.close();
        for (Thread w : workers) w.join();
        report("4. Full pipeline (text -> parse -> execute)", PIPELINE_N, pipeSec);

        // 4) Baseline: a "database" that reads a file for every lookup
        Path file = Files.createTempFile("minired_bench", ".txt");
        Files.writeString(file, "value");
        t0 = System.nanoTime();
        for (int i = 0; i < DISK_N; i++) {
            Files.readString(file);
        }
        double diskSec = (System.nanoTime() - t0) / 1e9;
        Files.deleteIfExists(file);
        report("5. Baseline: read a file per lookup", DISK_N, diskSec);

        double memPer = getSec / N, diskPer = diskSec / DISK_N;
        System.out.println("------------------------------------------------------------");
        System.out.printf("In-memory GET is about %.0f times faster than reading a file each time.%n", diskPer / memPer);
        System.out.println("(The OS caches small files, so a real disk or network database would be slower still.)");
        new java.io.File("bench.db").delete();
    }

    private static void report(String name, int ops, double seconds) {
        System.out.printf("%-45s %,12.0f ops/sec   %6.2f microseconds/op%n", name, ops / seconds, seconds / ops * 1e6);
    }
}