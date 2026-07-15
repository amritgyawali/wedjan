package com.wedjan.api.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class BlurHashTest {

    @Test
    void encodesSolidColorImage() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0xE7, 0x2E, 0x77)); // brand pink
        graphics.fillRect(0, 0, 16, 16);
        graphics.dispose();

        String hash = BlurHash.encode(image, 4, 3);

        // 1 (size flag) + 1 (max AC) + 4 (DC) + 2 * (4*3 - 1) (AC) = 28
        assertThat(hash).hasSize(28);
        assertThat(hash).matches("[0-9A-Za-z#$%*+,\\-.:;=?@\\[\\]^_{|}~]+");
    }

    @Test
    void encodesGradientDeterministically() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                image.setRGB(x, y, new Color(x * 8, y * 8, 128).getRGB());
            }
        }

        assertThat(BlurHash.encode(image, 4, 3)).isEqualTo(BlurHash.encode(image, 4, 3));
    }
}
