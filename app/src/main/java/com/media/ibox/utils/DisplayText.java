package com.media.ibox.utils;

import java.util.regex.Pattern;

public final class DisplayText {

    private static final Pattern UPDATE_DECORATION = Pattern.compile(
            "[\\u3010\\[](?=[^\\u3011\\]]*(?:更新日期|更新时间))[^\\u3011\\]]*[\\u3011\\]]");

    private DisplayText() {
    }

    public static String withoutEyeDecoration(String value) {
        if (value == null) return "";
        String text = UPDATE_DECORATION.matcher(value
                .replace("\uD83D\uDC40", "")
                .replace("\uD83D\uDC41\uFE0F", "")
                .replace("\uD83D\uDC41", "")
        ).replaceAll("");
        StringBuilder result = new StringBuilder();
        text.codePoints()
                .filter(codePoint -> {
                    int type = Character.getType(codePoint);
                    return type != Character.OTHER_SYMBOL && type != Character.MODIFIER_SYMBOL;
                })
                .forEach(result::appendCodePoint);
        return result.toString().trim();
    }
}
