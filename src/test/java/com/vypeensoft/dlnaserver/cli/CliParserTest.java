package com.vypeensoft.dlnaserver.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

public class CliParserTest {

    @TempDir
    File tempDir;

    @Test
    public void testSuccessfulParsingWithPositional() {
        CliParser parser = new CliParser();
        String[] args = { tempDir.getAbsolutePath() };
        
        assertTrue(parser.parse(args));
        assertEquals(tempDir.getAbsolutePath(), parser.getMediaDir());
        assertEquals("Java DLNA Server", parser.getServerName());
        assertEquals(8200, parser.getPort());
    }

    @Test
    public void testSuccessfulParsingWithOptions() {
        CliParser parser = new CliParser();
        String[] args = {
            "--media-dir", tempDir.getAbsolutePath(),
            "--name", "My custom Server",
            "--port", "9000"
        };
        
        assertTrue(parser.parse(args));
        assertEquals(tempDir.getAbsolutePath(), parser.getMediaDir());
        assertEquals("My custom Server", parser.getServerName());
        assertEquals(9000, parser.getPort());
    }

    @Test
    public void testMissingMediaDirFails() {
        CliParser parser = new CliParser();
        String[] args = { "--name", "Test Server" };
        
        assertFalse(parser.parse(args));
    }

    @Test
    public void testNonExistentMediaDirFails() {
        CliParser parser = new CliParser();
        String[] args = { "C:\\NonExistentFolderWhichShouldNotExistAtAll" };
        
        assertFalse(parser.parse(args));
    }

    @Test
    public void testHelpRequests() {
        CliParser parser = new CliParser();
        String[] args = { "--help" };
        
        assertFalse(parser.parse(args));
        assertTrue(parser.isHelpRequested());
    }
}
