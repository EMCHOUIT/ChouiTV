package ma.chouitv.home.tilawah;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * For tests/tilawah_lists_test.py: SurahQueue.java and Playlists.java on tests/tilawah_lists_cases.tsv (the last column, the answer, is
 * cut off before this runs). In a text of lists, «;» stands for a new line. Prints one line per case.
 */
public final class TilawahListsRun {
    private static String nl(String s) { return s.replace(";", "\n"); }
    private static String un(String s) { return s.replace("\n", ";"); }
    private static String arg(String[] f, int i) { return i < f.length ? f[i] : ""; }

    private static int[] queue(String csv) { return SurahQueue.parse(csv, 114); }

    private static String out(List<Playlists.Item> items) { return un(Playlists.serialize(items)); }

    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            for (int i = 0; i < f.length; i++) if (f[i].equals("_")) f[i] = "";
            switch (f[0]) {
                case "normalize": {
                    List<Integer> raw = new ArrayList<>();
                    for (String s : arg(f, 2).split(",")) if (!s.isEmpty()) raw.add(Integer.parseInt(s.trim()));
                    out.append(SurahQueue.csv(SurahQueue.normalize(raw, Integer.parseInt(f[1]))));
                    break;
                }
                case "parseq": out.append(SurahQueue.csv(SurahQueue.parse(f[2], Integer.parseInt(f[1])))); break;
                case "step": out.append(SurahQueue.step(queue(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]))); break;
                case "indexof": out.append(SurahQueue.indexOf(queue(f[1]), Integer.parseInt(f[2]))); break;
                case "position": {
                    int[] q = queue(f[1]);
                    out.append(SurahQueue.indexOf(q, Integer.parseInt(f[2])) + 1).append('/').append(q.length);
                    break;
                }
                case "roundtrip": out.append(out(Playlists.parse(nl(f[1])))); break;
                case "name": out.append(Playlists.nextName(Playlists.parse(nl(arg(f, 2))), f[1])); break;
                case "add": {
                    List<Playlists.Item> items = Playlists.parse(nl(arg(f, 3)));
                    Playlists.add(items, f[1], queue(f[2]));
                    out.append(out(items));
                    break;
                }
                case "update": {
                    List<Playlists.Item> items = Playlists.parse(nl(arg(f, 4)));
                    Playlists.update(items, f[1], f[2], queue(f[3]));
                    out.append(out(items));
                    break;
                }
                case "remove": {
                    List<Playlists.Item> items = Playlists.parse(nl(arg(f, 3)));
                    Playlists.remove(items, f[1], f[2]);
                    out.append(out(items));
                    break;
                }
                case "listsfor": {
                    StringBuilder names = new StringBuilder();
                    for (Playlists.Item it : Playlists.forReciter(Playlists.parse(nl(arg(f, 2))), f[1])) {
                        names.append(names.length() == 0 ? "" : ",").append(it.name);
                    }
                    out.append(names);
                    break;
                }
                case "favorder": {
                    StringBuilder idx = new StringBuilder();
                    for (int i : Playlists.favouriteOrder(Arrays.asList(f[1].split(",")),
                            new HashSet<>(Arrays.asList(arg(f, 2).split(","))))) {
                        idx.append(idx.length() == 0 ? "" : ",").append(i);
                    }
                    out.append(idx);
                    break;
                }
                case "count": out.append(Playlists.surahCount(Integer.parseInt(f[1]))); break;
                case "chip": {
                    int c = Playlists.defaultChip(f[1].equals("1"), f[2].equals("1"));
                    out.append(c == Playlists.CHIP_LISTS ? "lists" : c == Playlists.CHIP_FAV ? "fav" : "reciters");
                    break;
                }
                case "clean": out.append(Playlists.cleanName(f[1])); break;
                case "uniq": out.append(Playlists.uniqueName(Playlists.parse(nl(arg(f, 3))), f[1], f[2], arg(f, 4))); break;
                case "addnamed": {
                    List<Playlists.Item> items = Playlists.parse(nl(arg(f, 4)));
                    Playlists.addNamed(items, f[1], queue(f[2]), f[3]);
                    out.append(out(items));
                    break;
                }
                case "rename": {
                    List<Playlists.Item> items = Playlists.parse(nl(arg(f, 4)));
                    Playlists.rename(items, f[1], f[2], f[3]);
                    out.append(out(items));
                    break;
                }
                case "check": {
                    String bad = Playlists.check(arg(f, 1), nl(arg(f, 2)));
                    out.append(bad == null ? "OK" : bad);
                    break;
                }
                default: out.append("?? ").append(f[0]);
            }
            out.append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
