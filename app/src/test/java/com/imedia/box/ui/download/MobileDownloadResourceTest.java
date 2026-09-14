package com.imedia.box.ui.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class MobileDownloadResourceTest {

    private static final String MOBILE = "app/src/mobile/res/";

    @Test
    public void queueStatusAndStorageShareOneCompactMobileRow() throws Exception {
        Document layout = xml(MOBILE + "layout/activity_offline_download.xml");
        Element status = view(layout, "queueStatus");
        Element summary = view(layout, "summary");
        Element policy = view(layout, "policySummary");
        assertEquals(status.getParentNode(), summary.getParentNode());
        assertEquals("horizontal", ((Element) status.getParentNode()).getAttribute("android:orientation"));
        assertEquals("1", status.getAttribute("android:maxLines"));
        assertEquals("1", summary.getAttribute("android:maxLines"));
        assertEquals("gone", policy.getAttribute("android:visibility"));
        view(layout, "policy");
        view(layout, "filters");
        view(layout, "recycler");
        view(layout, "empty");
    }

    @Test
    public void filtersShowASelectedSegmentInsteadOfFourFilledButtons() throws Exception {
        Document layout = xml(MOBILE + "layout/activity_offline_download.xml");
        for (String id : new String[]{"filterAll", "filterActive", "filterCompleted", "filterFailed"}) {
            assertEquals("@style/Widget.IBox.Button.Segment", view(layout, id).getAttribute("style"));
            assertEquals("@color/selector_download_filter_background", view(layout, id).getAttribute("app:backgroundTint"));
        }
        Document fill = xml(MOBILE + "color/selector_download_filter_background.xml");
        NodeList colors = fill.getElementsByTagName("item");
        assertEquals("true", ((Element) colors.item(0)).getAttribute("android:state_checked"));
        assertEquals("@color/ibox_wall_chrome_surface", ((Element) colors.item(1)).getAttribute("android:color"));
        assertEquals("@color/ibox_text_primary", view(layout, "empty").getAttribute("android:drawableTint"));
    }

    @Test
    public void onlyMobileRequestsPersistentSpeed() throws Exception {
        assertEquals("true", value(xml(MOBILE + "values/download_display.xml"), "bool", "download_show_transfer_stats_always"));
        assertEquals("false", value(xml("app/src/main/res/values/download_display.xml"), "bool", "download_show_transfer_stats_always"));
    }

    @Test
    public void mobileTaskActionsHaveRoomForDetailsAndTouchTargets() throws Exception {
        Document layout = xml(MOBILE + "layout/adapter_offline_download.xml");
        Element poster = view(layout, "poster");
        Element actions = (Element) view(layout, "delete").getParentNode();
        Element row = layout.getDocumentElement();
        assertEquals("horizontal", row.getAttribute("android:orientation"));
        assertEquals("88dp", row.getAttribute("android:minHeight"));
        assertEquals("44dp", poster.getAttribute("android:layout_width"));
        assertEquals("66dp", poster.getAttribute("android:layout_height"));
        assertEquals(row, actions.getParentNode());
        assertEquals("horizontal", actions.getAttribute("android:orientation"));
        assertEquals("96dp", actions.getAttribute("android:layout_width"));
        for (String id : new String[]{"pause", "resume", "retry", "play", "delete"}) {
            Element action = view(layout, id);
            assertEquals("48dp", action.getAttribute("android:layout_width"));
            assertEquals("48dp", action.getAttribute("android:layout_height"));
            assertTrue(action.getAttribute("android:contentDescription").startsWith("@string/"));
        }
        for (String id : new String[]{"name", "episode", "source", "status", "progress", "progressText"}) {
            view(layout, id);
        }
    }

    @Test
    public void mobileCopyKeepsNetworkWarningsClearWithoutRepeatingLineLabel() throws Exception {
        for (String folder : new String[]{"values", "values-zh-rCN", "values-zh-rTW"}) {
            Document strings = xml(MOBILE + folder + "/download_strings.xml");
            assertEquals("%1$s", value(strings, "download_source_line"));
            String summary = value(strings, "download_summary");
            assertTrue(summary.contains("%1$d"));
            assertTrue(summary.contains("%2$s"));
            assertFalse(summary.toLowerCase().contains("downloaded"));
            assertFalse(summary.contains("已下载"));
            assertTrue(value(strings, "download_waiting_wifi").toLowerCase().contains("wi-fi"));
            assertFalse(value(strings, "download_waiting_network").isBlank());
        }
    }

    private static Document xml(String relativePath) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("app/build.gradle"))) root = root.getParent();
        if (root == null) throw new AssertionError("Repository root not found");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(root.resolve(relativePath).toFile());
    }

    private static Element view(Document document, String id) {
        NodeList nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (("@+id/" + id).equals(element.getAttribute("android:id"))) return element;
        }
        throw new AssertionError("Missing view " + id);
    }

    private static String value(Document document, String name) {
        return value(document, "string", name);
    }

    private static String value(Document document, String type, String name) {
        NodeList nodes = document.getElementsByTagName(type);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (name.equals(element.getAttribute("name"))) return element.getTextContent().trim();
        }
        throw new AssertionError("Missing " + type + " " + name);
    }
}
