public class GetCommand extends Command {
    private final String key;

    public GetCommand(String key) {
        this.key = key;
    }

    @Override
    public String execute(Database db) throws KeyNotFoundException {
        return db.get(key);
    }
}
