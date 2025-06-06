package iped.parsers.misc;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.executable.ExecutableParser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.standard.RawStringParser;
import iped.properties.ExtraProperties;
import junit.framework.TestCase;

public class MultipleParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testMultipleParserParsingDocx() throws IOException, SAXException, TikaException {

        MultipleParser parser = new MultipleParser();
        ExecutableParser exeParser = new ExecutableParser();
        RawStringParser rawParser = new RawStringParser();

        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.addParser(exeParser);
        parser.addParser(rawParser);
        parser.addSupportedTypes(exeParser.getSupportedTypes(context));
        parser.addSupportedTypes(rawParser.getSupportedTypes(context));
        try (InputStream stream = getStream("test-files/test_arj310.exe")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();

            assertTrue(hts.contains("Compressed by Petite (c)1999 Ian Luck."));
            assertTrue(hts.contains("ExitProcess"));
            assertTrue(hts.contains("Native versions for UNIX-like operating systems."));
            assertTrue(hts.contains("This is a self-extracting archive. Run it to extract all files."));
            assertTrue(hts.contains("C:\\ARJ\\ -m -b -x"));
            assertTrue(hts.contains("ARJ32 v 3.10/Win32"));

            assertEquals(ExecutableParser.class.getName(), metadata.getValues(ExtraProperties.TIKA_PARSER_USED)[0]);
            assertEquals(RawStringParser.class.getName(), metadata.getValues(ExtraProperties.TIKA_PARSER_USED)[1]);
            assertEquals("Little", metadata.get("machine:endian"));
            assertEquals("Windows", metadata.get("machine:platform"));
            assertEquals("32", metadata.get("machine:architectureBits"));
            assertEquals("application/x-msdownload", metadata.get(Metadata.CONTENT_TYPE));

        }

    }

}
