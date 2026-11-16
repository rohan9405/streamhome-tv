package io.github.rohan9405.feed;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class ImdbSummaryTest {
    @Test
    public void ratingAndVotes() throws Exception {
        assertArrayEquals(new String[] {"7.4", "123456"}, Imdb.parseSummary(
                "{data: {titles: [{id: 'tt1', ratingsSummary: {aggregateRating: 7.43, voteCount: 123456}}]}}", "tt1"));
    }

    @Test
    public void noRatingYet() throws Exception {
        assertArrayEquals(new String[] {"", ""}, Imdb.parseSummary(
                "{data: {titles: [{id: 'tt1', ratingsSummary: {aggregateRating: null, voteCount: 0}}]}}", "tt1"));
        assertArrayEquals(new String[] {"", ""}, Imdb.parseSummary("{data: {titles: []}}", "tt1"));
    }
}
