package ma.chouitv.home.tilawah;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * For tests/station_nav_test.py: StationNav.java on tests/station_nav_cases.tsv. A line is
 *   next|prev  GROUPS(comma list, one per station)  FROM   -> the index
 *   url        URLS(comma list)  URL                       -> the index (or -1)
 */
public final class StationNavRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            List<String> list = Arrays.asList(f[1].split(","));
            switch (f[0]) {
                case "next": out.append(StationNav.neighbour(list, Integer.parseInt(f[2]), 1)); break;
                case "prev": out.append(StationNav.neighbour(list, Integer.parseInt(f[2]), -1)); break;
                default: out.append(StationNav.indexOfUrl(list, f[2]));
            }
            out.append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
