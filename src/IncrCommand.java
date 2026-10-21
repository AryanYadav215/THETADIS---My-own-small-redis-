public class IncrCommand extends Command {
    private final String key;

    public IncrCommand(String key) {
        this.key = key;
    }

    @Override
    public String execute(Database db) throws InvalidCommandException {
        return String.valueOf(db.incr(key));
    }
}
