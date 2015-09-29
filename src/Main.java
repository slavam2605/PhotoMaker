import sun.security.provider.PolicySpiFile;
import sun.util.resources.cldr.fr.CalendarData_fr_MQ;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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

    public static final int sampleW = 20;
    public static final int sampleH = 20;

    private static BufferedImage[] loadImages() throws IOException {
        filenames = new ArrayList<>();
        List<BufferedImage> result = new ArrayList<>();
        Files.list(new File("data\\").toPath()).forEach(
                (Path p) -> {
                    try {
                        BufferedImage bf = compressImage(ImageIO.read(p.toFile()), sampleW, sampleH);
                        result.add(bf);
                        filenames.add(p.toFile().getName());
                    } catch (IOException ignored) {
                    }
                }
        );
        return result.toArray(new BufferedImage[result.size()]);
    }

    public static void main(String[] args) throws IOException {
        BufferedImage bufferedImage = compressImage(ImageIO.read(new File("data\\2.jpg")), 1920, 1080);
        if (bufferedImage == null) {
            return;
        }
        BufferedImage[] images = loadImages();
        System.out.println("Images loaded: " + images.length);

        int xCount = bufferedImage.getWidth() / sampleW;
        int yCount = bufferedImage.getHeight() / sampleH;
        System.out.println(xCount);
        BufferedImage[] imgArray = new BufferedImage[xCount * yCount];
        System.out.println(dist(images[0], bufferedImage, 0, 0));
        for (int i = 0; i < xCount; i++) {
            for (int j = 0; j < yCount; j++) {
                final int ci = i;
                final int cj = j;
                imgArray[i + j * xCount] =
                        Arrays.stream(images).min((BufferedImage a, BufferedImage b) -> Double.compare(
                                dist(a, bufferedImage, ci * sampleW, cj * sampleH),
                                dist(b, bufferedImage, ci * sampleW, cj * sampleH)
                        )
                ).get();
            }
            System.out.print(i + ", ");
        }
        System.out.println();
        BufferedImage image = makeGrid(imgArray, xCount, yCount);
        JFrame jFrame = new JFrame("Lel");
        MyPanel myPanel = new MyPanel(image);
        myPanel.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
        jFrame.add(myPanel);
        jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jFrame.pack();
        jFrame.setVisible(true);
    }

}
