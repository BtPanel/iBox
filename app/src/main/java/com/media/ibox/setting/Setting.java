package com.media.ibox.setting;

import androidx.appcompat.app.AppCompatDelegate;

import com.media.ibox.BuildConfig;
import com.media.ibox.R;
import com.media.ibox.utils.ResUtil;
import com.github.catvod.net.GitHubUrl;
import com.github.catvod.utils.Prefers;
import java.util.List;

public class Setting {

    private static final int DEFAULT_THEME_COLOR = 0xFF00875A;
    private static final int DEFAULT_FOCUS_COLOR = 0xFFFFFFFF;
    private static final int LEGACY_BLUE_THEME_COLOR = 0xFF1E88E5;
    private static final int PREVIOUS_DEFAULT_THEME_COLOR = 0xFF2F86E5;
    private static final int RELEASE_1_0_THEME_COLOR = 0xFF2882E1;
    private static final int TENCENT_BLUE_THEME_COLOR = 0xFF3D7FFF;

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;
    public static final int LAYOUT_AUTO = 0;
    public static final int LAYOUT_PHONE = 1;
    public static final int LAYOUT_TABLET = 2;
    public static final int HOME_RECOMMEND = 0;
    public static final int HOME_HISTORY = 1;
    public static final int HOME_KEEP = 2;
    public static final int BOOT_START_OFF = 0;
    public static final int BOOT_START_VOD = 1;
    public static final int BOOT_START_LIVE = 2;
    public static final int BOOT_START_HISTORY = 3;
    public static final int NAVIGATION_HOME = 1;
    public static final int NAVIGATION_LIVE = 1 << 1;
    public static final int NAVIGATION_LIBRARY = 1 << 2;
    public static final int NAVIGATION_SETTINGS = 1 << 3;
    public static final int NAVIGATION_DOWNLOADS = 1 << 4;
    public static final int NAVIGATION_ALL = NAVIGATION_HOME | NAVIGATION_LIVE | NAVIGATION_LIBRARY | NAVIGATION_SETTINGS | NAVIGATION_DOWNLOADS;
    public static final int NAVIGATION_HIDEABLE = NAVIGATION_HOME | NAVIGATION_LIVE | NAVIGATION_LIBRARY | NAVIGATION_DOWNLOADS;
    public static final int FONT_SCALE_STANDARD = 0;
    public static final int FONT_SCALE_LARGE = 1;
    public static final int FONT_SCALE_EXTRA_LARGE = 2;
    public static final int KEEP_SORT_RECENT = 0;
    public static final int KEEP_SORT_NAME = 1;
    public static final int KEEP_SORT_SOURCE = 2;

    private static final int MIN_WALL = 0;
    private static final int MAX_WALL = 3;
    private static final int MIN_WALL_TYPE = 0;
    private static final int MAX_WALL_TYPE = 2;
    private static final int MIN_SITE_MODE = 0;
    private static final int MAX_SITE_MODE = 1;
    private static final int MIN_SYNC_MODE = 0;
    private static final int MAX_SYNC_MODE = 2;
    private static final int MIN_SEARCH_THREADS = 1;
    private static final int MAX_SEARCH_THREADS = 20;
    private static final int MIN_KEEP_SORT = KEEP_SORT_RECENT;
    private static final int MAX_KEEP_SORT = KEEP_SORT_SOURCE;

    public static String getSwitch(boolean value) {
        return ResUtil.getString(value ? R.string.setting_on : R.string.setting_off);
    }

    public static String getDoh() {
        return Prefers.getString("doh");
    }

    public static void putDoh(String doh) {
        Prefers.put("doh", doh);
    }

    public static String getUa() {
        return Prefers.getString("ua");
    }

    public static void putUa(String ua) {
        Prefers.put("ua", ua);
    }

    public static String getKeyword() {
        return Prefers.getString("keyword");
    }

    public static void putKeyword(String keyword) {
        Prefers.put("keyword", keyword);
    }

    public static String getHot() {
        return Prefers.getString("hot");
    }

    public static void putHot(String hot) {
        Prefers.put("hot", hot);
    }

    public static int getWall() {
        int defaultWall = "mobile".equals(BuildConfig.FLAVOR_mode) ? 0 : 1;
        return Math.clamp(Prefers.getInt("wall", defaultWall), MIN_WALL, MAX_WALL);
    }

