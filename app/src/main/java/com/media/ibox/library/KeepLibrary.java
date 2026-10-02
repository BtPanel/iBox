package com.media.ibox.library;

import com.media.ibox.bean.Keep;
import com.media.ibox.setting.Setting;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class KeepLibrary {

    private KeepLibrary() {
    }

    public static List<Keep> filterAndSort(List<Keep> items, String keyword, int sortMode) {
        String query = normalize(keyword);
        List<Keep> result = new ArrayList<>();
        if (items != null) {
            for (Keep item : items) {
                if (item != null && matches(item, query)) result.add(item);
            }
        }
        result.sort(comparator(sortMode));
        return result;
    }

    private static boolean matches(Keep item, String query) {
        if (query.isEmpty()) return true;
        return normalize(item.getVodName()).contains(query) || normalize(item.getSiteName()).contains(query);
    }

    private static Comparator<Keep> comparator(int sortMode) {
        Comparator<Keep> recent = Comparator.comparingLong(Keep::getCreateTime).reversed();
        Comparator<Keep> name = textComparator(Keep::getVodName).thenComparing(recent);
        Comparator<Keep> source = textComparator(Keep::getSiteName).thenComparing(name);
        return switch (sortMode) {
            case Setting.KEEP_SORT_NAME -> name;
            case Setting.KEEP_SORT_SOURCE -> source;
            default -> recent;
        };
    }

    private static Comparator<Keep> textComparator(Function<Keep, String> value) {
        Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.PRIMARY);
        return (left, right) -> collator.compare(safe(value.apply(left)), safe(value.apply(right)));
    }

    private static String normalize(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
