/**
 * Abstract base class of the Command pattern: every request becomes an object.
 * Subclasses override execute(); the caller only knows about "Command"
 * (runtime polymorphism / dynamic method dispatch).
 */
public abstract class Command {
    public abstract String execute(Database db) throws MiniRedisException;
}
