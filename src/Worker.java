/**
 * Consumer thread: take a request, parse it into a Command, execute it.
 * cmd.execute(db) is runtime polymorphism: the right subclass method runs.
 *
 * The result is handed to a ResultListener (an interface), so the same Worker
 * can print to the console (Server) or update a window (MiniRedisGUI).
 */
public class Worker implements Runnable {

    /** Callback interface: "tell me when a command has finished". */
    public interface ResultListener {
        void onResult(String workerName, CommandQueue.Request req, String reply);
    }

    private final CommandQueue queue;
    private final Database db;
    private final Parser parser;
    private final ResultListener listener;

    /** Default: print every result to the console. */
    public Worker(CommandQueue queue, Database db, Parser parser) {
        this(queue, db, parser, (name, req, reply) ->
                System.out.println("[" + name + "] " + req.client + "> " + req.line + "  ->  " + reply));
    }

    public Worker(CommandQueue queue, Database db, Parser parser, ResultListener listener) {
        this.queue = queue;
        this.db = db;
        this.parser = parser;
        this.listener = listener;
    }

    @Override
    public void run() {
        String me = Thread.currentThread().getName();
        try {
            CommandQueue.Request req;
            while ((req = queue.take()) != null) {
                String reply;
                try {
                    Command cmd = parser.parse(req.line);
                    reply = cmd.execute(db);
                    db.countCommand();
                } catch (MiniRedisException e) {
                    reply = "ERROR: " + e.getMessage();
                } finally {
                    // finally always runs; nothing to clean up here, but shows the structure
                }
                listener.onResult(me, req, reply);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
