package com.media.ibox.ui.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 设置页信息架构的守卫：首页只放入口，具体开关留在各自的二级页面。
 */
public class MobileSettingLayoutTest {

    private static Path root() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("app/src/mobile/res/layout/fragment_setting.xml"))) {
            path = path.getParent();
        }
        if (path == null) throw new AssertionError("Repository root not found");
        return path;
    }

    private static Document layout(String name) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(root().resolve("app/src/mobile/res/layout/" + name).toFile());
    }

    private static List<Element> all(Document document) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) elements.add((Element) nodes.item(i));
        return elements;
    }

    private static Set<String> ids(Document document) {
        Set<String> ids = new LinkedHashSet<>();
        for (Element element : all(document)) {
            String id = element.getAttribute("android:id");
            if (id.startsWith("@+id/")) ids.add(id.substring(5));
        }
        return ids;
    }

    private static Element byId(Document document, String id) {
        for (Element element : all(document)) {
            if (("@+id/" + id).equals(element.getAttribute("android:id"))) return element;
        }
        throw new AssertionError("Missing setting row " + id);
    }

    @Test
    public void indexPageOnlyExposesEntryPoints() throws Exception {
        Set<String> ids = ids(layout("fragment_setting.xml"));
        assertTrue(ids.containsAll(Arrays.asList("rowSource", "rowGeneral", "rowAppearance", "rowPlayback", "rowData", "rowVersion")));
        for (String legacy : Arrays.asList("vod", "live", "wall", "incognito", "configNotice", "themeMode",
                "layoutMode", "themeColor", "homeContent", "size", "accessibility", "player", "danmaku",
                "autoFallbackLine", "autoFallbackSource", "githubAcceleration", "doh", "searchThreads",
                "sourceHealth", "cache", "webdav", "backup", "restore")) {
            assertFalse("首页不应再平铺 " + legacy, ids.contains(legacy));
        }
    }

    @Test
    public void everyEntryHasItsOwnSecondLevelPage() throws Exception {
        assertRows("fragment_setting_source.xml", "vod", "live", "rowWall");
        assertRows("fragment_setting_general.xml", "rowIncognito", "rowConfigNotice", "rowRemoteAccess",
                "rowHideNavigation", "rowBootAutoStart");
        assertRows("fragment_setting_appearance.xml", "rowThemeMode", "rowLayoutMode", "rowThemeColor",
                "rowHomeContent", "rowSize", "rowAccessibility");
        assertRows("fragment_setting_playback.xml", "rowPlayer", "rowDanmaku", "rowAutoFallbackLine",
                "rowAutoFallbackSource");
        assertRows("fragment_setting_data.xml", "rowGithubAcceleration", "rowGithubAccelerators", "rowDoh",
                "rowSearchThreads", "rowSourceHealth", "rowCache", "rowWebdav", "rowBackup", "rowRestore");
    }

    private void assertRows(String page, String... expected) throws Exception {
        Document document = layout(page);
        Set<String> ids = ids(document);
        for (String id : expected) assertTrue(page + " 缺少 " + id, ids.contains(id));
        assertTrue(page + " 缺少返回入口", ids.contains("toolbar"));
        Element toolbar = byId(document, "toolbar");
        assertEquals(page + " 标题样式", "@style/Widget.IBox.Toolbar", toolbar.getAttribute("style"));
    }

    @Test
    public void rowsShareOneTemplateWithNeutralIcons() throws Exception {
        for (String template : Arrays.asList("view_setting_row.xml", "view_setting_switch_row.xml")) {
            Document document = layout(template);
            Element icon = byId(document, "icon");
            assertEquals(template + " icon width", "24dp", icon.getAttribute("android:layout_width"));
            assertEquals(template + " icon height", "24dp", icon.getAttribute("android:layout_height"));
            assertEquals(template + " icon tint", "@color/ibox_text_secondary", icon.getAttribute("app:tint"));
            assertEquals(template + " row height", "@dimen/ibox_setting_row_height", document.getDocumentElement().getAttribute("android:minHeight"));
        }
        Document row = layout("view_setting_row.xml");
        assertTrue("行模板需要箭头", ids(row).contains("chevron"));
        assertTrue("行模板需要右侧值", ids(row).contains("value"));
        assertTrue("行模板需要副标题", ids(row).contains("summary"));
        assertTrue("开关模板需要开关", ids(layout("view_setting_switch_row.xml")).contains("toggle"));
    }
}
