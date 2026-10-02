package com.media.ibox.dlna;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CastPollResultTest {

    @Test
    public void positionSuccessKeepsDeviceReachableWhenTransportQueryFails() {
        CastPollResult poll = new CastPollResult();

        assertEquals(CastPollResult.Outcome.ALIVE, poll.position(true));
        assertEquals(CastPollResult.Outcome.WAITING, poll.transport(false));
    }

    @Test
    public void transportSuccessKeepsDeviceReachableWhenPositionQueryFails() {
        CastPollResult poll = new CastPollResult();

        assertEquals(CastPollResult.Outcome.WAITING, poll.position(false));
        assertEquals(CastPollResult.Outcome.ALIVE, poll.transport(true));
    }

    @Test
    public void bothQueriesMustFailBeforeCountingOneUnreachablePoll() {
        CastPollResult poll = new CastPollResult();

        assertEquals(CastPollResult.Outcome.WAITING, poll.transport(false));
        assertEquals(CastPollResult.Outcome.FAILED, poll.position(false));
    }

    @Test
    public void unansweredQueriesCountAsOneFailedPollAfterTimeout() {
        CastPollResult poll = new CastPollResult();

        assertEquals(CastPollResult.Outcome.FAILED, poll.timeout());
        assertEquals(CastPollResult.Outcome.WAITING, poll.position(true));
    }

    @Test
    public void aSuccessfulQueryPreventsTheTimeoutFromCountingAsFailure() {
        CastPollResult poll = new CastPollResult();

        assertEquals(CastPollResult.Outcome.ALIVE, poll.position(true));
        assertEquals(CastPollResult.Outcome.WAITING, poll.timeout());
    }
}
