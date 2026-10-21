/** Thrown when a key does not exist (or has expired). */
public class KeyNotFoundException extends MiniRedisException {
    private static final long serialVersionUID = 1L;
    public KeyNotFoundException(String key) {
        super("key not found: " + key);
    }
}
