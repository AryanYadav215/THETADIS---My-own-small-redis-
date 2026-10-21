import java.io.IOException;

/** Anything that can save/load a Database. FileStore is one implementation. */
public interface Storable {
    int save(Database db) throws IOException;   // returns number of keys saved

    int load(Database db) throws IOException;   // returns number of keys loaded
}
