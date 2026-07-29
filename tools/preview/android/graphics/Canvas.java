package android.graphics;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.*;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.List;

/** Java2D-backed stand-in for android.graphics.Canvas (harness only). */
public class Canvas {
    public static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    public final Graphics2D g;
    private final List<AffineTransform> xforms = new ArrayList<>();
    private final List<Shape> clips = new ArrayList<>();

    public Canvas(Bitmap bitmap){
        g = bitmap.image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    private static java.awt.Color awt(int c){return new java.awt.Color(Color.red(c),Color.green(c),Color.blue(c),Color.alpha(c));}

    private void apply(Paint p){
        if (p == null) { g.setPaint(awt(0xFF000000)); return; }
        Shader s = p.getShader();
        if (s instanceof LinearGradient){
            LinearGradient lg=(LinearGradient)s;
            float x0=lg.x0,y0=lg.y0,x1=lg.x1,y1=lg.y1;
            if (x0==x1 && y0==y1) y1=y0+1;
            g.setPaint(new GradientPaint(x0,y0,awt(lg.c0),x1,y1,awt(lg.c1)));
        } else {
            g.setPaint(awt(p.getColor()));
        }
        if (p.getStyle()==Paint.Style.STROKE){
            g.setStroke(new BasicStroke(Math.max(0.1f,p.getStrokeWidth()),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        }
    }

    private void paintShape(Shape shape, Paint p){
        apply(p);
        if (p!=null && p.getStyle()==Paint.Style.STROKE) g.draw(shape); else g.fill(shape);
    }

    public void drawRect(RectF r, Paint p){ paintShape(new Rectangle2D.Float(r.left,r.top,r.width(),r.height()), p); }
    public void drawRect(float l,float t,float rr,float b, Paint p){ paintShape(new Rectangle2D.Float(l,t,rr-l,b-t), p); }
    public void drawRoundRect(RectF r,float rx,float ry,Paint p){
        paintShape(new RoundRectangle2D.Float(r.left,r.top,r.width(),r.height(),rx*2,ry*2), p);
    }
    public void drawOval(RectF r, Paint p){ paintShape(new Ellipse2D.Float(r.left,r.top,r.width(),r.height()), p); }
    public void drawCircle(float cx,float cy,float rad,Paint p){
        paintShape(new Ellipse2D.Float(cx-rad,cy-rad,rad*2,rad*2), p);
    }
    public void drawAnalogArc(){}
    public void drawArc(RectF oval,float startAngle,float sweepAngle,boolean useCenter,Paint p){
        apply(p);
        java.awt.geom.Arc2D.Float a = new java.awt.geom.Arc2D.Float(
            oval.left, oval.top, oval.width(), oval.height(),
            -startAngle, -sweepAngle, useCenter ? java.awt.geom.Arc2D.PIE : java.awt.geom.Arc2D.OPEN);
        if (p.getStyle()==Paint.Style.STROKE){
            g.setStroke(new BasicStroke(Math.max(0.1f,p.getStrokeWidth()),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.draw(a);
        } else g.fill(a);
    }
    public void drawPath(Path path, Paint p){ paintShape(path.path, p); }
    public void drawLine(float x1,float y1,float x2,float y2,Paint p){
        apply(p);
        g.setStroke(new BasicStroke(Math.max(0.1f,p.getStrokeWidth()),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(x1,y1,x2,y2));
    }
    public void drawText(String text,float x,float y,Paint p){
        apply(p);
        g.setFont(p.awtFont());
        g.drawString(text,x,y);
    }
    public void drawBitmap(Bitmap b, float left, float top, Paint p){
        Composite old=g.getComposite();
        if (p!=null && p.getAlpha()<255) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,p.getAlpha()/255f));
        g.drawImage(b.image,(int)left,(int)top,null);
        g.setComposite(old);
    }
    public void drawBitmap(Bitmap b, Rect src, RectF dst, Paint p){
        Composite old=g.getComposite();
        if (p!=null && p.getAlpha()<255) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,p.getAlpha()/255f));
        g.drawImage(b.image,(int)dst.left,(int)dst.top,(int)Math.ceil(dst.width()),(int)Math.ceil(dst.height()),null);
        g.setComposite(old);
    }
    public int save(){ xforms.add(g.getTransform()); clips.add(g.getClip()); return xforms.size(); }
    public void restore(){ if(!xforms.isEmpty()){ g.setTransform(xforms.remove(xforms.size()-1)); g.setClip(clips.remove(clips.size()-1)); } }
    public void translate(float dx,float dy){ g.translate(dx,dy); }
    public void scale(float sx,float sy){ g.scale(sx,sy); }
    public void rotate(float deg){ g.rotate(Math.toRadians(deg)); }
    public void clipRect(RectF r){ g.clip(new Rectangle2D.Float(r.left,r.top,r.width(),r.height())); }
    public void clipPath(Path p){ g.clip(p.path); }
    public int getWidth(){ return 0; }
    public int getHeight(){ return 0; }
}
