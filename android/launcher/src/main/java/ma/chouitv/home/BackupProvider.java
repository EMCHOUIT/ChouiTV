package ma.chouitv.home;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import org.json.JSONObject;

/**
 * The home app's half of the settings backup, for the screensaver's settings screen (BACKUP permission: the same signing key only).
 * query: one row, "json" = the adhan and dock settings. update with "json": checks and writes them; the answer is the number of changes plus one,
 * 0 when the file was refused.
 */
public class BackupProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[] {"json"});
        try {
            c.addRow(new Object[] {BackupData.export(getContext()).toString()});
        } catch (Exception ignored) {
            // no row: the screensaver exports without the home settings
        }
        return c;
    }

    @Override public int update(Uri uri, ContentValues v, String s, String[] a) {
        String json = v == null ? null : v.getAsString("json");
        if (json == null) return 0;
        try {
            return BackupData.apply(getContext(), new JSONObject(json)) + 1;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
}
