package ma.chouitv.home.tilawah;

/**
 * Whether the alarm for a prayer arrived too late to play its adhan. A TV in standby may deliver the alarm only when it is switched
 * on again, hours after the prayer: the adhan must not start then (it «depasse son temps»). Judged by the prayer's own time
 * ({@code due}), as the duplicate rule is.
 *
 * Exact alarms are seconds late at most, so a short grace is enough. Without the exact-alarm permission (Android 12+) Android itself
 * may deliver the alarm minutes late (see AdhanScheduler.exactAllowed), so the grace is longer.
 */
final class AdhanDue {
    /** an exact alarm is at most this late and still plays (jitter) */
    static final long GRACE_EXACT = 2 * 60_000L;
    /** without the exact-alarm permission: Android's own delay */
    static final long GRACE_INEXACT = 10 * 60_000L;

    private AdhanDue() {}

    /** True when {@code now} is further past {@code due} than the grace. An alarm with no prayer time (due 0) cannot be judged. */
    static boolean stale(long due, long now, boolean exactAllowed) {
        if (due <= 0) return false;
        return now - due > (exactAllowed ? GRACE_EXACT : GRACE_INEXACT);
    }
}
