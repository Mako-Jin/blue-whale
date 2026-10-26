package com.bluewhale.contract.esign;

import com.bluewhale.contract.dto.SignExecuteRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

/**
 * PDF 视觉盖章服务（Apache PDFBox）。
 *
 * <p>支持单页落章与骑缝章：骑缝章按 PDF 页数均分印章图宽度，逐页加盖到页面右侧切边，
 * 每页只显示一段，合起来构成完整印章。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfStampService {

    /** PDF 文件头魔数 */
    private static final String PDF_MAGIC = "%PDF";

    /** 单页落章默认宽度（PDF 点） */
    private static final float DEFAULT_WIDTH = 120F;

    /** 单页落章默认高度（PDF 点） */
    private static final float DEFAULT_HEIGHT = 120F;

    /** 骑缝章默认总宽度（PDF 点） */
    private static final float DEFAULT_CROSS_WIDTH = 240F;

    /** 骑缝章默认高度（PDF 点） */
    private static final float DEFAULT_CROSS_HEIGHT = 120F;

    /** 落章距页面底边默认距离（PDF 点） */
    private static final float DEFAULT_BOTTOM_MARGIN = 72F;

    /** 落章距页面右边默认距离（PDF 点） */
    private static final float DEFAULT_RIGHT_MARGIN = 72F;

    /** 内置印章图片尺寸（像素） */
    private static final int BUILTIN_SEAL_SIZE = 400;

    private final FileClient fileClient;

    /**
     * 印章图片结果。
     *
     * @param bytes      签章后 PDF 字节
     * @param stampedPages 实际盖章页数
     */
    public record StampResult(byte[] bytes, int stampedPages) {
    }

    /**
     * 确保正文为合法 PDF：非法或为空时生成 2 页占位 PDF。
     *
     * @param original 原始正文
     * @param code     合同编号
     * @param title    合同标题
     * @param amount   合同金额
     * @return 可用 PDF 字节
     */
    public byte[] ensurePdf(byte[] original, String code, String title, BigDecimal amount) {
        if (original != null && original.length > 4
                && PDF_MAGIC.equals(new String(original, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1))) {
            return original;
        }
        return placeholderPdf(code, title, amount);
    }

    /**
     * 加载印章图片：优先使用 whale-file 中的印章图，缺失或非法时回落到内置印章。
     *
     * @param imageFileId 印章图片文件ID
     * @return PNG 字节
     */
    public byte[] loadSealImage(Long imageFileId) {
        if (imageFileId != null) {
            try {
                byte[] bytes = fileClient.download(imageFileId);
                if (ImageIO.read(new ByteArrayInputStream(bytes)) != null) {
                    return bytes;
                }
                log.warn("印章图片不是合法图片，回落内置印章: fileId={}", imageFileId);
            } catch (Exception ex) {
                log.warn("读取印章图片失败，回落内置印章: fileId={}, error={}", imageFileId, ex.getMessage());
            }
        }
        return builtInSeal();
    }

    /**
     * 加盖视觉印章。
     *
     * @param pdfBytes PDF 字节
     * @param sealPng  印章 PNG 字节
     * @param request  落章参数
     * @return 盖章结果
     */
    public StampResult stamp(byte[] pdfBytes, byte[] sealPng, SignExecuteRequest request) {
        try (PDDocument document = PDDocument.load(pdfBytes);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            int pageCount = document.getNumberOfPages();
            PDImageXObject image = PDImageXObject.createFromByteArray(document, sealPng, "seal");
            boolean crossPage = Boolean.TRUE.equals(request.getCrossPage());
            int stampedPages;
            if (crossPage) {
                stampCrossPage(document, image, request, pageCount);
                stampedPages = pageCount;
            } else {
                stampSinglePage(document, image, request, pageCount);
                stampedPages = 1;
            }
            document.save(output);
            return new StampResult(output.toByteArray(), stampedPages);
        } catch (Exception ex) {
            throw new IllegalStateException("PDF 盖章失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 单页落章。
     *
     * @param document  PDF 文档
     * @param image     印章图片
     * @param request   落章参数
     * @param pageCount 总页数
     * @throws Exception 写入异常
     */
    private void stampSinglePage(PDDocument document, PDImageXObject image, SignExecuteRequest request,
                                 int pageCount) throws Exception {
        int index = request.getPage() == null ? 0 : Math.max(0, Math.min(request.getPage() - 1, pageCount - 1));
        PDPage page = document.getPage(index);
        PDRectangle box = page.getMediaBox();
        float width = positive(request.getWidth(), DEFAULT_WIDTH);
        float height = positive(request.getHeight(), DEFAULT_HEIGHT);
        float x = request.getX() == null ? box.getWidth() - width - DEFAULT_RIGHT_MARGIN : request.getX();
        float y = request.getY() == null ? DEFAULT_BOTTOM_MARGIN : request.getY();
        Matrix matrix = new Matrix(width, 0, 0, height, x, y);
        float rotation = request.getRotation() == null ? 0F : request.getRotation();
        if (rotation != 0F) {
            Matrix rotate = Matrix.getRotateInstance(Math.toRadians(rotation), x + width / 2, y + height / 2);
            matrix = rotate.multiply(matrix);
        }
        try (PDPageContentStream stream = appendStream(document, page)) {
            stream.drawImage(image, matrix);
        }
    }

    /**
     * 骑缝章：按页数均分印章宽度，逐页裁切加盖到右侧切边。
     *
     * @param document  PDF 文档
     * @param image     印章图片
     * @param request   落章参数（width 为印章总宽）
     * @param pageCount 总页数
     * @throws Exception 写入异常
     */
    private void stampCrossPage(PDDocument document, PDImageXObject image, SignExecuteRequest request,
                                int pageCount) throws Exception {
        float totalWidth = positive(request.getWidth(), DEFAULT_CROSS_WIDTH);
        float height = positive(request.getHeight(), DEFAULT_CROSS_HEIGHT);
        float sliceWidth = totalWidth / pageCount;
        float y = request.getY() == null ? DEFAULT_BOTTOM_MARGIN : request.getY();
        for (int index = 0; index < pageCount; index++) {
            PDPage page = document.getPage(index);
            float right = page.getMediaBox().getWidth();
            float regionX = right - sliceWidth;
            try (PDPageContentStream stream = appendStream(document, page)) {
                stream.saveGraphicsState();
                stream.addRect(regionX, y, sliceWidth, height);
                stream.clip();
                stream.drawImage(image, regionX - index * sliceWidth, y, totalWidth, height);
                stream.restoreGraphicsState();
            }
        }
    }

    /**
     * 追加内容流（保留既有内容）。
     *
     * @param document PDF 文档
     * @param page     目标页
     * @return 内容流
     * @throws Exception 创建异常
     */
    private PDPageContentStream appendStream(PDDocument document, PDPage page) throws Exception {
        return new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true);
    }

    /**
     * 生成 2 页占位合同 PDF（英文标签，避免标准字体缺中文字形）。
     *
     * @param code   合同编号
     * @param title  合同标题
     * @param amount 合同金额
     * @return PDF 字节
     */
    private byte[] placeholderPdf(String code, String title, BigDecimal amount) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font bold = PDType1Font.HELVETICA_BOLD;
            PDType1Font regular = PDType1Font.HELVETICA;
            String[] lines = {
                    "Contract No: " + safe(code),
                    "Category: " + asciiSafe(title),
                    "Amount: " + (amount == null ? "-" : amount.toPlainString()),
                    "This is a placeholder generated by BlueWhale e-sign (no uploaded PDF)."
            };
            for (int pageNo = 1; pageNo <= 2; pageNo++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(bold, 20);
                    stream.newLineAtOffset(72, 760);
                    stream.showText("CONTRACT");
                    stream.endText();
                    float y = 720;
                    for (String line : lines) {
                        stream.beginText();
                        stream.setFont(regular, 12);
                        stream.newLineAtOffset(72, y);
                        stream.showText(line);
                        stream.endText();
                        y -= 24;
                    }
                    stream.beginText();
                    stream.setFont(regular, 10);
                    stream.newLineAtOffset(72, 60);
                    stream.showText("Page " + pageNo + " / 2");
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("生成占位合同 PDF 失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 生成内置蓝色圆形印章 PNG（合同印章图片缺失时的兜底）。
     *
     * @return PNG 字节
     */
    private byte[] builtInSeal() {
        BufferedImage image = new BufferedImage(BUILTIN_SEAL_SIZE, BUILTIN_SEAL_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color sealColor = new Color(0x1E5AA8);
            graphics.setColor(sealColor);
            graphics.setStroke(new BasicStroke(8F));
            int margin = 20;
            graphics.drawOval(margin, margin, BUILTIN_SEAL_SIZE - margin * 2, BUILTIN_SEAL_SIZE - margin * 2);
            graphics.setStroke(new BasicStroke(3F));
            graphics.drawOval(margin + 14, margin + 14,
                    BUILTIN_SEAL_SIZE - (margin + 14) * 2, BUILTIN_SEAL_SIZE - (margin + 14) * 2);
            int centerX = BUILTIN_SEAL_SIZE / 2;
            int outerRadius = 96;
            int innerRadius = 40;
            Polygon star = new Polygon();
            for (int i = 0; i < 10; i++) {
                double angle = Math.PI / 2 + i * Math.PI / 5;
                int radius = i % 2 == 0 ? outerRadius : innerRadius;
                star.addPoint(centerX + (int) Math.round(radius * Math.cos(angle)),
                        centerX - (int) Math.round(radius * Math.sin(angle)));
            }
            graphics.fillPolygon(star);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            drawCentered(graphics, "BlueWhale", centerX, 300);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
            drawCentered(graphics, "CONTRACT SEAL", centerX, 340);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("生成内置印章失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 居中绘制文本。
     *
     * @param graphics 画布
     * @param text     文本
     * @param centerX  中心 X
     * @param baseline 基线 Y
     */
    private void drawCentered(Graphics2D graphics, String text, int centerX, int baseline) {
        int width = graphics.getFontMetrics().stringWidth(text);
        graphics.drawString(text, centerX - width / 2, baseline);
    }

    /**
     * 正数取值，否则使用默认值。
     *
     * @param value        原值
     * @param defaultValue 默认值
     * @return 生效值
     */
    private float positive(Float value, float defaultValue) {
        return value == null || value <= 0F ? defaultValue : value;
    }

    /**
     * 空串兜底。
     *
     * @param value 原值
     * @return 非空字符串
     */
    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /**
     * 转成标准 14 字体（WinAnsi）可编码的 ASCII 文本，非 ASCII 字符以 {@code ?} 替代。
     *
     * @param value 原值
     * @return 仅含可打印 ASCII 的字符串
     */
    private String asciiSafe(String value) {
        String text = safe(value);
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            builder.append(ch >= 0x20 && ch <= 0x7E ? ch : '?');
        }
        return builder.toString();
    }
}
