package com.bluewhale.workflow.support.bpmn;

import com.bluewhale.workflow.support.ProcessModelException;
import com.bluewhale.workflow.support.ValidationCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link BpmnXmlSupport} 加固解析入口单元测试（防 XXE / 体积卡口）。
 *
 * @author BlueWhale
 */
class BpmnXmlSupportTest {

    @Test
    @DisplayName("空文本：报 XML_NOT_WELL_FORMED")
    void shouldRejectBlank() {
        ProcessModelException ex = assertThrows(ProcessModelException.class, () -> BpmnXmlSupport.parse("  "));

        assertEquals(ValidationCode.XML_NOT_WELL_FORMED, ex.getValidationCode());
    }

    @Test
    @DisplayName("非良构文本：报 XML_NOT_WELL_FORMED")
    void shouldRejectMalformed() {
        ProcessModelException ex = assertThrows(ProcessModelException.class, () -> BpmnXmlSupport.parse("<a><b></a>"));

        assertEquals(ValidationCode.XML_NOT_WELL_FORMED, ex.getValidationCode());
    }

    @Test
    @DisplayName("含 DOCTYPE：报 XML_DOCTYPE_FORBIDDEN")
    void shouldRejectDoctype() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><foo/>";

        ProcessModelException ex = assertThrows(ProcessModelException.class, () -> BpmnXmlSupport.parse(xml));

        assertEquals(ValidationCode.XML_DOCTYPE_FORBIDDEN, ex.getValidationCode());
    }

    @Test
    @DisplayName("DOCTYPE 中间插入换行/制表符仍被预判拦截")
    void shouldRejectDoctypeWithWhitespaceBypass() {
        String xml = "<?xml version=\"1.0\"?><!DOC\n\tTYPE foo><foo/>";

        ProcessModelException ex = assertThrows(ProcessModelException.class, () -> BpmnXmlSupport.parse(xml));

        assertEquals(ValidationCode.XML_DOCTYPE_FORBIDDEN, ex.getValidationCode());
    }

    @Test
    @DisplayName("超长文本：报 XML_TOO_LARGE")
    void shouldRejectTooLarge() {
        String xml = "<a>" + "x".repeat(BpmnXmlSupport.MAX_BPMN_XML_LENGTH) + "</a>";

        ProcessModelException ex = assertThrows(ProcessModelException.class, () -> BpmnXmlSupport.parse(xml));

        assertEquals(ValidationCode.XML_TOO_LARGE, ex.getValidationCode());
    }

    @Test
    @DisplayName("良构文本：返回 DOM 文档")
    void shouldParseWellFormed() {
        Document document = BpmnXmlSupport.parse("<definitions><process id=\"p1\"/></definitions>");

        assertNotNull(document);
        assertNotNull(document.getDocumentElement());
    }
}
