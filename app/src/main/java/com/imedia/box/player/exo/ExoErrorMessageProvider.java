package com.imedia.box.player.exo;

import androidx.media3.common.PlaybackException;

import com.imedia.box.R;
import com.imedia.box.utils.ResUtil;

public class ExoErrorMessageProvider {

    public String get(PlaybackException e) {
        String summary = switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_TIMEOUT -> ResUtil.getString(R.string.error_player_timeout);
            case PlaybackException.ERROR_CODE_UNSPECIFIED -> "Unspecified";
            case PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK -> "Failed Runtime Check";
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "IO Unspecified";
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Bad HTTP Status";
            case PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "Invalid HTTP Content Type";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network Connection Failed";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network Connection Timeout";
            case PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "Read Position Out Of Range";
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Manifest Malformed";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "Container Malformed";
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "Manifest Unsupported";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Container Unsupported";
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Decoder Init Failed";
            case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "Decoder Query Failed";
            case PlaybackException.ERROR_CODE_DECODING_FAILED -> "Decoding Failed";
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "Decoding Format Unsupported";
            case PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED -> "Decoding Resources Reclaimed";
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "Decoding Format Exceeds Capabilities";
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> ResUtil.getString(R.string.error_player_audio_output);
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> ResUtil.getString(R.string.error_player_audio_output);
            case PlaybackException.ERROR_CODE_DRM_UNSPECIFIED -> "DRM Unspecified";
            case PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR -> "DRM System Error";
            case PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR -> "DRM Content Error";
            case PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED -> "DRM Device Revoked";
            case PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> "DRM License Expired";
            case PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED -> "DRM Provisioning Failed";
            case PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION -> "DRM Disallowed Operation";
            case PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> "DRM License Acquisition Failed";
            default -> e.getErrorCodeName();
        };
        if (e.errorCode == PlaybackException.ERROR_CODE_TIMEOUT || e.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED || e.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED) return summary;
        Throwable cause = e.getCause();
        while (cause != null && cause.getCause() != null) cause = cause.getCause();
        String detail = cause == null ? null : cause.getMessage();
        return detail == null || detail.isBlank() || summary.equals(detail) ? summary : summary + ": " + detail;
    }
}
