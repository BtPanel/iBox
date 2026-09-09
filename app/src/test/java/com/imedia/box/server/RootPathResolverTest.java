package com.imedia.box.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class RootPathResolverTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void resolvesRelativeAndAbsolutePathsInsideRoot() throws Exception {
        File root = temporaryFolder.newFolder("storage");
        File nested = new File(root, "iBox/video.mp4");

        assertEquals(nested.getCanonicalFile(), RootPathResolver.resolve(root, "iBox/video.mp4"));
        assertEquals(nested.getCanonicalFile(), RootPathResolver.resolve(root, nested.getAbsolutePath()));
    }

    @Test
    public void rejectsTraversalOutsideRoot() throws Exception {
        File root = temporaryFolder.newFolder("storage");

        assertThrows(SecurityException.class, () -> RootPathResolver.resolve(root, "../private.txt"));
    }

    @Test
    public void rejectsTraversalInUploadedFileName() throws Exception {
        File root = temporaryFolder.newFolder("storage");
        assertThrows(SecurityException.class, () -> RootPathResolver.resolve(root, "uploads", "../../private.txt"));
    }
}
