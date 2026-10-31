import java.util.List;

/**
 * Simple self-checking tests for MiniRedis (no external libraries needed).
 *
 * Run from the project folder:
 *   mkdir -p out
 *   javac -d out src/*.java test/DatabaseTest.java
 *   java -cp out DatabaseTest
 */
public class DatabaseTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        testSetAndGet();
        testMissingKeyThrows();
        testDelete();
        testIncrCreatesAndIncrements();
        testIncrOnNonNumberThrows();
        testExpiryLazy();
        testTtl();
        testKeysSorted();
        testParserBuildsCorrectCommands();
        testParserRejectsBadInput();
        testConcurrentIncrIsExact();

        System.out.println();
        System.out.println("Passed: " + passed + "  Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ---------- tests ----------

    static void testSetAndGet() throws Exception {
        Database db = new Database();
        db.set("name", "Rahul", 0);
        check("SET then GET returns the value", "Rahul".equals(db.get("name")));
    }

    static void testMissingKeyThrows() {
        Database db = new Database();
        try {
            db.get("nope");
            check("GET on missing key throws KeyNotFoundException", false);
        } catch (KeyNotFoundException e) {
            check("GET on missing key throws KeyNotFoundException", true);
        }
    }

    static void testDelete() throws Exception {
        Database db = new Database();
        db.set("a", "1", 0);
        check("DEL existing key returns true", db.del("a"));
        check("DEL missing key returns false", !db.del("a"));
        check("EXISTS is false after DEL", !db.exists("a"));
    }

    static void testIncrCreatesAndIncrements() throws Exception {
        Database db = new Database();
        check("INCR on new key gives 1", db.incr("c") == 1);
        check("INCR again gives 2", db.incr("c") == 2);
        check("value stored as text '2'", "2".equals(db.get("c")));
    }

    static void testIncrOnNonNumberThrows() {
        Database db = new Database();
        db.set("word", "hello", 0);
        try {
            db.incr("word");
            check("INCR on non-number throws InvalidCommandException", false);
        } catch (InvalidCommandException e) {
            check("INCR on non-number throws InvalidCommandException", true);
        }
    }

    static void testExpiryLazy() throws Exception {
        Database db = new Database();
        db.set("temp", "x", 1); // expires in 1 second
        check("key readable before expiry", "x".equals(db.get("temp")));
        Thread.sleep(1300);
        try {
            db.get("temp");
            check("key gone after expiry", false);
        } catch (KeyNotFoundException e) {
            check("key gone after expiry", true);
        }
    }

    static void testTtl() throws Exception {
        Database db = new Database();
        db.set("forever", "v", 0);
        db.set("soon", "v", 10);
        check("TTL is -1 when no expiry", db.ttl("forever") == -1);
        long t = db.ttl("soon");
        check("TTL is between 1 and 10 for EX 10", t >= 1 && t <= 10);
    }

    static void testKeysSorted() {
        Database db = new Database();
        db.set("b", "1", 0);
        db.set("a", "1", 0);
        db.set("c", "1", 0);
        check("KEYS returns sorted live keys", db.keys().equals(List.of("a", "b", "c")));
    }

    static void testParserBuildsCorrectCommands() throws Exception {
        Parser p = new Parser(new FileStore("unused.db"));
        check("parse SET -> SetCommand", p.parse("SET a 1") instanceof SetCommand);
        check("parse set (lowercase) -> SetCommand", p.parse("set a 1") instanceof SetCommand);
        check("parse SET with EX -> SetCommand", p.parse("SET a 1 EX 5") instanceof SetCommand);
        check("parse GET -> GetCommand", p.parse("GET a") instanceof GetCommand);
        check("parse INCR -> IncrCommand", p.parse("INCR a") instanceof IncrCommand);
        check("parse KEYS -> KeysCommand", p.parse("KEYS") instanceof KeysCommand);
    }

    static void testParserRejectsBadInput() {
        Parser p = new Parser(new FileStore("unused.db"));
        expectInvalid(p, "FLY away", "unknown command");
        expectInvalid(p, "GET", "GET with no key");
        expectInvalid(p, "SET a", "SET with missing value");
        expectInvalid(p, "SET a 1 EX abc", "EX with non-number");
        expectInvalid(p, "SET a 1 EX -5", "EX with negative number");
        expectInvalid(p, "   ", "blank line");
    }

    static void testConcurrentIncrIsExact() throws Exception {
        final Database db = new Database();
        final int threads = 8;
        final int each = 500;
        Thread[] t = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            t[i] = new Thread(() -> {
                for (int j = 0; j < each; j++) {
                    try {
                        db.incr("counter");
                    } catch (InvalidCommandException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            t[i].start();
        }
        for (Thread x : t) {
            x.join();
        }
        check("8 threads x 500 INCR = 4000 exactly", "4000".equals(db.get("counter")));
    }

    // ---------- tiny test helpers ----------

    private static void expectInvalid(Parser p, String line, String label) {
        try {
            p.parse(line);
            check("parser rejects: " + label, false);
        } catch (InvalidCommandException e) {
            check("parser rejects: " + label, true);
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("PASS  " + name);
        } else {
            failed++;
            System.out.println("FAIL  " + name);
        }
    }
}