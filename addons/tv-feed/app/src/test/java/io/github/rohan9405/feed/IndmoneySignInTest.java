package io.github.rohan9405.feed;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class IndmoneySignInTest {
    @Test
    public void readsTheReturnAddress() {
        assertArrayEquals(new String[] {"abc-123", "s t"},
                Indmoney.codeAndState("http://127.0.0.1:53682/callback?code=abc-123&state=s%20t"));
    }

    @Test
    public void readsJustTheQueryInAnyOrder() {
        assertArrayEquals(new String[] {"abc", "xyz"}, Indmoney.codeAndState("state=xyz&iss=x&code=abc#frag"));
    }

    @Test
    public void refusesWhatIsNotASignIn() {
        assertNull(Indmoney.codeAndState("http://127.0.0.1:53682/callback?error=access_denied&state=xyz"));
        assertNull(Indmoney.codeAndState("http://127.0.0.1:53682/callback?code=abc"));
        assertNull(Indmoney.codeAndState("code=a<b&state=x"));
        assertNull(Indmoney.codeAndState(null));
    }
}
