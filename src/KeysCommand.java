import java.util.List;

public class KeysCommand extends Command {
    @Override
    public String execute(Database db) {
        List<String> keys = db.keys();
        return keys.isEmpty() ? "(empty)" : String.join(", ", keys);
    }
}
