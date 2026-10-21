/**
 * Factory: turns a text line like "SET a 5 EX 10" into the right Command object.
 * Returns the abstract type Command, so callers never care which subclass it is.
 */
public class Parser {
    private final Storable store;

    public Parser(Storable store) {
        this.store = store;
    }

    public Command parse(String line) throws InvalidCommandException {
        if (line == null || line.trim().isEmpty()) {
            throw new InvalidCommandException("empty command");
        }
        String[] p = line.trim().split("\\s+");
        String name = p[0].toUpperCase();

        switch (name) {
            case "SET":
                if (p.length == 3) {
                    return new SetCommand(p[1], p[2]);
                }
                if (p.length == 5 && p[3].equalsIgnoreCase("EX")) {
                    return new SetCommand(p[1], p[2], parseSeconds(p[4]));
                }
                throw new InvalidCommandException("usage: SET key value [EX seconds]");
            case "GET":
                needArgs(p, 2, "GET key");
                return new GetCommand(p[1]);
            case "DEL":
                needArgs(p, 2, "DEL key");
                return new DelCommand(p[1]);
            case "EXISTS":
                needArgs(p, 2, "EXISTS key");
                return new ExistsCommand(p[1]);
            case "INCR":
                needArgs(p, 2, "INCR key");
                return new IncrCommand(p[1]);
            case "TTL":
                needArgs(p, 2, "TTL key");
                return new TtlCommand(p[1]);
            case "KEYS":
                needArgs(p, 1, "KEYS");
                return new KeysCommand();
            case "STATS":
                needArgs(p, 1, "STATS");
                return new StatsCommand();
            case "SAVE":
                needArgs(p, 1, "SAVE");
                return new SaveCommand(store);
            default:
                throw new InvalidCommandException("unknown command: " + p[0]);
        }
    }

    private static void needArgs(String[] p, int count, String usage) throws InvalidCommandException {
        if (p.length != count) {
            throw new InvalidCommandException("usage: " + usage);
        }
    }

    private static long parseSeconds(String s) throws InvalidCommandException {
        try {
            long n = Long.parseLong(s);
            if (n <= 0) {
                throw new InvalidCommandException("EX must be a positive number");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new InvalidCommandException("EX must be a number");
        }
    }
}
