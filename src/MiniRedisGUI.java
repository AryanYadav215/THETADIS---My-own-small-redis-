import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Visual front end for MiniRedis. It uses the SAME classes as the console
 * version (Database, Parser, CommandQueue, Worker, ClientThread, FileStore);
 * only the display is new.
 *
 *   Run:  java -cp out MiniRedisGUI
 *
 * What it shows:
 *   - the CommandQueue (commands waiting to be picked up)
 *   - the 3 Worker threads and what each one handled last
 *   - the Database contents with live expiry countdown
 *   - a log of results and errors
 *   - a one-line explanation of which OOP concept just ran
 */
@SuppressWarnings({"serial", "this-escape"})
public class MiniRedisGUI extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final int WORKERS = 3;
    private static final Color RED = new Color(0xD8, 0x2C, 0x20);
    private static final Color DARK = new Color(0x1F, 0x29, 0x37);
    private static final Font UI = new Font("SansSerif", Font.PLAIN, 13);
    private static final Font MONO = new Font("Monospaced", Font.PLAIN, 13);

    // ----- the real MiniRedis engine (same classes as the console version) -----
    private final Database db = new Database();
    private final FileStore store = new FileStore("data.db");
    private final Parser parser = new Parser(store);
    private final CommandQueue queue = new CommandQueue();
    private final Database.Cleaner cleaner = db.new Cleaner();
    private final Thread[] workerThreads = new Thread[WORKERS];

    /** Slow-motion delay (ms) each worker waits after a command, so the queue is visible. */
    private volatile int slowMillis = 0;

    // ----- widgets -----
    private final JTextField input = new JTextField();
    private final DefaultListModel<String> queueModel = new DefaultListModel<>();
    private final JLabel queueTitle = new JLabel("Command Queue  (0 waiting)");
    private final JLabel[] workerLabels = new JLabel[WORKERS];
    private final DefaultTableModel tableModel = new DefaultTableModel(new Object[] {"Key", "Value", "Expires in"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
    };
    private final JTextArea log = new JTextArea();
    private final JTextArea concept = new JTextArea(3, 20);
    private final JLabel stats = new JLabel(" ");
    private final JLabel raceResult = new JLabel("Race demo: not run yet");
    private final Timer refreshTimer;

    public MiniRedisGUI() {
        super("MiniRedis - Multithreaded In-Memory Key-Value Database (Java OOP)");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        setLayout(new BorderLayout(0, 0));
        add(buildTop(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        startEngine();

        refreshTimer = new Timer(200, e -> refresh());
        refreshTimer.start();

        setSize(1300, 760);
        setMinimumSize(new Dimension(1000, 600));
        setLocationRelativeTo(null);
    }

    // =====================================================================
    //  Engine: start / stop threads (this is the multithreading part)
    // =====================================================================

    private void startEngine() {
        try {
            int n = store.load(db);
            appendLog("Loaded " + n + " key(s) from data.db");
        } catch (IOException e) {
            appendLog("Could not load data.db: " + e.getMessage());
        }

        Thread cleanerThread = new Thread(cleaner, "cleaner");
        cleanerThread.setDaemon(true);
        cleanerThread.start();

        // The listener is called by a worker thread when a command finishes.
        Worker.ResultListener listener = (name, req, reply) -> {
            SwingUtilities.invokeLater(() -> onResult(name, req, reply));
            int d = slowMillis;
            if (d > 0) {
                try {
                    Thread.sleep(d); // pretend the worker is busy
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        for (int i = 0; i < WORKERS; i++) {
            workerThreads[i] = new Thread(new Worker(queue, db, parser, listener), "worker-" + (i + 1));
            workerThreads[i].start();
        }
    }

    private void shutdown() {
        refreshTimer.stop();
        queue.close();                       // wake sleeping workers so they can finish
        for (Thread w : workerThreads) {
            try {
                w.join(3000);                // wait for them to stop
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        cleaner.stop();
        try {
            store.save(db);
        } catch (IOException ignored) {
            // nothing more we can do while closing
        }
        dispose();
        System.exit(0);
    }

    // =====================================================================
    //  Actions
    // =====================================================================

    /** Producer side: put a command on the shared queue (like a ClientThread would). */
    private void submit(String line) {
        line = line.trim();
        if (line.isEmpty()) {
            return;
        }
        queue.put(new CommandQueue.Request("You", line));
        concept.setText("PRODUCER: your command went into CommandQueue.put() - a synchronized method that "
                + "then calls notifyAll() to wake a sleeping worker thread (inter-thread communication).");
        input.setText("");
        input.requestFocusInWindow();
    }

    /** Runs on the Swing thread after a worker finished a command. */
    private void onResult(String workerName, CommandQueue.Request req, String reply) {
        appendLog("[" + workerName + "] " + req.client + "> " + req.line + "   ->   " + reply);
        int idx = workerName.charAt(workerName.length() - 1) - '1';
        if (idx >= 0 && idx < WORKERS) {
            workerLabels[idx].setText("<html><b>" + workerName + "</b> handled: " + escape(req.line)
                    + "<br>&nbsp;&nbsp;&nbsp;&rarr; " + escape(reply) + "</html>");
        }
        concept.setText(explain(req.line, reply));
    }

    private static String explain(String line, String reply) {
        String cmd = line.trim().split("\\s+")[0].toUpperCase();
        if (reply.startsWith("ERROR")) {
            return "EXCEPTION HANDLING: the Parser/Database threw a custom exception (InvalidCommandException or "
                    + "KeyNotFoundException). The Worker's try-catch caught it and printed a message - the program did not crash.";
        }
        switch (cmd) {
            case "SET":
                return "SET: Parser (factory) built a SetCommand. cmd.execute(db) is RUNTIME POLYMORPHISM - the worker only "
                        + "knows 'Command'. db.set() is synchronized and stores an immutable final Record (value + expiry time).";
            case "GET":
                return "GET: GetCommand overrides execute() from the abstract class Command. db.get() is synchronized and checks "
                        + "the expiry time (lazy expiry); a missing or expired key throws KeyNotFoundException.";
            case "INCR":
                return "INCR: read + add 1 + write happen inside ONE synchronized method, so two threads can never lose an update. "
                        + "Try the Race Demo button to see what happens without it.";
            case "DEL":
                return "DEL: DelCommand removes the Record from the private HashMap (encapsulation: only Database methods can touch it). "
                        + "The Record loses its last reference, so the garbage collector can free it.";
            case "EXISTS":
                return "EXISTS: ExistsCommand asks the Database; an expired record counts as not existing.";
            case "TTL":
                return "TTL: seconds left before the key's expiry time passes (-1 means it never expires).";
            case "KEYS":
                return "KEYS: returns a sorted copy of the live keys - the internal map is never exposed (encapsulation).";
            case "STATS":
                return "STATS: shows the number of keys and how many commands the workers have run in total.";
            case "SAVE":
                return "SAVE: SaveCommand uses the Storable INTERFACE; FileStore implements it with FILE I/O "
                        + "(writes a temp file, then renames it so a crash never leaves a half-written file).";
            default:
                return "Command executed.";
        }
    }

    private void fireClients() {
        List<String> a = List.of("SET city Jaipur", "SET otp 4821 EX 10", "GET otp", "TTL otp", "INCR hits", "SAVE");
        List<String> b = List.of("SET name Rahul", "GET name", "GET xyz", "FLY away", "SET age abc", "INCR age", "KEYS");
        new Thread(new ClientThread("ClientA", a, queue, 450), "clientA").start();
        new Thread(new ClientThread("ClientB", b, queue, 380), "clientB").start();
        concept.setText("MULTITHREADING: two ClientThread objects (they implement Runnable) are now putting commands into the "
                + "queue at the same time while 3 Worker threads take them out. This is the producer-consumer pattern.");
    }

    private void fireIncrBurst() {
        for (int c = 1; c <= 5; c++) {
            List<String> cmds = List.of("INCR hits", "INCR hits", "INCR hits", "INCR hits");
            new Thread(new ClientThread("Client" + c, cmds, queue, 60), "burst" + c).start();
        }
        concept.setText("SYNCHRONIZATION: 5 clients x 4 INCR = 20 increments of 'hits', handled by 3 workers at once. "
                + "Because Database.incr is synchronized, hits goes up by exactly 20 - none are lost.");
    }

    private void runRace() {
        raceResult.setText("Race demo: running...");
        new Thread(() -> {
            try {
                long[] r = RaceDemo.compute();
                int expected = RaceDemo.THREADS * RaceDemo.INCREMENTS;
                SwingUtilities.invokeLater(() -> {
                    raceResult.setText("<html>Race demo (" + RaceDemo.THREADS + " threads x " + RaceDemo.INCREMENTS
                            + " increments, expected " + expected + "):&nbsp; WITHOUT synchronized = <b style='color:#d82c20'>"
                            + r[0] + "</b> &nbsp; | &nbsp; WITH synchronized = <b style='color:#15803d'>" + r[1] + "</b></html>");
                    concept.setText("RACE CONDITION: without the lock, threads read the same old value and overwrite each other, "
                            + "so updates are lost (" + r[0] + " instead of " + expected + "). synchronized makes the read-add-write "
                            + "one indivisible step, so the answer is always exact (" + r[1] + ").");
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "race-demo").start();
    }

    // =====================================================================
    //  Refreshing the display (called 5 times a second by a Swing Timer)
    // =====================================================================

    private void refresh() {
        List<String> lines = db.snapshot();
        Collections.sort(lines);
        tableModel.setRowCount(0);
        long now = System.currentTimeMillis();
        for (String line : lines) {
            String[] p = line.split("\t");
            if (p.length != 3) {
                continue;
            }
            long expiry = Long.parseLong(p[2]);
            String ttl = expiry == 0 ? "never" : Math.max(0, (expiry - now + 999) / 1000) + " s";
            tableModel.addRow(new Object[] {p[0], p[1], ttl});
        }

        List<String> pending = queue.pending();
        queueModel.clear();
        for (String s : pending) {
            queueModel.addElement(s);
        }
        queueTitle.setText("Command Queue  (" + pending.size() + " waiting)");
        stats.setText("Keys: " + db.size() + "   |   Commands run: " + db.getCommandsRun()
                + "   |   Worker threads: " + WORKERS + " + cleaner thread");
    }

    private void appendLog(String text) {
        log.append(text + "\n");
        if (log.getLineCount() > 400) {
            try {
                log.replaceRange("", 0, log.getLineEndOffset(100));
            } catch (javax.swing.text.BadLocationException ignored) {
                // ignore: trimming old lines is best-effort
            }
        }
        log.setCaretPosition(log.getDocument().getLength());
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // =====================================================================
    //  Building the screen
    // =====================================================================

    private JPanel buildTop() {
        JPanel top = new JPanel(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(DARK);
        header.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        JLabel title = new JLabel("MiniRedis");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        JLabel sub = new JLabel("Multithreaded in-memory key-value database  |  Client > Queue > Worker > Parser > Command > Database");
        sub.setForeground(new Color(0xCB, 0xD5, 0xE1));
        sub.setFont(UI);
        header.add(title, BorderLayout.WEST);
        header.add(sub, BorderLayout.EAST);

        JPanel controls = new JPanel(new BorderLayout(8, 6));
        controls.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));

        input.setFont(MONO);
        input.setToolTipText("Type a command, e.g.  SET name Rahul   then press Enter");
        input.addActionListener(e -> submit(input.getText()));
        JButton send = styled(new JButton("Send command"), true);
        send.addActionListener(e -> submit(input.getText()));
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.add(new JLabel("Command > "), BorderLayout.WEST);
        inputRow.add(input, BorderLayout.CENTER);
        inputRow.add(send, BorderLayout.EAST);

        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        String[] samples = {"SET name Rahul", "GET name", "INCR counter", "SET otp 4821 EX 10", "TTL otp",
            "GET missing", "FLY away", "DEL name", "KEYS", "STATS", "SAVE"};
        for (String s : samples) {
            JButton b = styled(new JButton(s), false);
            b.addActionListener(e -> submit(s));
            quick.add(b);
        }

        JPanel demos = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton clients = styled(new JButton("Start 2 clients (threads)"), true);
        clients.addActionListener(e -> fireClients());
        JButton burst = styled(new JButton("5 clients x 4 INCR"), true);
        burst.addActionListener(e -> fireIncrBurst());
        JButton race = styled(new JButton("Run Race Demo"), true);
        race.addActionListener(e -> runRace());
        demos.add(clients);
        demos.add(burst);
        demos.add(race);

        JSlider slow = new JSlider(0, 2000, 0);
        slow.setPreferredSize(new Dimension(220, 28));
        JLabel slowLabel = new JLabel("Worker speed: full");
        slow.addChangeListener(e -> {
            slowMillis = slow.getValue();
            slowLabel.setText(slowMillis == 0 ? "Worker speed: full" : "Slow motion: " + slowMillis + " ms per command");
        });
        demos.add(Box(12));
        demos.add(slowLabel);
        demos.add(slow);

        JPanel rows = new JPanel(new GridLayout(3, 1, 0, 2));
        rows.add(inputRow);
        rows.add(quick);
        rows.add(demos);
        controls.add(rows, BorderLayout.CENTER);

        top.add(header, BorderLayout.NORTH);
        top.add(controls, BorderLayout.CENTER);
        return top;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel(new GridLayout(1, 3, 10, 0));
        center.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));

        // --- left: queue + workers ---
        JPanel left = new JPanel(new GridLayout(2, 1, 0, 10));
        JList<String> queueList = new JList<>(queueModel);
        queueList.setFont(MONO);
        JPanel queuePanel = new JPanel(new BorderLayout());
        queueTitle.setFont(UI.deriveFont(Font.BOLD));
        queuePanel.add(queueTitle, BorderLayout.NORTH);
        queuePanel.add(new JScrollPane(queueList), BorderLayout.CENTER);
        queuePanel.setBorder(titled("1. CommandQueue (shared buffer: put / wait / notifyAll)"));

        JPanel workersPanel = new JPanel(new GridLayout(WORKERS, 1, 0, 6));
        for (int i = 0; i < WORKERS; i++) {
            workerLabels[i] = new JLabel("<html><b>worker-" + (i + 1) + "</b> idle (waiting)</html>");
            workerLabels[i].setFont(UI);
            workerLabels[i].setOpaque(true);
            workerLabels[i].setBackground(new Color(0xF1, 0xF5, 0xF9));
            workerLabels[i].setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            workersPanel.add(workerLabels[i]);
        }
        workersPanel.setBorder(titled("2. Worker threads (take command > Parser > execute)"));
        left.add(queuePanel);
        left.add(workersPanel);

        // --- middle: database ---
        JTable table = new JTable(tableModel);
        table.setFont(MONO);
        table.setRowHeight(24);
        table.getTableHeader().setFont(UI.deriveFont(Font.BOLD));
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                String s = String.valueOf(v);
                boolean soon = s.endsWith(" s") && Integer.parseInt(s.replace(" s", "")) <= 3;
                comp.setForeground(soon ? RED : Color.BLACK);
                return comp;
            }
        });
        JPanel dbPanel = new JPanel(new BorderLayout());
        dbPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        dbPanel.setBorder(titled("3. Database (private HashMap of final Records)"));

        // --- right: log ---
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setFont(MONO);
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.add(new JScrollPane(log), BorderLayout.CENTER);
        logPanel.setBorder(titled("4. Results and errors"));

        center.add(left);
        center.add(dbPanel);
        center.add(logPanel);
        return center;
    }

    private JPanel buildBottom() {
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 12, 10, 12));

        concept.setEditable(false);
        concept.setLineWrap(true);
        concept.setWrapStyleWord(true);
        concept.setFont(UI.deriveFont(Font.BOLD, 14f));
        concept.setBackground(new Color(0xFE, 0xF9, 0xC3));
        concept.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        concept.setText("Type a command or press a button. This box explains which OOP concept just ran.");

        raceResult.setFont(UI);
        stats.setFont(UI);
        JPanel info = new JPanel(new GridLayout(2, 1));
        info.add(raceResult);
        info.add(stats);

        JScrollPane conceptScroll = new JScrollPane(concept);
        conceptScroll.setBorder(titled("What just happened (OOP concept)"));
        bottom.add(conceptScroll, BorderLayout.CENTER);
        bottom.add(info, BorderLayout.SOUTH);
        return bottom;
    }

    private static javax.swing.border.TitledBorder titled(String text) {
        javax.swing.border.TitledBorder b = BorderFactory.createTitledBorder(text);
        b.setTitleFont(UI.deriveFont(Font.BOLD));
        return b;
    }

    private static JButton styled(JButton b, boolean primary) {
        b.setFont(UI);
        b.setFocusPainted(false);
        if (primary) {
            b.setBackground(RED);
            b.setForeground(Color.WHITE);
            b.setOpaque(true);
            b.setBorderPainted(false);
        }
        return b;
    }

    private static Component Box(int width) {
        JPanel p = new JPanel();
        p.setPreferredSize(new Dimension(width, 1));
        p.setOpaque(false);
        return p;
    }

    // =====================================================================

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to the default look
        }
        SwingUtilities.invokeLater(() -> new MiniRedisGUI().setVisible(true));
    }
}
