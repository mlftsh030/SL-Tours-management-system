package com.sltours.media;
import com.sltours.support.Fixtures;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ImageCheckTest {
    private static final String UNREADABLE = "could not be read";

    @Test void completePhotosArePassed() {
        assertThatCode(() -> ImageCheck.verify(MediaService.Kind.JPEG, Fixtures.image("jpg", 1600, 1200))).doesNotThrowAnyException();
        assertThatCode(() -> ImageCheck.verify(MediaService.Kind.PNG, Fixtures.image("png", 300, 300))).doesNotThrowAnyException();
        assertThatCode(() -> ImageCheck.verify(MediaService.Kind.WEBP, losslessWebp(640, 480, 64))).doesNotThrowAnyException();
    }

    @Test void headerOnlyAndCutOffFilesAreRefused() {
        byte[] stub = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
        assertThatThrownBy(() -> ImageCheck.verify(MediaService.Kind.JPEG, stub)).hasMessageContaining(UNREADABLE);
        byte[] jpeg = Fixtures.image("jpg", 800, 600);
        assertThatThrownBy(() -> ImageCheck.verify(MediaService.Kind.JPEG, Arrays.copyOf(jpeg, jpeg.length / 2))).hasMessageContaining(UNREADABLE);
        byte[] png = Fixtures.image("png", 400, 400);
        assertThatThrownBy(() -> ImageCheck.verify(MediaService.Kind.PNG, Arrays.copyOf(png, png.length / 2))).hasMessageContaining(UNREADABLE);
        byte[] webp = losslessWebp(640, 480, 64);
        assertThatThrownBy(() -> ImageCheck.verify(MediaService.Kind.WEBP, Arrays.copyOf(webp, webp.length - 10))).hasMessageContaining(UNREADABLE);
    }

    @Test void tinyPicturesAreRefused() {
        assertThatThrownBy(() -> ImageCheck.verify(MediaService.Kind.JPEG, Fixtures.image("jpg", 40, 300))).hasMessageContaining("at least 100 × 100 pixels");
        assertThatThrownBy(() -> ImageCheck.verify(MediaService.Kind.WEBP, losslessWebp(16, 16, 32))).hasMessageContaining("at least 100 × 100 pixels");
    }

    /** A RIFF/WebP container with a lossless (VP8L) header for the given size, padded with {@code body} bytes. */
    private static byte[] losslessWebp(int width, int height, int body) {
        int bits = (width - 1) | (height - 1) << 14;
        int chunk = 5 + body;
        var out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes());
        le32(out, 4 + 8 + chunk);
        out.writeBytes("WEBPVP8L".getBytes());
        le32(out, chunk);
        out.write(0x2F);
        le32(out, bits);
        out.writeBytes(new byte[body]);
        return out.toByteArray();
    }
    private static void le32(ByteArrayOutputStream out, int value) {
        for (int i = 0; i < 4; i++) out.write(value >> (8 * i) & 0xFF);
    }
}
