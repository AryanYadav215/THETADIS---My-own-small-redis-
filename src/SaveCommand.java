import java.io.IOException;

public class SaveCommand extends Command {
    private final Storable store;

    public SaveCommand(Storable store) {
        this.store = store;
    }

    @Override
    public String execute(Database db) throws MiniRedisException {
        try {
            int n = store.save(db);
            return "Saved " + n + " key(s)";
        } catch (IOException e) {
            // wrap the low-level exception in our own type
            throw new MiniRedisException("could not save: " + e.getMessage());
        }
    }
}
