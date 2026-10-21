public class SetCommand extends Command {
    private final String key;
    private final String value;
    private final long ttlSeconds;

    public SetCommand(String key, String value, long ttlSeconds) {
        this.key = key;
        this.value = value;
        this.ttlSeconds = ttlSeconds;
    }

    // Constructor overloading: SET without expiry
    public SetCommand(String key, String value) {
        this(key, value, 0);
    }

    @Override
    public String execute(Database db) {
        db.set(key, value, ttlSeconds);
        return "OK";
    }
}
