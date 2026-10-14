package app.coolapk.kmp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialLoginScreenTest {
    @Test fun officialCallbackExtractsCodeAndCookie() {
        assertEquals(
            OfficialLoginCallback("one-time", "SESSID=web-session; uid=42"),
            parseOfficialLoginCallback(
                "https://account.coolapk.com/auth/callback?ac=access_token&code=one-time&ck=SESSID%3Dweb-session%3B%20uid%3D42",
            ),
        )
    }

    @Test fun forwardFragmentExtractsCode() {
        assertEquals(
            OfficialLoginCallback("next-code", null),
            parseOfficialLoginCallback(
                "https://www.coolapk.com/#/auth_callback?ac=access_token&code=next-code",
            ),
        )
    }

    @Test fun rejectsUntrustedOrIncompleteCallbacks() {
        assertNull(parseOfficialLoginCallback("http://account.coolapk.com/auth/callback?code=abc"))
        assertNull(parseOfficialLoginCallback("https://account.coolapk.com.evil.test/auth/callback?code=abc"))
        assertNull(parseOfficialLoginCallback("https://www.coolapk.com/#/unrelated?code=abc"))
        assertEquals(
            OfficialLoginCallback(null, null),
            parseOfficialLoginCallback("https://account.coolapk.com/auth/callback?code=abc"),
        )
    }

    @Test fun nativeCookiesOverrideCallbackAndOtherDomains() {
        val merged = mergeLoginCookies(
            listOf("SESSID=callback; uid=42", "SESSID=other; token=native", "SESSID=account"),
        )
        assertEquals(
            setOf("SESSID=account", "uid=42", "token=native"),
            merged.split(';').map(String::trim).toSet(),
        )
    }

    @Test fun rejectsPlaceholderSession() {
        assertTrue(hasLoginSession("uid=42; SESSID=real"))
        assertFalse(hasLoginSession("SESSID=deleted"))
        assertFalse(hasLoginSession("SESSID=expired"))
        assertFalse(hasLoginSession("uid=42"))
    }
}