    public static void putWall(int wall) {
        Prefers.put("wall", Math.clamp(wall, MIN_WALL, MAX_WALL));
    }

    public static int getWallType() {
        return Math.clamp(Prefers.getInt("wall_type", 0), MIN_WALL_TYPE, MAX_WALL_TYPE);
    }

    public static void putWallType(int type) {
        Prefers.put("wall_type", Math.clamp(type, MIN_WALL_TYPE, MAX_WALL_TYPE));
    }

    public static boolean isWallBlur() {
        return Prefers.getBoolean("wall_blur", true);
    }

    public static void putWallBlur(boolean enabled) {
        Prefers.put("wall_blur", enabled);
    }

    public static int getThemeColor() {
        int color = Prefers.getInt("theme_color", DEFAULT_THEME_COLOR);
        return isOldBlue(color) ? DEFAULT_THEME_COLOR : color;
    }

    public static void putThemeColor(int color) {
        Prefers.put("theme_color", color);
        if (color > 0) Prefers.put("last_theme_color", color);
    }

    public static int getFocusColor() {
        int color = Prefers.getInt("focus_color", DEFAULT_FOCUS_COLOR);
        return color == RELEASE_1_0_THEME_COLOR ? DEFAULT_FOCUS_COLOR : color;
    }

    public static void putFocusColor(int color) {
        Prefers.put("focus_color", color);
    }

    public static int getLastThemeColor() {
        int color = Prefers.getInt("last_theme_color", getThemeColor());
        if (isOldBlue(color)) color = DEFAULT_THEME_COLOR;
        return color > 0 ? color : DEFAULT_THEME_COLOR;
    }

    private static boolean isOldBlue(int color) {
        return color == LEGACY_BLUE_THEME_COLOR || color == PREVIOUS_DEFAULT_THEME_COLOR || color == RELEASE_1_0_THEME_COLOR || color == TENCENT_BLUE_THEME_COLOR;
    }

    public static int getThemeMode() {
        return Math.clamp(Prefers.getInt("theme_mode", THEME_DARK), THEME_SYSTEM, THEME_DARK);
    }

    public static void putThemeMode(int mode) {
        Prefers.put("theme_mode", Math.clamp(mode, THEME_SYSTEM, THEME_DARK));
    }

    public static boolean applyThemeMode() {
        int mode = "leanback".equals(BuildConfig.FLAVOR_mode) || isHighContrast() ? AppCompatDelegate.MODE_NIGHT_YES : switch (getThemeMode()) {
            case THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO;
            case THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES;
            default -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        };
        if (AppCompatDelegate.getDefaultNightMode() == mode) return false;
        AppCompatDelegate.setDefaultNightMode(mode);
        return true;
    }

    public static int getFontScaleMode() {
        int defaultMode = "mobile".equals(BuildConfig.FLAVOR_mode) ? FONT_SCALE_LARGE : FONT_SCALE_STANDARD;
        return Math.clamp(Prefers.getInt("accessibility_font_scale", defaultMode), FONT_SCALE_STANDARD, FONT_SCALE_EXTRA_LARGE);
    }

    public static void putFontScaleMode(int mode) {
        Prefers.put("accessibility_font_scale", Math.clamp(mode, FONT_SCALE_STANDARD, FONT_SCALE_EXTRA_LARGE));
    }

    public static float getFontScale() {
        return switch (getFontScaleMode()) {
            case FONT_SCALE_LARGE -> 1.15F;
            case FONT_SCALE_EXTRA_LARGE -> 1.3F;
            default -> 1.0F;
        };
    }

    public static boolean isHighContrast() {
        return Prefers.getBoolean("accessibility_high_contrast");
    }

    public static void putHighContrast(boolean enabled) {
        Prefers.put("accessibility_high_contrast", enabled);
    }

    public static boolean isReduceMotion() {
        return Prefers.getBoolean("accessibility_reduce_motion");
    }

    public static void putReduceMotion(boolean enabled) {
        Prefers.put("accessibility_reduce_motion", enabled);
    }

    public static int getLayoutMode() {
        return Math.clamp(Prefers.getInt("layout_mode", LAYOUT_AUTO), LAYOUT_AUTO, LAYOUT_TABLET);
    }

    public static void putLayoutMode(int mode) {
        Prefers.put("layout_mode", Math.clamp(mode, LAYOUT_AUTO, LAYOUT_TABLET));
    }

