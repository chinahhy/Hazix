package tv.hdao.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {
    @Test
    fun acceptsHdaoHttpsPagesAndSubdomains() {
        assertTrue(NavigationPolicy.isAllowed("https://hdao.tv"))
        assertTrue(NavigationPolicy.isAllowed("https://hdao.tv/play/123"))
        assertTrue(NavigationPolicy.isAllowed("https://www.hdao.tv/category/movie"))
        assertTrue(NavigationPolicy.isAllowed("https://img.hdao.tv/poster.jpg"))
    }

    @Test
    fun rejectsLookalikeHostsAndUnsafeSchemes() {
        assertFalse(NavigationPolicy.isAllowed("https://hdao.tv.example.com"))
        assertFalse(NavigationPolicy.isAllowed("https://evil-hdao.tv"))
        assertFalse(NavigationPolicy.isAllowed("http://hdao.tv"))
        assertFalse(NavigationPolicy.isAllowed("javascript:alert(1)"))
        assertFalse(NavigationPolicy.isAllowed("intent://hdao.tv"))
    }

    @Test
    fun acceptsOnlySiteScopedBlobUrls() {
        assertTrue(NavigationPolicy.isAllowed("blob:https://hdao.tv/abc"))
        assertFalse(NavigationPolicy.isAllowed("blob:https://example.com/abc"))
    }
}
