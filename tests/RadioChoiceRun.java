package ma.chouitv.home.tilawah;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * For tests/radio_choice_test.py: RadioChoice.java on tests/radio_choice_cases.tsv. A line is
 *   parse  CSV            -> the countries kept, joined by commas
 *   toggle CSV CC         -> the countries after the tick (or REFUSED)
 */
public final class RadioChoiceRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            if (f[0].equals("parse")) {
                out.append(RadioChoice.join(RadioChoice.parse(f[1])));
            } else {
                List<String> r = RadioChoice.toggle(RadioChoice.parse(f[1]), f[2]);
                out.append(r == null ? "REFUSED" : RadioChoice.join(r));
            }
            out.append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
