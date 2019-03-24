package au.com.advancedcontrols.jsjbridge;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.RenderingHints.Key;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.DataBuffer;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.AttributedCharacterIterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.json.JSONArray;

import net.sprd.image.webp.WebPRegister;
import net.sprd.image.webp.WebPWriteParam;

public class HTMLCanvas extends Graphics2D {
	private WebpageHelper	helper;
	private Font			currentFont = null;
	private Color			currentColor = null, currentBackgroundColor = null;
	private BasicStroke		currentStroke = null;
	private Area			currentClip = null;
	private AffineTransform	currentTransform = null;
	private Point			currentOrigin = new Point();
	
	private static ImageWriter webpWriter = null;
	private static ImageWriteParam webpWriterParams = null;
	private static AffineTransform identityTransform = new AffineTransform();
	
	public HTMLCanvas(WebpageHelper h) {
		helper = h;
		if (webpWriter == null) {
			WebPRegister.registerImageTypes();
			try {
				webpWriter = ImageIO.getImageWritersByFormatName("webp").next();
				webpWriterParams = webpWriter.getDefaultWriteParam();
				webpWriterParams.setCompressionType(WebPWriteParam.LOSSLESS);
			} catch (NoSuchElementException e) {
				helper.nlog.warning("Images disabled: No suitable webp image encoders were available");
			}
		}
	}

	private JSONArray graphicsQueue = new JSONArray();
	private HashMap<ImageObserver, HashSet<Image>> queueObserveredImages = new HashMap<ImageObserver, HashSet<Image>>();
	private WeakHashMap<Image, String> preparedImages = new WeakHashMap<Image, String>();
	
	void queue(Object... values) {
		graphicsQueue.put(new JSONArray(values));
	}
	
	synchronized void sendQueue() {
		if (!graphicsQueue.isEmpty()) {
			final JSONArray queueToRender = graphicsQueue;
			final HashMap<ImageObserver, HashSet<Image>> renderedQueueObserveredImages = queueObserveredImages;
			new Thread(() -> {
				helper.canvasJsObject.sendRequest("renderGraphicsQueue", queueToRender);
				renderedQueueObserveredImages.forEach((obs,images) -> images.forEach(img -> obs.imageUpdate(img, ImageObserver.ALLBITS, 0, 0, 0, 0)));
			}, "renderGraphicsQueue").start();
			graphicsQueue = new JSONArray();
			queueObserveredImages = new HashMap<ImageObserver, HashSet<Image>>();
		}
	}
	
	Object query(String command, Object... args) {
		return helper.canvasJsObject.sendRequest("canvasQuery", args);
	}
	
	public static String cssColor(Color c) {
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}
	
	public static String cssFont(Font f) {
		return (f.isItalic() ? "italic" : "") + (f.isBold() ? " bold " : "") + " " + f.getSize2D() + "px " + f.getFamily(); 
	}

	@Override
	public void addRenderingHints(Map<?, ?> arg0) {
		// TODO Auto-generated method stub
	}
	
	private PathIterator queueShapePath(Shape shape) {
		PathIterator shapePath = shape.getPathIterator(currentTransform);
		String command = null;
		int numParams = 0;
		double[] coords = new double[6];
		while(!shapePath.isDone()) {
			int pathType = shapePath.currentSegment(coords);
			switch (pathType) {
				case PathIterator.SEG_CLOSE: command = "closePath"; numParams = 0; break;
				case PathIterator.SEG_LINETO: command = "lineTo"; numParams = 2; break;
				case PathIterator.SEG_MOVETO: command = "moveTo"; numParams = 2; break;
				case PathIterator.SEG_CUBICTO: command = "bezierCurveTo"; numParams = 4; 
				case PathIterator.SEG_QUADTO: command = "quadraticCurveTo"; numParams = 6; break;
			}
			Object[] call = new Object[numParams + 1];
			call[0] = command;
			for (int i=0; i<numParams; i++) call[i+1] = coords[i];
			queue(call);
			shapePath.next();
		}
		return shapePath;
	}

