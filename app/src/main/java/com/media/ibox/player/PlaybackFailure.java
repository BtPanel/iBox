package com.media.ibox.player;

import androidx.media3.common.PlaybackException;

public record PlaybackFailure(Kind kind, String message, int errorCode) {

    public enum Kind { SOURCE, NETWORK, TIMEOUT, DECODE, PLAYBACK }

    public PlaybackFailure {
        kind = kind == null ? Kind.PLAYBACK : kind;
        message = message == null ? "" : message;
    }

    public static PlaybackFailure source(String message) {
        return new PlaybackFailure(Kind.SOURCE, message, PlaybackException.ERROR_CODE_UNSPECIFIED);
    }

    public static PlaybackFailure timeout(String message) {
        return new PlaybackFailure(Kind.TIMEOUT, message, PlaybackException.ERROR_CODE_TIMEOUT);
    }

    public static PlaybackFailure decode(String message, int errorCode) {
        return new PlaybackFailure(Kind.DECODE, message, errorCode);
    }

    public static PlaybackFailure fromErrorCode(String message, int errorCode) {
        Kind kind = switch (errorCode) {
            case PlaybackException.ERROR_CODE_TIMEOUT,
                 PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> Kind.TIMEOUT;
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                 PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                 PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                 PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                 PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                 PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                 PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
                 PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> Kind.NETWORK;
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                 PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                 PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                 PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> Kind.SOURCE;
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                 PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                 PlaybackException.ERROR_CODE_DECODING_FAILED,
                 PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                 PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                 PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
                 PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                 PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                 PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED,
                 PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED -> Kind.DECODE;
            default -> Kind.PLAYBACK;
        };
        return new PlaybackFailure(kind, message, errorCode);
    }
}
