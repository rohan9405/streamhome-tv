package io.github.rohan9405.feed;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class TopBarTest {
    @Test
    public void settingsIsNeverOffered() {
        for (String[] n : TopBar.NAMES) assertFalse("btnSettings".equals(n[0]));
    }
}
