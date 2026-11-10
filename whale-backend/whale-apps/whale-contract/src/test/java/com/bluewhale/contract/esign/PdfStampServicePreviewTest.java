package com.bluewhale.contract.esign;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 印章预览测试：低分辨率上限与半透明水印。
 *
 * @author BlueWhale
 */
class PdfStampServicePreviewTest {

    /** 预览最长边上限，与实现保持一致 */
    private static final int PREVIEW_MAX_EDGE = 240;

    /**
     * 内置印章（400×400）预览后最长边不超过上限，且与无水印缩放图存在像素差异。
     *
     * @throws Exception 图片解析异常
     */
    @Test
    void previewsBuiltInSealWithWatermark() throws Exception {
        PdfStampService service = new PdfStampService(mock(FileClient.class));

        byte[] preview = service.previewImage(null);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(preview));

        assertNotNull(image);
        assertEquals(PREVIEW_MAX_EDGE, Math.max(image.getWidth(), image.getHeight()));
        BufferedImage plain = plainScale(builtInSealSource(service), PREVIEW_MAX_EDGE);
        assertFalse(samePixels(plain, image), "预览图应包含半透明水印");
    }

    /**
     * 上传的超大印章图（800×400）按最长边等比缩放。
     *
     * @throws Exception 图片解析异常
     */
    @Test
    void scalesOversizedUploadedSeal() throws Exception {
        FileClient fileClient = mock(FileClient.class);
        when(fileClient.download(9L)).thenReturn(opaquePng(800, 400));
        PdfStampService service = new PdfStampService(fileClient);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(service.previewImage(9L)));

        assertNotNull(image);
        assertEquals(240, image.getWidth());
        assertEquals(120, image.getHeight());
        assertTrue(image.getWidth() <= PREVIEW_MAX_EDGE);
    }

    /**
     * 取内置印章原始图（通过公开加载方法）。
     *
     * @param service 被测服务
     * @return 原始图
     * @throws Exception 解析异常
     */
    private BufferedImage builtInSealSource(PdfStampService service) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(service.loadSealImage(null)));
    }

    /**
     * 按实现相同规则缩放到最长边。
     *
     * @param source  原图
     * @param maxEdge 最长边
     * @return 缩放图
     */
    private BufferedImage plainScale(BufferedImage source, int maxEdge) {
        double ratio = Math.min(1.0, (double) maxEdge / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /**
     * 逐像素比较。
     *
     * @param left  左图
     * @param right 右图
     * @return 完全一致时返回 {@code true}
     */
    private boolean samePixels(BufferedImage left, BufferedImage right) {
        if (left.getWidth() != right.getWidth() || left.getHeight() != right.getHeight()) {
            return false;
        }
        for (int y = 0; y < left.getHeight(); y++) {
            for (int x = 0; x < left.getWidth(); x++) {
                if (left.getRGB(x, y) != right.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 生成指定尺寸的不透明 PNG。
     *
     * @param width  宽
     * @param height 高
     * @return PNG 字节
     * @throws Exception 编码异常
     */
    private byte[] opaquePng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(java.awt.Color.ORANGE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
