package android.graphics;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.geom.Ellipse2D;
public class Path {
    public enum Direction { CW, CCW }
    public final Path2D.Float path = new Path2D.Float();
    public void moveTo(float x,float y){path.moveTo(x,y);}
    public void lineTo(float x,float y){path.lineTo(x,y);}
    public void close(){path.closePath();}
    public void reset(){path.reset();}
    public void addRoundRect(RectF r,float rx,float ry,Direction d){
        path.append(new RoundRectangle2D.Float(r.left,r.top,r.width(),r.height(),rx*2,ry*2),false);
    }
    public void addRoundRect(RectF r,float[] radii,Direction d){
        // Only the uniform-corner cases are used by the game; approximate with
        // the largest radius, which is what the artwork expects visually.
        float m=0; for(float v: radii) m=Math.max(m,v);
        path.append(new RoundRectangle2D.Float(r.left,r.top,r.width(),r.height(),m*2,m*2),false);
    }
    public void addCircle(float cx,float cy,float r,Direction d){
        path.append(new Ellipse2D.Float(cx-r,cy-r,r*2,r*2),false);
    }
}
