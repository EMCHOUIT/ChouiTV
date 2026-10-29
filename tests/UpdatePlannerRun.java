package ma.chouitv.home;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * For tests/update_planner_test.py: UpdatePlanner.java on tests/update_planner_cases.tsv. A line is
 *   plan    LATEST LISTED INSTALLED                 -> the packages to install (or "-"); LISTED: h (one entry), sh (old two-entry manifest), s
 *   accept  PKG APKVERSION ANNOUNCED INSTALLED SAMESIGNER(1/0) -> ok, or why not (package, older-than-announced, not-newer, signer)
 *   signer  GOT MINE                                -> yes / no (comma lists of certificates)
 *   left    TARGET PLAN INSTALLED                   -> what a plan has left once the installs are looked at again (or "-")
 *   wait    NOW FORCE LATEST DECLINEDVERSION DECLINEDAT FAILEDAT -> yes / no
 *   stale   NOW FORCE STARTEDAT                     -> yes / no
 * INSTALLED is pkg=code,... with s = the old screensaver, h = the home app.
 */
public final class UpdatePlannerRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "plan": {
                    List<String> p = UpdatePlanner.plan(Long.parseLong(f[1]), listed(f[2]), installed(f[3]));
                    out.append(p.isEmpty() ? "-" : String.join(",", p));
                    break;
                }
                case "accept": {
                    String r = UpdatePlanner.refusal(name(f[1]), Long.parseLong(f[2]), Long.parseLong(f[3]), Long.parseLong(f[4]), f[5].equals("1"));
                    out.append(r == null ? "ok" : r);
                    break;
                }
                case "signer": out.append(UpdatePlanner.sameSigner(set(f[1]), set(f[2])) ? "yes" : "no"); break;
                case "left": {
                    List<String> p = UpdatePlanner.remaining(listed(f[2]), Long.parseLong(f[1]), installed(f[3]));
                    out.append(p.isEmpty() ? "-" : String.join(",", p));
                    break;
                }
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

    private static String name(String s) { return "s".equals(s) ? "ma.chouitv.screensaver" : "h".equals(s) ? "ma.chouitv.home" : s; }

    private static List<String> listed(String s) {
        List<String> l = new ArrayList<>();
        for (char ch : s.toCharArray()) l.add(name(String.valueOf(ch)));
        return l;
    }

    private static Set<String> set(String s) { return s.isEmpty() ? new HashSet<String>() : new HashSet<>(Arrays.asList(s.split(","))); }

    private static Map<String, Long> installed(String s) {
        Map<String, Long> m = new HashMap<>();
        for (String kv : s.split(",")) {
            if (kv.isEmpty()) continue;
            String[] p = kv.split("=");
            m.put(name(p[0]), Long.parseLong(p[1]));
        }
        return m;
    }
}
