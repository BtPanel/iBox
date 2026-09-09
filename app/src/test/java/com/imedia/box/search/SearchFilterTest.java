package com.imedia.box.search;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class SearchFilterTest {

    @Test
    public void classifiesMissingSearchMetadata() {
        assertEquals(List.of(SearchFilter.TYPE_MOVIE), SearchFilter.classify("", "Avatar", "HD", false));
        assertEquals(List.of(SearchFilter.TYPE_SERIES), SearchFilter.classify("", "Avatar Season 2", "", false));
        assertEquals(List.of(SearchFilter.TYPE_ANIME), SearchFilter.classify("", "\u52a8\u6f2b\u7b2c\u4e8c\u5b63", "", false));
        assertEquals(List.of(SearchFilter.TYPE_OTHER), SearchFilter.classify("", "Cloud result", "", true));
    }

    @Test
    public void prefersBroadTypeButKeepsUnknownExplicitGenres() {
        assertEquals(List.of(SearchFilter.TYPE_DOCUMENTARY), SearchFilter.classify("\u7eaa\u5f55\u7247", "Nature", "", false));
        assertEquals(List.of("Sci-Fi", "Mystery"), SearchFilter.classify("Sci-Fi / Mystery", "Example", "", false));
    }
}
