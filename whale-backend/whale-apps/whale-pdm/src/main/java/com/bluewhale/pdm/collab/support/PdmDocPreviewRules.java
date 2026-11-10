package com.bluewhale.pdm.collab.support;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * 在线预览格式判定
 *
 * <p>原生可预览格式（PDF/图片/STL/glTF）直接由前端查看器读取原文件；
 * Office、DWG 与 STEP/IGES 需要外部转换服务，本阶段未配置时标记为暂不支持并给出可诊断原因。
 * 不依赖冻结的 {@code whale-common-three}。</p>
 *
 * @author BlueWhale
 */
public final class PdmDocPreviewRules {

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp");

    private static final Set<String> OFFICE_EXTENSIONS =
            Set.of("doc", "docx", "xls", "xlsx", "ppt", "pptx");

    private static final Set<String> CAD_2D_EXTENSIONS = Set.of("dwg", "dxf");

    private static final Set<String> STEP_EXTENSIONS = Set.of("step", "stp", "iges", "igs");

    private static final Set<String> GLTF_EXTENSIONS = Set.of("gltf", "glb");

    /**
     * 工具类禁止实例化
     */
    private PdmDocPreviewRules() {
    }

    /**
     * 预览判定结果
     *
     * @param status       状态
     * @param targetFormat 目标格式
     * @param viewerHint   前端查看器提示
     * @param message      不可预览原因
     */
    public record Decision(String status, String targetFormat, String viewerHint, String message) {
    }

    /**
     * 按预览类型与文件信息判定
     *
     * @param previewType 预览类型 2D/3D
     * @param fileName    文件名
     * @param contentType 内容类型
     * @return 判定结果
     */
    public static Decision evaluate(String previewType, String fileName, String contentType) {
        String extension = extension(fileName);
        String contentTypeLower = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (PdmDocPreviewStatus.TYPE_3D.equals(previewType)) {
            return evaluate3d(extension, contentTypeLower);
        }
        return evaluate2d(extension, contentTypeLower);
    }

    /**
     * 判定 2D 预览
     *
     * @param extension 扩展名
     * @param contentType 内容类型
     * @return 判定结果
     */
    private static Decision evaluate2d(String extension, String contentType) {
        if ("pdf".equals(extension) || contentType.contains("application/pdf")) {
            return new Decision(PdmDocPreviewStatus.SUCCESS, PdmDocPreviewStatus.FORMAT_ORIGINAL,
                    "PDF", null);
        }
        if (IMAGE_EXTENSIONS.contains(extension) || contentType.startsWith("image/")) {
            return new Decision(PdmDocPreviewStatus.SUCCESS, PdmDocPreviewStatus.FORMAT_ORIGINAL,
                    "IMAGE", null);
        }
        if (OFFICE_EXTENSIONS.contains(extension)) {
            return new Decision(PdmDocPreviewStatus.UNSUPPORTED, null, null,
                    "Office 文档预览需配置 LibreOffice 转换服务");
        }
        if (CAD_2D_EXTENSIONS.contains(extension)) {
            return new Decision(PdmDocPreviewStatus.UNSUPPORTED, null, null,
                    "DWG/DXF 按 D-PDM-3 决策仅支持下载");
        }
        return new Decision(PdmDocPreviewStatus.UNSUPPORTED, null, null,
                "不支持的 2D 预览格式：" + (StringUtils.hasText(extension) ? extension : contentType));
    }

    /**
     * 判定 3D 预览
     *
     * @param extension 扩展名
     * @param contentType 内容类型
     * @return 判定结果
     */
    private static Decision evaluate3d(String extension, String contentType) {
        if ("stl".equals(extension) || contentType.contains("model/stl")) {
            return new Decision(PdmDocPreviewStatus.SUCCESS, PdmDocPreviewStatus.FORMAT_ORIGINAL,
                    "STL", null);
        }
        if (GLTF_EXTENSIONS.contains(extension)
                || contentType.contains("model/gltf")) {
            return new Decision(PdmDocPreviewStatus.SUCCESS, PdmDocPreviewStatus.FORMAT_ORIGINAL,
                    "GLTF", null);
        }
        if (STEP_EXTENSIONS.contains(extension)) {
            return new Decision(PdmDocPreviewStatus.UNSUPPORTED, null, null,
                    "STEP/IGES 预览需配置 FreeCAD/OCCT 转换服务");
        }
        return new Decision(PdmDocPreviewStatus.UNSUPPORTED, null, null,
                "不支持的 3D 预览格式：" + (StringUtils.hasText(extension) ? extension : contentType));
    }

    /**
     * 提取小写扩展名
     *
     * @param fileName 文件名
     * @return 扩展名，无则空串
     */
    private static String extension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
