package android.graphics;
import java.awt.image.BufferedImage;
public class Bitmap {
    public enum Config { ARGB_8888, RGB_565 }
    public final BufferedImage image;
    private Bitmap(int w,int h){image=new BufferedImage(Math.max(1,w),Math.max(1,h),BufferedImage.TYPE_INT_ARGB);}
    public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(w,h);}
    public int getWidth(){return image.getWidth();}
    public int getHeight(){return image.getHeight();}
}
