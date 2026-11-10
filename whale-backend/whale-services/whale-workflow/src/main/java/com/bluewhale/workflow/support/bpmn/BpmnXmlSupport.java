package com.bluewhale.workflow.support.bpmn;

import com.bluewhale.workflow.support.ProcessModelException;
import com.bluewhale.workflow.support.ValidationCode;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

/**
 * BPMN XML 安全支撑：加固的 DOM 解析入口（防 XXE / 实体膨胀 / 外部资源加载）。
 *
 * <p>加固项：禁用 DOCTYPE、禁用外部通用/参数实体、禁用外部 DTD·schema、禁用 XInclude、
 * 关闭实体展开、开启 {@code FEATURE_SECURE_PROCESSING}、空 {@code EntityResolver}，
 * 并在解析前做体积卡口（{@link #MAX_BPMN_XML_LENGTH}）与 DOCTYPE 文本预判。</p>
 *
 * @author BlueWhale
 */
public final class BpmnXmlSupport {

    /** XML 文本长度上限（字符数）：256KB */
    public static final int MAX_BPMN_XML_LENGTH = 262144;

    /** DOCTYPE 声明片段（用于体积卡口后的快速预判） */
    private static final String DOCTYPE_TOKEN = "<!DOCTYPE";

    /** 工具类不允许实例化 */
    private BpmnXmlSupport() {
    }

    /**
     * 以加固配置解析 XML 文本为 DOM。
     *
     * @param xml XML 文本
     * @return DOM 文档
     * @throws ProcessModelException 超长 / 含 DOCTYPE / 非良构时抛出对应校验码
     */
    public static Document parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new ProcessModelException(ValidationCode.XML_NOT_WELL_FORMED, "BPMN XML 为空");
        }
        if (xml.length() > MAX_BPMN_XML_LENGTH) {
            throw new ProcessModelException(ValidationCode.XML_TOO_LARGE,
                    "BPMN XML 长度 " + xml.length() + " 超出上限 " + MAX_BPMN_XML_LENGTH);
        }
        if (containsDoctype(xml)) {
            throw new ProcessModelException(ValidationCode.XML_DOCTYPE_FORBIDDEN,
                    "BPMN XML 禁止使用 DOCTYPE");
        }
        try {
            DocumentBuilder builder = newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (ProcessModelException e) {
            throw e;
        } catch (Exception e) {
            throw new ProcessModelException(ValidationCode.XML_NOT_WELL_FORMED, "BPMN XML 不是良构文档");
        }
    }

    /**
     * 构建加固的 {@link DocumentBuilder}。
     *
     * @return 文档构建器
     * @throws ProcessModelException 解析器能力不支持加固项时抛出
     */
    private static DocumentBuilder newDocumentBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://apache.org/xml/features/xinclude", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder();
        } catch (Exception e) {
            throw new ProcessModelException(ValidationCode.XML_NOT_WELL_FORMED, "XML 解析器加固失败");
        }
    }

    /**
     * 判断文本是否含 DOCTYPE 声明（大小写不敏感，容忍空白）。
     *
     * @param xml XML 文本
     * @return 含 DOCTYPE 返回 {@code true}
     */
    private static boolean containsDoctype(String xml) {
        return xml.toUpperCase().replace(" ", "").contains(DOCTYPE_TOKEN);
    }
}
