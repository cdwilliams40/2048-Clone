package android.graphics;
public class Typeface {
    public static final int NORMAL=0, BOLD=1;
    public static final Typeface SERIF=new Typeface("Serif",NORMAL);
    public static final Typeface SANS_SERIF=new Typeface("SansSerif",NORMAL);
    public final String family; public final int style;
    private Typeface(String f,int s){family=f;style=s;}
    public static Typeface create(Typeface base,int style){return new Typeface(base.family,style);}
}
