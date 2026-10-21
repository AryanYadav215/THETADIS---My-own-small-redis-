/**
 * Consumer thread: take a request, parse it into a Command, execute it.
 * cmd.execute(db) is runtime polymorphism: the right subclass method runs.
 */
public class Worker implements Runnable {
    private final CommandQueue queue;
    private final Database db;
    private final Parser parser;

    public Worker(CommandQueue queue, Database db, Parser parser) {
        this.queue = queue;
        this.db = db;
        this.parser = parser;
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
                System.out.println("[" + me + "] " + req.client + "> " + req.line + "  ->  " + reply);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