	private void shapeOp(String op, Shape shape) {
		queue("beginPath");
		PathIterator shapePath = queueShapePath(shape);
		if (op.equals("stroke"))
			queue(op);
		else
			queue(op, shapePath.getWindingRule() == PathIterator.WIND_EVEN_ODD ? "evenodd" : "nonzero");
	}


	// Clipping
	
	@Override
	public void setClip(Shape shape) {
		Area oldCurrentClip = currentClip;
		currentClip = shape == null ? null : new Area(shape);
		if (shape == null && oldCurrentClip != null)
			shape = new Rectangle(0, 0, helper.getWidth(), helper.getHeight()); // Works? Maybe save & reload canvas
		if (shape != null) shapeOp("clip", shape);
		
	}

	@Override
	public void setClip(int x, int y, int width, int height) {
		setClip(new Rectangle(x, y, width, height));
	}

	@Override
	public void clip(Shape shape) {
		if (shape != null && currentClip != null) {
			currentClip.intersect(new Area(shape));
			shapeOp("clip", currentClip);
		} else {
			setClip(shape);
		}
	}
	
	@Override
	public void clipRect(int x,int y,int width, int height) {
		clip(new Rectangle(x, y, width, height));
	}
	
	@Override
	public Shape getClip() {
		return currentClip;
	}

	@Override
	public Rectangle getClipBounds() {
		return currentClip.getBounds();
	}
	
	public Rectangle getClipBounds(Rectangle r) {
		r.setBounds(getClipBounds());
		return r;
	}
	

	// Drawing

	@Override
	public void draw(Shape shape) {
		shapeOp("stroke", shape);
	}

	@Override
	public void drawGlyphVector(GlyphVector g, float x, float y) {
		fill(g.getOutline(x, y));
	}

	
	// Images

	private int sevenBitsToUTF8CodePoint(int in) {
		// Mapping 7 bits to single-byte UTF8 characters when JSON-encoded: 00-38 => 23-5B, 39-5A => 5D-7E, 5B-78 => C2-DF, 79-7F => E1-E7
		return in < 0x39 ? in+0x23 : in < 0x5B ? in+0x24 : in < 0x79 ? in+0x67 : in+0x68;
	}
	
	public static byte[] webpBytes = null;
	
	private String imageToUTF8String(RenderedImage image) {
		if (webpWriter != null) try {
			ByteArrayOutputStream webpData = new ByteArrayOutputStream();
			ImageOutputStream ios = ImageIO.createImageOutputStream(webpData);
			webpWriter.setOutput(ios);
			webpWriter.write(null, new IIOImage(image, null, null), webpWriterParams);
			ios.flush();
			webpBytes = webpData.toByteArray();

			//DataBuffer imgData = image.getData().getDataBuffer();
			//helper.nlog.info("IMAGE size = " + imgData.getSize()*DataBuffer.getDataTypeSize(imgData.getDataType())/8 + " WEBP size = " + webpData.size());

			StringBuilder serialization = new StringBuilder();
			int inIndex = 0, highBits = 0;
			for (byte webpByte: webpData.toByteArray()) {
				serialization.appendCodePoint(sevenBitsToUTF8CodePoint(webpByte & 0x7f));
				highBits |= (webpByte & 0x80) >>> (inIndex%7 + 1);
				if (inIndex++ % 7 == 6) {
					serialization.appendCodePoint(sevenBitsToUTF8CodePoint(highBits));
					highBits = 0;
				}
			}
			if (inIndex % 7 != 0) serialization.appendCodePoint(sevenBitsToUTF8CodePoint(highBits));
			return serialization.toString();
		} catch (IOException e) {
		}

		return null;
	}
	
	static HashMap<Integer, HashSet<Integer>> cachedImages = new HashMap<Integer, HashSet<Integer>>();
	