    public static int getHomeContent() {
        return Math.clamp(Prefers.getInt("home_content", HOME_RECOMMEND), HOME_RECOMMEND, HOME_KEEP);
    }

    public static void putHomeContent(int content) {
        Prefers.put("home_content", Math.clamp(content, HOME_RECOMMEND, HOME_KEEP));
    }

    public static boolean isNavigationHidden() {
        return getHiddenNavigationItems() != 0;
    }

    public static void putNavigationHidden(boolean hidden) {
        putHiddenNavigationItems(hidden ? NAVIGATION_HIDEABLE : 0);
    }

    public static int getHiddenNavigationItems() {
        int defaultItems = "mobile".equals(BuildConfig.FLAVOR_mode) ? NAVIGATION_DOWNLOADS : 0;
        int legacy = Prefers.getBoolean("navigation_hidden") ? NAVIGATION_ALL : defaultItems;
        return Prefers.getInt("hidden_navigation_items", legacy) & NAVIGATION_HIDEABLE;
    }

    public static void putHiddenNavigationItems(int items) {
        Prefers.put("hidden_navigation_items", items & NAVIGATION_HIDEABLE);
        Prefers.put("navigation_hidden", false);
    }

    public static boolean isBootAutoStart() {
        if (!"leanback".equals(BuildConfig.FLAVOR_mode)) return getBootStartMode() != BOOT_START_OFF;
        return Prefers.getBoolean("boot_auto_start_enabled");
    }

    public static void putBootAutoStart(boolean enabled) {
        if ("leanback".equals(BuildConfig.FLAVOR_mode)) Prefers.put("boot_auto_start_enabled", enabled);
        else putBootStartMode(enabled ? BOOT_START_VOD : BOOT_START_OFF);
    }

    public static int getBootStartMode() {
        int legacy = Prefers.getBoolean("boot_auto_start") ? BOOT_START_VOD : BOOT_START_OFF;
        return Math.clamp(Prefers.getInt("boot_start_mode", legacy), getMinBootStartMode(), getMaxBootStartMode());
    }

    public static void putBootStartMode(int mode) {
        Prefers.put("boot_start_mode", Math.clamp(mode, getMinBootStartMode(), getMaxBootStartMode()));
        Prefers.put("boot_auto_start", false);
    }

    private static int getMinBootStartMode() {
        return "leanback".equals(BuildConfig.FLAVOR_mode) ? BOOT_START_VOD : BOOT_START_OFF;
    }

    private static int getMaxBootStartMode() {
        return BOOT_START_HISTORY;
    }

    public static int getWallColor() {
        return Prefers.getInt("wall_color", 0);
    }

    public static void putWallColor(int color) {
        Prefers.put("wall_color", color);
    }

    public static int getDynamicColor() {
        int color = getThemeColor();
        if (color == -1) return 0;
        return color != 0 ? color : getWallColor();
    }

    /** Custom palettes are applied by the activity before layouts are inflated. */
    public static int getThemeOverlay() {
        return switch (getThemeColor()) {
            case 0xFF6750A4 -> R.style.ThemeOverlay_IBox_Color_Purple;
            case 0xFF3949AB -> R.style.ThemeOverlay_IBox_Color_Indigo;
            case 0xFF1769AA -> R.style.ThemeOverlay_IBox_Color_Blue;
            case 0xFF00ACC1 -> R.style.ThemeOverlay_IBox_Color_Cyan;
            case 0xFF00897B -> R.style.ThemeOverlay_IBox_Color_Teal;
            case 0xFF43A047 -> R.style.ThemeOverlay_IBox_Color_Green;
            case 0xFF7CB342 -> R.style.ThemeOverlay_IBox_Color_Lime;
            case DEFAULT_THEME_COLOR -> R.style.ThemeOverlay_IBox_Color_IBoxGreen;
            case 0xFFE8590C, 0xFFFB8C00 -> R.style.ThemeOverlay_IBox_Color_Orange;
            case 0xFF00C250 -> R.style.ThemeOverlay_IBox_Color_IqiyiGreen;
            case 0xFFFB7299 -> R.style.ThemeOverlay_IBox_Color_BiliPink;
            case 0xFFE43D30 -> R.style.ThemeOverlay_IBox_Color_ChinaRed;
            case 0xFFE53935 -> R.style.ThemeOverlay_IBox_Color_Red;
            case 0xFFD81B60 -> R.style.ThemeOverlay_IBox_Color_Pink;
            case 0xFF8E24AA -> R.style.ThemeOverlay_IBox_Color_Violet;
            case 0xFF6D4C41 -> R.style.ThemeOverlay_IBox_Color_Brown;
            default -> 0;
        };
    }

