package com.imedia.box.dlna;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class BoundedHttpInputTest {

    @Test
    public void rejectsOversizedRequestLine() {
        ByteArrayInputStream input = bytes("G".repeat(9_000) + "\r\n");
        BoundedHttpInput.HttpInputException error = assertThrows(BoundedHttpInput.HttpInputException.class,
                () -> BoundedHttpInput.readLine(input, 8 * 1024));
        assertEquals(413, error.statusCode());
    }

    @Test
    public void rejectsOversizedHeaderBlock() {
        ByteArrayInputStream input = bytes("A: " + "x".repeat(20) + "\r\nB: " + "y".repeat(20) + "\r\n\r\n");
        BoundedHttpInput.HttpInputException error = assertThrows(BoundedHttpInput.HttpInputException.class,
                () -> BoundedHttpInput.readHeaders(input, 32, 40));
        assertEquals(413, error.statusCode());
    }

    @Test
    public void rejectsHeaderBlockWithoutTerminatingBlankLine() {
        BoundedHttpInput.HttpInputException error = assertThrows(BoundedHttpInput.HttpInputException.class,
                () -> BoundedHttpInput.readHeaders(bytes("Host: renderer\r\n"), 64, 128));
        assertEquals(400, error.statusCode());
    }

    @Test
    public void rejectsBodyLimitBeforeAllocation() {
        BoundedHttpInput.HttpInputException error = assertThrows(BoundedHttpInput.HttpInputException.class,
                () -> BoundedHttpInput.readBody(bytes("small"), "1048577", 1024 * 1024));
        assertEquals(413, error.statusCode());
    }

    @Test
    public void rejectsInvalidOrTruncatedBodies() {
        assertEquals(400, assertThrows(BoundedHttpInput.HttpInputException.class,
                () -> BoundedHttpInput.readBody(bytes("body"), "invalid", 1024)).statusCode());
        assertEquals(400, assertThrows(BoundedHttpInput.HttpInputException.class,
                () -> BoundedHttpInput.readBody(bytes("body"), "10", 1024)).statusCode());
    }

    private static ByteArrayInputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.ISO_8859_1));
    }
}
