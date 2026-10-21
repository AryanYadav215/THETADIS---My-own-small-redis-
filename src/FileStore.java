import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * File persistence. save() writes to a temp file and then renames it, so a
 * crash in the middle of a save can never leave a half-written data file.
 */
public class FileStore implements Storable {
    private final File dataFile;

    public FileStore(String path) {
        this.dataFile = new File(path);
    }

    @Override
    public int save(Database db) throws IOException {
        File tmp = new File(dataFile.getPath() + ".tmp");
        List<String> lines = db.snapshot();
        // try-with-resources closes the writer automatically
        try (BufferedWriter w = new BufferedWriter(new FileWriter(tmp))) {
            for (String line : lines) {
                w.write(line);
                w.newLine();
            }
        }
        Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return lines.size();
    }

    @Override
    public int load(Database db) throws IOException {
        if (!dataFile.exists()) {
            return 0;
        }
        int count = 0;
        try (BufferedReader r = new BufferedReader(new FileReader(dataFile))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length != 3) {
                    continue; // skip malformed line
                }
                try {
                    db.restore(p[0], p[1], Long.parseLong(p[2]));
                    count++;
                } catch (NumberFormatException e) {
                    // skip corrupt line
                }
            }
        }
        return count;
    }
}
