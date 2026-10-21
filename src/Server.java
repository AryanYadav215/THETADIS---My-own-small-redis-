import java.io.IOException;
import java.util.List;
import java.util.Scanner;

/**
 * Wires everything together.
 *   java Server              -> scripted demo with 2 clients and 3 workers
 *   java Server interactive  -> type commands yourself (EXIT to quit)
 */
public class Server {
    private static final String DATA_FILE = "data.db";
    private static final int WORKERS = 3;

    public static void main(String[] args) throws Exception {
        Database db = new Database();
        FileStore store = new FileStore(DATA_FILE);
        Parser parser = new Parser(store);
        CommandQueue queue = new CommandQueue();

        try {
            int loaded = store.load(db);
            System.out.println("Loaded " + loaded + " key(s) from " + DATA_FILE);
        } catch (IOException e) {
            System.out.println("Could not load data file: " + e.getMessage());
        }

        // Background cleaner (daemon thread dies with the program)
        Database.Cleaner cleaner = db.new Cleaner();
        Thread cleanerThread = new Thread(cleaner, "cleaner");
        cleanerThread.setDaemon(true);
        cleanerThread.start();

        // Worker threads
        Thread[] workers = new Thread[WORKERS];
        for (int i = 0; i < WORKERS; i++) {
            workers[i] = new Thread(new Worker(queue, db, parser), "worker-" + (i + 1));
            workers[i].start();
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("interactive")) {
            runInteractive(queue, store, db);
        } else {
            runDemo(queue);
        }

        // Shutdown: no more input, let workers drain the queue and stop
        queue.close();
        for (Thread w : workers) {
            w.join();
        }
        cleaner.stop();

        try {
            int saved = store.save(db);
            System.out.println("Final save: " + saved + " key(s) written to " + DATA_FILE);
        } catch (IOException e) {
            System.out.println("Final save failed: " + e.getMessage());
        }
    }

    private static void runDemo(CommandQueue queue) throws InterruptedException {
        List<String> a = List.of(
                "SET city Jaipur",
                "SET otp 4821 EX 3",
                "GET otp",
                "TTL otp",
                "INCR hits",
                "SAVE");
        List<String> b = List.of(
                "SET name Rahul",
                "GET name",
                "GET xyz",
                "FLY away",
                "SET age abc",
                "INCR age",
                "KEYS");

        Thread clientA = new Thread(new ClientThread("ClientA", a, queue, 120), "clientA");
        Thread clientB = new Thread(new ClientThread("ClientB", b, queue, 100), "clientB");
        clientA.start();
        clientB.start();
        clientA.join();
        clientB.join();

        System.out.println("--- waiting 4 seconds so 'otp' expires ---");
        Thread.sleep(4000);

        Thread late = new Thread(new ClientThread("ClientA", List.of("GET otp", "EXISTS otp", "KEYS", "STATS"),
                queue, 100), "clientA-late");
        late.start();
        late.join();
    }

    private static void runInteractive(CommandQueue queue, Storable store, Database db) throws InterruptedException {
        System.out.println("MiniRedis ready. Commands: SET GET DEL EXISTS INCR TTL KEYS STATS SAVE | EXIT");
        try (Scanner in = new Scanner(System.in)) {
            while (in.hasNextLine()) {
                String line = in.nextLine().trim();
                if (line.equalsIgnoreCase("EXIT")) {
                    break;
                }
                if (!line.isEmpty()) {
                    queue.put(new CommandQueue.Request("you", line));
                    Thread.sleep(50); // let the worker print before the next prompt
                }
            }
        }
    }
}
