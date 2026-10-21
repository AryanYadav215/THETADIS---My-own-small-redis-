import java.util.LinkedList;

/**
 * Producer-consumer buffer between client threads (producers) and worker
 * threads (consumers), built with synchronized + wait()/notifyAll().
 */
public class CommandQueue {

    /** A command line plus the name of the client that sent it. */
    public static final class Request {
        public final String client;
        public final String line;

        public Request(String client, String line) {
            this.client = client;
            this.line = line;
        }
    }

    private final LinkedList<Request> queue = new LinkedList<>();
    private boolean closed = false;

    public synchronized void put(Request r) {
        queue.add(r);
        notifyAll(); // wake up sleeping workers
    }

    /**
     * Blocks while the queue is empty. wait() RELEASES the lock and sleeps.
     * 'while' (not 'if') because after waking we must re-check: another worker
     * may have taken the item first, and wakeups can be spurious.
     * Returns null when the queue is closed and empty (worker should stop).
     */
    public synchronized Request take() throws InterruptedException {
        while (queue.isEmpty()) {
            if (closed) {
                return null;
            }
            wait();
        }
        return queue.removeFirst();
    }

    /** No more commands will arrive; wake everyone so workers can finish and exit. */
    public synchronized void close() {
        closed = true;
        notifyAll();
    }
}
