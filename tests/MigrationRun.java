package ma.chouitv.screensaver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * For tests/migration_test.py: MigrationCore.java on tests/migration_cases.tsv ("-" = nothing / null; lists are joined by "|"). A line is
 *   decide   FLAG OLD SAMECERT                 -> ALREADY_DONE / NO_OLD_APP / UNTRUSTED / RUN   (flag, old installed, same certificate: yes/no)
 *   photos   OFFERED HERE                      -> how many to copy
 *   tasks    HAVE INCOMING ROOM                -> positions of INCOMING to add, joined by ",", or "-"
 *   owner    COMPONENTS                        -> package of the chosen screensaver, or "-"
 *   finished OLD COMPONENTS PKG                -> yes / no
 *   fallback FRAGMENT HOMEPANELS GREETING ADHANBAR -> key=value;key=value of the settings file
 *   outcome  SOURCE SETTINGS TASKS OFFERED PHOTOS OFFERED PROBLEM -> the status line
 */
public final class MigrationRun {
    private static String a(String s) { return "-".equals(s) ? null : s; }
    private static boolean yes(String s) { return "yes".equals(s); }
    private static List<String> list(String s) { return "-".equals(s) ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(s.split("\\|", -1))); }

    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "decide":
                    out.append(MigrationCore.decide(yes(f[1]), yes(f[2]), yes(f[3])));
                    break;
                case "photos":
                    out.append(MigrationCore.photosToCopy(Integer.parseInt(f[1]), Integer.parseInt(f[2])));
                    break;
                case "tasks": {
                    List<Integer> r = MigrationCore.tasksToAdd(list(f[1]), list(f[2]), Integer.parseInt(f[3]));
                    StringBuilder b = new StringBuilder();
                    for (int k : r) b.append(b.length() > 0 ? "," : "").append(k);
                    out.append(b.length() == 0 ? "-" : b.toString());
                    break;
                }
                case "owner":
                    out.append(MigrationCore.dreamOwner(a(f[1])).isEmpty() ? "-" : MigrationCore.dreamOwner(a(f[1])));
                    break;
                case "finished":
                    out.append(MigrationCore.moveFinished(yes(f[1]), a(f[2]), f[3]) ? "yes" : "no");
                    break;
                case "fallback": {
                    StringBuilder b = new StringBuilder();
                    for (Map.Entry<String, Object> e : MigrationCore.fallbackSettings(a(f[1]), a(f[2]), a(f[3]), a(f[4])).entrySet())
                        b.append(b.length() > 0 ? ";" : "").append(e.getKey()).append('=').append(e.getValue());
                    out.append(b.length() == 0 ? "-" : b.toString());
                    break;
                }
                case "outcome":
                    out.append(MigrationCore.outcome(f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]),
                        Integer.parseInt(f[5]), Integer.parseInt(f[6]), a(f[7])));
                    break;
                default:
                    out.append("?? ").append(f[0]);
            }
            out.append('\n');
        }
        System.out.print(out);
    }
}
