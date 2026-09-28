import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipFile;

/**
 * Draws the round door panel textures (48x48, one continuous picture per wood) and the knob texture.
 * Colours come from each wood's vanilla door texture, read from the Minecraft client jar.
 *
 * <p>Run from the project root: {@code java tools/GenTextures.java [path/to/client-extra.jar]}
 * The output is committed, so the mod builds without running this.
 */
public class GenTextures {
    static final int S = 48;
    static final double C = 24;
    static final Path OUT = Path.of("src", "main", "resources", "assets", "dynamicdoors", "textures", "block");
    static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
            "bamboo", "crimson", "warped", "iron"};

    public static void main(String[] args) throws IOException {
        String jar = args.length > 0 ? args[0] : System.getProperty("user.home")
                + "/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar";
        Files.createDirectories(OUT);
        try (ZipFile zip = new ZipFile(jar)) {
            for (String wood : WOODS) {
                BufferedImage door = read(zip, "assets/minecraft/textures/block/" + wood + "_door_bottom.png");
                Palette pal = new Palette(door);
                int[][] px = new int[S][S];
                draw(wood, pal, new Random(wood.hashCode()), px);
                write(px, OUT.resolve(wood + "_round_door.png").toFile());
            }
        }
        write(knob(), OUT.resolve("round_door_knob.png").toFile());
    }

    // ---- palette ----

    /** Opaque colours of a vanilla texture sorted by brightness; tones are picked by quantile. */
    static final class Palette {
        final int[] sorted;

        Palette(BufferedImage img) {
            List<Integer> list = new ArrayList<>();
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int c = img.getRGB(x, y);
                    if ((c >>> 24) > 200) list.add(c | 0xFF000000);
                }
            }
            // Leave out the grey hinge/handle pixels of coloured woods.
            List<Integer> coloured = list.stream().filter(c -> chroma(c) >= 18).toList();
            if (coloured.size() > list.size() * 0.6) list = new ArrayList<>(coloured);
            list.sort((a, b) -> Double.compare(lum(a), lum(b)));
            sorted = list.stream().mapToInt(Integer::intValue).toArray();
        }

        int at(double t) {
            t = Math.max(0, Math.min(1, t));
            return sorted[(int) Math.round(t * (sorted.length - 1))];
        }
    }

    static double lum(int c) {
        return 0.299 * (c >> 16 & 255) + 0.587 * (c >> 8 & 255) + 0.114 * (c & 255);
    }

    static int chroma(int c) {
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static int shade(int c, double f) {
        return rgb((int) ((c >> 16 & 255) * f), (int) ((c >> 8 & 255) * f), (int) ((c & 255) * f));
    }

    static double r(double x, double y) {
        return Math.hypot(x + 0.5 - C, y + 0.5 - C);
    }

    // ---- design ----

    /** Tones as quantiles of the wood's palette, before mapping to colours. */
    static void draw(String wood, Palette pal, Random rnd, int[][] px) {
        double[][] t = new double[S][S];
        int board = switch (wood) {
            case "bamboo" -> 4;
            case "iron" -> 12;
            default -> 6;
        };
        boards(t, board, rnd, wood.equals("iron") ? 0.02 : 0.06);
        switch (wood) {
            case "oak" -> ring(t, 18.5, 20.5, 0.8, 0.25);
            case "spruce" -> {
            }
            case "birch" -> {
            }
            case "jungle" -> diamonds(t);
            case "dark_oak" -> tallPanels(t);
            case "mangrove" -> {
                ring(t, 19, 20, 0.15, -1);
                ring(t, 14, 15, 0.15, -1);
                ring(t, 9, 10, 0.15, -1);
            }
            case "iron" -> rivets(t);
            default -> {
            }
        }
        for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) px[y][x] = pal.at(t[y][x]);

        // Colour overlays that are not tones of the wood palette.
        switch (wood) {
            case "spruce" -> {
                strap(px, 11);
                strap(px, 34);
            }
            case "birch" -> porthole(px, pal, 24, 12, 6.5);
            case "acacia" -> slots(px, pal);
            case "warped" -> veins(px);
            case "crimson" -> strap(px, 22);
            case "cherry" -> porthole(px, pal, 24, 12, 5.5);
            default -> {
            }
        }
        // Dark rim where the panel meets the frame.
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                if (r(x, y) >= 22.6 && (px[y][x] >>> 24) != 0) px[y][x] = shade(pal.at(0.08), 1);
            }
        }
    }

    /** Vertical boards, full height: dark gap, lit left edge, shaded right edge and streaky grain. */
    static void boards(double[][] t, int w, Random rnd, double grain) {
        for (int b = 0; b * w < S; b++) {
            double base = 0.52 + rnd.nextGaussian() * 0.07;
            for (int dx = 0; dx < w && b * w + dx < S; dx++) {
                int x = b * w + dx;
                int y = 0;
                while (y < S) {
                    int len = 2 + rnd.nextInt(8);
                    double off = switch (rnd.nextInt(5)) {
                        case 0 -> -grain * 1.4;
                        case 1 -> grain;
                        default -> 0;
                    };
                    for (int k = 0; k < len && y < S; k++, y++) {
                        double v = base + off;
                        if (dx == 0) v = 0.04;
                        else if (dx == 1) v = base + 0.16;
                        else if (dx == w - 1) v = base - 0.14;
                        t[y][x] = v;
                    }
                }
            }
        }
    }

    /** A circular trim ring; {@code lit} &lt; 0 means a carved (dark) line. */
    static void ring(double[][] t, double r0, double r1, double tone, double lit) {
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                double r = r(x, y);
                if (r >= r0 && r < r1) t[y][x] = tone;
                else if (lit >= 0 && r >= r1 && r < r1 + 1) t[y][x] = lit;
            }
        }
    }

    /** Jungle: a carved chain of diamonds down the middle, top to bottom. */
    static void diamonds(double[][] t) {
        for (int y = 0; y < S; y++) {
            int phase = Math.abs((y % 8) - 4);
            for (int s : new int[]{-1, 1}) {
                int x = 24 + s * (phase + 1) - (s > 0 ? 1 : 0);
                t[y][x] = 0.06;
            }
            for (int s : new int[]{-1, 1}) {
                int x = 12 + s * (phase + 1) - (s > 0 ? 1 : 0);
                if (x >= 0) t[y][x] = 0.12;
                x = 36 + s * (phase + 1) - (s > 0 ? 1 : 0);
                if (x < S) t[y][x] = 0.12;
            }
        }
    }

    /** Dark oak: two recessed panels running the full height of the door. */
    static void tallPanels(double[][] t) {
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                boolean left = x >= 8 && x < 22, right = x >= 26 && x < 40;
                if (!(left || right) || r(x, y) >= 18) continue;
                boolean edgeTop = r(x, y - 1) >= 18 || r(x - 1, y) >= 18 || x == 8 || x == 26;
                boolean edgeBottom = r(x, y + 1) >= 18 || r(x + 1, y) >= 18 || x == 21 || x == 39;
                t[y][x] = edgeTop ? 0.05 : edgeBottom ? 0.7 : 0.3 + (x % 3 == 0 ? 0.04 : 0);
            }
        }
    }

    static void rivets(double[][] t) {
        for (int y = 4; y < S; y += 8) {
            for (int x = 3; x < S; x += 12) {
                t[y][x] = 0.95;
                t[y + 1][x + 1 < S ? x + 1 : x] = 0.1;
                t[y][x + 7] = 0.95;
                t[y + 1][x + 8 < S ? x + 8 : x + 7] = 0.1;
            }
        }
    }

    /** Spruce/crimson: an iron strap across the whole door with nails. */
    static void strap(int[][] px, int y0) {
        int dark = rgb(38, 38, 42), mid = rgb(72, 72, 78), light = rgb(112, 112, 118);
        for (int x = 0; x < S; x++) {
            px[y0][x] = light;
            px[y0 + 1][x] = mid;
            px[y0 + 2][x] = mid;
            px[y0 + 3][x] = dark;
            if (x % 6 == 3) px[y0 + 1][x] = rgb(150, 150, 156);
        }
    }

    /** A round four-pane window near the top. */
    static void porthole(int[][] px, Palette pal, double cx, double cy, double rad) {
        int glass = pal.at(0.97), glassShade = shade(pal.at(0.97), 0.88), frame = pal.at(0.12);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                if (d < rad - 1.2) {
                    boolean bar = Math.abs(x + 0.5 - cx) < 0.6 || Math.abs(y + 0.5 - cy) < 0.6;
                    px[y][x] = bar ? frame : (x + y) % 5 == 0 ? glassShade : glass;
                } else if (d < rad) {
                    px[y][x] = frame;
                }
            }
        }
    }

    /** Acacia: its vanilla slots, stretched into three see-through slots from bottom to top. */
    static void slots(int[][] px, Palette pal) {
        int edge = pal.at(0.05);
        for (int cx : new int[]{13, 24, 35}) {
            double reach = Math.sqrt(19 * 19 - (cx + 0.5 - C) * (cx + 0.5 - C)) - 1;
            for (int y = 0; y < S; y++) {
                if (Math.abs(y + 0.5 - C) > reach) continue;
                for (int x = cx - 2; x <= cx + 2; x++) {
                    boolean inside = x > cx - 2 && x < cx + 2 && Math.abs(y + 0.5 - C) < reach - 1;
                    px[y][x] = inside ? 0 : edge;
                }
            }
        }
    }

    /** Warped: wavy teal veins running the full height. */
    static void veins(int[][] px) {
        int vein = rgb(22, 182, 166), veinDark = rgb(14, 120, 110);
        for (int cx : new int[]{9, 24, 39}) {
            for (int y = 0; y < S; y++) {
                int x = (int) Math.round(cx + 2.2 * Math.sin(y / 3.2 + cx));
                if (x < 0 || x >= S) continue;
                px[y][x] = vein;
                if (x + 1 < S) px[y][x + 1] = veinDark;
            }
        }
    }

    /** Knob: a small dark iron texture with a lit top-left. */
    static int[][] knob() {
        int[][] px = new int[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int v = 70 + (15 - x - y) * 3 + ((x * 7 + y * 13) % 5) * 3;
                px[y][x] = rgb(v, v, v + 6);
            }
        }
        return px;
    }

    // ---- io ----

    static BufferedImage read(ZipFile zip, String path) throws IOException {
        try (InputStream in = zip.getInputStream(zip.getEntry(path))) {
            return ImageIO.read(in);
        }
    }

    static void write(int[][] px, File file) throws IOException {
        BufferedImage img = new BufferedImage(px[0].length, px.length, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < px.length; y++) for (int x = 0; x < px[0].length; x++) img.setRGB(x, y, px[y][x]);
        ImageIO.write(img, "png", file);
    }
}
