package com.imedia.box.player;

import androidx.media3.common.PlaybackException;

import org.junit.Test;

import static com.imedia.box.player.PlaybackFailure.Kind.DECODE;
import static com.imedia.box.player.PlaybackFailure.Kind.NETWORK;
import static com.imedia.box.player.PlaybackFailure.Kind.PLAYBACK;
import static com.imedia.box.player.PlaybackFailure.Kind.SOURCE;
import static com.imedia.box.player.PlaybackFailure.Kind.TIMEOUT;
import static org.junit.Assert.assertEquals;

public class PlaybackFailureTest {

    @Test
    public void classifiesStableMedia3ErrorCodesWithoutReadingMessage() {
        assertEquals(TIMEOUT, PlaybackFailure.fromErrorCode("任意文案", PlaybackException.ERROR_CODE_TIMEOUT).kind());
        assertEquals(NETWORK, PlaybackFailure.fromErrorCode("任意文案", PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED).kind());
        assertEquals(SOURCE, PlaybackFailure.fromErrorCode("任意文案", PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED).kind());
        assertEquals(DECODE, PlaybackFailure.fromErrorCode("任意文案", PlaybackException.ERROR_CODE_DECODING_FAILED).kind());
        assertEquals(PLAYBACK, PlaybackFailure.fromErrorCode("任意文案", PlaybackException.ERROR_CODE_UNSPECIFIED).kind());
    }

    @Test
    public void factoriesPreserveMessageAndUnsetCode() {
        PlaybackFailure failure = PlaybackFailure.source("bad url");

        assertEquals(SOURCE, failure.kind());
        assertEquals("bad url", failure.message());
        assertEquals(PlaybackException.ERROR_CODE_UNSPECIFIED, failure.errorCode());
    }
}
