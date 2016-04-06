package technology.tabula;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.Test;
import technology.tabula.extractors.BasicExtractionAlgorithm;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;
import technology.tabula.writers.CSVWriter;

public class TestObjectExtractor {

    @Test(expected=IOException.class)
    public void testWrongPasswordRaisesException() throws IOException {
        PDDocument pdf_document = PDDocument.load(new File("src/test/resources/technology/tabula/encrypted.pdf"));
        ObjectExtractor oe = new ObjectExtractor(pdf_document, pdf_document.getPage(1), "wrongpass");
        //oe.extract().next();
    }
    
    @Test(expected=IOException.class)
    public void testEmptyOnEncryptedFileRaisesException() throws IOException {
        PDDocument pdf_document = PDDocument.load(new File("src/test/resources/technology/tabula/encrypted.pdf"));
        ObjectExtractor oe = new ObjectExtractor(pdf_document, pdf_document.getPage(1));
        //oe.extract().next();
    }
    
    @Test
    public void testCanReadPDFWithOwnerEncryption() throws IOException {
        PDDocument pdf_document = PDDocument.load(new File("src/test/resources/technology/tabula/S2MNCEbirdisland.pdf"));
        ObjectExtractor oe = new ObjectExtractor(pdf_document, pdf_document.getPage(1));
        /*PageIterator pi = oe.extract();
        int i = 0;
        while (pi.hasNext()) {
            i++;
            pi.next();
        }
        assertEquals(2, i);*/
    }
    
    @Test
    public void testGoodPassword() throws IOException {
        PDDocument pdf_document = PDDocument.load(new File("src/test/resources/technology/tabula/encrypted.pdf"));
        ObjectExtractor oe = new ObjectExtractor(pdf_document, pdf_document.getPage(1), "userpassword");
        List<Page> pages = new ArrayList<Page>();
        /*PageIterator pi = oe.extract();
        while (pi.hasNext()) {
            pages.add(pi.next());
        }
        assertEquals(1, pages.size());*/
    }
    
    @Test
    public void testTextExtractionDoesNotRaise() throws IOException {
        PDDocument pdf_document = PDDocument.load(new File("src/test/resources/technology/tabula/rotated_page.pdf"));
        ObjectExtractor oe = new ObjectExtractor(pdf_document ,pdf_document.getPage(1));
        /*PageIterator pi = oe.extract();
        
        assertTrue(pi.hasNext());
        assertNotNull(pi.next());
        assertFalse(pi.hasNext());*/
        
    }
    
    @Test
    public void testShouldDetectRulings() throws IOException {
        PDDocument pdf_document = PDDocument.load(new File("src/test/resources/technology/tabula/should_detect_rulings.pdf"));
        ObjectExtractor oe = new ObjectExtractor(pdf_document ,pdf_document.getPage(1));
        /*PageIterator pi = oe.extract();
       
        while (pi.hasNext()) {
            assertNotEquals(0, pi.next().getRulings().size());
        }*/
    }
    
    @Test
    public void testDontThrowNPEInShfill() throws IOException {
        PDDocument pdf_document = PDDocument.load(new File("src/test/resources/technology/tabula/labor.pdf"));
        ObjectExtractor oe = new ObjectExtractor(pdf_document ,pdf_document.getPage(1));
        /*PageIterator pi = oe.extract();
        assertTrue(pi.hasNext());
        try {
            Page p = pi.next();
            assertNotNull(p);
        }
        catch (NullPointerException e) {
            fail("NPE in ObjectExtractor " + e.toString());
        }*/
    }
    
    @Test
    public void testExtractOnePage() throws IOException{
        PDDocument pdf_document = PDDocument.load(new File("src/test/resources/technology/tabula/S2MNCEbirdisland.pdf"));
        assertEquals(2, pdf_document.getNumberOfPages());
        
        ObjectExtractor oe = new ObjectExtractor(pdf_document, pdf_document.getPage(1));
        /*Page page = oe.extract(2);
        
        assertNotNull(page);*/
    	
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testExtractWrongPageNumber() throws IOException{
        PDDocument pdf_document = PDDocument.load(new File("src/test/resources/technology/tabula/S2MNCEbirdisland.pdf"));
        assertEquals(2, pdf_document.getNumberOfPages());
        
        ObjectExtractor oe = new ObjectExtractor(pdf_document, pdf_document.getPage(3));
        oe.extractPage(3);
    	
    }
    
    @Test
    public void testExtractWithoutExtractingRulings() throws IOException {
        PDDocument pdf_document = PDDocument.load(new File("src/test/resources/technology/tabula/should_detect_rulings.pdf"));
        ObjectExtractor oe = new ObjectExtractor(pdf_document, pdf_document.getPage(1), null, false, false);
        /*PageIterator pi = oe.extract();
       
        assertTrue(pi.hasNext());
        Page page = pi.next();
        assertNotNull(page);
        assertEquals(0, page.getRulings().size());
        assertFalse(pi.hasNext());*/
    }

    @Test
    public void testExtract() throws IOException {
        PDDocument pdf_document = PDDocument.load(new File("src/test/resources/technology/tabula/should_detect_rulings.pdf"));
        ObjectExtractor oe = new ObjectExtractor(pdf_document, pdf_document.getPage(0));
        SpreadsheetExtractionAlgorithm se = new SpreadsheetExtractionAlgorithm();
        BasicExtractionAlgorithm be = new BasicExtractionAlgorithm();
        Page page = oe.extractPage(0);
        List<? extends Table> tables = se.extract(page);
        List<? extends Table> btables = be.extract(page);
        for (Table table : tables) {
            StringBuilder sb = new StringBuilder();
            (new CSVWriter()).write(sb, table);
            String result = sb.toString();
            System.out.println(result);
        }


    }
    
}
