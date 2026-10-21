public class StatsCommand extends Command {
    @Override
    public String execute(Database db) {
        return "keys=" + db.size() + ", commands_run=" + db.getCommandsRun();
    }
}
