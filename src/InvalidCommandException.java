/** Thrown for unknown commands, wrong arguments or non-numeric INCR values. */
public class InvalidCommandException extends MiniRedisException {
    private static final long serialVersionUID = 1L;
    public InvalidCommandException(String message) {
        super(message);
    }
}
