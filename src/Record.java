/**
 * One stored value plus its expiry time.
 * final class + final fields = immutable, so it can never be seen half-modified
 * by another thread. To "update" a key we replace the whole Record.
 */
public final class Record {
    private final String value;
    private final long expiryTime; // epoch millis, 0 means "never expires"

    public Record(String value, long expiryTime) {
        this.value = value;
        this.expiryTime = expiryTime;
    }

    // Constructor overloading: a record that never expires.
    public Record(String value) {
        this(value, 0);
    }

    public String getValue() {
        return value;
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    public boolean isExpired() {
        return expiryTime != 0 && System.currentTimeMillis() > expiryTime;
    }
}
