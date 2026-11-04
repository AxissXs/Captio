package com.captio.studio;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SubtitleFormattingTest {
    @Test public void writesSrtTimestampsWithMillisecondsAndHours() {
        assertEquals("00:00:00,000", SubtitleFormatting.srtTimestamp(0));
        assertEquals("01:02:03,004", SubtitleFormatting.srtTimestamp(3_723_004));
        assertEquals("25:00:00,000", SubtitleFormatting.srtTimestamp(90_000_000));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsNegativeTimestamp() {
        SubtitleFormatting.srtTimestamp(-1);
    }
    @Test public void removesPersianAndLatinPunctuation() {
        assertEquals("سلام دنیا hello", SubtitleFormatting.withoutPunctuation(" سلام، دنیا! hello؟ "));
        assertEquals("", SubtitleFormatting.withoutPunctuation("«؟!»"));
    }
}
