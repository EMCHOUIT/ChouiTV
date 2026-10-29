package ma.chouitv.home.tilawah;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * For tests/radio_backup_test.py: RadioBackupRules.java on tests/radio_backup_cases.tsv. A line is
 *   countries TAB off TAB fav (the favourites separated by the two characters backslash-n) TAB the answer (OK, or the start of the reason)
 * The offered countries are ma, fr, sa and dz. Prints one line per case: OK or the reason.
 */
public final class RadioBackupRulesRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            String bad = RadioBackupRules.check(Arrays.asList("ma", "fr", "sa", "dz"), f[0], f[1], f[2].replace("\n", "\n"));
            out.append(bad == null ? "OK" : bad).append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