    public static int getSiteMode() {
        return Math.clamp(Prefers.getInt("site_mode"), MIN_SITE_MODE, MAX_SITE_MODE);
    }

    public static void putSiteMode(int mode) {
        Prefers.put("site_mode", Math.clamp(mode, MIN_SITE_MODE, MAX_SITE_MODE));
    }

    public static int getSyncMode() {
        return Math.clamp(Prefers.getInt("sync_mode"), MIN_SYNC_MODE, MAX_SYNC_MODE);
    }

    public static void putSyncMode(int mode) {
        Prefers.put("sync_mode", Math.clamp(mode, MIN_SYNC_MODE, MAX_SYNC_MODE));
    }

    public static boolean isIncognito() {
        return Prefers.getBoolean("incognito");
    }

    public static void putIncognito(boolean incognito) {
        Prefers.put("incognito", incognito);
    }

    public static int getSearchThreads() {
        return Math.clamp(Prefers.getInt("search_threads", 10), MIN_SEARCH_THREADS, MAX_SEARCH_THREADS);
    }

    public static void putSearchThreads(int threads) {
        Prefers.put("search_threads", Math.clamp(threads, MIN_SEARCH_THREADS, MAX_SEARCH_THREADS));
    }

    public static int getKeepSortMode() {
        return Math.clamp(Prefers.getInt("keep_sort_mode", KEEP_SORT_RECENT), MIN_KEEP_SORT, MAX_KEEP_SORT);
    }

    public static void putKeepSortMode(int mode) {
        Prefers.put("keep_sort_mode", Math.clamp(mode, MIN_KEEP_SORT, MAX_KEEP_SORT));
    }

    public static boolean isAutoFallbackLine() {
        return Prefers.getBoolean("auto_fallback_line", true);
    }

    public static void putAutoFallbackLine(boolean enabled) {
        Prefers.put("auto_fallback_line", enabled);
    }

    public static boolean isAutoFallbackSource() {
        return Prefers.getBoolean("auto_fallback_source", true);
    }

    public static void putAutoFallbackSource(boolean enabled) {
        Prefers.put("auto_fallback_source", enabled);
    }

    public static boolean isGitHubAcceleration() {
        return Prefers.getBoolean("github_acceleration", false);
    }

    public static void putGitHubAcceleration(boolean enabled) {
        Prefers.put("github_acceleration", enabled);
        GitHubUrl.setAccelerationEnabled(enabled);
    }

    /**
     * 用户自定义的 GitHub 加速前缀（原始多行文本，用于编辑回显）。
     */
    public static String getGitHubAccelerators() {
        return Prefers.getString("github_accelerators", "");
    }

    /**
     * 保存自定义加速前缀：按行解析、规范化，并立即生效。
     */
    public static void putGitHubAccelerators(String text) {
        String value = text == null ? "" : text.trim();
        Prefers.put("github_accelerators", value);
        GitHubUrl.setCustomAccelerators(value);
    }

    public static List<String> getGitHubAcceleratorList() {
        return GitHubUrl.parse(getGitHubAccelerators());
    }

    /**
     * 应用启动时调用，把已保存的自定义前缀注入 {@link GitHubUrl}。
     */
    public static void applyGitHubAccelerators() {
        GitHubUrl.setCustomAccelerators(getGitHubAccelerators());
    }

    public static boolean isConfigNoticeEnabled() {
        return Prefers.getBoolean("config_notice_enabled", false);
    }

    public static void putConfigNoticeEnabled(boolean enabled) {
        Prefers.put("config_notice_enabled", enabled);
    }

    public static boolean isAdblock() {
        return Prefers.getBoolean("adblock", true);
    }

    public static void putAdblock(boolean adblock) {
        Prefers.put("adblock", adblock);
    }

    public static boolean isZhuyin() {
        return Prefers.getBoolean("zhuyin");
    }

    public static void putZhuyin(boolean zhuyin) {
        Prefers.put("zhuyin", zhuyin);
    }
}
