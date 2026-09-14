package com.imedia.box.ui.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class UiResourceContractTest {

    private static Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("app/src/main/res/values/ibox_colors.xml"))) {
            path = path.getParent();
        }
        if (path == null) throw new AssertionError("Repository root not found");
        return path;
    }

    private static Document xml(String relativePath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(
                Files.readString(repoRoot().resolve(relativePath)))));
    }

    private static String value(Document document, String elementName, String name) {
        NodeList elements = document.getElementsByTagName(elementName);
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (name.equals(element.getAttribute("name"))) return element.getTextContent().trim();
        }
        throw new AssertionError("Missing " + elementName + " " + name);
    }

    private static String attribute(Document document, String elementName, String attributeName) {
        NodeList elements = document.getElementsByTagName(elementName);
        if (elements.getLength() == 0) throw new AssertionError("Missing " + elementName);
        return ((Element) elements.item(0)).getAttribute(attributeName);
    }

    private static String color(Document document, String name) {
        String value = value(document, "color", name);
        assertTrue("Expected literal color for " + name + ", got " + value,
                value.matches("#[0-9A-Fa-f]{6,8}"));
        return value;
    }

    @Test
    public void wallpaperRemainsVisibleOnBothHomeScreens() throws Exception {
        Document wall = xml("app/src/main/res/layout/view_wall.xml");
        assertEquals("@color/ibox_wall_scrim", view(wall, "scrim").getAttribute("android:background"));
        for (String path : new String[]{
                "app/src/main/res/values/ibox_colors.xml",
                "app/src/main/res/values-night/ibox_colors.xml",
                "app/src/leanback/res/values/ibox_tv_colors.xml",
                "app/src/leanback/res/values-night/ibox_tv_colors.xml"}) {
            String scrim = color(xml(path), "ibox_wall_scrim");
            int alpha = scrim.length() == 9 ? Integer.parseInt(scrim.substring(1, 3), 16) : 255;
            assertTrue(path + " hides more than 40% of the wallpaper", alpha <= 0x66);
        }
        Document mobileHome = xml("app/src/mobile/res/layout/activity_home.xml");
        Document tvHome = xml("app/src/leanback/res/layout/activity_home.xml");
        assertEquals("@android:color/transparent", mobileHome.getDocumentElement().getAttribute("android:background"));
        assertEquals("@android:color/transparent", tvHome.getDocumentElement().getAttribute("android:background"));
        NodeList views = tvHome.getElementsByTagName("View");
        for (int i = 0; i < views.getLength(); i++) {
            assertFalse("TV home must not cover the wallpaper with another full-screen scrim",
                    "@drawable/bg_tv_home_scrim".equals(((Element) views.item(i)).getAttribute("android:background")));
        }
        NodeList images = tvHome.getElementsByTagName("androidx.appcompat.widget.AppCompatImageView");
        for (int i = 0; i < images.getLength(); i++) {
            assertFalse("TV home must not layer focused posters over any selected wallpaper",
                    "@+id/backdrop".equals(((Element) images.item(i)).getAttribute("android:id")));
        }
    }

    @Test
    public void mobileHomeControlsStayReadableOverDarkOrBrightWallpaper() throws Exception {
        for (String drawable : new String[]{"bg_home_action", "bg_ibox_chip"}) {
            Document selector = xml("app/src/mobile/res/drawable/" + drawable + ".xml");
            NodeList items = selector.getElementsByTagName("item");
            Element normal = (Element) items.item(items.getLength() - 1);
            Element solid = (Element) normal.getElementsByTagName("solid").item(0);
            assertEquals(drawable, "@color/ibox_home_control_surface", solid.getAttribute("android:color"));
        }
        Document chipText = xml("app/src/mobile/res/color/selector_ibox_text.xml");
        NodeList textItems = chipText.getElementsByTagName("item");
        assertEquals("@color/ibox_text_primary",
                ((Element) textItems.item(textItems.getLength() - 1)).getAttribute("android:color"));
        for (boolean night : new boolean[]{false, true}) {
            String path = night ? "app/src/main/res/values-night/ibox_colors.xml"
                    : "app/src/main/res/values/ibox_colors.xml";
            Document colors = xml(path);
            Document home = xml(night ? "app/src/mobile/res/values-night/home_colors.xml"
                    : "app/src/mobile/res/values/home_colors.xml");
            for (String wallpaper : new String[]{"#000000", "#FFFFFF"}) {
                String background = compositeColor(color(home, "ibox_home_control_surface"),
                        compositeColor(color(colors, "ibox_wall_scrim"), wallpaper));
                assertTrue(path + " has unreadable category text over " + wallpaper,
                        contrast(color(colors, "ibox_text_primary"), background) >= 4.5);
            }
        }
    }

    @Test
    public void mobileSearchAndNavigationBlendWithWallpaperWithoutLosingContrast() throws Exception {
        for (String drawable : new String[]{"bg_home_search", "bg_floating_navigation"}) {
            Document background = xml("app/src/mobile/res/drawable/" + drawable + ".xml");
            assertEquals(drawable, "@color/ibox_home_chrome_surface",
                    attribute(background, "solid", "android:color"));
        }
        assertEquals("@color/ibox_wall_chrome_surface", attribute(
                xml("app/src/mobile/res/drawable/bg_search_input.xml"), "solid", "android:color"));
        Document homeLayout = xml("app/src/mobile/res/layout/fragment_vod.xml");
        Element search = view(homeLayout, "search");
        Element hint = (Element) search.getElementsByTagName(
                "com.google.android.material.textview.MaterialTextView").item(0);
        assertEquals("@color/ibox_home_chrome_secondary", hint.getAttribute("android:textColor"));
        Document navigationIcon = xml("app/src/mobile/res/color/selector_app_navigation_icon.xml");
        NodeList iconStates = navigationIcon.getElementsByTagName("item");
        assertEquals("@color/ibox_home_chrome_secondary",
                ((Element) iconStates.item(iconStates.getLength() - 1)).getAttribute("android:color"));
        for (String path : new String[]{"app/src/mobile/res/values/home_colors.xml",
                "app/src/mobile/res/values-night/home_colors.xml"}) {
            Document chrome = xml(path);
            boolean night = path.contains("values-night");
            Document palette = xml(night ? "app/src/main/res/values-night/ibox_colors.xml"
                    : "app/src/main/res/values/ibox_colors.xml");
            String surface = color(chrome, "ibox_home_chrome_surface");
            int alpha = Integer.parseInt(surface.substring(1, 3), 16);
            assertTrue(path + " home chrome is too opaque", alpha <= 0xB8);
            for (String wallpaper : new String[]{"#000000", "#FFFFFF"}) {
                String background = compositeColor(surface,
                        compositeColor(color(palette, "ibox_wall_scrim"), wallpaper));
                assertTrue(path + " has unreadable search hint or navigation icon over " + wallpaper,
                        contrast(color(chrome, "ibox_home_chrome_secondary"), background) >= 4.5);
            }
        }
    }

    @Test
    public void mobileSettingsGroupsRevealWallpaperWithoutLosingSecondaryTextContrast() throws Exception {
        Document group = xml("app/src/mobile/res/drawable/bg_setting_group.xml");
        assertEquals("@color/ibox_wall_chrome_surface", attribute(group, "solid", "android:color"));
        for (boolean night : new boolean[]{false, true}) {
            Document palette = xml(night ? "app/src/main/res/values-night/ibox_colors.xml"
                    : "app/src/main/res/values/ibox_colors.xml");
            Document chrome = xml(night ? "app/src/mobile/res/values-night/home_colors.xml"
                    : "app/src/mobile/res/values/home_colors.xml");
            int alpha = Integer.parseInt(color(chrome, "ibox_wall_chrome_surface").substring(1, 3), 16);
            assertTrue("Settings group still hides too much wallpaper", alpha <= 0xC6);
            for (String wallpaper : new String[]{"#000000", "#FFFFFF"}) {
                String surface = compositeColor(color(chrome, "ibox_wall_chrome_surface"),
                        compositeColor(color(palette, "ibox_wall_scrim"), wallpaper));
                assertTrue("Setting labels fade over " + wallpaper,
                        contrast(color(palette, "ibox_text_secondary"), surface) >= 4.5);
            }
        }
    }

    @Test
    public void mobileDialogsUseAReadableTintedSurfaceWithoutChangingTvTheme() throws Exception {
        String stylePath = "app/src/mobile/res/values/dialog_styles.xml";
        assertTrue("Mobile dialogs need a mobile-only overlay", Files.isRegularFile(repoRoot().resolve(stylePath)));
        Document styles = xml(stylePath);
        String name = "ThemeOverlay.IBox.Dialog.Alert";
        assertEquals("@color/ibox_mobile_dialog_surface", styleItem(styles, name, "colorSurface"));
        assertEquals("@color/ibox_mobile_dialog_surface", styleItem(styles, name, "colorSurfaceContainerHigh"));
        assertTrue("Dialog backdrop hides the wallpaper", Double.parseDouble(
                styleItem(styles, name, "android:backgroundDimAmount")) <= 0.30);
        assertEquals("?attr/colorSurface", attribute(
                xml("app/src/mobile/res/drawable/bg_dialog_config.xml"), "solid", "android:color"));
        Document tvStyles = xml("app/src/main/res/values/ibox_ui_system.xml");
        assertEquals("0.56", styleItem(tvStyles, name, "android:backgroundDimAmount"));
        for (boolean night : new boolean[]{false, true}) {
            String colorsPath = night ? "app/src/mobile/res/values-night/dialog_colors.xml"
                    : "app/src/mobile/res/values/dialog_colors.xml";
            Document colors = xml(colorsPath);
            Document palette = xml(night ? "app/src/main/res/values-night/ibox_colors.xml"
                    : "app/src/main/res/values/ibox_colors.xml");
            String surface = color(colors, "ibox_mobile_dialog_surface");
            int alpha = surface.length() == 9 ? Integer.parseInt(surface.substring(1, 3), 16) : 255;
            assertTrue(colorsPath + " shows underlying text through the dialog", alpha >= 0xE8);
            if (!night) assertTrue("Mobile dialog must not be a white slab", luminance(surface) < 0.82);
            for (String behind : new String[]{"#000000", "#FFFFFF"}) {
                assertTrue(colorsPath + " has unreadable text over " + behind,
                        contrast(color(palette, "ibox_text_secondary"), compositeColor(surface, behind)) >= 4.5);
            }
        }
    }

    @Test
    public void mobileNavigationBarHasNoTopDivider() throws Exception {
        Document background = xml("app/src/mobile/res/drawable/bg_floating_navigation.xml");
        assertEquals("Navigation should be a single uninterrupted surface", "shape",
                background.getDocumentElement().getTagName());
        assertEquals("@color/ibox_home_chrome_surface", attribute(background, "solid", "android:color"));
    }

    @Test
    public void mobileNavigationHighlightIsInsetWithoutShrinkingTouchTargets() throws Exception {
        Document navigation = xml("app/src/mobile/res/layout/view_app_navigation.xml");
        Document background = xml("app/src/mobile/res/drawable/bg_app_navigation_item.xml");
        for (String id : new String[]{"navHome", "navLive", "navLibrary", "navDownloads", "navSettings"}) {
            Element target = view(navigation, id);
            assertEquals("@dimen/ibox_navigation_item_height", target.getAttribute("android:layout_height"));
            assertEquals("@drawable/bg_app_navigation_item", target.getAttribute("android:background"));
        }
        NodeList states = background.getElementsByTagName("item");
        for (int i = 0; i < states.getLength(); i++) {
            Element state = (Element) states.item(i);
            Element inset = (Element) state.getElementsByTagName("inset").item(0);
            assertTrue("Navigation state fills the entire touch target", inset != null);
            assertEquals("@dimen/ibox_space_2", inset.getAttribute("android:insetLeft"));
            assertEquals("@dimen/ibox_space_2", inset.getAttribute("android:insetRight"));
            assertEquals("@dimen/ibox_space_1", inset.getAttribute("android:insetTop"));
            assertEquals("@dimen/ibox_space_1", inset.getAttribute("android:insetBottom"));
        }
    }

    @Test
    public void mobileLiveMenuAndNestedDialogsUseNeutralReadableGlass() throws Exception {
        assertEquals("@color/ibox_live_settings_surface", attribute(
                xml("app/src/main/res/drawable/bg_live_settings.xml"), "solid", "android:color"));
        Document liveStyles = xml("app/src/mobile/res/values/dialog_styles.xml");
        assertEquals("@color/ibox_live_dialog_surface", styleItem(liveStyles,
                "ThemeOverlay.IBox.Dialog.LiveAlert", "colorSurface"));
        for (boolean night : new boolean[]{false, true}) {
            Document palette = xml(night ? "app/src/main/res/values-night/ibox_colors.xml"
                    : "app/src/main/res/values/ibox_colors.xml");
            String glass = color(palette, "ibox_live_settings_surface");
            String dialog = color(palette, "ibox_live_dialog_surface");
            int alpha = Integer.parseInt(glass.substring(1, 3), 16);
            int rgb = Integer.parseUnsignedInt(glass.substring(3), 16);
            int red = (rgb >> 16) & 0xFF;
            int green = (rgb >> 8) & 0xFF;
            int blue = rgb & 0xFF;
            assertTrue("Live menu is too opaque or exposes background labels", alpha >= 0xE0 && alpha <= 0xEF);
            assertTrue("Live dialog surface has a color cast", Math.max(red, Math.max(green, blue))
                    - Math.min(red, Math.min(green, blue)) <= 1);
            assertTrue("Nested dialog exposes channel text",
                    Integer.parseInt(dialog.substring(1, 3), 16) >= 0xF6);
            for (String behind : new String[]{"#000000", "#FFFFFF"}) {
                assertTrue("Live menu text loses contrast over " + behind,
                        contrast(color(palette, "ibox_text_primary"), compositeColor(glass, behind)) >= 4.5);
            }
        }
    }

    @Test
    public void selectedMobileLiveChannelAndToolbarRemainLegible() throws Exception {
        Document text = xml("app/src/mobile/res/color/selector_live_text.xml");
        assertEquals("?attr/colorOnPrimary", tvSelectorItem(text, true, false).getAttribute("android:color"));
        for (boolean night : new boolean[]{false, true}) {
            Document colors = xml(night ? "app/src/main/res/values-night/ibox_colors.xml"
                    : "app/src/main/res/values/ibox_colors.xml");
            assertTrue("Selected channel text loses contrast",
                    contrast(color(colors, "ibox_on_primary"), color(colors, "ibox_primary")) >= 4.5);
        }
        Document live = xml("app/src/mobile/res/layout/activity_live.xml");
        for (String id : new String[]{"summarySearch", "summaryCast", "summaryConfig"}) {
            Element button = view(live, id);
            assertEquals("?attr/selectableItemBackgroundBorderless", button.getAttribute("android:background"));
            assertEquals("@dimen/ibox_touch_target", button.getAttribute("android:layout_width"));
            assertEquals("@dimen/ibox_touch_target", button.getAttribute("android:layout_height"));
        }
    }

    @Test
    public void mobilePlayerInfoLeadsTheTrailingActionsAndCastHasOneVisualSize() throws Exception {
        for (String[] screen : new String[][]{
                {"view_control_vod.xml", "cast"},
                {"view_control_live.xml", "browserButton"}}) {
            Document control = xml("app/src/mobile/res/layout/" + screen[0]);
            Element info = view(control, "info");
            Element firstAction = view(control, screen[1]);
            assertEquals("Info belongs inside the trailing action group", firstAction.getParentNode(), info.getParentNode());
            assertTrue("Info must precede the other player actions",
                    (info.compareDocumentPosition(firstAction) & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
            assertEquals("@drawable/ic_msr_cast_mobile", view(control, "cast").getAttribute("android:src"));
            assertEquals("@dimen/ibox_icon_action_size", view(control, "cast").getAttribute("android:layout_width"));
        }

        Document live = xml("app/src/mobile/res/layout/activity_live.xml");
        assertEquals("@drawable/ic_msr_cast_mobile", view(live, "summaryCast").getAttribute("android:src"));
        Document detail = xml("app/src/mobile/res/layout/activity_video.xml");
        Element detailCast = view(detail, "detail_cast");
        Element detailIcon = (Element) detailCast.getElementsByTagName("androidx.appcompat.widget.AppCompatImageView").item(0);
        assertEquals("@drawable/ic_msr_cast_mobile", detailIcon.getAttribute("android:src"));
        assertEquals("20dp", detailIcon.getAttribute("android:layout_width"));

        Document icon = xml("app/src/mobile/res/drawable/ic_msr_cast_mobile.xml");
        NodeList layers = icon.getElementsByTagName("item");
        assertEquals(2, layers.getLength());
        assertEquals("24dp", ((Element) layers.item(0)).getAttribute("android:width"));
        assertEquals("20dp", ((Element) layers.item(1)).getAttribute("android:width"));
        assertEquals("@drawable/ic_msr_cast", ((Element) layers.item(1)).getAttribute("android:drawable"));
    }

    @Test
    public void mobilePlayerVerticalIconsMatchHorizontalControlSize() throws Exception {
        Document right = xml("app/src/mobile/res/layout/view_control_right.xml");
        for (String player : new String[]{"view_control_vod.xml", "view_control_live.xml"}) {
            Element horizontal = view(xml("app/src/mobile/res/layout/" + player), "info");
            for (String id : new String[]{"lock", "rotate", "pip"}) {
                Element vertical = view(right, id);
                for (String attribute : new String[]{"android:layout_width", "android:layout_height", "android:padding"}) {
                    assertEquals(player + " " + id + " " + attribute,
                            horizontal.getAttribute(attribute), vertical.getAttribute(attribute));
                }
            }
        }
        assertEquals("@drawable/ic_msr_screen_rotation_mobile", view(right, "rotate").getAttribute("android:src"));
        Document rotation = xml("app/src/mobile/res/drawable/ic_msr_screen_rotation_mobile.xml");
        NodeList layers = rotation.getElementsByTagName("item");
        assertEquals("24dp", ((Element) layers.item(0)).getAttribute("android:width"));
        assertEquals("20dp", ((Element) layers.item(1)).getAttribute("android:width"));
        assertEquals("@drawable/ic_msr_screen_rotation", ((Element) layers.item(1)).getAttribute("android:drawable"));
    }

    @Test
    public void mobilePlayerDialogActionsContrastWithDayAndNightSurfaces() throws Exception {
        for (String[] dialog : new String[][]{
                {"mobile", "dialog_track.xml", "search", "choose", "setting"},
                {"mobile", "dialog_danmaku.xml", "search", "choose", "setting"},
                {"mobile", "dialog_player_engine.xml", "debug"},
                {"main", "dialog_subtitle_search.xml", "setting"}}) {
            Document layout = xml("app/src/" + dialog[0] + "/res/layout/" + dialog[1]);
            for (int i = 2; i < dialog.length; i++) {
                String tint = view(layout, dialog[i]).getAttribute("app:tint");
                for (boolean night : new boolean[]{false, true}) {
                    Document palette = xml("app/src/main/res/" + (night ? "values-night" : "values")
                            + "/ibox_colors.xml");
                    Document surfaces = xml("app/src/mobile/res/" + (night ? "values-night" : "values")
                            + "/dialog_colors.xml");
                    String foreground = switch (tint) {
                        case "?attr/colorOnSurface", "@color/ibox_text_primary" -> color(palette, "ibox_text_primary");
                        case "?attr/colorOnSurfaceVariant", "@color/ibox_text_secondary" -> color(palette, "ibox_text_secondary");
                        default -> "#FFFFFF";
                    };
                    assertTrue(dialog[1] + " " + dialog[i] + " loses contrast in "
                                    + (night ? "night" : "day") + " mode",
                            contrast(foreground, compositeColor(
                                    color(surfaces, "ibox_mobile_dialog_surface"), night ? "#FFFFFF" : "#000000")) >= 3.0);
                }
            }
        }
    }

    @Test
    public void mobileDialogSurfacesAreNeutralAndReadableOverVideo() throws Exception {
        Document dialogTheme = xml("app/src/mobile/res/values/dialog_styles.xml");
        assertEquals("@color/ibox_mobile_dialog_surface", styleItem(dialogTheme,
                "ThemeOverlay.IBox.Dialog.Alert", "colorSurface"));
        for (boolean night : new boolean[]{false, true}) {
            String qualifier = night ? "values-night" : "values";
            String surface = color(xml("app/src/mobile/res/" + qualifier + "/dialog_colors.xml"),
                    "ibox_mobile_dialog_surface");
            int value = (int) Long.parseLong(surface.substring(1), 16);
            int alpha = surface.length() == 9 ? value >>> 24 : 0xFF;
            int red = (value >> 16) & 0xFF;
            int green = (value >> 8) & 0xFF;
            int blue = value & 0xFF;
            assertTrue("Dialog surface has a green or other color cast",
                    Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) <= 2);
            assertTrue("Dialog must show a little backdrop without exposing video labels",
                    alpha >= 0xE8 && alpha <= 0xF6);
            Document palette = xml("app/src/main/res/" + qualifier + "/ibox_colors.xml");
            for (String backdrop : new String[]{"#000000", "#FFFFFF", "#006B5F"}) {
                String displayed = compositeColor(surface, backdrop);
                assertTrue("Dialog title loses contrast over " + backdrop,
                        contrast(color(palette, "ibox_text_primary"), displayed) >= 4.5);
                assertTrue("Dialog supporting text loses contrast over " + backdrop,
                        contrast(color(palette, "ibox_text_secondary"), displayed) >= 4.5);
            }
        }
    }

    @Test
    public void movieFullscreenDoesNotApplyDelayedAutoRotateReset() throws Exception {
        String video = Files.readString(repoRoot().resolve(
                "app/src/mobile/java/com/imedia/box/ui/activity/VideoActivity.java"));
        assertTrue("A delayed orientation reset must not override the user's fullscreen landscape",
                video.contains("private void setOrient() {\n        if (isFullscreen()) return;"));
    }

    @Test
    public void movieVideoSizeDoesNotOverrideFullscreenOrientation() throws Exception {
        String video = Files.readString(repoRoot().resolve(
                "app/src/mobile/java/com/imedia/box/ui/activity/VideoActivity.java"));
        assertFalse("A late video-size event must not rotate fullscreen playback",
                video.contains("checkOrientation();"));
    }

    @Test
    public void remoteHighContrastSwitchesNightModeBeforeThemeRefresh() throws Exception {
        String manage = Files.readString(repoRoot().resolve(
                "app/src/main/java/com/imedia/box/server/process/Manage.java"));
        String refresh = manage.substring(manage.indexOf("private void postSettingEvent("),
                manage.indexOf("private void saveConfig("));
        assertTrue("Remote high contrast must update night mode before recreating the UI",
                refresh.contains("if (key.equals(\"highContrast\")) {")
                        && refresh.contains("if (!Setting.applyThemeMode()) RefreshEvent.theme();"));
    }

    @Test
    public void tvHomeCategoryLabelsStayReadableOverBrightWallpaper() throws Exception {
        Document selector = xml("app/src/leanback/res/drawable/selector_home_category.xml");
        Element normal = tvSelectorItem(selector, false, false);
        NodeList solids = normal.getElementsByTagName("solid");
        assertTrue("Unfocused TV categories need a local surface over wallpaper", solids.getLength() > 0);
        assertEquals("@color/ibox_glass_surface", ((Element) solids.item(0)).getAttribute("android:color"));
        for (String path : new String[]{"app/src/leanback/res/values/ibox_tv_colors.xml",
                "app/src/leanback/res/values-night/ibox_tv_colors.xml"}) {
            Document colors = xml(path);
            String background = compositeColor(color(colors, "ibox_glass_surface"),
                    compositeColor(color(colors, "ibox_wall_scrim"), "#FFFFFF"));
            assertTrue(path + " has unreadable TV categories over bright wallpaper",
                    contrast(color(colors, "ibox_text_secondary"), background) >= 4.5);
        }
    }

    @Test
    public void mobileThemeDefaultsToFollowingTheSystem() throws Exception {
        String setting = Files.readString(repoRoot().resolve(
                "app/src/main/java/com/imedia/box/setting/Setting.java"));
        assertTrue("New mobile installs must follow the system theme by default",
                setting.contains("Prefers.getInt(\"theme_mode\", THEME_SYSTEM)"));
    }

    @Test
    public void legacyBrandPrefixesAreNotUsedOutsideStablePackageIdentity() throws Exception {
        Pattern legacySymbol = Pattern.compile("(?i)" + "i" + "media");
        try (Stream<Path> files = Files.walk(repoRoot().resolve("app/src"))) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString();
                assertFalse("Legacy brand in file name: " + path,
                        name.toLowerCase().contains("i" + "media"));
                if (!name.matches(".*\\.(java|xml|gradle|md|pro|properties|json|html|js|css)$")) continue;
                String source = Files.readString(path)
                        .replace("com." + "i" + "media.box", "")
                        .replace("com/" + "i" + "media/box", "");
                assertFalse("Legacy brand symbol in " + path, legacySymbol.matcher(source).find());
            }
        }
    }

    private static double contrast(String foreground, String background) {
        double a = luminance(foreground);
        double b = luminance(background);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(String hex) {
        int rgb = Integer.parseUnsignedInt(hex.substring(hex.length() - 6), 16);
        double r = linear((rgb >> 16) & 0xFF);
        double g = linear((rgb >> 8) & 0xFF);
        double b = linear(rgb & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    @Test
    public void approvedMobilePalettesMeetTextContrast() throws Exception {
        assertPalette("app/src/main/res/values/ibox_colors.xml", "#F6F9F8", "#13201D", "#52615D", "#006B5F", "#FFFFFF");
        assertEquals("#D95D45", color(xml("app/src/main/res/values/ibox_colors.xml"), "ibox_content_accent"));
        assertPalette("app/src/main/res/values-night/ibox_colors.xml", "#0D1513", "#E8F1EE", "#AABAB5", "#62DAC4", "#003731");
        assertTrue(contrast(color(xml("app/src/main/res/values-night/ibox_colors.xml"), "ibox_content_accent"), "#0D1513") >= 3.0);
    }

    @Test
    public void approvedTvPaletteSeparatesTextAndFocus() throws Exception {
        String[][] approved = {
                {"ibox_surface_container", "#080F0D"}, {"ibox_surface", "#141D1A"},
                {"ibox_surface_variant", "#23302C"}, {"ibox_text_primary", "#F0F7F5"},
                {"ibox_text_secondary", "#A9B8B3"}, {"ibox_primary", "#68DFC9"},
                {"ibox_on_primary", "#003731"}, {"ibox_primary_container", "#164E46"},
                {"ibox_on_primary_container", "#B7EBDD"}, {"ibox_content_accent", "#FFB4A8"},
                {"ibox_outline", "#71817C"}, {"ibox_outline_strong", "#72827D"},
                {"ibox_focus_surface", "#23302C"}, {"ibox_focus_outline", "#E2FFF8"},
                {"ibox_player_surface", "#0E1013"}, {"ibox_player_scrim", "#B30E1013"},
                {"ibox_player_text_primary", "#F5F7FA"}, {"ibox_player_text_secondary", "#CCD2DA"},
                {"ibox_player_control_surface", "#E6262D35"},
                {"ibox_player_control_surface_focused", "#F23A4550"},
                {"ibox_player_control_outline", "#8A7F8D9C"}
        };
        for (boolean night : new boolean[]{false, true}) {
            Document[] resources = platformColorResources("leanback", night);
            for (String[] role : approved) {
                assertEquals("Effective TV " + (night ? "night " : "default ") + role[0],
                        role[1], resolveColor("@color/" + role[0], resources));
            }
            for (String role : new String[]{"ibox_text_primary", "ibox_text_secondary"}) {
                assertTrue(contrast(resolveColor("@color/" + role, resources), "#080F0D") >= 4.5);
            }
            assertTrue(contrast(resolveColor("@color/ibox_outline", resources), "#080F0D") >= 3.0);
            assertTrue(contrast(resolveColor("@color/ibox_focus_outline", resources), "#141D1A") >= 3.0);
            assertTrue(contrast(resolveColor("@color/ibox_content_accent", resources), "#141D1A") >= 3.0);
        }
    }

    @Test
    public void forcedNightTvKeepsTheEntireStablePaletteInSync() throws Exception {
        Document defaults = xml("app/src/leanback/res/values/ibox_tv_colors.xml");
        Document[] nightResources = platformColorResources("leanback", true);
        Map<String, String> expected = new LinkedHashMap<>();
        NodeList colors = defaults.getElementsByTagName("color");
        for (int i = 0; i < colors.getLength(); i++) {
            Element color = (Element) colors.item(i);
            String name = color.getAttribute("name");
            String value = color.getTextContent().trim();
            expected.put(name, value);
            assertEquals("Forced-night TV role drift: " + name, value,
                    resolveColor("@color/" + name, nightResources));
        }
        Path nightPath = repoRoot().resolve("app/src/leanback/res/values-night/ibox_tv_colors.xml");
        assertTrue("TV must explicitly shield its stable palette from main night overrides", Files.isRegularFile(nightPath));
        NodeList nightColors = xml(repoRoot().relativize(nightPath).toString()).getElementsByTagName("color");
        Map<String, String> qualified = new LinkedHashMap<>();
        for (int i = 0; i < nightColors.getLength(); i++) {
            Element color = (Element) nightColors.item(i);
            qualified.put(color.getAttribute("name"), color.getTextContent().trim());
        }
        assertEquals("TV default/night palettes must declare the same complete roles and values", expected, qualified);
    }

    @Test
    public void homeAndFocusResourcesUseSemanticColors() throws Exception {
        Document home = xml("app/src/mobile/res/values/home_colors.xml");
        assertEquals("@color/ibox_surface_container", value(home, "color", "ibox_home_scrim"));
        Document nightHome = xml("app/src/mobile/res/values-night/home_colors.xml");
        assertEquals("@color/ibox_surface_container", value(nightHome, "color", "ibox_home_scrim"));

        Document styles = xml("app/src/main/res/values/ibox_styles.xml");
        assertEquals("@color/ibox_text_primary", styleItem(styles, "Theme.IBox.Mobile", "focusContentColor"));
        assertEquals("@color/ibox_on_primary", styleItem(styles, "Theme.IBox.TV", "focusContentColor"));
    }

    @Test
    public void colorOverlaysDeclareDistinctTonalPrimaryRoles() throws Exception {
        Document commonColors = xml("app/src/main/res/values/colors.xml");
        Document mobileColors = xml("app/src/main/res/values/ibox_colors.xml");
        Document mobileOverlays = xml("app/src/main/res/values/ibox_theme_overlays.xml");
        Document tvColors = xml("app/src/leanback/res/values/ibox_tv_colors.xml");
        Document tvOverlays = xml("app/src/leanback/res/values/theme_color_overlays.xml");
        assertTonalColorOverlays(mobileOverlays, mobileOverlays, mobileColors, commonColors);
        assertTonalColorOverlays(tvOverlays, tvOverlays, tvColors, commonColors);
    }

    @Test
    public void mobileColorOverlaysKeepSecondaryTextReadableInDayAndNight() throws Exception {
        Document commonColors = xml("app/src/main/res/values/colors.xml");
        Document dayColors = xml("app/src/main/res/values/ibox_colors.xml");
        Document nightColors = xml("app/src/main/res/values-night/ibox_colors.xml");
        Document overlays = xml("app/src/main/res/values/ibox_theme_overlays.xml");
        assertSecondaryContrast(overlays, overlays, dayColors, commonColors);
        assertSecondaryContrast(overlays, overlays, nightColors, dayColors, commonColors);
    }

    @Test
    public void tvRedPresetsResolveToDistinctTonalPairs() throws Exception {
        Document overlays = xml("app/src/leanback/res/values/theme_color_overlays.xml");
        Document tvColors = xml("app/src/leanback/res/values/ibox_tv_colors.xml");
        Map<String, String> chinaRed = directStyleItems(style(overlays, "ThemeOverlay.IBox.Color.ChinaRed"));
        Map<String, String> red = directStyleItems(style(overlays, "ThemeOverlay.IBox.Color.Red"));
        String[] roles = {"colorPrimary", "colorOnPrimary", "colorPrimaryContainer", "colorOnPrimaryContainer"};
        boolean different = false;
        for (String role : roles) {
            if (!resolveColor(chinaRed.get(role), overlays, tvColors)
                    .equals(resolveColor(red.get(role), overlays, tvColors))) {
                different = true;
            }
        }
        assertTrue("ChinaRed and Red must retain distinct TV tonal pairs", different);
    }

    @Test
    public void colorResolutionRejectsUnresolvedAndCyclicReferences() throws Exception {
        Document unresolved = xmlText("<resources><color name=\"first\">@color/missing</color></resources>");
        Document cyclic = xmlText("<resources><color name=\"first\">@color/second</color><color name=\"second\">@color/first</color></resources>");
        assertResolutionFails("@color/first", unresolved);
        assertResolutionFails("@color/first", cyclic);
    }

    @Test
    public void tvFocusOverlaysUseSemanticColorResources() throws Exception {
        Document focus = xml("app/src/leanback/res/values/focus_color_overlays.xml");
        Document tvColors = xml("app/src/leanback/res/values/ibox_tv_colors.xml");
        Document tvHomeColors = xml("app/src/leanback/res/values/tv_home_colors.xml");
        Document tvOverlays = xml("app/src/leanback/res/values/theme_color_overlays.xml");
        Document mobileOverlays = xml("app/src/main/res/values/ibox_theme_overlays.xml");
        Document mobileColors = xml("app/src/main/res/values/ibox_colors.xml");
        Document commonColors = xml("app/src/main/res/values/colors.xml");
        assertEquals("@color/ibox_focus_outline", styleItem(focus, "ThemeOverlay.IBox.Focus", "focusFrameColor"));
        assertEquals("@color/ibox_on_primary", styleItem(focus, "ThemeOverlay.IBox.Focus", "focusContentColor"));

        NodeList styles = focus.getElementsByTagName("style");
        for (int i = 0; i < styles.getLength(); i++) {
            Element style = (Element) styles.item(i);
            if (!style.getAttribute("name").startsWith("ThemeOverlay.IBox.Focus.")) continue;
            assertSemanticColorReference(styleItem(focus, style.getAttribute("name"), "focusFrameColor"), focus, tvOverlays, tvHomeColors, tvColors, mobileOverlays, mobileColors, commonColors);
            assertSemanticColorReference(styleItem(focus, style.getAttribute("name"), "focusContentColor"), focus, tvOverlays, tvHomeColors, tvColors, mobileOverlays, mobileColors, commonColors);
        }
    }

    @Test
    public void typographyAndDialogResourcesUseCanonicalPlatformTokens() throws Exception {
        Document mobileDimens = xml("app/src/main/res/values/ibox_dimens.xml");
        Document tvDimens = xml("app/src/leanback/res/values/tv_dimens.xml");
        Document dialogSystem = xml("app/src/main/res/values/ibox_ui_system.xml");
        Document mobileDialog = xml("app/src/mobile/res/drawable/bg_dialog_config.xml");
        Document tvDialog = xml("app/src/leanback/res/drawable/bg_dialog_config.xml");
        Document tvDialogStyles = xml("app/src/leanback/res/values/dialog_styles.xml");
        Document textStyles = xml("app/src/main/res/values/ibox_styles.xml");
        Document components = xml("app/src/main/res/values/ibox_components.xml");

        assertEquals("20sp", value(mobileDimens, "dimen", "ibox_text_page_title"));
        assertEquals("20sp", value(mobileDimens, "dimen", "ibox_text_dialog_title"));
        assertEquals("14sp", value(mobileDimens, "dimen", "ibox_text_body"));
        assertEquals("12sp", value(mobileDimens, "dimen", "ibox_text_supporting"));
        assertEquals("48dp", value(mobileDimens, "dimen", "ibox_touch_target"));
        assertEquals("28sp", value(tvDimens, "dimen", "ibox_text_page_title"));
        assertEquals("22sp", value(tvDimens, "dimen", "ibox_text_dialog_title"));
        assertEquals("16sp", value(tvDimens, "dimen", "ibox_text_body"));
        assertEquals("14sp", value(tvDimens, "dimen", "ibox_text_supporting"));
        assertEquals("56dp", value(tvDimens, "dimen", "ibox_touch_target"));
        assertEquals("56dp", value(tvDimens, "dimen", "ibox_icon_action_size"));
        assertEquals("16dp", value(dialogSystem, "dimen", "ibox_dialog_radius_mobile"));
        assertEquals("12dp", value(dialogSystem, "dimen", "ibox_dialog_radius_tv"));
        assertEquals("24dp", value(mobileDimens, "dimen", "ibox_dialog_content_padding"));
        assertEquals("16dp", value(mobileDimens, "dimen", "ibox_dialog_group_spacing"));
        assertEquals("24dp", value(tvDimens, "dimen", "ibox_dialog_content_padding"));
        assertEquals("16dp", value(tvDimens, "dimen", "ibox_dialog_group_spacing"));

        assertEquals("?attr/colorSurface", attribute(mobileDialog, "solid", "android:color"));
        assertEquals("?attr/colorOutline", attribute(mobileDialog, "stroke", "android:color"));
        assertEquals("@dimen/ibox_stroke_thin", attribute(mobileDialog, "stroke", "android:width"));
        assertEquals("@dimen/ibox_dialog_radius_mobile", attribute(mobileDialog, "corners", "android:radius"));
        assertEquals("?attr/colorSurface", attribute(tvDialog, "solid", "android:color"));
        assertEquals("?attr/colorOutline", attribute(tvDialog, "stroke", "android:color"));
        assertEquals("@dimen/ibox_stroke_thin", attribute(tvDialog, "stroke", "android:width"));
        assertEquals("@dimen/ibox_dialog_radius_tv", attribute(tvDialog, "corners", "android:radius"));
        assertEquals("@dimen/ibox_dialog_radius_tv", styleItem(tvDialogStyles,
                "ShapeAppearance.IBox.Dialog.TV", "cornerSize"));

        assertAllTextAppearancesHaveZeroLetterSpacing(textStyles);
        assertEquals("@dimen/ibox_touch_target", styleItem(textStyles,
                "Widget.IBox.Button.Base", "android:minHeight"));
        assertEquals("true", styleItem(textStyles, "Widget.IBox.Button.Base", "android:singleLine"));
        assertEquals("1", styleItem(textStyles, "Widget.IBox.Button.Base", "android:maxLines"));
        assertEquals("none", styleItem(textStyles, "Widget.IBox.Button.Base", "android:ellipsize"));
        assertEquals("0", styleItem(components, "Widget.IBox.TextInputEditText", "android:letterSpacing"));

        assertAlertDialogTheme(dialogSystem, "shapeAppearanceMediumComponent");
        assertDialogTheme(dialogSystem, "ThemeOverlay.IBox.Dialog.BottomSheet", "shapeAppearanceLargeComponent");
        assertDialogTheme(dialogSystem, "ThemeOverlay.IBox.Dialog.SideSheet", "shapeAppearanceLargeComponent");
        assertAlertDialogTheme(tvDialogStyles, "shapeAppearanceMediumComponent");
        assertDialogTheme(tvDialogStyles, "ThemeOverlay.IBox.Dialog.BottomSheet", "shapeAppearanceLargeComponent");
        assertPlatformLayoutsUseTokenizedTypographyAndColors("app/src/mobile/res/layout");
        assertPlatformLayoutsUseTokenizedTypographyAndColors("app/src/leanback/res/layout");
    }

    @Test
    public void tvContentSurfacesKeepSemanticTypographyAndFocusableCards() throws Exception {
        for (String name : new String[]{"activity_home", "adapter_home_category", "adapter_home_vod",
                "adapter_vod", "adapter_vod_rect", "adapter_vod_oval", "adapter_repository",
                "dialog_config", "dialog_repository", "view_empty"}) {
            Document layout = xml("app/src/leanback/res/layout/" + name + ".xml");
            NodeList elements = layout.getElementsByTagName("*");
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                assertFalse(name + " has literal text sizing", element.getAttribute("android:textSize").matches("[0-9.]+[sd]p"));
                assertSemanticVisualAttributes(name, element);
            }
            if (name.startsWith("adapter_") && !name.equals("adapter_repository")) {
                assertEquals(name, "true", layout.getDocumentElement().getAttribute("android:focusable"));
                assertEquals(name, "true", layout.getDocumentElement().getAttribute("android:focusableInTouchMode"));
            }
        }
        assertEquals("true", styleItem(xml("app/src/leanback/res/values/styles.xml"),
                "Widget.IBox.RepositoryItem", "android:focusable"));
        assertEquals("@style/Widget.IBox.RepositoryItem", xml("app/src/leanback/res/layout/adapter_repository.xml")
                .getDocumentElement().getAttribute("style"));
        assertEquals("true", view(xml("app/src/leanback/res/layout/dialog_config.xml"), "code").getAttribute("android:focusable"));
        Document tvStyles = xml("app/src/leanback/res/values/styles.xml");
        assertEquals("true", styleItem(tvStyles, "Widget.IBox.IconButton", "android:focusable"));
        for (String id : new String[]{"selected", "edit", "delete"}) {
            assertEquals("@style/Widget.IBox.IconButton", view(xml("app/src/leanback/res/layout/adapter_repository.xml"), id).getAttribute("style"));
        }
        for (String id : new String[]{"name", "text"}) {
            assertEquals("com.imedia.box.ui.custom.CustomEditText", view(xml("app/src/leanback/res/layout/dialog_config.xml"), id).getTagName());
        }
        for (String id : new String[]{"choose", "positive", "negative"}) {
            assertEquals("com.google.android.material.button.MaterialButton", view(xml("app/src/leanback/res/layout/dialog_config.xml"), id).getTagName());
        }
        for (String id : new String[]{"vod", "live"}) {
            assertEquals("com.google.android.material.button.MaterialButton", view(xml("app/src/leanback/res/layout/dialog_repository.xml"), id).getTagName());
        }
    }

    @Test
    public void tvSelectionSurvivesFocusWithAnIndependentOutline() throws Exception {
        for (String name : new String[]{"selector_home_category", "selector_ibox_repository_item"}) {
            Document selector = xml("app/src/leanback/res/drawable/" + name + ".xml");
            Element normal = tvSelectorItem(selector, false, false);
            Element selected = tvSelectorItem(selector, true, false);
            Element focused = tvSelectorItem(selector, false, true);
            Element combined = tvSelectorItem(selector, true, true);
            assertTrue(name + " must distinguish selection from rest", normal != selected);
            assertTrue(name + " must distinguish focus from selection", selected != focused);
            String selectedFill = ((Element) selected.getElementsByTagName("solid").item(0)).getAttribute("android:color");
            assertEquals(name + " loses selection while focused", selectedFill,
                    ((Element) combined.getElementsByTagName("solid").item(0)).getAttribute("android:color"));
            for (Element item : new Element[]{focused, combined}) {
                assertEquals(name + " needs a visible focus ring", 1, item.getElementsByTagName("stroke").getLength());
                Element stroke = (Element) item.getElementsByTagName("stroke").item(0);
                assertEquals("@dimen/ibox_focus_stroke", stroke.getAttribute("android:width"));
                assertEquals("?attr/focusFrameColor", stroke.getAttribute("android:color"));
            }
        }
        Document text = xml("app/src/leanback/res/color/selector_home_category_text.xml");
        assertEquals("?attr/colorOnPrimaryContainer", tvSelectorItem(text, true, true).getAttribute("android:color"));
        assertEquals("@color/ibox_text_primary", tvSelectorItem(text, false, true).getAttribute("android:color"));
    }

    @Test
    public void tvPosterFocusBoundariesStayVisibleAcrossArbitraryArtwork() throws Exception {
        Document colors = xml("app/src/leanback/res/values/ibox_tv_colors.xml");
        Document dimens = xml("app/src/main/res/values/ibox_dimens.xml");
        for (String name : new String[]{"selector_vod", "shape_vod_oval_focused"}) {
            Document drawable = xml("app/src/leanback/res/drawable/" + name + ".xml");
            NodeList layers = drawable.getElementsByTagName("layer-list");
            assertEquals(name + " needs a controlled dark/light focus boundary", 1, layers.getLength());
            Element layerList = (Element) layers.item(0);
            List<Element> items = new ArrayList<>();
            for (int i = 0; i < layerList.getChildNodes().getLength(); i++) {
                if (layerList.getChildNodes().item(i) instanceof Element) {
                    items.add((Element) layerList.getChildNodes().item(i));
                }
            }
            assertEquals(name + " must expose both boundary layers", 2, items.size());
            Element dark = (Element) items.get(0).getElementsByTagName("stroke").item(0);
            Element light = (Element) items.get(1).getElementsByTagName("stroke").item(0);
            assertEquals("@color/ibox_surface_container", dark.getAttribute("android:color"));
            assertEquals("?attr/focusFrameColor", light.getAttribute("android:color"));
            assertEquals("@dimen/ibox_focus_halo_stroke", dark.getAttribute("android:width"));
            assertEquals("@dimen/ibox_focus_stroke", light.getAttribute("android:width"));
            double inset = dp(dimens, "ibox_stroke_thin");
            double lightWidth = dp(dimens, "ibox_focus_stroke");
            double darkWidth = dp(dimens, "ibox_focus_halo_stroke");
            assertEquals(2.0, lightWidth, 0.0);
            assertTrue(name + " hides the inner dark boundary", darkWidth > inset + lightWidth);
            for (String edge : new String[]{"left", "top", "right", "bottom"}) {
                assertFalse(name + " shifts the outer boundary", items.get(0).hasAttribute("android:" + edge));
                assertEquals(name + " hides the outer dark boundary", "@dimen/ibox_stroke_thin",
                        items.get(1).getAttribute("android:" + edge));
            }
            double darkLuminance = luminance(color(colors, "ibox_surface_container"));
            double lightLuminance = luminance(color(colors, "ibox_focus_outline"));
            // This crossing is the minimum of max(dark contrast, light contrast) across
            // the continuous luminance interval; endpoints also cover white/black artwork.
            double worstArtwork = Math.sqrt((darkLuminance + 0.05) * (lightLuminance + 0.05)) - 0.05;
            for (double artwork : new double[]{0.0, worstArtwork, 1.0}) {
                double darkContrast = (Math.max(darkLuminance, artwork) + 0.05)
                        / (Math.min(darkLuminance, artwork) + 0.05);
                double lightContrast = (Math.max(lightLuminance, artwork) + 0.05)
                        / (Math.min(lightLuminance, artwork) + 0.05);
                assertTrue(name + " loses focus contrast at artwork luminance " + artwork,
                        Math.max(darkContrast, lightContrast) >= 3.0);
            }
        }
    }

    @Test
    public void tvHomeAncestorsAndDialogRowsLeaveRoomForFocus() throws Exception {
        Document home = xml("app/src/leanback/res/layout/activity_home.xml");
        Element ancestor = view(home, "recycler");
        while (ancestor != null) {
            assertEquals(ancestor.getTagName() + " clips focused content", "false", ancestor.getAttribute("android:clipChildren"));
            assertEquals(ancestor.getTagName() + " clips padded focus space", "false", ancestor.getAttribute("android:clipToPadding"));
            ancestor = ancestor.getParentNode() instanceof Element ? (Element) ancestor.getParentNode() : null;
        }
        assertEquals("false", view(home, "title").getAttribute("android:focusable"));
        for (String name : new String[]{"dialog_config", "dialog_repository"}) {
            assertEquals(name, "@dimen/ibox_dialog_content_padding", xml("app/src/leanback/res/layout/" + name + ".xml")
                    .getDocumentElement().getAttribute("android:padding"));
        }
        Document config = xml("app/src/leanback/res/layout/dialog_config.xml");
        assertEquals("@dimen/ibox_touch_target", view(config, "bottom").getAttribute("android:layout_height"));
        Document repository = xml("app/src/leanback/res/layout/dialog_repository.xml");
        assertEquals("@dimen/ibox_touch_target", ((Element) view(repository, "add").getParentNode()).getAttribute("android:layout_height"));
        assertEquals("@dimen/ibox_touch_target", view(repository, "repositoryType").getAttribute("android:layout_height"));
    }

    @Test
    public void tvHomeToolbarStaysAboveScrollableArtwork() throws Exception {
        Document home = xml("app/src/leanback/res/layout/activity_home.xml");
        Element toolbar = view(home, "toolbar");
        assertEquals("@color/black_70", toolbar.getAttribute("android:background"));
        assertEquals("#B3000000", color(xml("app/src/main/res/values/colors.xml"), "black_70"));
        assertEquals("1dp", toolbar.getAttribute("android:elevation"));
    }

    @Test
    public void liveChannelPreviewStaysAboveTheCompactMobileList() throws Exception {
        Document tv = xml("app/src/leanback/res/layout/adapter_channel.xml");
        assertEquals("end", view(tv, "summary").getAttribute("android:ellipsize"));
        assertEquals("1", view(tv, "next").getAttribute("android:maxLines"));

        Document mobile = xml("app/src/mobile/res/layout/adapter_channel.xml");
        NodeList mobileViews = mobile.getElementsByTagName("*");
        for (int i = 0; i < mobileViews.getLength(); i++) {
            assertFalse("Channel rows should not duplicate the program preview",
                    "@+id/summary".equals(((Element) mobileViews.item(i)).getAttribute("android:id")));
        }
        assertEquals("44dp", mobile.getDocumentElement().getAttribute("android:minHeight"));
        assertEquals("@dimen/ibox_text_body", view(mobile, "name").getAttribute("android:textSize"));
        Document live = xml("app/src/mobile/res/layout/activity_live.xml");
        assertEquals("64dp", view(live, "channelSummary").getAttribute("android:layout_height"));
        assertEquals("@dimen/ibox_text_supporting", view(live, "summaryNow").getAttribute("android:textSize"));
    }

    private static Element tvSelectorItem(Document selector, boolean selected, boolean focused) {
        NodeList items = selector.getDocumentElement().getChildNodes();
        for (int i = 0; i < items.getLength(); i++) {
            if (!(items.item(i) instanceof Element)) continue;
            Element item = (Element) items.item(i);
            if (item.hasAttribute("android:state_selected")
                    && Boolean.parseBoolean(item.getAttribute("android:state_selected")) != selected) continue;
            if (item.hasAttribute("android:state_focused")
                    && Boolean.parseBoolean(item.getAttribute("android:state_focused")) != focused) continue;
            if ("true".equals(item.getAttribute("android:state_pressed"))) continue;
            return item;
        }
        throw new AssertionError("No selector state selected=" + selected + ", focused=" + focused);
    }

    @Test
    public void mobileContentSurfacesUseCanonicalTypographyAndSemanticVisuals() throws Exception {
        String[] layouts = {"fragment_vod", "fragment_search", "view_app_navigation", "adapter_vod",
                "adapter_vod_rect", "adapter_vod_oval", "adapter_repository", "dialog_config",
                "dialog_repository", "view_empty", "view_sheet_header", "adapter_type"};
        for (String name : layouts) {
            Document layout = xml("app/src/mobile/res/layout/" + name + ".xml");
            NodeList elements = layout.getElementsByTagName("*");
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                assertFalse(name + " has literal text sizing", element.getAttribute("android:textSize").matches("[0-9.]+[sd]p"));
                assertSemanticVisualAttributes(name, element);
                if (element.getTagName().endsWith("MaterialButton")) {
                    assertFalse(name + " truncates a command", element.hasAttribute("android:ellipsize"));
                }
                if (element.getTagName().endsWith("TextView")) {
                    assertTrue(name + " bypasses canonical text appearance: " + element.getAttribute("android:id"),
                            element.getAttribute("android:textAppearance").startsWith("@style/TextAppearance.IBox.")
                                    || element.getAttribute("style").equals("@style/Widget.IBox.PosterTitle"));
                    assertFalse(name + " shrinks readable text", element.hasAttribute("app:autoSizeMinTextSize"));
                }
            }
        }
        for (String name : new String[]{"bg_home_action", "bg_home_search", "bg_app_navigation_item",
                "bg_floating_navigation", "shape_vod_name"}) {
            NodeList elements = xml("app/src/mobile/res/drawable/" + name + ".xml").getElementsByTagName("*");
            for (int i = 0; i < elements.getLength(); i++) assertSemanticVisualAttributes(name, (Element) elements.item(i));
        }
        assertEquals("@style/Widget.IBox.RepositoryItem", xml("app/src/mobile/res/layout/adapter_repository.xml")
                .getDocumentElement().getAttribute("style"));
    }

    @Test
    public void mobileActionsAndDialogGroupsKeepTheirAccessibleGeometry() throws Exception {
        Document navigation = xml("app/src/mobile/res/layout/view_app_navigation.xml");
        Document dimens = xml("app/src/main/res/values/ibox_dimens.xml");
        for (String id : new String[]{"navHome", "navLive", "navLibrary", "navDownloads", "navSettings"}) {
            assertEquals("@dimen/ibox_navigation_item_height", view(navigation, id).getAttribute("android:layout_height"));
        }
        for (String token : new String[]{"ibox_navigation_item_height", "ibox_navigation_height"}) {
            assertTrue(token + " is smaller than the touch target", dp(dimens, token) >= dp(dimens, "ibox_touch_target"));
        }
        for (String name : new String[]{"dialog_config", "dialog_repository"}) {
            Element root = xml("app/src/mobile/res/layout/" + name + ".xml").getDocumentElement();
            assertEquals(name, "@dimen/ibox_dialog_content_padding", root.getAttribute("android:padding"));
        }
        Document config = xml("app/src/mobile/res/layout/dialog_config.xml");
        Document header = xml("app/src/mobile/res/layout/view_sheet_header.xml");
        assertEquals("@dimen/ibox_touch_target", view(header, "sheet_positive").getAttribute("android:layout_height"));
        assertEquals("@style/TextAppearance.IBox.DialogTitle", view(header, "sheet_title").getAttribute("android:textAppearance"));
        assertEquals("@dimen/ibox_dialog_group_spacing", view(config, "input").getAttribute("android:layout_marginBottom"));
        assertEquals("true", view(config, "url").getAttribute("android:singleLine"));
        assertEquals("true", view(config, "url").getAttribute("android:scrollHorizontally"));
        Document repository = xml("app/src/mobile/res/layout/adapter_repository.xml");
        assertFalse("Repository item must retain shared minimum height", repository.getDocumentElement().hasAttribute("android:minHeight"));
        for (String id : new String[]{"selected", "edit", "delete"}) {
            assertEquals("@dimen/ibox_touch_target", view(repository, id).getAttribute("android:layout_height"));
            assertEquals("@dimen/ibox_touch_target", view(repository, id).getAttribute("android:layout_width"));
        }
        Document search = xml("app/src/mobile/res/layout/fragment_search.xml");
        assertEquals("@dimen/ibox_home_row_height", view(xml("app/src/mobile/res/layout/adapter_type.xml"), "text")
                .getAttribute("android:layout_height"));
        for (String id : new String[]{"keywordClear", "historyClear", "scopeAll", "scopeCurrent"}) {
            assertEquals(id, "@dimen/ibox_touch_target", view(search, id).getAttribute("android:layout_height"));
        }
    }

    @Test
    public void mobileHomeChromeIsCompactAcrossScreens() throws Exception {
        Document dimensions = xml("app/src/main/res/values/ibox_dimens.xml");
        assertEquals("60dp", value(dimensions, "dimen", "ibox_navigation_height"));
        assertEquals("56dp", value(dimensions, "dimen", "ibox_navigation_item_height"));
        Document homeDimensions = xml("app/src/mobile/res/values/home_dimens.xml");
        assertEquals("44dp", value(homeDimensions, "dimen", "ibox_home_row_height"));

        Document home = xml("app/src/mobile/res/layout/fragment_vod.xml");
        Element source = view(home, "source");
        assertEquals("@dimen/ibox_home_row_height", ((Element) source.getParentNode()).getAttribute("android:layout_height"));
        for (String id : new String[]{"repository", "keep", "history"}) {
            Element action = view(home, id);
            assertEquals("@dimen/ibox_home_row_height", action.getAttribute("android:layout_height"));
            assertEquals("@dimen/ibox_home_row_height", action.getAttribute("android:minHeight"));
        }
        assertEquals("@dimen/ibox_home_row_height", view(home, "search").getAttribute("android:layout_height"));
        assertEquals("@dimen/ibox_home_row_height", view(home, "linkQuick").getAttribute("android:layout_height"));
        assertEquals("@dimen/ibox_home_row_height", view(home, "type").getAttribute("android:layout_height"));
        assertEquals("@dimen/ibox_home_row_height", xml("app/src/mobile/res/layout/adapter_type.xml")
                .getDocumentElement().getAttribute("android:layout_height"));

        for (String layout : new String[]{"activity_home.xml", "activity_live.xml", "activity_video.xml",
                "activity_library.xml", "activity_keep.xml", "activity_history.xml"}) {
            assertEquals(layout, "@dimen/ibox_navigation_height", view(xml("app/src/mobile/res/layout/" + layout),
                    "navigation").getAttribute("android:layout_height"));
        }
        assertEquals("@dimen/ibox_navigation_height", view(xml("app/src/mobile/res/layout-sw600dp/activity_video.xml"),
                "navigation").getAttribute("android:layout_height"));
    }

    @Test
    public void mobileHomeCategoriesStayWithinHorizontalInsetsWhileScrolling() throws Exception {
        Element categories = view(xml("app/src/mobile/res/layout/fragment_vod.xml"), "type");
        assertEquals("true", categories.getAttribute("android:clipToPadding"));
        assertEquals("@dimen/ibox_space_2", categories.getAttribute("android:paddingStart"));
        assertEquals("@dimen/ibox_space_2", categories.getAttribute("android:paddingEnd"));
    }

    @Test
    public void mobileHomeChromePaintsCompactBackgroundsWithinFullSizeTargets() throws Exception {
        Document home = xml("app/src/mobile/res/layout/fragment_vod.xml");
        Element source = view(home, "source");
        assertEquals("match_parent", source.getAttribute("android:layout_height"));
        assertEquals("@dimen/ibox_home_row_height", ((Element) source.getParentNode()).getAttribute("android:layout_height"));
        for (String id : new String[]{"repository", "keep", "history", "search", "type"}) {
            assertEquals(id, "@dimen/ibox_home_row_height", view(home, id).getAttribute("android:layout_height"));
        }
        Element category = xml("app/src/mobile/res/layout/adapter_type.xml").getDocumentElement();
        assertEquals("@dimen/ibox_home_row_height", category.getAttribute("android:layout_height"));
        assertEquals("@dimen/ibox_home_row_height", category.getAttribute("android:minWidth"));
        assertEquals("@dimen/ibox_home_category_padding", category.getAttribute("android:paddingStart"));
        assertEquals("@dimen/ibox_home_category_padding", category.getAttribute("android:paddingEnd"));
        assertEquals(6.0, dp(xml("app/src/mobile/res/values/home_dimens.xml"), "ibox_home_category_padding")
                - dp(xml("app/src/main/res/values/ibox_dimens.xml"), "ibox_space_1"), 0.001);

        for (String name : new String[]{"bg_home_action", "bg_home_search", "bg_ibox_chip"}) {
            Document background = xml("app/src/mobile/res/drawable/" + name + ".xml");
            NodeList items = background.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element inset = (Element) ((Element) items.item(i)).getElementsByTagName("inset").item(0);
                assertNotNull(name + " item " + i + " lacks compact visual bounds", inset);
                for (String side : new String[]{"Left", "Right", "Top", "Bottom"}) {
                    String expected = name.equals("bg_ibox_chip") && (side.equals("Top") || side.equals("Bottom"))
                            ? "@dimen/ibox_home_category_vertical_inset" : "@dimen/ibox_space_1";
                    assertEquals(name + " item " + i + " " + side, expected,
                            inset.getAttribute("android:inset" + side));
                }
            }
        }
        assertEquals(6.0, dp(xml("app/src/mobile/res/values/home_dimens.xml"),
                "ibox_home_category_vertical_inset"), 0.001);
    }

    @Test
    public void mobileCastSlidersUseCircularThumbsInBothOrientations() throws Exception {
        for (String layout : new String[]{"layout", "layout-land"}) {
            Document control = xml("app/src/mobile/res/" + layout + "/activity_cast_control.xml");
            for (String id : new String[]{"progress", "volume"}) {
                Element slider = view(control, id);
                assertEquals(layout + " " + id, "@style/Widget.Material3.Slider.Legacy",
                        slider.getAttribute("style"));
                assertEquals(layout + " " + id, "6dp", slider.getAttribute("app:thumbRadius"));
            }
        }
    }

    @Test
    public void mobileCastPlaybackIconsStayCenteredInTheirButtons() throws Exception {
        for (String layout : new String[]{"layout", "layout-land"}) {
            Document control = xml("app/src/mobile/res/" + layout + "/activity_cast_control.xml");
            for (String id : new String[]{"rewind", "play", "forward"}) {
                Element button = view(control, id);
                assertEquals(layout + " " + id, "textStart", button.getAttribute("app:iconGravity"));
                assertEquals(layout + " " + id, "0dp", button.getAttribute("app:iconPadding"));
            }
        }
    }

    @Test
    public void mobileCastDisconnectIconStaysWithCenteredLabel() throws Exception {
        for (String layout : new String[]{"layout", "layout-land"}) {
            Element button = view(xml("app/src/mobile/res/" + layout + "/activity_cast_control.xml"), "disconnect");
            assertEquals(layout, "textStart", button.getAttribute("app:iconGravity"));
        }
    }

    @Test
    public void mobileCastConnectionRetryIsAvailableInBothOrientations() throws Exception {
        for (String layout : new String[]{"layout", "layout-land"}) {
            Element button = view(xml("app/src/mobile/res/" + layout + "/activity_cast_control.xml"), "retry");
            assertEquals(layout, "gone", button.getAttribute("android:visibility"));
            assertEquals(layout, "@string/cast_retry_playback", button.getAttribute("android:contentDescription"));
        }
    }

    @Test
    public void repositoryAddressProvidesAUserScrollableViewport() throws Exception {
        Element url = view(xml("app/src/mobile/res/layout/adapter_repository.xml"), "url");
        assertEquals("com.google.android.material.textview.MaterialTextView", url.getTagName());
        Element viewport = (Element) url.getParentNode();
        assertEquals("Address needs a user-pannable viewport", "HorizontalScrollView", viewport.getTagName());
        assertEquals("wrap_content", url.getAttribute("android:layout_width"));
        assertEquals("true", url.getAttribute("android:singleLine"));
        assertFalse(url.hasAttribute("android:ellipsize"));
        assertEquals("0dp", viewport.getAttribute("android:layout_width"));
        assertEquals("parent", viewport.getAttribute("app:layout_constraintStart_toStartOf"));
        assertEquals("parent", viewport.getAttribute("app:layout_constraintEnd_toEndOf"));
        assertEquals("@id/edit", viewport.getAttribute("app:layout_constraintTop_toBottomOf"));
    }

    @Test
    public void repositoryAddressTapUsesTheSameSelectionCallbackAsTheRow() throws Exception {
        Path adapter = repoRoot().resolve("app/src/mobile/java/com/imedia/box/ui/adapter/RepositoryAdapter.java");
        // This JUnit source set has no Android view runtime. Inspect the real binding contract;
        // platform touch-slop/interception behavior still needs a device gesture test.
        String source = Files.readString(adapter).replaceAll("(?s)/\\*.*?\\*/|(?m)//[^\\r\\n]*", "");
        String binding = source.substring(source.indexOf("public void onBindViewHolder"),
                source.indexOf("public int getItemCount"));
        for (String target : new String[]{"holder.binding.getRoot()", "holder.binding.selected", "holder.binding.url"}) {
            Pattern callback = Pattern.compile(Pattern.quote(target)
                    + "\\s*\\.\\s*setOnClickListener\\s*\\(\\s*\\w+\\s*->\\s*listener\\s*\\.\\s*onSelect\\s*\\(\\s*item\\s*\\)\\s*\\)");
            assertTrue(target + " must select the bound repository on tap", callback.matcher(binding).find());
        }
    }

    @Test
    public void mobileNightPaletteUsesQualifiedColorsAndDefaultFallbacks() throws Exception {
        Document[] day = mobileColorResources(false);
        Document[] night = mobileColorResources(true);
        assertEquals("#D7E9FA", resolveColor("@color/theme_blue_container", day));
        assertEquals("#0B3558", resolveColor("@color/theme_blue_on_container", day));
        assertEquals("#164B75", resolveColor("@color/theme_blue_container", night));
        assertEquals("#D8EBFF", resolveColor("@color/theme_blue_on_container", night));
        assertEquals("#69B4FF", resolveColor("@color/theme_blue", night));
        assertEquals("#E9DDFF", resolveColor("@color/theme_purple_container", night));
        assertEquals("#B30E1013", resolveColor("@color/bg_remark", night));
    }

    private static Document[] mobileColorResources(boolean night) throws Exception {
        return platformColorResources("mobile", night);
    }

    private static Document[] platformColorResources(String platform, boolean night) throws Exception {
        List<Document> resources = new ArrayList<>();
        // Qualifiers beat source sets: main values-night overrides a platform default;
        // the platform wins over main only within the same qualifier.
        for (String qualifier : night ? new String[]{"values-night", "values"} : new String[]{"values"}) {
            for (String sourceSet : new String[]{platform, "main"}) {
                Path directory = repoRoot().resolve("app/src/" + sourceSet + "/res/" + qualifier);
                if (!Files.isDirectory(directory)) continue;
                try (Stream<Path> paths = Files.list(directory)) {
                    var iterator = paths.filter(path -> path.toString().endsWith(".xml")).sorted().iterator();
                    while (iterator.hasNext()) {
                        resources.add(xml(repoRoot().relativize(iterator.next()).toString()));
                    }
                }
            }
        }
        return resources.toArray(new Document[0]);
    }

    @Test
    public void sheetCommandKeepsItsTouchTargetForShortLabels() throws Exception {
        Element command = view(xml("app/src/mobile/res/layout/view_sheet_header.xml"), "sheet_positive");
        assertEquals("@dimen/ibox_touch_target", command.getAttribute("android:minWidth"));
        assertEquals("@dimen/ibox_touch_target", command.getAttribute("android:layout_height"));
    }

    @Test
    public void selectedNavigationUsesMatchingThemeRolesInEveryMobilePalette() throws Exception {
        Document background = xml("app/src/mobile/res/drawable/bg_app_navigation_item.xml");
        Element selectedBackground = (Element) background.getElementsByTagName("item").item(0);
        assertEquals("Selection must retain its color pair during press/focus/hover", "true",
                selectedBackground.getAttribute("android:state_selected"));
        String backgroundRole = ((Element) selectedBackground.getElementsByTagName("solid").item(0))
                .getAttribute("android:color");
        assertEquals("?attr/colorPrimaryContainer", backgroundRole);
        Document overlays = xml("app/src/main/res/values/ibox_theme_overlays.xml");
        Document base = xml("app/src/main/res/values/ibox_styles.xml");
        Document[] dayResources = mobileColorResources(false);
        Document[] nightResources = mobileColorResources(true);
        for (String selector : new String[]{"selector_app_navigation_text", "selector_app_navigation_icon"}) {
            Element selectedForeground = (Element) xml("app/src/mobile/res/color/" + selector + ".xml")
                    .getElementsByTagName("item").item(0);
            assertEquals("true", selectedForeground.getAttribute("android:state_selected"));
            String foregroundRole = selectedForeground.getAttribute("android:color");
            assertEquals("?attr/colorOnPrimaryContainer", foregroundRole);
            Map<String, String> baseRoles = directStyleItems(style(base, "Theme.IBox.Mobile"));
            assertNavigationPairContrast("Mobile default day", foregroundRole, backgroundRole, baseRoles, dayResources);
            assertNavigationPairContrast("Mobile default night", foregroundRole, backgroundRole, baseRoles, nightResources);
            NodeList styles = overlays.getElementsByTagName("style");
            for (int i = 0; i < styles.getLength(); i++) {
                Element overlay = (Element) styles.item(i);
                Map<String, String> roles = new LinkedHashMap<>(baseRoles);
                roles.putAll(directStyleItems(overlay));
                // Includes every supported color preset and the high-contrast accessibility overlay.
                if (!directStyleItems(overlay).containsKey("colorPrimaryContainer")) continue;
                String name = overlay.getAttribute("name");
                assertNavigationPairContrast(name + " day", foregroundRole, backgroundRole, roles, dayResources);
                assertNavigationPairContrast(name + " night", foregroundRole, backgroundRole, roles, nightResources);
            }
        }
    }

    @Test
    public void mobileCategorySelectionAndPressKeepReadablePairsAcrossOverlays() throws Exception {
        Element category = xml("app/src/mobile/res/layout/adapter_type.xml").getDocumentElement();
        Document background = xml("app/src/mobile/res/drawable/"
                + category.getAttribute("android:background").substring("@drawable/".length()) + ".xml");
        Document foreground = xml("app/src/mobile/res/color/"
                + category.getAttribute("android:textColor").substring("@color/".length()) + ".xml");
        Map<String, String> baseRoles = directStyleItems(style(
                xml("app/src/main/res/values/ibox_styles.xml"), "Theme.IBox.Mobile"));
        Map<String, Map<String, String>> palettes = new LinkedHashMap<>();
        palettes.put("Mobile default", baseRoles);
        NodeList overlays = xml("app/src/main/res/values/ibox_theme_overlays.xml").getElementsByTagName("style");
        for (int i = 0; i < overlays.getLength(); i++) {
            Element overlay = (Element) overlays.item(i);
            Map<String, String> items = directStyleItems(overlay);
            if (!items.containsKey("colorPrimaryContainer")) continue;
            Map<String, String> roles = new LinkedHashMap<>(baseRoles);
            roles.putAll(items);
            palettes.put(overlay.getAttribute("name"), roles);
        }
        for (Set<String> state : List.of(Set.of("selected"), Set.of("checked"),
                Set.of("pressed"), Set.of("selected", "pressed"), Set.of("checked", "pressed"))) {
            for (boolean night : new boolean[]{false, true}) {
                Document[] resources = mobileColorResources(night);
                for (Map.Entry<String, Map<String, String>> palette : palettes.entrySet()) {
                    String fill = ((Element) matchingState(background, state).getElementsByTagName("solid").item(0))
                            .getAttribute("android:color");
                    String text = matchingState(foreground, state).getAttribute("android:color");
                    String label = palette.getKey() + (night ? " night " : " day ") + state;
                    double ratio = contrast(resolveThemeColor(text, palette.getValue(), resources),
                            resolveThemeColor(fill, palette.getValue(), resources));
                    assertTrue(label + " category contrast=" + ratio + " below 4.5:1", ratio >= 4.5);
                }
            }
        }
    }

    @Test
    public void selectedEpisodeTextStaysReadableOnDialogSurfacesAcrossThemes() throws Exception {
        Document background = xml("app/src/main/res/drawable/selector_ibox_episode.xml");
        Document foreground = xml("app/src/main/res/color/selector_ibox_episode_text.xml");
        String fillRole = attributeOf(matchingState(background, Set.of("selected")), "solid", "android:color");
        String textRole = matchingState(foreground, Set.of("selected")).getAttribute("android:color");

        for (String platform : new String[]{"mobile", "leanback"}) {
            for (boolean night : new boolean[]{false, true}) {
                Document[] resources = platformColorResources(platform, night);
                for (var palette : palettes(platform).entrySet()) {
                    String fill = resolveThemeColor(fillRole, palette.getValue(), resources);
                    String surface = resolveThemeColor("?attr/colorSurface", palette.getValue(), resources);
                    String text = resolveThemeColor(textRole, palette.getValue(), resources);
                    double ratio = contrast(text, compositeColor(fill, surface));
                    assertTrue(platform + " " + palette.getKey() + " night=" + night
                            + " selected episode contrast=" + ratio, ratio >= 4.5);
                }
            }
        }
    }

    @Test
    public void mobileEpisodeActionsUseAFullWidthFlexibleRow() throws Exception {
        for (String path : new String[]{"app/src/mobile/res/layout/activity_video.xml",
                "app/src/mobile/res/layout-sw600dp/activity_video.xml"}) {
            Document layout = xml(path);
            Element reverse = view(layout, "reverse");
            Element more = view(layout, "more");
            assertEquals(path, reverse.getParentNode(), more.getParentNode());
            Element actions = (Element) reverse.getParentNode();
            assertEquals(path, "match_parent", actions.getAttribute("android:layout_width"));
            assertEquals(path, "wrap_content", actions.getAttribute("android:layout_height"));
            for (Element button : new Element[]{reverse, more}) {
                assertEquals(path, "0dp", button.getAttribute("android:layout_width"));
                assertEquals(path, "1", button.getAttribute("android:layout_weight"));
                assertEquals(path, "wrap_content", button.getAttribute("android:layout_height"));
                assertEquals(path, "@dimen/ibox_touch_target", button.getAttribute("android:minHeight"));
                assertEquals(path, "false", button.getAttribute("android:singleLine"));
                assertEquals(path, "2", button.getAttribute("android:maxLines"));
            }
        }
    }

    @Test
    public void highContrastSurfaceRolesFlowIntoTvHomeAndAlerts() throws Exception {
        Map<String, String> highContrast = directStyleItems(style(
                xml("app/src/main/res/values/ibox_theme_overlays.xml"),
                "ThemeOverlay.IBox.Accessibility.HighContrast"));
        for (String role : new String[]{"colorSurface", "colorSurfaceDim", "colorSurfaceBright",
                "colorSurfaceContainerLowest", "colorSurfaceContainerLow", "colorSurfaceContainer",
                "colorSurfaceContainerHigh", "colorSurfaceContainerHighest"}) {
            assertEquals(role, "@color/ibox_high_contrast_surface", highContrast.get(role));
        }
        assertEquals("@color/ibox_high_contrast_surface", highContrast.get("android:windowBackground"));
        assertEquals("@android:color/transparent",
                xml("app/src/leanback/res/layout/activity_home.xml").getDocumentElement()
                        .getAttribute("android:background"));
        for (String path : new String[]{"app/src/main/res/values/ibox_ui_system.xml",
                "app/src/leanback/res/values/dialog_styles.xml"}) {
            Map<String, String> alert = directStyleItems(style(xml(path), "ThemeOverlay.IBox.Dialog.Alert"));
            for (String inherited : new String[]{"colorSurface", "colorOnSurface", "colorOnSurfaceVariant", "colorOutline"}) {
                assertFalse(path + " overrides caller accessibility role " + inherited, alert.containsKey(inherited));
            }
        }
        for (String path : new String[]{"app/src/mobile/res/drawable/bg_dialog_config.xml",
                "app/src/leanback/res/drawable/bg_dialog_config.xml"}) {
            Document background = xml(path);
            assertEquals(path, "?attr/colorSurface", attribute(background, "solid", "android:color"));
            assertEquals(path, "?attr/colorOutline", attribute(background, "stroke", "android:color"));
        }
        Document textStyles = xml("app/src/main/res/values/ibox_styles.xml");
        for (String style : new String[]{"TextAppearance.IBox.PageTitle", "TextAppearance.IBox.Section",
                "TextAppearance.IBox.ItemTitle", "TextAppearance.IBox.Body"}) {
            assertEquals(style, "?attr/colorOnSurface", styleItem(textStyles, style, "android:textColor"));
        }
        for (String style : new String[]{"TextAppearance.IBox.Supporting", "TextAppearance.IBox.Label"}) {
            assertEquals(style, "?attr/colorOnSurfaceVariant", styleItem(textStyles, style, "android:textColor"));
        }
    }

    @Test
    public void everyMobilePrimaryConsumerKeepsContrastInEffectiveDayAndNight() throws Exception {
        List<String> failures = new ArrayList<>();
        for (boolean night : new boolean[]{false, true}) {
            Document[] resources = platformColorResources("mobile", night);
            Map<String, Map<String, String>> palettes = palettes("mobile");
            for (var palette : palettes.entrySet()) {
                double ratio = contrast(resolveThemeColor("?attr/colorOnPrimary", palette.getValue(), resources),
                        resolveThemeColor("?attr/colorPrimary", palette.getValue(), resources));
                if (ratio < 4.5) failures.add(palette.getKey() + " night=" + night + " contrast=" + ratio);
            }
        }
        assertTrue(String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void tvFilledDialogCommandsKeepContrastForEveryFocusPreference() throws Exception {
        Document backgrounds = xml("app/src/leanback/res/drawable/selector_dialog_button.xml");
        Document foregrounds = xml("app/src/leanback/res/color/selector_dialog_button_text.xml");
        Document overlays = xml("app/src/leanback/res/values/focus_color_overlays.xml");
        for (boolean night : new boolean[]{false, true}) {
            Document[] resources = platformColorResources("leanback", night);
            NodeList styles = overlays.getElementsByTagName("style");
            for (int i = 0; i < styles.getLength(); i++) {
                Element overlay = (Element) styles.item(i);
                Map<String, String> roles = directStyleItems(overlay);
                for (String state : new String[]{"focused", "selected", "pressed"}) {
                    String fill = attributeOf(matchingState(backgrounds, Set.of(state)), "solid", "android:color");
                    String text = matchingState(foregrounds, Set.of(state)).getAttribute("android:color");
                    double ratio = contrast(resolveThemeColor(text, roles, resources), resolveThemeColor(fill, roles, resources));
                    assertTrue(overlay.getAttribute("name") + " " + state + " contrast=" + ratio, ratio >= 4.5);
                }
            }
        }
    }

    @Test
    public void tvSelectorsRespectThemeAndFocusPreferencesInFirstMatchStates() throws Exception {
        Document focus = xml("app/src/leanback/res/values/focus_color_overlays.xml");
        Document[] resources = platformColorResources("leanback", true);
        for (String preference : new String[]{"White", "Blue", "Green", "Red", "Amber"}) {
            for (var palette : palettes("leanback").entrySet()) {
                Map<String, String> roles = new LinkedHashMap<>(palette.getValue());
                roles.putAll(directStyleItems(style(focus, "ThemeOverlay.IBox.Focus." + preference)));
                if (palette.getKey().endsWith("HighContrast")) {
                    roles.putAll(directStyleItems(style(xml("app/src/main/res/values/ibox_theme_overlays.xml"),
                            "ThemeOverlay.IBox.Accessibility.HighContrast")));
                    assertEquals("High contrast must retain its accessible focus color", resolveColor("@color/ibox_high_contrast_primary", resources),
                            resolveThemeColor("?attr/focusFrameColor", roles, resources));
                }
                String expectedFocus = resolveThemeColor("?attr/focusFrameColor", roles, resources);
                for (String name : new String[]{"selector_vod", "shape_vod_oval_focused", "selector_ibox_repository_item", "selector_home_category"}) {
                    Document drawable = xml("app/src/leanback/res/drawable/" + name + ".xml");
                    for (Set<String> state : List.of(Set.of("focused"), Set.of("selected", "focused"))) {
                        Element item = name.startsWith("shape_") ? drawable.getDocumentElement() : matchingState(drawable, state);
                        NodeList strokes = item.getElementsByTagName("stroke");
                        Element ring = (Element) strokes.item(strokes.getLength() - 1);
                        assertEquals(name + " ignores " + preference, expectedFocus,
                                resolveThemeColor(ring.getAttribute("android:color"), roles, resources));
                    }
                }
                Document category = xml("app/src/leanback/res/drawable/selector_home_category.xml");
                Document text = xml("app/src/leanback/res/color/selector_home_category_text.xml");
                for (Set<String> state : List.of(Set.of("selected"), Set.of("selected", "focused"))) {
                    String fill = resolveThemeColor(attributeOf(matchingState(category, state), "solid", "android:color"), roles, resources);
                    String foreground = resolveThemeColor(matchingState(text, state).getAttribute("android:color"), roles, resources);
                    assertEquals(palette.getKey(), resolveThemeColor("?attr/colorPrimaryContainer", roles, resources), fill);
                    assertEquals(palette.getKey(), resolveThemeColor("?attr/colorOnPrimaryContainer", roles, resources), foreground);
                    assertTrue(palette.getKey() + " selected category contrast", contrast(foreground, fill) >= 4.5);
                }
            }
        }
    }

    @Test
    public void material3AlertUsesItsConsumedExtraLargeShapeSlot() throws Exception {
        for (String platform : new String[]{"mobile", "leanback"}) {
            Document system = xml(platform.equals("mobile") ? "app/src/main/res/values/ibox_ui_system.xml"
                    : "app/src/leanback/res/values/dialog_styles.xml");
            String shape = styleItem(system, "ThemeOverlay.IBox.Dialog.Alert", "shapeAppearanceCornerExtraLarge");
            String radius = styleItem(system, shape.substring("@style/".length()), "cornerSize");
            assertEquals(platform.equals("mobile") ? "16dp" : "12dp", value(
                    xml("app/src/main/res/values/ibox_ui_system.xml"), "dimen", radius.substring("@dimen/".length())));
        }
    }

    @Test
    public void textNotificationFitsCanonicalBodyAndAccessibleGeometry() throws Exception {
        Element view = xml("app/src/main/res/layout/view_toast_compact.xml").getDocumentElement();
        assertEquals("@style/TextAppearance.IBox.PlayerBody", view.getAttribute("android:textAppearance"));
        assertEquals("@dimen/ibox_touch_target", view.getAttribute("android:minHeight"));
        assertEquals("3", view.getAttribute("android:maxLines"));
        assertEquals("end", view.getAttribute("android:ellipsize"));
        for (String edge : new String[]{"Start", "End", "Top", "Bottom"}) {
            String token = view.getAttribute("android:padding" + edge);
            double padding = dp(xml("app/src/main/res/values/ibox_dimens.xml"), token.substring("@dimen/".length()));
            assertEquals(0, padding % 4, 0);
        }
        Document background = xml("app/src/main/res/drawable/bg_toast_compact.xml");
        assertEquals("@color/ibox_player_control_outline", attribute(background, "stroke", "android:color"));
        for (String platform : new String[]{"mobile", "leanback"}) {
            Document dimensions = xml(platform.equals("mobile") ? "app/src/main/res/values/ibox_dimens.xml"
                    : "app/src/leanback/res/values/tv_dimens.xml");
            assertEquals(0, dp(dimensions, "ibox_notification_max_width") % 4, 0);
            assertEquals(0, dp(dimensions, "ibox_notification_bottom_offset") % 4, 0);
        }
    }

    private static String attributeOf(Element element, String tag, String attribute) {
        return ((Element) element.getElementsByTagName(tag).item(0)).getAttribute(attribute);
    }

    private static Map<String, Map<String, String>> palettes(String platform) throws Exception {
        Map<String, String> base = directStyleItems(style(xml("app/src/main/res/values/ibox_styles.xml"),
                platform.equals("mobile") ? "Theme.IBox.Mobile" : "Theme.IBox.TV"));
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        result.put(platform + " default", base);
        for (String source : new String[]{platform.equals("mobile") ? "app/src/main/res/values/ibox_theme_overlays.xml"
                : "app/src/leanback/res/values/theme_color_overlays.xml", "app/src/main/res/values/ibox_theme_overlays.xml"}) {
            NodeList styles = xml(source).getElementsByTagName("style");
            for (int i = 0; i < styles.getLength(); i++) {
                Element overlay = (Element) styles.item(i);
                Map<String, String> direct = directStyleItems(overlay);
                if (!direct.containsKey("colorPrimary")) continue;
                Map<String, String> roles = new LinkedHashMap<>(base);
                roles.putAll(direct);
                result.putIfAbsent(overlay.getAttribute("name"), roles);
            }
        }
        return result;
    }

    private static Element matchingState(Document selector, Set<String> active) {
        NodeList items = selector.getDocumentElement().getChildNodes();
        for (int i = 0; i < items.getLength(); i++) {
            if (!(items.item(i) instanceof Element)) continue;
            Element item = (Element) items.item(i);
            boolean matches = true;
            for (int j = 0; j < item.getAttributes().getLength(); j++) {
                var attribute = item.getAttributes().item(j);
                if (!attribute.getNodeName().startsWith("android:state_")) continue;
                String state = attribute.getNodeName().substring("android:state_".length());
                matches &= active.contains(state) == Boolean.parseBoolean(attribute.getNodeValue());
            }
            if (matches) return item;
        }
        throw new AssertionError("Missing selector state " + active);
    }

    private static String resolveThemeColor(String reference, Map<String, String> roles, Document... resources) {
        return resolveColor(reference.startsWith("?attr/")
                ? roles.get(reference.substring("?attr/".length())) : reference, resources);
    }

    private static void assertNavigationPairContrast(String name, String foregroundRole, String backgroundRole,
                                                     Map<String, String> roles, Document... resources) {
        String foreground = roles.get(foregroundRole.substring("?attr/".length()));
        String background = roles.get(backgroundRole.substring("?attr/".length()));
        assertTrue(name + " selected navigation contrast is below 4.5:1",
                contrast(resolveColor(foreground, resources), resolveColor(background, resources)) >= 4.5);
    }

    private static double dp(Document dimens, String name) {
        return Double.parseDouble(value(dimens, "dimen", name).replace("dp", ""));
    }

    @Test
    public void sharedMobileSheetHeaderHasExactlyOneHorizontalInset() throws Exception {
        Document header = xml("app/src/mobile/res/layout/view_sheet_header.xml");
        NodeList elements = header.getElementsByTagName("androidx.appcompat.widget.LinearLayoutCompat");
        for (int i = 0; i < elements.getLength(); i++) {
            assertFalse("Header must defer horizontal inset to its caller", ((Element) elements.item(i)).hasAttribute("android:paddingStart"));
            assertFalse("Header must defer horizontal inset to its caller", ((Element) elements.item(i)).hasAttribute("android:paddingEnd"));
        }
        for (String name : new String[]{"dialog_live", "dialog_history", "dialog_site"}) {
            Element include = view(xml("app/src/mobile/res/layout/" + name + ".xml"), "header");
            assertEquals(name, "@dimen/ibox_dialog_content_padding", include.getAttribute("android:layout_marginStart"));
            assertEquals(name, "@dimen/ibox_dialog_content_padding", include.getAttribute("android:layout_marginEnd"));
        }
        for (String name : new String[]{"dialog_config", "dialog_repository", "dialog_link", "dialog_ua"}) {
            Element include = view(xml("app/src/mobile/res/layout/" + name + ".xml"), "header");
            assertFalse(name + " doubles its caller's inset", include.hasAttribute("android:layout_marginStart"));
            assertFalse(name + " doubles its caller's inset", include.hasAttribute("android:layout_marginEnd"));
        }
    }

    @Test
    public void sharedInfoAndLoadingSurfacesUsePlatformGeometryAndTypography() throws Exception {
        Document info = xml("app/src/main/res/layout/dialog_info.xml");
        assertEquals("@dimen/ibox_dialog_content_padding", attribute(info,
                "androidx.appcompat.widget.LinearLayoutCompat", "android:padding"));
        assertEquals("@style/TextAppearance.IBox.DialogTitle", attribute(info,
                "com.google.android.material.textview.MaterialTextView", "android:textAppearance"));
        for (String id : new String[]{"share", "copy"}) {
            assertEquals("@dimen/ibox_touch_target", view(info, id).getAttribute("android:layout_height"));
        }
        for (String id : new String[]{"eventsGroup", "urlGroup"}) {
            assertEquals("@dimen/ibox_dialog_group_spacing", view(info, id).getAttribute("android:layout_marginTop"));
        }
        assertEquals("1", view(info, "mediaTitle").getAttribute("android:maxLines"));
        Document detail = xml("app/src/main/res/layout/activity_vod_detail.xml");
        assertEquals("@color/ibox_surface_container", detail.getDocumentElement().getAttribute("android:background"));
        for (String name : new String[]{"activity_vod_detail", "dialog_info", "view_toast_compact"}) {
            NodeList elements = xml("app/src/main/res/layout/" + name + ".xml").getElementsByTagName("*");
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                assertSemanticVisualAttributes(name, element);
                if (element.getTagName().endsWith("TextView")) {
                    assertTrue(name + " needs canonical typography",
                            element.getAttribute("android:textAppearance").startsWith("@style/TextAppearance.IBox.")
                            || element.getAttribute("android:textSize").startsWith("@dimen/ibox_text_"));
                }
            }
        }
    }

    @Test
    public void artworkMetadataAndToastKeepReadableSemanticScrims() throws Exception {
        Document base = xml("app/src/main/res/values/ibox_colors.xml");
        for (Document palette : new Document[]{base,
                xml("app/src/main/res/values-night/ibox_colors.xml"),
                xml("app/src/leanback/res/values/ibox_tv_colors.xml")}) {
            for (String name : new String[]{"shape_vod_remark", "shape_vod_site", "bg_toast_compact"}) {
                Document drawable = xml("app/src/main/res/drawable/" + name + ".xml");
                String fill = attribute(drawable, "solid", "android:color");
                assertTrue(name + " must use the semantic artwork/player palette", fill.startsWith("@color/ibox_player_"));
                assertTrue(name + " needs a shared radius", attribute(drawable, "corners", "android:radius").startsWith("@dimen/ibox_radius_"));
                for (String artwork : new String[]{"#FFFFFF", "#000000"}) {
                    String background = compositeColor(resolveColor(fill, palette, base), artwork);
                    assertTrue(name + " loses text on " + artwork,
                            contrast(resolveColor("@color/ibox_player_text_primary", palette, base), background) >= 4.5);
                }
            }
        }
    }

    @Test
    public void mobilePosterBadgesUseThemeMatchedContainerPairs() throws Exception {
        String[][] badges = {
                {"site", "colorErrorContainer", "colorOnErrorContainer"},
                {"remark", "colorSecondaryContainer", "colorOnSecondaryContainer"}
        };
        for (String[] badge : badges) {
            String name = badge[0];
            Document background = xml("app/src/mobile/res/drawable/shape_vod_" + name + ".xml");
            assertEquals("@color/bg_" + name, attribute(background, "solid", "android:color"));

            Element backgroundItem = (Element) xml("app/src/mobile/res/color/bg_" + name + ".xml")
                    .getElementsByTagName("item").item(0);
            Element textItem = (Element) xml("app/src/mobile/res/color/text_" + name + ".xml")
                    .getElementsByTagName("item").item(0);
            assertEquals("?attr/" + badge[1], backgroundItem.getAttribute("android:color"));
            assertFalse("Poster badges need an opaque container over arbitrary artwork",
                    backgroundItem.hasAttribute("android:alpha"));
            assertEquals("?attr/" + badge[2], textItem.getAttribute("android:color"));

            for (boolean night : new boolean[]{false, true}) {
                Document[] resources = mobileColorResources(night);
                for (var palette : palettes("mobile").entrySet()) {
                    double ratio = contrast(
                            resolveThemeColor(textItem.getAttribute("android:color"), palette.getValue(), resources),
                            resolveThemeColor(backgroundItem.getAttribute("android:color"), palette.getValue(), resources));
                    assertTrue(name + " " + palette.getKey() + " night=" + night
                            + " contrast=" + ratio + " below 4.5:1", ratio >= 4.5);
                }
            }
        }
        assertEquals("@color/text_site", view(xml("app/src/mobile/res/layout/adapter_search.xml"),
                "site").getAttribute("android:textColor"));
        assertEquals("@color/text_site", view(xml("app/src/mobile/res/layout/adapter_vod.xml"),
                "site").getAttribute("android:textColor"));
        assertEquals("@color/text_remark", view(xml("app/src/mobile/res/layout/adapter_vod.xml"),
                "remark").getAttribute("android:textColor"));
    }

    @Test
    public void mobileLibraryActionsMatchHomeIconColorAndSize() throws Exception {
        for (String screen : new String[]{"keep", "history"}) {
            Document layout = xml("app/src/mobile/res/layout/activity_" + screen + ".xml");
            Element toolbar = view(layout, "toolbar");
            assertEquals("@menu/menu_" + screen, toolbar.getAttribute("app:menu"));
            Document menu = xml("app/src/mobile/res/menu/menu_" + screen + ".xml");
            assertEquals("@drawable/ic_action_sync", view(menu, "sync").getAttribute("android:icon"));
            assertEquals("@drawable/ic_action_delete", view(menu, "delete").getAttribute("android:icon"));
            assertEquals("@color/ibox_text_primary", view(menu, "sync").getAttribute("app:iconTint"));
            assertEquals("@color/ibox_text_primary", view(menu, "delete").getAttribute("app:iconTint"));
        }
        for (String icon : new String[]{"ic_action_sync", "ic_action_delete"}) {
            Element vector = xml("app/src/mobile/res/drawable/" + icon + ".xml").getDocumentElement();
            assertEquals("30dp", vector.getAttribute("android:width"));
            assertEquals("30dp", vector.getAttribute("android:height"));
            assertEquals("960", vector.getAttribute("android:viewportWidth"));
            assertEquals("@android:color/white", ((Element) vector.getElementsByTagName("path").item(0))
                    .getAttribute("android:fillColor"));
        }
    }

    @Test
    public void liveSettingsUsesCanonicalDialogSpacingAndDisablesListAnimations() throws Exception {
        Document layout = xml("app/src/main/res/layout/dialog_live_settings.xml");
        assertEquals("@dimen/ibox_dialog_content_padding",
                layout.getDocumentElement().getAttribute("android:padding"));
        assertEquals("@style/TextAppearance.IBox.DialogTitle", attribute(layout,
                "com.google.android.material.textview.MaterialTextView", "android:textAppearance"));
    }

    @Test
    public void liveSettingRowsKeepSingleLineTitlesAndACompactCurrentIndicator() throws Exception {
        for (String[] variant : new String[][]{{"main", "15sp"}, {"leanback", "18sp"}}) {
            Document row = xml("app/src/" + variant[0] + "/res/layout/adapter_live_setting_action.xml");
            Element title = view(row, "title");
            assertEquals("1", title.getAttribute("android:maxLines"));
            assertEquals("end", title.getAttribute("android:ellipsize"));
            assertEquals(variant[1], title.getAttribute("android:textSize"));
            Element summary = view(row, "summary");
            assertEquals("1", summary.getAttribute("android:maxLines"));
            assertEquals("end", summary.getAttribute("android:ellipsize"));
            assertEquals("@string/live_menu_current", view(row, "current")
                    .getAttribute("android:contentDescription"));
        }
        Document category = xml("app/src/leanback/res/layout/adapter_live_setting_category.xml");
        Element categoryTitle = view(category, "title");
        assertEquals("1", categoryTitle.getAttribute("android:maxLines"));
        assertEquals("end", categoryTitle.getAttribute("android:ellipsize"));
        assertEquals("17sp", categoryTitle.getAttribute("android:textSize"));
    }

    @Test
    public void bothPlayerSettingsExposeTheAutoNextChoice() throws Exception {
        for (String path : new String[]{
                "app/src/leanback/res/layout/activity_setting_player.xml",
                "app/src/mobile/res/layout/fragment_setting_player.xml"}) {
            Document layout = xml(path);
            assertEquals("@string/player_auto_next", view(layout, "autoNextLabel")
                    .getAttribute("android:text"));
            view(layout, "autoNext");
            view(layout, "autoNextText");
        }
    }

    @Test
    public void bothPlayerSettingsExposeIndependentAudioAndSubtitleLanguages() throws Exception {
        for (String[] variant : new String[][]{
                {"leanback", "activity_setting_player.xml", "16sp"},
                {"mobile", "fragment_setting_player.xml", "@dimen/ibox_text_body"}}) {
            Document layout = xml("app/src/" + variant[0] + "/res/layout/" + variant[1]);
            view(layout, "audioLanguage");
            view(layout, "textLanguage");
            assertEquals("@string/player_audio_language", view(layout, "audioLanguageLabel").getAttribute("android:text"));
            assertEquals("@string/player_text_language", view(layout, "textLanguageLabel").getAttribute("android:text"));
            assertEquals(variant[2], view(layout, "audioLanguageText").getAttribute("android:textSize"));
            assertEquals(variant[2], view(layout, "textLanguageText").getAttribute("android:textSize"));
        }
    }

    @Test
    public void everyDialogPresentationUsesTheSameFadeMotion() throws Exception {
        Document system = xml("app/src/main/res/values/ibox_ui_system.xml");
        for (String style : new String[]{"Animation.IBox.Dialog.Alert",
                "Animation.IBox.Dialog.BottomSheet", "Animation.IBox.Dialog.SideSheet"}) {
            assertEquals(style, "@anim/ibox_dialog_enter",
                    styleItem(system, style, "android:windowEnterAnimation"));
            assertEquals(style, "@anim/ibox_dialog_exit",
                    styleItem(system, style, "android:windowExitAnimation"));
        }
        for (String name : new String[]{"ibox_dialog_enter", "ibox_dialog_exit"}) {
            Document motion = xml("app/src/main/res/anim/" + name + ".xml");
            assertEquals(name + " must be a pure fade", 1, motion.getElementsByTagName("alpha").getLength());
            assertEquals(name + " must not scale the dialog", 0, motion.getElementsByTagName("scale").getLength());
            assertEquals(name + " must not slide the dialog", 0, motion.getElementsByTagName("translate").getLength());
        }
    }

    @Test
    public void everyDialogBaseDisablesNestedListChangeAnimations() throws Exception {
        String helper = Files.readString(repoRoot().resolve(
                "app/src/main/java/com/imedia/box/ui/dialog/DialogMotion.java"));
        assertTrue("Dialog motion policy must cover nested RecyclerViews",
                helper.contains("instanceof RecyclerView"));
        assertTrue("Dialog motion policy must disable item change animations",
                helper.contains("setItemAnimator(null)"));
        for (String base : new String[]{"BaseAlertDialog", "BaseBottomSheetDialog", "BaseSideSheetDialog"}) {
            String source = Files.readString(repoRoot().resolve(
                    "app/src/main/java/com/imedia/box/ui/dialog/" + base + ".java"));
            assertTrue(base + " must apply the shared content motion policy",
                    source.contains("DialogMotion.disableListAnimations"));
        }
    }

    @Test
    public void delayedBottomSheetLayoutDoesNotDependOnFragmentResources() throws Exception {
        String source = Files.readString(repoRoot().resolve(
                "app/src/main/java/com/imedia/box/ui/dialog/BaseBottomSheetDialog.java"));
        assertFalse("A delayed dialog callback can run after the Fragment is detached",
                Pattern.compile("(?<![.\\w])getResources\\s*\\(").matcher(source).find());
        assertTrue("Destroying the Fragment view must release its delayed dialog listener",
                source.contains("dialog.setOnShowListener(null)"));
    }

    @Test
    public void mobileFormerBottomSheetsUseAStableCenteredWindow() throws Exception {
        String source = Files.readString(repoRoot().resolve(
                "app/src/main/java/com/imedia/box/ui/dialog/BaseBottomSheetDialog.java"));
        assertTrue("Mobile dialogs must use a centered dialog with the activity-specific theme",
                source.contains("new AppCompatDialog(requireContext(), IBoxAlertDialogBuilder.themeFor("));
        assertTrue("Mobile dialogs must be centered before they are shown",
                source.contains("window.setGravity(Gravity.CENTER)"));
        assertFalse("Post-layout sheet offsets make dialogs jump when content changes",
                source.contains("addOnLayoutChangeListener"));
        assertFalse("Mobile dialogs must not be repositioned after drawing",
                source.contains("offsetTopAndBottom"));
    }

    @Test
    public void liveAlertThemeComesFromTheActivityTheme() throws Exception {
        Document manifest = xml("app/src/mobile/AndroidManifest.xml");
        NodeList activities = manifest.getElementsByTagName("activity");
        boolean themed = false;
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (!".ui.activity.LiveActivity".equals(activity.getAttribute("android:name"))) continue;
            themed = "@style/Theme.Live".equals(activity.getAttribute("android:theme"));
        }
        assertTrue("LiveActivity must declare its own dialog theme", themed);

        Document styles = xml("app/src/mobile/res/values/styles.xml");
        NodeList themes = styles.getElementsByTagName("style");
        boolean liveAlert = false;
        for (int i = 0; i < themes.getLength(); i++) {
            Element theme = (Element) themes.item(i);
            if (!"Theme.Live".equals(theme.getAttribute("name"))) continue;
            NodeList items = theme.getElementsByTagName("item");
            for (int j = 0; j < items.getLength(); j++) {
                Element item = (Element) items.item(j);
                liveAlert |= "materialAlertDialogTheme".equals(item.getAttribute("name"))
                        && "@style/ThemeOverlay.IBox.Dialog.LiveAlert".equals(item.getTextContent().trim());
            }
        }
        assertTrue("Live theme must select the live dialog overlay", liveAlert);

        String builder = Files.readString(repoRoot().resolve(
                "app/src/main/java/com/imedia/box/ui/custom/IBoxAlertDialogBuilder.java"));
        assertFalse("Shared builder must not depend on LiveActivity", builder.contains("LiveActivity"));
        assertTrue("Shared builder must resolve the theme attribute", builder.contains("materialAlertDialogTheme"));
    }

    @Test
    public void documentedPaletteRolesResolveInEveryPlatformPalette() throws Exception {
        String documentation = Files.readString(repoRoot().resolve("docs/UI_DESIGN_SYSTEM.md"));
        Set<String> roles = Set.of("ibox_primary", "ibox_on_primary", "ibox_primary_container",
                "ibox_on_primary_container", "ibox_surface", "ibox_surface_container",
                "ibox_surface_variant", "ibox_text_primary", "ibox_text_secondary", "ibox_content_accent",
                "ibox_outline", "ibox_outline_strong", "ibox_focus_surface", "ibox_focus_outline",
                "ibox_player_surface", "ibox_player_scrim", "ibox_player_text_primary",
                "ibox_player_text_secondary", "ibox_player_control_surface",
                "ibox_player_control_surface_focused", "ibox_player_control_outline");
        Document base = xml("app/src/main/res/values/ibox_colors.xml");
        for (String role : roles) {
            assertTrue("Required palette role is undocumented: " + role, documentation.contains(role));
            for (Document palette : new Document[]{base,
                    xml("app/src/main/res/values-night/ibox_colors.xml"),
                    xml("app/src/leanback/res/values/ibox_tv_colors.xml")}) {
                assertSemanticColorReference("@color/" + role, palette, base);
            }
        }
    }

    private static String compositeColor(String foreground, String background) {
        if (foreground.length() == 7) return foreground;
        long rgba = Long.parseLong(foreground.substring(1), 16);
        int rgb = Integer.parseInt(background.substring(1), 16);
        double alpha = ((rgba >> 24) & 0xFF) / 255.0;
        int result = 0;
        for (int shift : new int[]{16, 8, 0}) {
            int channel = (int) Math.round(((rgba >> shift) & 0xFF) * alpha
                    + ((rgb >> shift) & 0xFF) * (1 - alpha));
            result |= channel << shift;
        }
        return String.format("#%06X", result);
    }

    private static Element view(Document document, String id) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (("@+id/" + id).equals(element.getAttribute("android:id"))) return element;
        }
        throw new AssertionError("Missing view " + id);
    }

    private static void assertSemanticVisualAttributes(String name, Element element) {
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            String attribute = element.getAttributes().item(i).getNodeValue();
            assertFalse(name + " has a literal color", attribute.matches("#[0-9A-Fa-f]{3,8}"));
        }
    }

    private static void assertPlatformLayoutsUseTokenizedTypographyAndColors(String relativeDirectory)
            throws Exception {
        try (Stream<Path> paths = Files.walk(repoRoot().resolve(relativeDirectory))) {
            var iterator = paths.filter(path -> path.toString().endsWith(".xml")).iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                String relativePath = repoRoot().relativize(path).toString().replace('\\', '/');
                Document layout = xml(relativePath);
                NodeList elements = layout.getElementsByTagName("*");
                for (int i = 0; i < elements.getLength(); i++) {
                    Element element = (Element) elements.item(i);
                    assertLayoutTextSizeIsCanonical(relativePath, element);
                    assertLayoutColorsAreSemantic(relativePath, element);
                }
            }
        }
    }

    private static void assertLayoutTextSizeIsCanonical(String path, Element element) {
        String textSize = element.getAttribute("android:textSize");
        if (!textSize.matches("[0-9]+(?:\\.[0-9]+)?sp")) return;
        double size = Double.parseDouble(textSize.substring(0, textSize.length() - 2));
        assertTrue(path + " uses a literal text size below 12sp: " + textSize, size >= 12.0);
    }

    private static void assertLayoutColorsAreSemantic(String path, Element element) {
        String[] visualColorAttributes = {"android:background", "android:foreground", "android:textColor",
                "android:textColorHint", "android:tint", "android:color"};
        for (String attribute : visualColorAttributes) {
            String color = element.getAttribute(attribute);
            assertFalse(path + " uses a literal visual color in " + attribute + ": " + color,
                    color.matches("#(?:[0-9A-Fa-f]{3,4}|[0-9A-Fa-f]{6,8})"));
        }
    }

    private static void assertAllTextAppearancesHaveZeroLetterSpacing(Document styles) {
        NodeList elements = styles.getElementsByTagName("style");
        for (int i = 0; i < elements.getLength(); i++) {
            Element style = (Element) elements.item(i);
            String name = style.getAttribute("name");
            if (name.startsWith("TextAppearance.IBox.")) {
                assertEquals(name + " must explicitly reset letter spacing", "0",
                        directStyleItems(style).get("android:letterSpacing"));
            }
        }
    }

    private static void assertDialogTheme(Document styles, String themeName, String shapeItem) {
        assertEquals("@color/ibox_surface", styleItem(styles, themeName, "colorSurface"));
        assertEquals("@color/ibox_text_primary", styleItem(styles, themeName, "colorOnSurface"));
        assertEquals("@color/ibox_outline", styleItem(styles, themeName, "colorOutline"));
        assertEquals("@color/ibox_error", styleItem(styles, themeName, "colorError"));
        assertTrue(themeName + " must dim the window", styleItem(styles, themeName,
                "android:backgroundDimAmount").matches("0\\.[0-9]+"));
        assertTrue(themeName + " must define a shape appearance",
                styleItem(styles, themeName, shapeItem).startsWith("@style/ShapeAppearance.IBox."));
    }

    private static void assertAlertDialogTheme(Document styles, String shapeItem) {
        String themeName = "ThemeOverlay.IBox.Dialog.Alert";
        Map<String, String> items = directStyleItems(style(styles, themeName));
        for (String inherited : new String[]{"colorSurface", "colorOnSurface", "colorOnSurfaceVariant", "colorOutline"}) {
            assertFalse(themeName + " must inherit caller role " + inherited, items.containsKey(inherited));
        }
        assertEquals("@color/ibox_error", items.get("colorError"));
        assertTrue(themeName + " must dim the window", items.get("android:backgroundDimAmount").matches("0\\.[0-9]+"));
        assertTrue(themeName + " must define a shape appearance",
                items.get(shapeItem).startsWith("@style/ShapeAppearance.IBox."));
    }

    private static void assertTonalColorOverlays(Document document, Document... colorResources) {
        NodeList styles = document.getElementsByTagName("style");
        for (int i = 0; i < styles.getLength(); i++) {
            Element style = (Element) styles.item(i);
            String name = style.getAttribute("name");
            if (!name.startsWith("ThemeOverlay.IBox.Color.")
                    || name.endsWith(".Base") || name.endsWith(".DarkContent")) continue;

            Map<String, String> roles = directStyleItems(style);
            assertTrue(name + " is missing colorPrimary", roles.containsKey("colorPrimary"));
            assertTrue(name + " is missing colorOnPrimary", roles.containsKey("colorOnPrimary"));
            assertTrue(name + " is missing colorPrimaryContainer", roles.containsKey("colorPrimaryContainer"));
            assertTrue(name + " is missing colorOnPrimaryContainer", roles.containsKey("colorOnPrimaryContainer"));
            assertTrue(name + " primary text contrast is below 4.5:1",
                    contrast(resolveColor(roles.get("colorOnPrimary"), colorResources),
                            resolveColor(roles.get("colorPrimary"), colorResources)) >= 4.5);
            assertTrue(name + " primary container text contrast is below 4.5:1",
                    contrast(resolveColor(roles.get("colorOnPrimaryContainer"), colorResources),
                            resolveColor(roles.get("colorPrimaryContainer"), colorResources)) >= 4.5);
            assertFalse(name + " must distinguish primary and primary container",
                    resolveColor(roles.get("colorPrimary"), colorResources)
                            .equals(resolveColor(roles.get("colorPrimaryContainer"), colorResources)));
            assertTrue(name + " is missing colorSecondaryContainer", roles.containsKey("colorSecondaryContainer"));
            assertFalse(name + " must reserve secondary container for low emphasis",
                    resolveColor(roles.get("colorPrimary"), colorResources)
                            .equals(resolveColor(roles.get("colorSecondaryContainer"), colorResources)));
        }
    }

    private static void assertSecondaryContrast(Document overlays, Document... colorResources) {
        NodeList styles = overlays.getElementsByTagName("style");
        for (int i = 0; i < styles.getLength(); i++) {
            Element style = (Element) styles.item(i);
            if (!style.getAttribute("name").startsWith("ThemeOverlay.IBox.Color.")
                    || style.getAttribute("name").endsWith(".Base") || style.getAttribute("name").endsWith(".DarkContent")) continue;
            Map<String, String> roles = directStyleItems(style);
            assertTrue(style.getAttribute("name") + " is missing colorSecondary", roles.containsKey("colorSecondary"));
            assertTrue(style.getAttribute("name") + " is missing colorOnSecondary", roles.containsKey("colorOnSecondary"));
            assertTrue(style.getAttribute("name") + " secondary text contrast is below 4.5:1",
                    contrast(resolveColor(roles.get("colorOnSecondary"), colorResources),
                            resolveColor(roles.get("colorSecondary"), colorResources)) >= 4.5);
        }
    }

    private static Map<String, String> directStyleItems(Element style) {
        Map<String, String> items = new LinkedHashMap<>();
        NodeList children = style.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element)) continue;
            Element item = (Element) children.item(i);
            if ("item".equals(item.getTagName())) items.put(item.getAttribute("name"), item.getTextContent().trim());
        }
        return items;
    }

    private static void assertSemanticColorReference(String value, Document... colorResources) {
        assertTrue("Expected named color resource, got " + value, value.startsWith("@color/"));
        assertTrue("Expected resolved literal color for " + value + ", got " + resolveColor(value, colorResources),
                resolveColor(value, colorResources).matches("#[0-9A-Fa-f]{6,8}"));
    }

    private static String resolveColor(String value, Document... colorResources) {
        String resolved = value;
        Set<String> seen = new HashSet<>();
        while (resolved.startsWith("@color/")) {
            String name = resolved.substring("@color/".length());
            assertTrue("Cyclic color reference " + resolved, seen.add(name));
            boolean found = false;
            for (Document resource : colorResources) {
                try {
                    resolved = value(resource, "color", name);
                    found = true;
                    break;
                } catch (AssertionError ignored) {
                    // The resource may be defined by the next source set.
                }
            }
            assertTrue("Unresolved color reference " + resolved, found);
        }
        assertTrue("Expected terminal literal color, got " + resolved, resolved.matches("#[0-9A-Fa-f]{6,8}"));
        return resolved;
    }

    private static void assertResolutionFails(String value, Document... colorResources) {
        boolean failed = false;
        try {
            resolveColor(value, colorResources);
        } catch (AssertionError expected) {
            failed = true;
        }
        assertTrue("Expected color resolution to fail for " + value, failed);
    }

    private static Document xmlText(String source) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(source)));
    }

    private static Element style(Document document, String styleName) {
        NodeList styles = document.getElementsByTagName("style");
        for (int i = 0; i < styles.getLength(); i++) {
            Element style = (Element) styles.item(i);
            if (styleName.equals(style.getAttribute("name"))) return style;
        }
        throw new AssertionError("Missing style " + styleName);
    }

    private static String styleItem(Document document, String styleName, String itemName) {
        NodeList styles = document.getElementsByTagName("style");
        for (int i = 0; i < styles.getLength(); i++) {
            Element style = (Element) styles.item(i);
            if (!styleName.equals(style.getAttribute("name"))) continue;
            NodeList items = style.getElementsByTagName("item");
            for (int j = 0; j < items.getLength(); j++) {
                Element item = (Element) items.item(j);
                if (itemName.equals(item.getAttribute("name"))) return item.getTextContent().trim();
            }
        }
        throw new AssertionError("Missing style item " + styleName + "." + itemName);
    }

    private static void assertPalette(String path, String background, String text, String supporting,
                                      String primary, String onPrimary) throws Exception {
        Document source = xml(path);
        assertEquals(background, color(source, "ibox_surface_container"));
        assertEquals(text, color(source, "ibox_text_primary"));
        assertEquals(supporting, color(source, "ibox_text_secondary"));
        assertEquals(primary, color(source, "ibox_primary"));
        assertEquals(onPrimary, color(source, "ibox_on_primary"));
        assertTrue(contrast(text, background) >= 4.5);
        assertTrue(contrast(supporting, background) >= 4.5);
        assertTrue(contrast(onPrimary, primary) >= 4.5);
        assertTrue(contrast(color(source, "ibox_outline"), background) >= 3.0);
    }
}