	private boolean doDrawImage(RenderedImage img, AffineTransform xform, ImageObserver obs) {
		if (img != null) {
			if (!xform.equals(currentTransform)) queueTransform(xform); 
			
			String jsonImage = null;
			int cacheIndex = helper.jsObject.portId, imageHash = img.hashCode();
			HashSet<Integer> cachedImagesForWebpage = cachedImages.get(cacheIndex);
			
			if (cachedImagesForWebpage == null || !cachedImagesForWebpage.contains(imageHash)) {
				jsonImage = preparedImages.get((Image)img);
				if (jsonImage == null) jsonImage = imageToUTF8String(img);
				if (jsonImage == null) return true;
				
				if (cachedImagesForWebpage == null) {
					cachedImagesForWebpage = new HashSet<Integer>();
					cachedImages.put(cacheIndex, cachedImagesForWebpage);
				} 
				cachedImagesForWebpage.add(imageHash);
			}
				
			queue("image", imageHash, jsonImage);
			if (!xform.equals(currentTransform)) queueTransform(currentTransform);
			if (obs != null) {
				synchronized (this) {
					HashSet<Image> imagesObserved = queueObserveredImages.get(obs);
					if (imagesObserved == null) {
						imagesObserved =  new HashSet<Image>();
						queueObserveredImages.put(obs, imagesObserved);
					}
					imagesObserved.add((Image)img);
				}
			}
			return false;
		} else {
			return true;
		}
	}
	
	private boolean doDrawImage(RenderedImage img, int x, int y, ImageObserver obs) {
		return doDrawImage(img, AffineTransform.getTranslateInstance(x,y), obs);
	}
	
	private RenderedImage fillImageBackground(RenderedImage img, int x, int y, Color bgcolor) {
		setColor(bgcolor);
		fillRect(x, y, img.getWidth(), img.getHeight());
		return img;
	}

