package android.graphics;
import java.awt.Font;
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Align { LEFT, CENTER, RIGHT }
    private int color = 0xFF000000;
    private Style style = Style.FILL;
    private float strokeWidth = 0f;
    private float textSize = 12f;
    private Typeface typeface = Typeface.SANS_SERIF;
    private Shader shader;
    private boolean antiAlias;
    private Align align = Align.LEFT;

    public Paint(){}
    public Paint(int flags){antiAlias=(flags&ANTI_ALIAS_FLAG)!=0;}

    public void reset(){color=0xFF000000;style=Style.FILL;strokeWidth=0f;textSize=12f;
        typeface=Typeface.SANS_SERIF;shader=null;align=Align.LEFT;}

    public int getColor(){return color;}
    public void setColor(int c){color=c;}
    public int getAlpha(){return Color.alpha(color);}
    public void setAlpha(int a){color=Color.argb(a&0xff,Color.red(color),Color.green(color),Color.blue(color));}
    public Style getStyle(){return style;}
    public void setStyle(Style s){style=s;}
    public float getStrokeWidth(){return strokeWidth;}
    public void setStrokeWidth(float w){strokeWidth=w;}
    public float getTextSize(){return textSize;}
    public void setTextSize(float s){textSize=s;}
    public Typeface getTypeface(){return typeface;}
    public Typeface setTypeface(Typeface t){typeface=t;return t;}
    public Shader getShader(){return shader;}
    public Shader setShader(Shader s){shader=s;return s;}
    public boolean isAntiAlias(){return antiAlias;}
    public void setAntiAlias(boolean v){antiAlias=v;}
    public Align getTextAlign(){return align;}
    public void setTextAlign(Align a){align=a;}

    java.awt.Font awtFont(){
        int s = (typeface!=null && typeface.style==Typeface.BOLD) ? Font.BOLD : Font.PLAIN;
        String fam = (typeface!=null) ? typeface.family : "SansSerif";
        return new Font(fam, s, 10).deriveFont(textSize);
    }
    public float measureText(String text){
        return (float) awtFont().getStringBounds(text, Canvas.FRC).getWidth();
    }
    public float ascent(){
        java.awt.font.LineMetrics m = awtFont().getLineMetrics("Ag", Canvas.FRC);
        return -m.getAscent();
    }
    public float descent(){
        java.awt.font.LineMetrics m = awtFont().getLineMetrics("Ag", Canvas.FRC);
        return m.getDescent();
    }
}
