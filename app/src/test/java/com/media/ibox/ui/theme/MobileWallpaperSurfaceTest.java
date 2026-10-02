package com.media.ibox.ui.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class MobileWallpaperSurfaceTest {

    private static final String MOBILE = "app/src/mobile/res/";
    private static final String CHROME = "@color/ibox_wall_chrome_surface";

    @Test
    public void downloadAndLibraryRowsUseMobileGlassSurface() throws Exception {
        Document selector = xml(MOBILE + "drawable/selector_download_row.xml");
        NodeList solids = selector.getElementsByTagName("solid");
        assertEquals(2, solids.getLength());
        for (int i = 0; i < solids.getLength(); i++) {
            assertEquals(CHROME, ((Element) solids.item(i)).getAttribute("android:color"));
        }
    }

    @Test
    public void offlineLibraryPageLeavesWallpaperVisibleBehindRows() throws Exception {
        Document layout = xml(MOBILE + "layout/activity_offline_library.xml");
        assertEquals("@android:color/transparent", layout.getDocumentElement()
                .getAttribute("android:background"));
        NodeList nodes = layout.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (!"@+id/empty".equals(element.getAttribute("android:id"))) continue;
            assertEquals("@color/ibox_text_secondary", element.getAttribute("android:drawableTint"));
            return;
        }
        throw new AssertionError("Missing offline library empty state");
    }

    @Test
    public void alertAndDeviceDialogsDoNotCoverWallpaperWithWhite() throws Exception {
        for (String folder : new String[]{"values", "values-night"}) {
            Document dialogColors = xml(MOBILE + folder + "/dialog_colors.xml");
            String surface = color(dialogColors, "ibox_mobile_dialog_surface");
            int alpha = Integer.parseInt(surface.substring(1, 3), 16);
            assertTrue(alpha >= 0xE8 && alpha <= 0xF2);
        }
        assertEquals("@android:color/transparent", xml(MOBILE + "layout/dialog_device.xml")
                .getDocumentElement().getAttribute("android:background"));
    }

    @Test
    public void otherRepeatedCardsUseWallpaperSurface() throws Exception {
        for (String layout : new String[]{"adapter_device.xml", "adapter_quick.xml"}) {
            assertEquals(CHROME, xml(MOBILE + "layout/" + layout).getDocumentElement()
                    .getAttribute("app:cardBackgroundColor"));
        }
        Document styles = xml(MOBILE + "values/card_styles.xml");
        NodeList styleNodes = styles.getElementsByTagName("style");
        for (int i = 0; i < styleNodes.getLength(); i++) {
            Element style = (Element) styleNodes.item(i);
            if (!"Widget.IBox.Card".equals(style.getAttribute("name"))) continue;
            NodeList items = style.getElementsByTagName("item");
            for (int j = 0; j < items.getLength(); j++) {
                Element item = (Element) items.item(j);
                if ("cardBackgroundColor".equals(item.getAttribute("name"))) {
                    assertEquals(CHROME, item.getTextContent().trim());
                    return;
                }
            }
        }
        throw new AssertionError("Mobile card surface is missing");
    }

    @Test
    public void searchAndMineCardsAreNotNearlyOpaqueWhite() throws Exception {
        for (String drawable : new String[]{"bg_search_result.xml", "bg_search_source.xml",
                "bg_search_trend.xml", "bg_mine_tile.xml"}) {
            NodeList solids = xml(MOBILE + "drawable/" + drawable).getElementsByTagName("solid");
            boolean hasChrome = false;
            for (int i = 0; i < solids.getLength(); i++) {
                if (CHROME.equals(((Element) solids.item(i)).getAttribute("android:color"))) {
                    hasChrome = true;
                    break;
                }
            }
            assertTrue(drawable + " still hides wallpaper", hasChrome);
        }
    }

    @Test
    public void homeLoadingAndListRowsLeaveWallpaperVisible() throws Exception {
        assertEquals("@android:color/transparent", xml(MOBILE + "layout/view_home_loading.xml")
                .getDocumentElement().getAttribute("android:background"));
        NodeList solids = xml(MOBILE + "drawable/shape_vod_list.xml")
                .getElementsByTagName("solid");
        assertEquals(1, solids.getLength());
        assertEquals(CHROME, ((Element) solids.item(0)).getAttribute("android:color"));
    }

    private static String color(Document document, String name) {
        NodeList nodes = document.getElementsByTagName("color");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (name.equals(element.getAttribute("name"))) return element.getTextContent().trim();
        }
        throw new AssertionError("Missing color " + name);
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
}
