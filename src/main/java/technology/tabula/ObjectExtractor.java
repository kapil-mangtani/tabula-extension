package technology.tabula;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.*;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShading;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.PDTextState;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.PageDrawer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType3Font;
import org.apache.pdfbox.rendering.PageDrawerParameters;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

public class ObjectExtractor extends PDFGraphicsStreamEngine {

    private static final char[] spaceLikeChars = { ' ', '-', '1', 'i' };
    private static final String NBSP = "\u00A0";

    private float minCharWidth;
    private float minCharHeight;
    private List<TextElement> characters;
    private List<Ruling> rulings;
    private RectangleSpatialIndex<TextElement> spatialIndex;
    private AffineTransform pageTransform;
    public List<Shape> clippingPaths;
    private boolean debugClippingPaths;
    private boolean extractRulingLines;
    private final PDDocument pdf_document;
    // adding a PageDrawer instance for the ease of implementation
    private PageDrawer pageDrawer;
    // Since the PDFGraphicsStreamEngine focuses on page only
    // we add this argument in all our constructors, so we can
    // call super
    public ObjectExtractor(PDDocument pdf_document, PDPage pdPage) throws IOException {
        this(pdf_document, pdPage, null, true, false);
    }

    public ObjectExtractor(PDDocument pdf_document, PDPage pdPage, boolean debugClippingPaths) throws IOException {
        this(pdf_document, pdPage, null, true, debugClippingPaths);
    }
    
    public ObjectExtractor(PDDocument pdf_document, PDPage pdPage, String password) throws IOException {
        this(pdf_document, pdPage, password, true, false);
    }

    public ObjectExtractor(PDDocument pdf_document, PDPage pdPage, String password, boolean extractRulingLines, boolean debugClippingPaths)
            throws IOException {
        // how about creating a page drawer instance for each page?
        // can call page drawers method for overiding
        super(pdPage);
        pageDrawer = new PageDrawer(new PageDrawerParameters(new PDFRenderer(pdf_document), pdPage));

        this.clippingPaths = new ArrayList<Shape>();
        this.debugClippingPaths = debugClippingPaths;
        this.extractRulingLines = extractRulingLines;
        
        this.initialize();
        
        // patch PageDrawer: dummy Graphics2D context so some drawing operators don't complain
        try {
            Field field = PageDrawer.class.getDeclaredField("graphics");
            field.setAccessible(true);
            field.set(this, new DummyGraphics2D());
        } 
        catch (Exception e1) {
        }

        this.pdf_document = pdf_document;

        // todo : ignoring encrypted documents for now
    }


    public Page extractPage(int page_number) throws IOException {

        this.initialize();

        PDPage pdPage = (PDPage) this.getPage();
        pdPage = this.drawPage(pdPage);

        if(pdPage != null) {

            Utils.sort(this.characters);

            float w, h;
            int pageRotation = pdPage.getRotation();
            if (Math.abs(pageRotation) == 90 || Math.abs(pageRotation) == 270) {
                w = pdPage.getCropBox().getHeight();
                h = pdPage.getCropBox().getWidth();
            }
            else {
                w = pdPage.getCropBox().getWidth();
                h = pdPage.getCropBox().getHeight();
            }

            return new Page(0, 0, w, h, pageRotation, page_number, pdPage, this.characters,
                    this.rulings, this.minCharWidth, this.minCharHeight,
                    this.spatialIndex);
        }
        return null;//TODO: content is empty, return null? or empty Page? or exception?
    }
    /*protected Page extractPage(Integer page_number) throws IOException {

        if (page_number > this.pdf_document.getNumberOfPages() || page_number < 1) {
            throw new java.lang.IndexOutOfBoundsException(
                    "Page number does not exist");
        }
        this.initialize();

        PDPage pdPage = (PDPage) this.pdf_document.getPage(page_number - 1);
        pdPage = this.drawPage(pdPage);
        
        if(pdPage != null) {
        	
        	Utils.sort(this.characters);
        	
        	float w, h;
        	int pageRotation = pdPage.getRotation();
        	if (Math.abs(pageRotation) == 90 || Math.abs(pageRotation) == 270) {
        		w = pdPage.getCropBox().getHeight();
        		h = pdPage.getCropBox().getWidth();
        	}
        	else {
        		w = pdPage.getCropBox().getWidth();
        		h = pdPage.getCropBox().getHeight();
        	}
        	
        	return new Page(0, 0, w, h, pageRotation, page_number, pdPage, this.characters,
        			this.rulings, this.minCharWidth, this.minCharHeight,
        			this.spatialIndex);
        }
        return null;//TODO: content is empty, return null? or empty Page? or exception?
    }

    public PageIterator extract(Iterable<Integer> pages) {
        return new PageIterator(this, pages);
    }

    public PageIterator extract() {
        return extract(Utils.range(1, this.pdf_document.getNumberOfPages() + 1));
    }

    public Page extract(int pageNumber) {
        return extract(Utils.range(pageNumber, pageNumber + 1)).next();
    } */

    public void close() throws IOException {
        this.pdf_document.close();
    }

