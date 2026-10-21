import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The shared store. The map is private (encapsulation); the only way in is
 * through the synchronized methods below, so only one thread touches it at a time.
 * Every method uses the same lock (this), so there is no deadlock risk.
 */
public class Database {
    private final HashMap<String, Record> map = new HashMap<>();
    private long commandsRun = 0;

    // ---------- core operations ----------

    public synchronized void set(String key, String value, long ttlSeconds) {
        long expiry = ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000 : 0;
        map.put(key, new Record(value, expiry));
    }

    public synchronized String get(String key) throws KeyNotFoundException {
        Record r = map.get(key);
        if (r == null) {
            throw new KeyNotFoundException(key);
        }
        if (r.isExpired()) {          // lazy expiry: check the deadline on access
            map.remove(key);
            throw new KeyNotFoundException(key);
        }
        return r.getValue();
    }

    public synchronized boolean del(String key) {
        Record r = map.remove(key);
        return r != null && !r.isExpired();
    }

    public synchronized boolean exists(String key) {
        Record r = map.get(key);
        if (r != null && r.isExpired()) {
            map.remove(key);
            return false;
        }
        return r != null;
    }

    /**
     * INCR is read -> add 1 -> write. Without 'synchronized' two threads could
     * both read 10 and both write 11 (a lost update = race condition).
     */
    public synchronized long incr(String key) throws InvalidCommandException {
        Record r = map.get(key);
        long current = 0;
        long expiry = 0;
        if (r != null && !r.isExpired()) {
            try {
                current = Long.parseLong(r.getValue());
            } catch (NumberFormatException e) {
                throw new InvalidCommandException("value of '" + key + "' is not an integer");
            }
            expiry = r.getExpiryTime(); // keep the existing TTL
        }
        long next = current + 1;
        map.put(key, new Record(String.valueOf(next), expiry));
        return next;
    }

    /** Seconds left, or -1 if the key never expires. */
    public synchronized long ttl(String key) throws KeyNotFoundException {
        Record r = map.get(key);
        if (r == null || r.isExpired()) {
            map.remove(key);
            throw new KeyNotFoundException(key);
        }
        if (r.getExpiryTime() == 0) {
            return -1;
        }
        return Math.max(0, (r.getExpiryTime() - System.currentTimeMillis() + 999) / 1000);
    }

    public synchronized List<String> keys() {
        List<String> result = new ArrayList<>();
        for (var e : map.entrySet()) {
            if (!e.getValue().isExpired()) {
                result.add(e.getKey());
            }
        }
        java.util.Collections.sort(result);
        return result;
    }

    public synchronized int size() {
        return keys().size();
    }

    public synchronized void countCommand() {
        commandsRun++;
    }

    public synchronized long getCommandsRun() {
        return commandsRun;
    }

    // ---------- persistence support ----------

    /** Lines "key<TAB>value<TAB>expiry" of all live records. */
    public synchronized List<String> snapshot() {
        List<String> lines = new ArrayList<>();
        for (var e : map.entrySet()) {
            Record r = e.getValue();
            if (!r.isExpired()) {
                lines.add(e.getKey() + "\t" + r.getValue() + "\t" + r.getExpiryTime());
            }
        }
        return lines;
    }

    public synchronized void restore(String key, String value, long expiry) {
        Record r = new Record(value, expiry);
        if (!r.isExpired()) {
            map.put(key, r);
        }
    }

    // ---------- expiry cleaner ----------

    /** Removes expired records; returns how many were removed. */
    public synchronized int removeExpired() {
        int before = map.size();
        map.entrySet().removeIf(e -> e.getValue().isExpired());
        return before - map.size();
    }

    /**
     * Non-static INNER class: it can use the outer object's members directly.
     * Runs in its own (daemon) thread and wakes up every second to delete
     * expired keys (active expiry). Removed Records lose their last reference,
     * so the garbage collector can reclaim them.
     */
    public class Cleaner implements Runnable {
        private volatile boolean running = true; // volatile: stop flag visible across threads

        public void stop() {
            running = false;
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                int removed = removeExpired();
                if (removed > 0) {
                    System.out.println("[cleaner] removed " + removed + " expired key(s)");
                }
            }
        }
    }
}
