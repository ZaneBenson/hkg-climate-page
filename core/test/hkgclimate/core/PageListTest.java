package hkgclimate.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PageListTest {
    @Test
    public void withPageInsertsAfterTheFirstEntry() {
        assertEquals("0,4,21,3,2,9,10", PageList.withPage("0,21,3,2,9,10"));
        assertEquals("10,4", PageList.withPage("10"));
    }

    @Test
    public void withPageKeepsAListThatHasIt() {
        assertEquals("0,4,21", PageList.withPage("0,4,21"));
        assertEquals("4", PageList.withPage("4"));
    }

    @Test
    public void withPageOnEmptyAndMissing() {
        assertEquals("4", PageList.withPage(""));
        assertNull(PageList.withPage(null));
    }

    @Test
    public void withoutPageRemovesToken4() {
        assertEquals("0,21,3", PageList.withoutPage("0,4,21,3"));
        assertEquals("", PageList.withoutPage("4"));
        assertEquals("0,21", PageList.withoutPage("0,21"));
        assertEquals("0,21", PageList.withoutPage("0,4,21,4"));
        assertNull(PageList.withoutPage(null));
    }

    // Review Focus 5: tokens, not substrings (14, 24 and 40 contain the digit 4)
    @Test
    public void onlyWholeTokensCount() {
        assertEquals("14,4,24", PageList.withPage("14,24"));
        assertEquals("14,24,40", PageList.withoutPage("14,4,24,40"));
        assertEquals("14,24,40", PageList.withoutPage("14,24,40"));
        assertEquals("40,4", PageList.withPage("40"));
    }
}
