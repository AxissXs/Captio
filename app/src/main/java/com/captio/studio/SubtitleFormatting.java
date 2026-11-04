package com.captio.studio;

import java.util.Locale;

/** Shared subtitle formatting rules, independent of Android UI and media APIs. */
final class SubtitleFormatting {
    private SubtitleFormatting() {}

    static String withoutPunctuation(String text) {
        return text.replaceAll("[\\p{Punct}،؛؟٪«»]", "").trim();
    }

    static String srtTimestamp(long millis) {
        if (millis < 0) throw new IllegalArgumentException("Negative subtitle timestamp");
        return String.format(Locale.US, "%02d:%02d:%02d,%03d",
                millis / 3_600_000, (millis / 60_000) % 60,
                (millis / 1_000) % 60, millis % 1_000);
    }
}