	@Override
	public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
		return doDrawImage((RenderedImage)img, xform, obs);
	}

	@Override
	public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
		doDrawImage(img.createDefaultRendering(), xform, null);
	}

	@Override
	public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
		doDrawImage(img, xform, null);
	}	
	
	@Override
	public boolean drawImage(Image img, int x, int y, ImageObserver obs) {
	  return doDrawImage((RenderedImage)img, x, y, obs);
	}

	@Override
	public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
		if (op != null) img = op.filter(img, null);
		doDrawImage(img, x, y, null);
	}
	
	@Override
	public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver obs) {
		return doDrawImage(fillImageBackground((RenderedImage)img, x, y, bgcolor), x, y, obs);
	}

	@Override
	public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver obs) {
		RenderedImage bimg = (RenderedImage)img;
		AffineTransform xform = AffineTransform.getScaleInstance((double)width/bimg.getWidth(),(double)height/bimg.getHeight());
		xform.translate(x, y);
		return drawImage(img, xform, obs);
	}

	@Override
	public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver obs) {
		fillImageBackground((RenderedImage)img, x, y, bgcolor);
		return drawImage(img, x, y, width, height, obs);
	}

	@Override
	public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, ImageObserver obs) {
		BufferedImage tile = ((BufferedImage)img).getSubimage(sx1, sy1, Math.abs(sx2-sx1), Math.abs(sy2-sy1));
		return drawImage(tile, Math.min(dx1, dx2), Math.min(dy1, dy2), dx2-dx1, dy2-dy1, obs);
	}

	@Override
	public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, Color bgcolor, ImageObserver obs) {
		fillImageBackground((RenderedImage)img, dx1, dy1, bgcolor);
		return drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, obs);
	}
	
	boolean prepareImage(Image img, ImageObserver obs) {
		synchronized(this) {
			if (preparedImages.containsKey(img))
				return preparedImages.get(img) != null;
			else
				preparedImages.put(img, null);
		}

		new Thread(() -> {
			String jsonImage = imageToUTF8String((RenderedImage)img);
			if (jsonImage != null) {
				synchronized(this) { preparedImages.put(img, jsonImage); }
				obs.imageUpdate(img, ImageObserver.ALLBITS, 0, 0, 0, 0);
			}
		}, "preparation of " + img.toString()).start();

		return false;
	}
	
	synchronized int checkImage(Image img, ImageObserver obs) {
		return preparedImages.containsKey(img) ?	(preparedImages.get(img) == null ? ImageObserver.SOMEBITS : ImageObserver.ALLBITS) :
													ImageObserver.ERROR;
	}
	
	
	// Text
	
	@Override
	public void drawString(String str, int x, int y) {
		queue("strokeText", str, x, y);
	}

	@Override
	public void drawString(String str, float x, float y) {
		queue("strokeText", str, x, y);
	}

	@Override
	public void drawString(AttributedCharacterIterator iterator, int x, int y) {
		// TODO Auto-generated method stub
	}

	@Override
	public void drawString(AttributedCharacterIterator arg0, float arg1, float arg2) {
		// TODO Auto-generated method stub
	}

	@Override
	public void fill(Shape shape) {
		shapeOp("fill", shape);
	}

	@Override
	public Color getBackground() {
		return currentBackgroundColor;
	}

	@Override
	public Composite getComposite() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public GraphicsConfiguration getDeviceConfiguration() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FontRenderContext getFontRenderContext() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Paint getPaint() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getRenderingHint(Key arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RenderingHints getRenderingHints() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Stroke getStroke() {
		return currentStroke;
	}

	@Override
	public AffineTransform getTransform() {
		return currentTransform;
	}

	@Override
	public boolean hit(Rectangle arg0, Shape arg1, boolean arg2) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setBackground(Color c) {
		currentBackgroundColor = c;
	}

	@Override
	public void setComposite(Composite arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void setPaint(Paint arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void setRenderingHint(Key arg0, Object arg1) {
		// TODO Auto-generated method stub
	}

	@Override
	public void setRenderingHints(Map<?, ?> arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void setStroke(Stroke s) {
		currentStroke = (BasicStroke)s;
		int 	lineJoinInt = currentStroke.getLineJoin();
		String 	lineJoinStr = lineJoinInt == BasicStroke.JOIN_ROUND ? "round" : lineJoinInt == BasicStroke.JOIN_BEVEL ? "bevel" : "miter";
		int 	lineCapInt = currentStroke.	getEndCap();
		String 	lineCapStr = lineCapInt == BasicStroke.CAP_BUTT ? "butt" : lineCapInt == BasicStroke.CAP_ROUND ? "round" : "square";
		queue("assign",	"lineWidth", currentStroke.getLineWidth(),
						"lineJoin", lineJoinStr,
						"miterLimit", currentStroke.getMiterLimit(),
						"lineCap", lineCapStr,
						"lineDashOffset", currentStroke.getDashPhase()
		);
		queue("setLineDash", currentStroke.getDashArray());
	}
	
	
	private void queueTransform(AffineTransform xform) {
		queue("resetTransform");
		if (xform != null && !xform.isIdentity()) {
			double[] coords = new double[6];
			xform.getMatrix(coords);
			Object[] call = new Object[7];
			call[0] = "transform";
			for (int i=0; i<6; i++) call[i+1] = coords[i];
			queue(call);
		}
	}

	@Override
	public void setTransform(AffineTransform xform) {
		currentTransform = xform;
		queueTransform(xform);
	}

	@Override
	public void transform(AffineTransform xform) {
		if (currentTransform == null)
			setTransform(xform);
		else
			currentTransform.concatenate(xform);
	}
	
	@Override
	public void translate(double x, double y) {
		currentOrigin.setLocation(x, y);
	}

	@Override
	public void rotate(double theta) {
		transform(AffineTransform.getRotateInstance(theta));
	}

	@Override
	public void rotate(double theta, double x, double y) {
		transform(AffineTransform.getTranslateInstance(x, y));
		rotate(theta);
	}

	@Override
	public void scale(double sx, double sy) {
		transform(AffineTransform.getScaleInstance(sx, sy));
	}

	@Override
	public void shear(double shx, double shy){
		transform(AffineTransform.getShearInstance(shx, shy));
	}

	@Override
	public void translate(int x, int y) {
		currentOrigin.move(x, y);
		queue("translate", x, y);
	}


	@Override
	public void copyArea(int x, int y, int width, int height, int dx, int dy) {
		queue("putImageData", x+dx, y+dy, x, y, width, height);
	}
	
	private HTMLCanvas createCommon() {
		HTMLCanvas copy = new HTMLCanvas(helper);
		copy.currentBackgroundColor = currentBackgroundColor;
		copy.currentColor = currentColor;
		copy.currentTransform = new AffineTransform(currentTransform);
		copy.currentFont = currentFont;
		copy.currentOrigin = new Point(currentOrigin);
		return copy;
	}

	@Override
	public Graphics create() {
		HTMLCanvas copy = createCommon();
		copy.currentClip = new Area (currentClip);
		return copy;
	}
	
	public Graphics create(int x, int y, int width, int height) {
		HTMLCanvas copy = createCommon();
		copy.currentOrigin.setLocation(x, y);
		copy.currentClip = new Area(new Rectangle(x, y, width, height));
		return copy;
	}

	@Override
	public synchronized void dispose() {
		graphicsQueue = null;
		for(HashSet<Image> observeredImages: queueObserveredImages.values()) observeredImages.clear();
		queueObserveredImages = null;
	}
	
	
	private void beginPath() {
		queue("beginPath");
	}
	
	private void closePath() {
		queue("closePath");
	}
	
	private void moveTo(int x, int y) {
		queue("moveTo", x, y);
	}
	
	private void lineTo(int x, int y) {
		queue("lineTo", x, y);
	}

	private void stroke() {
		queue("stroke");
	}
	
	private void fill() {
		queue("fill");
	}	

	@Override
	public void drawLine(int x1, int y1, int x2, int y2) {
		beginPath();
		moveTo(x1, y1);
		lineTo(x2, y2);
		stroke();
	}

	private void doDrawPolyline(int[] xPoints, int[] yPoints, int nPoints, boolean closedPath, boolean fill) {
		if (nPoints > 1) {
			moveTo(xPoints[0], yPoints[0]);
			for (int i=1; i<nPoints; i++) lineTo(xPoints[i], yPoints[i]);
			if (closedPath) closePath();
			if (fill) fill(); else stroke();
		}
	}

	@Override
	public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
		doDrawPolyline(xPoints, yPoints, nPoints, true, false);
	}

	@Override
	public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
		doDrawPolyline(xPoints, yPoints, nPoints, false, false);
	}

	@Override
	public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
		doDrawPolyline(xPoints, yPoints, nPoints, true, true);
	}

	@Override
	public void fillRect(int x, int y, int width, int height) {
		queue("fillRect", x, y, width, height, true);
	}

	@Override
	public void clearRect(int x, int y, int width, int height) {
		queue("fillRect", x, y, width, height, false);
	}
	
	private void queueRoundRectPath(String op, int x, int y, int width, int height, int arcWidth, int arcHeight) {
		shapeOp(op, new RoundRectangle2D.Double(x, y, width, height, arcWidth, arcHeight));
	}
	
	@Override
	public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
		queueRoundRectPath("stroke", x, y, width, height, arcWidth, arcHeight);
	}

	@Override
	public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
		queueRoundRectPath("fill", x, y, width, height, arcWidth, arcHeight);
	}

	private void arcPath(int x, int y, int width, int height, int startAngle, int arcAngle) {
		queue("ellipse", x, y, width, height, 0, startAngle, Math.toRadians(startAngle+Math.abs(arcAngle)), arcAngle < 0);
	}

	@Override
	public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
		beginPath();
		arcPath(x, y, width, height, startAngle, arcAngle);
		stroke();
	}
	
	@Override
	public void drawOval(int x, int y, int width, int height) {
		drawArc(x, y, width, height, 0, 360); 
	}

	@Override
	public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
		beginPath();
		arcPath(x, y, width, height, startAngle, arcAngle);
		lineTo(x, y);
		closePath();
		fill();
	}
	
	@Override
	public void fillOval(int x, int y, int width, int height) {
		beginPath();
		arcPath(x, y, width, height, 0, 360);
		fill();
	}

	@Override
	public Color getColor() {
		return currentColor;
	}

	@Override
	public Font getFont() {
		return currentFont;
	}

	@Override
	public FontMetrics getFontMetrics(Font font) {
		return new HTMLCanvasFontMetrics(this, font);
	}

	@Override
	public void setColor(Color c) {
		currentColor = c;
		String cssc = cssColor(c);
		queue("assign", "fillStyle", cssc, "strokeStyle", cssc);
	}

	@Override
	public void setFont(Font f) {
		currentFont = f;
		queue("font", cssFont(f));
	}

	@Override
	public void setPaintMode() {
		// TODO Auto-generated method stub
	}

	@Override
	public void setXORMode(Color arg0) {
		// TODO Auto-generated method stub
	}


}
