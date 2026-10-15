package com.sltours.media;
import java.awt.image.Raster;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/**
 * Confirms that an upload whose first bytes look like a picture is a complete, usable picture. JPEG and PNG are
 * decoded in full (at reduced resolution), so damaged or partly uploaded files are refused. The JDK has no WebP
 * decoder, so WebP files are checked for a complete container and readable dimensions instead.
 */
final class ImageCheck {
    static final int MIN_SIDE = 100;
    static final long MAX_PIXELS = 100_000_000L;
    private static final int PREVIEW_SIDE = 512;

    private ImageCheck() {}

    /** Rejected uploads: the message is safe to show to the person uploading. */
    static final class Rejected extends IllegalArgumentException {
        Rejected(String message) { super(message); }
    }

    static void verify(MediaService.Kind kind, byte[] bytes) {
        int[] size = kind == MediaService.Kind.WEBP ? webpSize(bytes) : decode(kind, bytes);
        if (size[0] < MIN_SIDE || size[1] < MIN_SIDE)
            throw new Rejected("Photos must be at least " + MIN_SIDE + " × " + MIN_SIDE + " pixels");
    }

    private static int[] decode(MediaService.Kind kind, byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(kind == MediaService.Kind.JPEG ? "jpeg" : "png");
            if (!readers.hasNext()) throw new IllegalStateException("No image reader for " + kind);
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) throw new Rejected("This photo is too large. Use a picture under 100 megapixels.");
                // Decoders report a cut-off file as a warning and fill the rest with grey; treat that as damage.
                List<String> warnings = new ArrayList<>();
                reader.addIIOReadWarningListener((source, warning) -> warnings.add(warning));
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, (int) Math.ceil(Math.max(width, height) / (double) PREVIEW_SIDE));
                param.setSourceSubsampling(step, step, 0, 0);
                // Rasters skip colour conversion, so CMYK JPEGs from design tools are accepted.
                Raster raster = reader.canReadRaster() ? reader.readRaster(0, param) : reader.read(0, param).getRaster();
                if (raster == null || !warnings.isEmpty()) throw unreadable();
                return new int[]{width, height};
            } finally {
                reader.dispose();
            }
        } catch (Rejected e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw unreadable();
        }
    }

    /** Reads the dimensions from a complete RIFF/WebP container (lossy VP8, lossless VP8L or extended VP8X). */
    static int[] webpSize(byte[] b) {
        if (b.length < 30) throw unreadable();
        long riffSize = le32(b, 4);
        long chunkSize = le32(b, 16);
        if (8 + riffSize > b.length || 20 + chunkSize > 8 + riffSize) throw unreadable();
        String chunk = new String(b, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return switch (chunk) {
            case "VP8X" -> new int[]{1 + le24(b, 24), 1 + le24(b, 27)};
            case "VP8 " -> {
                if ((b[23] & 0xFF) != 0x9D || (b[24] & 0xFF) != 0x01 || (b[25] & 0xFF) != 0x2A) throw unreadable();
                yield new int[]{le16(b, 26) & 0x3FFF, le16(b, 28) & 0x3FFF};
            }
            case "VP8L" -> {
                if ((b[20] & 0xFF) != 0x2F) throw unreadable();
                long bits = le32(b, 21);
                yield new int[]{(int) (bits & 0x3FFF) + 1, (int) ((bits >> 14) & 0x3FFF) + 1};
            }
            default -> throw unreadable();
        };
    }

    private static Rejected unreadable() {
        return new Rejected("This picture could not be read. It may be damaged or only partly uploaded; take or choose it again.");
    }
    private static int le16(byte[] b, int at) { return (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8; }
    private static int le24(byte[] b, int at) { return le16(b, at) | (b[at + 2] & 0xFF) << 16; }
    private static long le32(byte[] b, int at) { return (le24(b, at) | (long) (b[at + 3] & 0xFF) << 24); }
}
