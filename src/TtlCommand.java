public class TtlCommand extends Command {
    private final String key;

    public TtlCommand(String key) {
        this.key = key;
    }

    @Override
    public String execute(Database db) throws KeyNotFoundException {
        return String.valueOf(db.ttl(key)); // -1 means no expiry
    }
}
