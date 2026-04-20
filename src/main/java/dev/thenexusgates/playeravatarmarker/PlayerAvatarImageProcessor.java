package dev.thenexusgates.playeravatarmarker;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class PlayerAvatarImageProcessor {

    private PlayerAvatarImageProcessor() {}

    static byte[] process(byte[] rawPng, int size, int bgColorRGB, boolean enableBackground) {
        if (rawPng == null || rawPng.length == 0) return rawPng;
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(rawPng));
            if (src == null) return rawPng;

            BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            if (!enableBackground) {
                g.drawImage(src, 0, 0, size, size, null);
            } else {
                float strokeW = Math.max(2f, size / 24f);
                int inset = (int) Math.ceil(strokeW / 2.0);

                g.setColor(new Color(bgColorRGB));
                g.fillOval(inset, inset, size - 2 * inset, size - 2 * inset);

                int avatarPad = inset + Math.max(2, size / 20);
                int avatarDim = size - avatarPad * 2;
                g.setClip(new Ellipse2D.Double(avatarPad, avatarPad, avatarDim, avatarDim));
                g.drawImage(src, avatarPad, avatarPad, avatarDim, avatarDim, null);

                g.setClip(null);
                g.setColor(new Color(0, 0, 0, 100));
                g.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawOval(inset, inset, size - 2 * inset, size - 2 * inset);
            }

            g.dispose();

            if (enableBackground) {
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        int argb = out.getRGB(x, y);
                        if (((argb >>> 24) & 0xFF) > 0) {
                            out.setRGB(x, y, argb | 0xFF000000);
                        }
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            return rawPng;
        }
    }

    static byte[] createTransparentPng() {
        try {
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    static byte[] createFallbackMarkerPng(int size) {
        try {
            int iconSize = Math.max(16, size);
            BufferedImage out = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            g.setColor(new Color(28, 33, 40, 220));
            g.fillOval(1, 1, iconSize - 2, iconSize - 2);

            g.setColor(new Color(110, 123, 139, 255));
            int headSize = Math.max(6, iconSize / 3);
            int headX = (iconSize - headSize) / 2;
            int headY = Math.max(4, iconSize / 6);
            g.fillOval(headX, headY, headSize, headSize);

            int torsoWidth = Math.max(8, iconSize / 2);
            int torsoHeight = Math.max(6, iconSize / 3);
            int torsoX = (iconSize - torsoWidth) / 2;
            int torsoY = headY + headSize - Math.max(1, iconSize / 16);
            g.fill(new RoundRectangle2D.Float(
                    torsoX,
                    torsoY,
                    torsoWidth,
                    torsoHeight,
                    Math.max(4, iconSize / 5f),
                    Math.max(4, iconSize / 5f)));

            g.setColor(new Color(255, 255, 255, 38));
            g.setStroke(new BasicStroke(Math.max(1.5f, iconSize / 20f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(1, 1, iconSize - 2, iconSize - 2);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            return createTransparentPng();
        }
    }
}

