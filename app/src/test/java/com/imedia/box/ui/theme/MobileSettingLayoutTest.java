package com.imedia.box.ui.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class MobileSettingLayoutTest {

    private static Path root() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("app/src/mobile/res/layout/fragment_setting.xml"))) {
            path = path.getParent();
        }
        if (path == null) throw new AssertionError("Repository root not found");
        return path;
    }

    private static Document layout() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(root().resolve("app/src/mobile/res/layout/fragment_setting.xml").toFile());
    }

    private static List<Element> children(Element parent) {
        List<Element> elements = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element) elements.add((Element) node);
        }
        return elements;
    }

    private static Element byId(Document document, String id) {
        NodeList nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (("@+id/" + id).equals(element.getAttribute("android:id"))) return element;
        }
        throw new AssertionError("Missing setting row " + id);
    }

    @Test
    public void entriesRemainAvailableInPurposefulSections() throws Exception {
        Element content = byId(layout(), "settingsContent");
        List<String> headings = new ArrayList<>();
        List<List<String>> groups = new ArrayList<>();
        for (Element child : children(content)) {
            String heading = child.getAttribute("android:text");
            if (heading.startsWith("@string/settings_section_")) headings.add(heading);
            if (!"@drawable/bg_setting_group".equals(child.getAttribute("android:background"))
                    || !child.getAttribute("android:id").isEmpty()) continue;
            List<String> ids = new ArrayList<>();
            for (Element row : children(child)) {
                String id = row.getAttribute("android:id");
                if (id.startsWith("@+id/")) ids.add(id.substring(5));
            }
            groups.add(ids);
        }
        assertEquals(Arrays.asList("@string/settings_section_sources", "@string/settings_section_general",
                "@string/settings_section_appearance", "@string/settings_section_playback",
                "@string/settings_section_data", "@string/settings_section_about"), headings);
        assertEquals(Arrays.asList(
                Arrays.asList("vod", "live", "wall"),
                Arrays.asList("incognito", "configNotice", "remoteAccess", "hideNavigation", "bootAutoStart"),
                Arrays.asList("themeMode", "layoutMode", "themeColor", "homeContent", "size", "accessibility"),
                Arrays.asList("player", "danmaku", "autoFallbackLine", "autoFallbackSource"),
                Arrays.asList("githubAcceleration", "doh", "searchThreads", "sourceHealth", "cache", "webdav", "backup", "restore")), groups);
    }

    @Test
    public void leadingIconsUseConsistentSizeAndNeutralTint() throws Exception {
        Document document = layout();
        for (String id : Arrays.asList("vod", "live", "wall", "incognito", "configNotice",
                "remoteAccess", "hideNavigation", "bootAutoStart", "themeMode", "layoutMode",
                "themeColor", "homeContent", "size", "accessibility", "player", "danmaku",
                "autoFallbackLine", "autoFallbackSource", "githubAcceleration", "doh",
                "searchThreads", "sourceHealth", "cache", "webdav", "backup", "restore")) {
            Element icon = (Element) byId(document, id).getElementsByTagName("androidx.appcompat.widget.AppCompatImageView").item(0);
            assertEquals(id + " icon width", "24dp", icon.getAttribute("android:layout_width"));
            assertEquals(id + " icon height", "24dp", icon.getAttribute("android:layout_height"));
            assertEquals(id + " icon tint", "@color/ibox_text_secondary", icon.getAttribute("app:tint"));
        }
    }

    @Test
    public void privacyThemeAndPlaybackIconsDescribeTheirActions() throws Exception {
        Document document = layout();
        String[][] icons = {
                {"incognito", "ic_msr_lock"}, {"configNotice", "ic_msr_info"},
                {"remoteAccess", "ic_msr_link"}, {"hideNavigation", "ic_setting_navigation"},
                {"themeMode", "ic_setting_theme_mode"},
                {"themeColor", "ic_setting_palette"}, {"homeContent", "ic_msr_home"},
                {"player", "ic_msr_tune"}
        };
        for (String[] item : icons) {
            Element icon = (Element) byId(document, item[0]).getElementsByTagName("androidx.appcompat.widget.AppCompatImageView").item(0);
            assertEquals(item[0], "@drawable/" + item[1], icon.getAttribute("android:src"));
        }
        for (String icon : Arrays.asList("ic_setting_navigation", "ic_setting_theme_mode", "ic_setting_palette")) {
            assertTrue(Files.exists(root().resolve("app/src/mobile/res/drawable/" + icon + ".xml")));
        }
    }
}
