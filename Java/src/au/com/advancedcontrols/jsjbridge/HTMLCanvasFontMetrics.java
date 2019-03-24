package au.com.advancedcontrols.jsjbridge;

import java.awt.Font;
import java.awt.FontMetrics;
import org.json.JSONObject;

public class HTMLCanvasFontMetrics extends FontMetrics {
	private static final long serialVersionUID = -3200278773768384497L;
	
	private HTMLCanvas canvas;
	private int ascent = 0, descent = 0;

	public HTMLCanvasFontMetrics(HTMLCanvas c, Font font) {
		super(font);
		canvas = c;
	}
	
	public int stringWidth(String str) {
		JSONObject textMetrics = (JSONObject)canvas.query("measureText", str);
		if (ascent == 0) {
			ascent = textMetrics.getInt("fontBoundingBoxAscent");
			descent = textMetrics.getInt("fontBoundingBoxDecent");
		}
		return textMetrics.getInt("width");
	}
	
	public int charsWidth(char[] data, int off, int len) {
		return stringWidth(new String(data, off, len));
	}
	
	public int charWidth(char ch) {
		return stringWidth(Character.toString(ch));
	}
	
	public int getAscent() {
		if (ascent == 0) stringWidth("");
		return ascent;
	}
	
	public int getDecent() {
		if (descent == 0) stringWidth("");
		return descent;
	}

}