    private PDPage drawPage(PDPage p) throws IOException {
        InputStream contents = p.getContents();
        if (contents != null) {
            //ensurePageSize();
            this.processPage(p);
            return p;
        }
        return null;
    }

    /*private void ensurePageSize() {
        if (pageDrawer.pageSize == null && this.getPage() != null) {
            this.pageSize = this.getPage().getCropBox();
        }
    }*/

    private void initialize() {
        this.characters = new ArrayList<TextElement>();
        this.rulings = new ArrayList<Ruling>();
        this.pageTransform = null;
        this.spatialIndex = new RectangleSpatialIndex<TextElement>();
        this.minCharWidth = Float.MAX_VALUE;
        this.minCharHeight = Float.MAX_VALUE;
    }

    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) throws IOException {
        pageDrawer.appendRectangle(p0, p1, p2, p3);
    }

    @Override
    public void drawImage(PDImage pdImage) throws IOException {
        pageDrawer.drawImage(pdImage);
    }

    @Override
    public void clip(int windingRule) throws IOException {
        pageDrawer.clip(windingRule);
    }

    @Override
    public void moveTo(float x, float y) throws IOException {
        pageDrawer.moveTo(x, y);
    }

    @Override
    public void lineTo(float x, float y) throws IOException {
        pageDrawer.lineTo(x, y);
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) throws IOException {
        pageDrawer.curveTo(x1, y1, x2, y2, x3, y3);
    }

    @Override
    public Point2D getCurrentPoint() throws IOException {
        return pageDrawer.getCurrentPoint();
    }

    @Override
    public void closePath() throws IOException {
        pageDrawer.closePath();
    }

    @Override
    public void endPath() throws IOException {
        pageDrawer.endPath();
    }

    public void strokeOrFillPath(boolean isFill) {
        GeneralPath path = pageDrawer.getLinePath();

        if (!this.extractRulingLines) {
            pageDrawer.getLinePath().reset();
            return;
        }

        PathIterator pi = path.getPathIterator(this.getPageTransform());
        float[] c = new float[6];
        int currentSegment;

        // skip paths whose first operation is not a MOVETO
        // or contains operations other than LINETO, MOVETO or CLOSE
        if ((pi.currentSegment(c) != PathIterator.SEG_MOVETO)) {
            path.reset();
            return;
        }
        pi.next();
        while (!pi.isDone()) {
            currentSegment = pi.currentSegment(c);
            if (currentSegment != PathIterator.SEG_LINETO
                    && currentSegment != PathIterator.SEG_CLOSE
                    && currentSegment != PathIterator.SEG_MOVETO) {
                path.reset();
                return;
            }
            pi.next();
        }

        // TODO: how to implement color filter?

        // skip the first path operation and save it as the starting position
        float[] first = new float[6];
        pi = path.getPathIterator(this.getPageTransform());
        pi.currentSegment(first);
        // last move
        Point2D.Float start_pos = new Point2D.Float(Utils.round(first[0], 2), Utils.round(first[1], 2));
        Point2D.Float last_move = start_pos;
        Point2D.Float end_pos = null;
        Line2D.Float line;
        PointComparator pc = new PointComparator();

        while (!pi.isDone()) {
            pi.next();
            currentSegment = pi.currentSegment(c);
            switch (currentSegment) {
            case PathIterator.SEG_LINETO:
                end_pos = new Point2D.Float(c[0], c[1]);

                line = pc.compare(start_pos, end_pos) == -1 ? new Line2D.Float(
                        start_pos, end_pos) : new Line2D.Float(end_pos,
                        start_pos);

                if (line.intersects(this.currentClippingPath())) {
                    Ruling r = new Ruling(line.getP1(), line.getP2())
                            .intersect(this.currentClippingPath());

                    if (r.length() > 0.01) {
                        this.rulings.add(r);
                    }
                }
                break;
            case PathIterator.SEG_MOVETO:
                last_move = new Point2D.Float(c[0], c[1]);
                end_pos = last_move;
                break;
            case PathIterator.SEG_CLOSE:
                // according to PathIterator docs:
                // "the preceding subpath should be closed by appending a line
                // segment
                // back to the point corresponding to the most recent
                // SEG_MOVETO."
                line = pc.compare(end_pos, last_move) == -1 ? new Line2D.Float(
                        end_pos, last_move) : new Line2D.Float(last_move,
                        end_pos);

                if (line.intersects(this.currentClippingPath())) {
                    Ruling r = new Ruling(line.getP1(), line.getP2())
                            .intersect(this.currentClippingPath());

                    if (r.length() > 0.01) {
                        this.rulings.add(r);
                    }
                }
                break;
            }
            start_pos = end_pos;
        }
        path.reset();
    }

    @Override
    public void strokePath() throws IOException {
//        this.strokeOrFillPath(false);
        pageDrawer.strokePath();
    }

    @Override
    public void fillPath(int windingRule) throws IOException {
//        float[] color = this.getGraphicsState().getNonStrokingColor().getComponents();
//        // TODO use color_comps as filter_by_color
//        this.strokeOrFillPath(true);
        pageDrawer.fillPath(windingRule);
    }

    @Override
    public void fillAndStrokePath(int windingRule) throws IOException {
//        // TODO can we avoid cloning the path?
//        GeneralPath path = (GeneralPath)linePath.clone();
//        fillPath(windingRule);
//        linePath = path;
//        strokePath();
        pageDrawer.fillAndStrokePath(windingRule);
    }

    @Override
    public void shadingFill(COSName shadingName) throws IOException {
        PDShading shading = getResources().getShading(shadingName);
        Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
        Paint paint = shading.toPaint(ctm);
    }

    private float currentSpaceWidth() throws IOException {
        PDGraphicsState gs = this.getGraphicsState();
        PDTextState ts = gs.getTextState();
        PDFont font = ts.getFont();
        float fontSizeText = ts.getFontSize();
        float horizontalScalingText = ts.getHorizontalScaling() / 100.0f;
        float spaceWidthText = 1000;

        if (font instanceof PDType3Font) {
            // TODO WHAT?
        }

        for (int i = 0; i < spaceLikeChars.length; i++) {
            spaceWidthText = font.getWidth(spaceLikeChars[i]);
            if (spaceWidthText > 0)
                break;
        }

        float ctm00 = gs.getCurrentTransformationMatrix().getValue(0, 0);

        return (float) ((spaceWidthText / 1000.0) * fontSizeText
                * horizontalScalingText * (ctm00 == 0 ? 1 : ctm00));
    }

    protected void processTextPosition(TextPosition textPosition) throws IOException {
        String c = textPosition.getUnicode();

        // if c not printable, return
        if (!isPrintable(c)) {
            return;
        }

        Float h = textPosition.getHeightDir();

        if (c.equals(NBSP)) { // replace non-breaking space for space
            c = " ";
        }

        float wos = textPosition.getWidthOfSpace();

        TextElement te = new TextElement(
                Utils.round(textPosition.getYDirAdj() - h, 2),
                Utils.round(textPosition.getXDirAdj(), 2),
                Utils.round(textPosition.getWidthDirAdj(), 2),
                Utils.round(textPosition.getHeightDir(), 2),
                textPosition.getFont(),
                textPosition.getFontSize(),
                c,
                // workaround a possible bug in PDFBox:
                // https://issues.apache.org/jira/browse/PDFBOX-1755
                (Float.isNaN(wos) || wos == 0) ? this.currentSpaceWidth() : wos,
                textPosition.getDir());

       if (this.currentClippingPath().intersects(te)) {

            this.minCharWidth = (float) Math.min(this.minCharWidth, te.getWidth());
            this.minCharHeight = (float) Math.min(this.minCharHeight, te.getHeight());

            this.spatialIndex.add(te);
            this.characters.add(te);
        }

        if (this.isDebugClippingPaths() && !this.clippingPaths.contains(this.currentClippingPath())) {
            this.clippingPaths.add(this.currentClippingPath());
        }

    }

    public AffineTransform getPageTransform() {

        if (this.pageTransform != null) {
            return this.pageTransform;
        }

        PDRectangle cb = getPage().getCropBox();
        int rotation = Math.abs(getPage().getRotation());

        this.pageTransform = new AffineTransform();

        if (rotation == 90 || rotation == 270) {
            this.pageTransform = AffineTransform.getRotateInstance(rotation * (Math.PI / 180.0), 0, 0);
            this.pageTransform.concatenate(AffineTransform.getScaleInstance(1, -1));
            this.pageTransform.concatenate(AffineTransform.getTranslateInstance(0, cb.getHeight()));
            this.pageTransform.concatenate(AffineTransform.getScaleInstance(1, -1));
        }
        return this.pageTransform;
    }

    public Rectangle2D currentClippingPath() {
   
    	Shape clippingPath = this.getGraphicsState().getCurrentClippingPath();
        Shape transformedClippingPath = this.getPageTransform()
                .createTransformedShape(clippingPath);
        Rectangle2D transformedClippingPathBounds = transformedClippingPath
                .getBounds2D();

        return transformedClippingPathBounds;
    }

    public boolean isExtractRulingLines() {
        return extractRulingLines;
    }

    private static boolean isPrintable(String s) {
        Character c = s.charAt(0);
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return (!Character.isISOControl(c)) && c != KeyEvent.CHAR_UNDEFINED
                && block != null && block != Character.UnicodeBlock.SPECIALS;
    }

    public boolean isDebugClippingPaths() {
        return debugClippingPaths;
    }

    public int getPageCount() {
        return this.pdf_document.getNumberOfPages();
    }
    
    class PointComparator implements Comparator<Point2D> {
        @Override
        public int compare(Point2D o1, Point2D o2) {
            float o1X = Utils.round(o1.getX(), 2);
            float o1Y = Utils.round(o1.getY(), 2);
            float o2X = Utils.round(o2.getX(), 2);
            float o2Y = Utils.round(o2.getY(), 2);

            if (o1Y > o2Y)
                return 1;
            if (o1Y < o2Y)
                return -1;
            if (o1X > o2X)
                return 1;
            if (o1X < o2X)
                return -1;
            return 0;
        }
    }

}
