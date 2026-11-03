package io.github.rohan9405.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.security.SecureRandom;
import java.util.Map;

public class PhoneServerTest {
    @Test
    public void formDecodes() {
        Map<String, String> f = PhoneServer.parseForm("pin=012345&words=the+family%20man&empty=&=x");
        assertEquals("012345", f.get("pin"));
        assertEquals("the family man", f.get("words"));
        assertEquals("", f.get("empty"));
        assertEquals(3, f.size());
    }

    @Test
    public void pinMustMatchInFull() {
        assertTrue(PhoneServer.pinMatches("012345", " 012345 "));
        assertFalse(PhoneServer.pinMatches("012345", "01234"));
        assertFalse(PhoneServer.pinMatches("012345", null));
    }

    @Test
    public void pinIsAlwaysSixDigits() {
        SecureRandom r = new SecureRandom();
        for (int i = 0; i < 200; i++) assertTrue(PhoneServer.newPin(r).matches("\\d{6}"));
    }

    @Test
    public void resultPagesEscapeWhatTheyShow() {
        assertTrue(PhoneServer.result("<b>&").contains("&lt;b&gt;&amp;"));
    }
}
