package ma.chouitv.home;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For tests/update_planner_test.py: UpdatePlanner.java on tests/update_planner_cases.tsv. A line is
 *   plan   LATEST  INSTALLED(pkg=code,...)            -> the packages to install, in order (or "-")
 *   mixed  LATEST  INSTALLED                          -> yes / no
 *   wait   NOW FORCE LATEST DECLINEDVERSION DECLINEDAT FAILEDAT -> yes / no
 *   stale  NOW FORCE STARTEDAT                        -> yes / no
 * The listed packages are always the screensaver then the home app (as latest.json lists them).
 */
public final class UpdatePlannerRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        List<String> listed = Arrays.asList("ma.chouitv.screensaver", "ma.chouitv.home");
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "plan": {
                    List<String> p = UpdatePlanner.plan(Long.parseLong(f[1]), listed, installed(f[2]));
                    out.append(p.isEmpty() ? "-" : String.join(",", p));
                    break;
                }
                case "mixed": out.append(UpdatePlanner.mixed(Long.parseLong(f[1]), listed, installed(f[2])) ? "yes" : "no"); break;
                case "wait":
                    out.append(UpdatePlanner.mustWait(Long.parseLong(f[1]), f[2].equals("1"), Integer.parseInt(f[3]), Integer.parseInt(f[4]),
                            Long.parseLong(f[5]), Long.parseLong(f[6])) ? "yes" : "no");
                    break;
                default: out.append(UpdatePlanner.staleInstall(Long.parseLong(f[1]), f[2].equals("1"), Long.parseLong(f[3])) ? "yes" : "no");
            }
            out.append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }

    private static Map<String, Long> installed(String s) {
        Map<String, Long> m = new HashMap<>();
        for (String kv : s.split(",")) {
            if (kv.isEmpty()) continue;
            String[] p = kv.split("=");
            m.put("s".equals(p[0]) ? "ma.chouitv.screensaver" : "ma.chouitv.home", Long.parseLong(p[1]));
        }
        return m;
    }
}
