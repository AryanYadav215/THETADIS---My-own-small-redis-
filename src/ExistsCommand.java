public class ExistsCommand extends Command {
    private final String key;

    public ExistsCommand(String key) {
        this.key = key;
    }

    @Override
    public String execute(Database db) {
        return db.exists(key) ? "1" : "0";
    }
}
