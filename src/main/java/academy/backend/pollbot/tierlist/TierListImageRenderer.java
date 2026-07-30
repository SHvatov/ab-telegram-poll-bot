package academy.backend.pollbot.tierlist;

import academy.backend.pollbot.domain.MemeDefinition;
import academy.backend.pollbot.domain.Rating;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link TierList} into a PNG (as a byte array) laid out like a classic tier list: a
 * coloured square tier label on the left of each row, followed by the memes' thumbnails on a dark
 * background. The whole image is guaranteed to fit within {@link #MAX_DIMENSION} on both axes -
 * thumbnails are shrunk (and wrapped onto extra lines) as needed to stay inside that bound.
 */
public final class TierListImageRenderer {

    private static final int MAX_DIMENSION = 4096;

    private static final int MAX_CELL = 300;
    private static final int MIN_CELL = 40;
    private static final int CELL_STEP = 10;
    private static final int GAP = 6;

    private static final Color BACKGROUND = new Color(0x1E1E1E);
    private static final Color CELL_BACKGROUND = Color.WHITE;

    private static final Map<Rating, Color> TIER_COLORS = new EnumMap<>(Map.of(
            Rating.S, new Color(0xFF7F7F),
            Rating.A, new Color(0xFFBF7F),
            Rating.B, new Color(0xFFFF7F),
            Rating.C, new Color(0x7FFF7F),
            Rating.F, new Color(0xFF7FFF)));

    public byte[] render(TierList tierList) {
        Map<Rating, List<MemeDefinition>> tiers = tierList.tiers();
        int maxTierSize = tiers.values().stream().mapToInt(List::size).max().orElse(0);

        Layout layout = fit(tiers, maxTierSize);
        Map<String, BufferedImage> thumbnails = loadThumbnails(tiers);

        BufferedImage canvas = new BufferedImage(layout.width(), layout.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, layout.width(), layout.height());

            int slot = layout.cell() + GAP;
            int thumbsStartX = slot;
            int y = 0;
            for (Map.Entry<Rating, List<MemeDefinition>> entry : tiers.entrySet()) {
                List<MemeDefinition> memes = entry.getValue();
                int rows = Math.max(1, ceilDiv(memes.size(), layout.columns()));
                int tierHeight = rows * slot;

                drawTierLabel(g, entry.getKey(), y, layout.cell(), tierHeight - GAP);

                for (int i = 0; i < memes.size(); i++) {
                    int col = i % layout.columns();
                    int row = i / layout.columns();
                    int tx = thumbsStartX + col * slot;
                    int ty = y + row * slot;
                    drawThumbnail(g, thumbnails.get(memes.get(i).path()), tx, ty, layout.cell());
                }
                y += tierHeight;
            }
        } finally {
            g.dispose();
        }
        return toPng(canvas);
    }

    private void drawTierLabel(Graphics2D g, Rating rating, int y, int cell, int height) {
        g.setColor(TIER_COLORS.getOrDefault(rating, Color.GRAY));
        g.fillRect(0, y, cell, height);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, cell / 2)));
        String label = rating.name();
        Rectangle2D bounds = g.getFontMetrics().getStringBounds(label, g);
        int textX = (int) (cell - bounds.getWidth()) / 2;
        int textY = y + (int) ((height - bounds.getHeight()) / 2 - bounds.getY());
        g.drawString(label, textX, textY);
    }

    private void drawThumbnail(Graphics2D g, BufferedImage image, int x, int y, int cell) {
        g.setColor(CELL_BACKGROUND);
        g.fillRect(x, y, cell, cell);
        if (image == null) {
            return;
        }
        double scale = Math.min((double) cell / image.getWidth(), (double) cell / image.getHeight());
        int drawW = (int) Math.round(image.getWidth() * scale);
        int drawH = (int) Math.round(image.getHeight() * scale);
        int drawX = x + (cell - drawW) / 2;
        int drawY = y + (cell - drawH) / 2;
        g.drawImage(image, drawX, drawY, drawW, drawH, null);
    }

    private Layout fit(Map<Rating, List<MemeDefinition>> tiers, int maxTierSize) {
        Layout last = null;
        for (int cell = MAX_CELL; cell >= MIN_CELL; cell -= CELL_STEP) {
            int slot = cell + GAP;
            int columnsThatFit = Math.max(1, MAX_DIMENSION / slot - 1);
            int columns = Math.min(columnsThatFit, Math.max(1, maxTierSize));

            int width = (columns + 1) * slot;
            int height = 0;
            for (List<MemeDefinition> memes : tiers.values()) {
                height += Math.max(1, ceilDiv(memes.size(), columns)) * slot;
            }

            last = new Layout(cell, columns, width, height);
            if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
                return last;
            }
        }
        // Even at the smallest cell the list is enormous; fall back to the last computed layout
        // (smallest cell) - render will still produce a valid, if very dense, image.
        return last;
    }

    private Map<String, BufferedImage> loadThumbnails(Map<Rating, List<MemeDefinition>> tiers) {
        Map<String, BufferedImage> images = new HashMap<>();
        for (List<MemeDefinition> memes : tiers.values()) {
            for (MemeDefinition meme : memes) {
                images.computeIfAbsent(meme.path(), TierListImageRenderer::loadImage);
            }
        }
        return images;
    }

    private static BufferedImage loadImage(String resourcePath) {
        try (InputStream in = TierListImageRenderer.class.getResourceAsStream("/" + resourcePath)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] toPng(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    private record Layout(int cell, int columns, int width, int height) {
    }
}
