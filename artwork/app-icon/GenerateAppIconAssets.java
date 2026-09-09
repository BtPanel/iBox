import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

public final class GenerateAppIconAssets {
    private static final Color CORAL=Color.decode("#F26455"), CREAM=Color.decode("#FFF0CE"), DEPTH=Color.decode("#B93F3B");
    private static File root;

    public static void main(String[] args) throws Exception {
        root=new File(args.length==0 ? "." : args[0]).getCanonicalFile();
        File pack=new File(root,"artwork/app-icon");
        write(fullIcon(1024,false),new File(pack,"ibox-app-icon-1024.png"));
        write(markOnly(1024),new File(pack,"ibox-foreground-1024.png"));
        write(monochrome(1024),new File(pack,"ibox-monochrome-1024.png"));
        write(fullIcon(512,false),new File(pack,"ibox-play-store-512.png"));
        write(tvBanner(),new File(pack,"ibox-tv-banner-320x180.png"));
        writeIco(fullIcon(256,false),new File(pack,"ibox-favicon.ico"));
        write(preview(),new File(pack,"preview.png"));
        int[] sizes={48,72,96,144,192}; String[] densities={"mdpi","hdpi","xhdpi","xxhdpi","xxxhdpi"};
        for(int n=0;n<sizes.length;n++) {
            File dir=new File(root,"app/src/main/res/mipmap-"+densities[n]);
            write(fullIcon(sizes[n],true),new File(dir,"ic_launcher.png"));
            write(roundIcon(sizes[n]),new File(dir,"ic_launcher_round.png"));
        }
        write(fullIcon(1024,false),new File(root,"app/src/main/ic_launcher-playstore.png"));
        writeIco(fullIcon(256,false),new File(root,"app/src/main/assets/favicon.ico"));
        write(fullIcon(512,false),new File(root,"other/image/logo-1.png"));
    }

    private static BufferedImage fullIcon(int size,boolean rounded) {
        BufferedImage out=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB); Graphics2D g=out.createGraphics(); quality(g);
        if(rounded) g.setClip(new RoundRectangle2D.Double(0,0,size,size,size*.22,size*.22));
        g.setColor(CORAL); g.fillRect(0,0,size,size); drawMark(g,size); g.dispose(); return out;
    }
    private static BufferedImage roundIcon(int size) {
        BufferedImage out=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB); Graphics2D g=out.createGraphics(); quality(g); g.setClip(new Ellipse2D.Double(0,0,size,size)); g.setColor(CORAL); g.fillRect(0,0,size,size); drawMark(g,size); g.dispose(); return out;
    }
    private static BufferedImage markOnly(int size) { BufferedImage out=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB); Graphics2D g=out.createGraphics(); quality(g); drawMark(g,size); g.dispose(); return out; }
    private static BufferedImage monochrome(int size) { BufferedImage out=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB); Graphics2D g=out.createGraphics(); quality(g); double s=size/1024.0; g.scale(s,s); Area shape=new Area(new RoundRectangle2D.Double(204,244,616,526,184,184)); for(int x:new int[]{266,454,642}) { shape.subtract(new Area(new RoundRectangle2D.Double(x,280,116,54,54,54))); shape.subtract(new Area(new RoundRectangle2D.Double(x,680,116,54,54,54))); } shape.subtract(new Area(play())); g.setColor(Color.BLACK); g.fill(shape); g.dispose(); return out; }
    private static BufferedImage tvBanner() { BufferedImage out=new BufferedImage(320,180,BufferedImage.TYPE_INT_RGB); Graphics2D g=out.createGraphics(); quality(g); g.setColor(CORAL); g.fillRect(0,0,320,180); AffineTransform old=g.getTransform(); g.translate(85.5,16.6); g.scale(.1455,.1455); drawMark1024(g); g.setTransform(old); g.dispose(); return out; }
    private static BufferedImage preview() { BufferedImage out=new BufferedImage(1600,900,BufferedImage.TYPE_INT_RGB); Graphics2D g=out.createGraphics(); quality(g); g.setColor(Color.decode("#F5F3EE")); g.fillRect(0,0,1600,900); g.setColor(Color.decode("#17253D")); g.setFont(new Font("Microsoft YaHei",Font.BOLD,38)); g.drawString("iBox App Icon · Final Asset Preview",60,66); g.setColor(Color.decode("#626B76")); g.setFont(new Font("Microsoft YaHei",Font.PLAIN,19)); g.drawString("三色纯色 · 平面播放符号 · 手机 / TV / 主题图标",60,104); BufferedImage icon=fullIcon(1024,true); g.drawImage(icon,70,150,620,620,null); int[] sizes={192,96,64,32,16}; int x=790; for(int size:sizes){ int y=224+(192-size)/2; g.drawImage(icon,x,y,size,size,null); g.setColor(Color.decode("#626B76")); g.setFont(new Font("Segoe UI",Font.PLAIN,16)); g.drawString(size+" px",x,y+size+28); x+=size+38; } BufferedImage banner=tvBanner(); g.drawImage(banner,790,540,640,360,null); g.dispose(); return out; }
    private static void drawMark(Graphics2D g,int size) { double s=size/1024.0; g.scale(s,s); drawMark1024(g); }
    private static void drawMark1024(Graphics2D g) { g.setColor(DEPTH); g.fill(new RoundRectangle2D.Double(204,272,616,526,184,184)); g.setColor(CREAM); g.fill(new RoundRectangle2D.Double(204,244,616,526,184,184)); g.setColor(CORAL); for(int x:new int[]{266,454,642}) { g.fill(new RoundRectangle2D.Double(x,280,116,54,54,54)); g.fill(new RoundRectangle2D.Double(x,680,116,54,54,54)); } g.fill(play()); }
    private static Shape play() { Path2D p=new Path2D.Double(); p.moveTo(420,420); p.curveTo(420,388,444,372,472,387); p.lineTo(628,478); p.curveTo(657,495,657,517,628,534); p.lineTo(472,625); p.curveTo(444,640,420,624,420,592); p.closePath(); return p; }
    private static void write(BufferedImage image,File target) throws Exception { target.getParentFile().mkdirs(); ImageIO.write(image,"png",target); }
    private static void writeIco(BufferedImage image,File target) throws Exception { ByteArrayOutputStream png=new ByteArrayOutputStream(); ImageIO.write(image,"png",png); byte[] data=png.toByteArray(); target.getParentFile().mkdirs(); try(OutputStream out=new BufferedOutputStream(new FileOutputStream(target))) { out.write(new byte[]{0,0,1,0,1,0,0,0,0,0,1,0,32,0}); writeIntLE(out,data.length); writeIntLE(out,22); out.write(data); } }
    private static void writeIntLE(OutputStream out,int value) throws IOException { out.write(value&255); out.write((value>>>8)&255); out.write((value>>>16)&255); out.write((value>>>24)&255); }
    private static void quality(Graphics2D g) { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC); }
}
