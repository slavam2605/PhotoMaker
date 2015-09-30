import sun.security.provider.PolicySpiFile;
import sun.util.resources.cldr.fr.CalendarData_fr_MQ;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Моклев Вячеслав
 */
public class Main {

    private static class MyPanel extends JPanel {
        private BufferedImage bufferedImage;

        public MyPanel(BufferedImage bufferedImage) {
            super();
            this.bufferedImage = bufferedImage;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(bufferedImage, 0, 0, null);
        }
    }

    private static BufferedImage compressImage(BufferedImage bufferedImage, int targetWidth, int targetHeight) {
        if (targetWidth == bufferedImage.getWidth() && targetHeight == bufferedImage.getHeight()) {
            return bufferedImage;
        }
        int scale = (int) Math.floor(Math.min(
                (float) bufferedImage.getWidth() / targetWidth,
                (float) bufferedImage.getHeight() / targetHeight
        ));
        if (scale < 1) {
            System.err.println("Original image is smaller than requested size");
            return null;
        }
        int xOffset = (bufferedImage.getWidth() - targetWidth * scale) / 2;
        int yOffset = (bufferedImage.getHeight() - targetHeight * scale) / 2;
        BufferedImage result = new BufferedImage(
                targetWidth,
                targetHeight,
                BufferedImage.TYPE_3BYTE_BGR
        );
        int[] dest = new int[3 * result.getWidth() * result.getHeight()];
        int[] srcRGB = bufferedImage.getRGB(
                0,
                0,
                bufferedImage.getWidth(),
                bufferedImage.getHeight(),
                null,
                0,
                bufferedImage.getWidth()
        );
        for (int i = 0; i < result.getWidth(); i++) {
            for (int j = 0; j < result.getHeight(); j++) {
                long r = 0;
                long g = 0;
                long b = 0;
                for (int k = 0; k < scale; k++) {
                    for (int l = 0; l < scale; l++) {
                        int rgb = srcRGB[i * scale + k + xOffset + (j * scale + l + yOffset) * bufferedImage.getWidth()];
                        r += rgb & 0xFF;
                        g += (rgb >> 8) & 0xFF;
                        b += (rgb >> 16) & 0xFF;
                    }
                }
                r /= scale * scale;
                g /= scale * scale;
                b /= scale * scale;
                dest[3 * (i + j * result.getWidth())] = (int) b;
                dest[3 * (i + j * result.getWidth()) + 1] = (int) g;
                dest[3 * (i + j * result.getWidth()) + 2] = (int) r;
            }
        }
        result.getRaster().setPixels(0, 0, result.getWidth(), result.getHeight(), dest);
        return result;
    }

    private static long sqr(long x) {
        return x * x;
    }

