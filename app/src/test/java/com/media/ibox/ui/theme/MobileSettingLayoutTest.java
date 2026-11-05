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
 * 设置页信息架构的守卫：单页平铺，不再使用二级菜单。
 * 所有开关/选择项按分组直接列在首页，只有「播放器」「弹幕」保留为入口。
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
    public void indexPageFlattensEverySettingIntoGroups() throws Exception {
        Set<String> ids = ids(layout("fragment_setting.xml"));
        // 内容源
        assertTrue(ids.containsAll(Arrays.asList("vod", "live", "rowWall")));
        // 通用
        assertTrue(ids.containsAll(Arrays.asList("rowIncognito", "rowConfigNotice", "rowRemoteAccess",
                "rowHideNavigation", "rowFloatingNavigation", "rowResumeButton", "rowBootAutoStart")));
        // 外观
        assertTrue(ids.containsAll(Arrays.asList("rowThemeMode", "rowLayoutMode", "rowThemeColor",
                "rowHomeContent", "rowSize", "rowAccessibility")));
        // 播放与弹幕（播放器/弹幕为入口）
        assertTrue(ids.containsAll(Arrays.asList("rowPlayer", "rowDanmaku", "rowAutoFallbackLine",
                "rowAutoFallbackSource")));
        // 网络与数据
        assertTrue(ids.containsAll(Arrays.asList("rowGithubAcceleration", "rowGithubAccelerators", "rowDoh",
                "rowSearchThreads", "rowSourceHealth", "rowCache", "rowWebdav", "rowBackup", "rowRestore")));
        // 关于
        assertTrue(ids.contains("rowVersion"));
    }

    @Test
    public void noSecondLevelEntryRowsRemain() throws Exception {
        Set<String> ids = ids(layout("fragment_setting.xml"));
        // 旧的二级入口行已被平铺取代，不应再存在
        for (String legacy : Arrays.asList("rowSource", "rowGeneral", "rowAppearance", "rowPlayback", "rowData")) {
            assertFalse("首页不应再有二级入口 " + legacy, ids.contains(legacy));
        }
    }

    @Test
    public void rowsShareOneTemplateWithNeutralIcons() throws Exception {
        for (String template : Arrays.asList("view_setting_row.xml", "view_setting_switch_row.xml", "view_setting_source_row.xml")) {
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
        Document source = layout("view_setting_source_row.xml");
        assertTrue("内容源行需要地址副标题", ids(source).contains("url"));
        assertTrue("内容源行需要设为首页按钮", ids(source).contains("home"));
        assertTrue("内容源行需要订阅管理按钮", ids(source).contains("history"));
    }
}
