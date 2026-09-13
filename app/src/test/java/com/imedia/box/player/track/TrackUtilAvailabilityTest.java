package com.imedia.box.player.track;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import android.text.TextUtils;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;

import org.junit.Test;
import org.mockito.MockedStatic;

public class TrackUtilAvailabilityTest {

    @Test
    public void unsupportedFormatsDoNotCountAsSelectableTracks() {
        try (MockedStatic<TextUtils> text = mockStatic(TextUtils.class)) {
            text.when(() -> TextUtils.isEmpty(any())).thenAnswer(call -> {
                CharSequence value = call.getArgument(0);
                return value == null || value.length() == 0;
            });
            Format unsupported = new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build();
            Format supported = new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).build();
            Tracks.Group group = new Tracks.Group(new TrackGroup(unsupported, supported), false,
                    new int[]{C.FORMAT_UNSUPPORTED_TYPE, C.FORMAT_HANDLED}, new boolean[]{false, true});
            Tracks tracks = new Tracks(java.util.List.of(group));

            assertEquals(1, TrackUtil.count(tracks, C.TRACK_TYPE_AUDIO));
        }
    }
}
