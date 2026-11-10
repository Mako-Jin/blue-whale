package com.bluewhale.pdm.collab.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 在线预览格式判定单元测试。
 *
 * @author BlueWhale
 */
class PdmDocPreviewRulesTest {

    @Test
    void pdfIsNative2dPreview() {
        PdmDocPreviewRules.Decision decision =
                PdmDocPreviewRules.evaluate("2D", "图纸.pdf", "application/pdf");

        assertEquals(PdmDocPreviewStatus.SUCCESS, decision.status());
        assertEquals("PDF", decision.viewerHint());
        assertEquals(PdmDocPreviewStatus.FORMAT_ORIGINAL, decision.targetFormat());
    }

    @Test
    void imageIsNative2dPreview() {
        PdmDocPreviewRules.Decision decision =
                PdmDocPreviewRules.evaluate("2D", "扫描件.png", "image/png");

        assertEquals(PdmDocPreviewStatus.SUCCESS, decision.status());
        assertEquals("IMAGE", decision.viewerHint());
    }

    @Test
    void officeRequiresExternalConverter() {
        PdmDocPreviewRules.Decision decision =
                PdmDocPreviewRules.evaluate("2D", "说明书.docx", "application/octet-stream");

        assertEquals(PdmDocPreviewStatus.UNSUPPORTED, decision.status());
        assertNotNull(decision.message());
        assertEquals(true, decision.message().contains("LibreOffice"));
    }

    @Test
    void stlIsNative3dPreview() {
        PdmDocPreviewRules.Decision decision =
                PdmDocPreviewRules.evaluate("3D", "壳体.stl", "model/stl");

        assertEquals(PdmDocPreviewStatus.SUCCESS, decision.status());
        assertEquals("STL", decision.viewerHint());
    }

    @Test
    void stepRequiresFreeCadConverter() {
        PdmDocPreviewRules.Decision decision =
                PdmDocPreviewRules.evaluate("3D", "壳体.step", "application/octet-stream");

        assertEquals(PdmDocPreviewStatus.UNSUPPORTED, decision.status());
        assertNotNull(decision.message());
    }

    @Test
    void unknownFormatIsUnsupported() {
        PdmDocPreviewRules.Decision decision =
                PdmDocPreviewRules.evaluate("2D", "文件.abc", "application/octet-stream");

        assertEquals(PdmDocPreviewStatus.UNSUPPORTED, decision.status());
    }
}