    private static double dist(BufferedImage bf1, BufferedImage bf2, int xOffset, int yOffset) {
        int width = bf1.getWidth();
        int height = bf1.getHeight();
        int[] rgb1 = bf1.getRGB(
                0,
                0,
                width,
                height,
                null,
                0,
                width
        );
        int[] rgb2 = bf2.getRGB(
                xOffset,
                yOffset,
                width,
                height,
                null,
                0,
                width
        );
        int method = 2;
        if (method == 1) {
            long r1 = 0;
            long g1 = 0;
            long b1 = 0;
            long r2 = 0;
            long g2 = 0;
            long b2 = 0;
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    int p1 = rgb1[i + j * width];
                    int p2 = rgb2[i + j * width];
                    r1 += p1 & 0xFF;
                    g1 += (p1 >> 8) & 0xFF;
                    b1 += (p1 >> 16) & 0xFF;
                    r2 += p2 & 0xFF;
                    g2 += (p2 >> 8) & 0xFF;
                    b2 += (p2 >> 16) & 0xFF;
                }
            }
            //noinspection SuspiciousNameCombination
            return Math.sqrt((double) (sqr(r1 - r2) + sqr(g1 - g2) + sqr(b1 - b2)) / sqr(width) / sqr(height) / 3.0);
        }
        if (method == 2) {
            long r = 0;
            long g = 0;
            long b = 0;
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    int p1 = rgb1[i + j * width];
                    int p2 = rgb2[i + j * width];
                    r += sqr((p1 & 0xFF) - (p2 & 0xFF));
                    g += sqr(((p1 >> 8) & 0xFF) - ((p2 >> 8) & 0xFF));
                    b += sqr(((p1 >> 16) & 0xFF) - ((p2 >> 16) & 0xFF));
                }
            }
            int submethod = 2;
            if (submethod == 1) {
                return Math.sqrt((double) (r + g + b));
            } else {
                return Math.sqrt(r) + Math.sqrt(g) + Math.sqrt(b);
            }
        }
        return 0;
    }

    private static double cachedist(int a, int b) {
        return cache1.get(a).dist(cache2[b]);
    }

    private static BufferedImage makeGrid(BufferedImage[] images, int w, int h) {
        // assuming images.size() = w * h, \forall i, j: images[i].size = images[j].size
        int width = images[0].getWidth();
        int height = images[0].getHeight();
        BufferedImage result = new BufferedImage(width * w, height * h, BufferedImage.TYPE_3BYTE_BGR);
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                for (int k = 0; k < width; k++) {
                    for (int l = 0; l < height; l++) {
                        result.setRGB(
                                i * width + k,
                                j * height + l,
                                images[i + j * w].getRGB(k, l)
                        );
                    }
                }
            }
        }
        return result;
    }

    private static List<String> filenames;

    public static final int sampleW = 15;
    public static final int sampleH = 10;

    private static class Counter {
        private int x;
        public Counter() {
            x = 0;
        }
        public void inc() {
            x++;
        }
        public int get() {
            return x;
        }
    }

    private static class RGB {
        int r;
        int g;
        int b;

        public RGB(long r, long g, long b) {
            this.r = (int) r;
            this.g = (int) g;
            this.b = (int) b;
        }

        public double dist(RGB other) {
            return Math.sqrt((double) (sqr(r - other.r) + sqr(g - other.g) + sqr(b - other.b)) / 3.0);
        }

    }

    private static RGB cacheColor(BufferedImage bf, int xOffset, int yOffset, int width, int height) {
        int[] rgb = bf.getRGB(
                xOffset,
                yOffset,
                width,
                height,
                null,
                0,
                width
        );
        long r = 0;
        long g = 0;
        long b = 0;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                int p = rgb[i + j * width];
                r += p & 0xFF;
                g += (p >> 8) & 0xFF;
                b += (p >> 16) & 0xFF;
            }
        }
        return new RGB(r / width / height, g / width / height, b / width / height);
    }

    private static List<RGB> cache1 = new ArrayList<>();
    private static RGB[] cache2;

    private static BufferedImage[] loadImages() throws IOException {
        filenames = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("list.txt")));
        br.lines().forEach(filenames::add);
        List<BufferedImage> result = new ArrayList<>();
        final Counter c = new Counter();
        Files.list(new File("data\\").toPath()).forEach(
                (Path p) -> {
                    try {
                        c.inc();
                        //if (c.get() > 4590) {
                        BufferedImage bf = compressImage(ImageIO.read(p.toFile()), sampleW, sampleH);
                        //ImageIO.write(bf, "bmp", new File("data\\" + (c.get() - 1) + ".bmp"));
                        result.add(bf);
                        cache1.add(cacheColor(bf, 0, 0, sampleW, sampleH));
                        System.out.println(c.get());
                        //filenames.add(p.toFile().getName());
                        //}
                    } catch (IOException ignored) {
                    }
                }
        );
        return result.toArray(new BufferedImage[result.size()]);
    }

    private static String getExt(String s) {
        int i = s.length() - 1;
        while (s.charAt(i) != '.') {
            i--;
        }
        return s.substring(i);
    }

    public static void main(String[] args) throws IOException {
        long time = System.nanoTime();
        BufferedImage bufferedImage = compressImage(ImageIO.read(new File("in.jpg")), 2400, 1320);
        if (bufferedImage == null) {
            return;
        }
        BufferedImage image;
        if (true) {
            BufferedImage[] images = loadImages();
            System.out.println("Images loaded: " + images.length);

            int xCount = bufferedImage.getWidth() / sampleW;
            int yCount = bufferedImage.getHeight() / sampleH;
            System.out.println(yCount);

            cache2 = new RGB[xCount * yCount];
            for (int j = 0; j < yCount; j++) {
                for (int i = 0; i < xCount; i++) {
                    cache2[i + j * xCount] = cacheColor(bufferedImage, sampleW * i, sampleH * j, sampleW, sampleH);
                }
            }

            BufferedImage[] imgArray = new BufferedImage[xCount * yCount];
            //StringBuilder sb = new StringBuilder();
            PrintWriter pw = new PrintWriter(new FileOutputStream("copy.bat"));
            for (int j = 0; j < yCount; j++) {
                pw.print("montage ");
                for (int i = 0; i < xCount; i++) {
                    final int ci = i;
                    final int cj = j;
                    if (false) {
                        int minInd = 0;
                        double minDist = dist(images[0], bufferedImage, ci * sampleW, cj * sampleH);
                        for (int k = 1; k < images.length; k++) {
                            double newMin = dist(images[k], bufferedImage, ci * sampleW, cj * sampleH);
                            if (newMin < minDist) {
                                minInd = k;
                                minDist = newMin;
                            }
                        }
                        imgArray[i + j * xCount] = images[minInd];
                        /*imgArray[i + j * xCount] =
                                Arrays.stream(images).min((BufferedImage a, BufferedImage b) -> Double.compare(
                                                dist(a, bufferedImage, ci * sampleW, cj * sampleH),
                                                dist(b, bufferedImage, ci * sampleW, cj * sampleH)
                                        )
                                ).get();*/
                    } else {
                        int minInd = 0;
                        double minDist = cachedist(0, ci + cj * xCount);
                        for (int k = 1; k < images.length; k++) {
                            double newMin = cachedist(k, ci + cj * xCount);
                            if (newMin < minDist) {
                                minInd = k;
                                minDist = newMin;
                            }
                        }
                        //sb.append(filenames.get(minInd)).append(" ");
                        pw.print("\"" + filenames.get(minInd) + "\" ");
                        imgArray[i + j * xCount] = images[minInd];
                    }
                }
                pw.println("-tile 1x -resize 60x90 -geometry +0+0 folder\\" + j + ".JPG");
                System.out.print(j + ", ");
            }
            System.out.println();
            //pw.print("montage ");
            //pw.print(sb.toString());
            //pw.print("-resize 20x30 -tile " + xCount + "x" + yCount + " -geometry 20x30+0+0 out.png");
            pw.close();
            image = makeGrid(imgArray, xCount, yCount);
        } else {
            image = bufferedImage;
        }
        ImageIO.write(image, "png", new File("out.png"));
        System.out.println("Время: " + (System.nanoTime() - time) / 1000000000 + " секунд");
        JFrame jFrame = new JFrame("Lel");
        MyPanel myPanel = new MyPanel(image);
        myPanel.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
        jFrame.add(myPanel);
        jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jFrame.pack();
        jFrame.setVisible(true);
    }

}
