package app.sloplex.core.model
import org.junit.Assert.*
import org.junit.Test
class TimelineTest {
    @Test fun directTimeIsSourceTime() { assertEquals(42000L,PlaybackTimeline(0,90000).sourcePosition(42000)) }
    @Test fun resumeAddsOffsetToHlsClock() { val t=PlaybackTimeline(90125,180000); assertEquals("90.125",t.offsetSeconds); assertEquals(91125L,t.sourcePosition(1000)); assertEquals(1000L,t.outputPosition(91125)) }
    @Test fun sourceTimeIsBounded() { assertEquals(10000L,PlaybackTimeline(9000,10000).sourcePosition(5000)); assertEquals(9000L,PlaybackTimeline(9000,10000).sourcePosition(-100)) }
    @Test fun negativeOutputSeekIsClamped() { assertEquals(0L,PlaybackTimeline(9000,10000).outputPosition(5000)) }
    @Test fun unknownDurationDoesNotZeroProgress() { assertEquals(10100L,PlaybackTimeline(100,0).sourcePosition(10000)) }
    @Test fun cacheIdentityIncludesBothServerAndAccount() { assertNotEquals(CacheScope("a",1),CacheScope("a",2)); assertNotEquals(CacheScope("a",1),CacheScope("b",1)) }
    @Test fun tokenAndPasswordNeverAppearInToString() { assertFalse(DeviceToken(1,token="secret").toString().contains("secret")); assertFalse(LoginRequest("u","secret","d").toString().contains("secret")) }
}
