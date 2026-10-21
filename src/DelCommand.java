public class DelCommand extends Command {
    private final String key;

    public DelCommand(String key) {
        this.key = key;
    }

    @Override
    public String execute(Database db) {
        return db.del(key) ? "1" : "0";
    }
}
