import java.util.List;

/** Simulated client: sends its commands to the shared queue, one every few ms. */
public class ClientThread implements Runnable {
    private final String name;
    private final List<String> commands;
    private final CommandQueue queue;
    private final long delayMillis;

    public ClientThread(String name, List<String> commands, CommandQueue queue, long delayMillis) {
        this.name = name;
        this.commands = commands;
        this.queue = queue;
        this.delayMillis = delayMillis;
    }

    @Override
    public void run() {
        for (String c : commands) {
            queue.put(new CommandQueue.Request(name, c));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
