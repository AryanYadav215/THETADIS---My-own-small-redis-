/** Parent of all our custom (checked) exceptions: one catch handles them all. */
public class MiniRedisException extends Exception {
    private static final long serialVersionUID = 1L;
    public MiniRedisException(String message) {
        super(message);
    }
}
