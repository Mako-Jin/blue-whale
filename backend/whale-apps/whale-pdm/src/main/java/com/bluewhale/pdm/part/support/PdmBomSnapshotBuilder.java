package com.bluewhale.pdm.part.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.part.entity.PdmBom;
import com.bluewhale.pdm.part.entity.PdmBomLine;
import com.bluewhale.pdm.part.vo.PdmBomSnapshotLineVO;
import com.bluewhale.pdm.part.vo.PdmBomSnapshotVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BOM 发布快照构建器。
 *
 * <p>将 BOM 头与行展开为深度优先的稳定快照：行按同级 {@code lineNo} 排序，
 * 路径形如 {@code 1 / 1.1}，JSON 字段顺序固定，{@code checksum} 为规范化 JSON 的 SHA-256。</p>
 *
 * @author BlueWhale
 */
public final class PdmBomSnapshotBuilder {

    /** JSON 序列化器（无状态，可复用） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 用量小数位（对齐 DECIMAL(18,6)） */
    private static final int QTY_SCALE = 6;

    /** 损耗率小数位（对齐 DECIMAL(9,4)） */
    private static final int LOSS_SCALE = 4;

    /**
     * 工具类禁止实例化
     */
    private PdmBomSnapshotBuilder() {
    }

    /**
     * 构建快照（不含 checksum）。
     *
     * @param bom   BOM 头
     * @param lines BOM 行
     * @param parts 零件主档（键为零件ID）
     * @return 快照视图
     */
    public static PdmBomSnapshotVO build(PdmBom bom, List<PdmBomLine> lines, Map<Long, PdmProduct> parts) {
        Map<Long, List<PdmBomLine>> byParent = new LinkedHashMap<>();
        for (PdmBomLine line : lines) {
            byParent.computeIfAbsent(line.getParentPartId(), key -> new ArrayList<>()).add(line);
        }
        byParent.values().forEach(list -> list.sort(Comparator
                .comparing(PdmBomLine::getLineNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(PdmBomLine::getChildPartId, Comparator.nullsLast(Long::compareTo))));

        List<PdmBomSnapshotLineVO> flat = new ArrayList<>();
        walk(bom.getProductId(), "", 0, byParent, parts, flat, new HashSet<>());

        PdmBomSnapshotVO snapshot = new PdmBomSnapshotVO();
        snapshot.setBomNo(bom.getBomNo());
        snapshot.setVersionNo(bom.getVersionNo());
        snapshot.setView(bom.getView());
        snapshot.setProductCode(bom.getProductCode());
        PdmProduct product = parts.get(bom.getProductId());
        snapshot.setProductName(product == null ? null : product.getName());
        snapshot.setLineCount(flat.size());
        snapshot.setLines(flat);
        return snapshot;
    }

    /**
     * 深度优先展开子件。
     *
     * @param parentId  父件ID
     * @param parentPath 父路径
     * @param depth     当前层级
     * @param byParent  父件分组
     * @param parts     零件主档
     * @param flat      输出行
     * @param pathSet   当前路径集合（检测环，允许不同分支复用同一零件）
     */
    private static void walk(Long parentId, String parentPath, int depth,
                             Map<Long, List<PdmBomLine>> byParent, Map<Long, PdmProduct> parts,
                             List<PdmBomSnapshotLineVO> flat, Set<Long> pathSet) {
        if (parentId == null) {
            return;
        }
        if (!pathSet.add(parentId)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "BOM_CYCLE_DETECTED: 零件 " + parentId + " 在结构树中形成环");
        }
        List<PdmBomLine> edges = byParent.getOrDefault(parentId, List.of());
        for (int index = 0; index < edges.size(); index++) {
            PdmBomLine edge = edges.get(index);
            String path = parentPath.isEmpty()
                    ? String.valueOf(index + 1)
                    : parentPath + "." + (index + 1);
            PdmProduct child = parts.get(edge.getChildPartId());
            PdmBomSnapshotLineVO item = new PdmBomSnapshotLineVO();
            item.setPath(path);
            item.setDepth(depth + 1);
            PdmProduct parent = parts.get(parentId);
            item.setParentPartNo(parent == null ? null : parent.getCode());
            item.setChildPartNo(child == null ? null : child.getCode());
            item.setChildPartName(child == null ? null : child.getName());
            item.setQty(scale(edge.getQty(), QTY_SCALE));
            item.setUom(edge.getUom());
            item.setLossRate(scale(edge.getLossRate(), LOSS_SCALE));
            item.setMaterial(child == null ? null : child.getMaterial());
            item.setSpec(child == null ? null : child.getSpec());
            item.setRemark(edge.getRemark());
            flat.add(item);
            walk(edge.getChildPartId(), path, depth + 1, byParent, parts, flat, pathSet);
        }
        pathSet.remove(parentId);
    }

    /**
     * 生成稳定 JSON（不含 checksum，字段顺序固定）。
     *
     * @param snapshot 快照
     * @return JSON 字符串
     */
    public static String canonicalJson(PdmBomSnapshotVO snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("bomNo", snapshot.getBomNo());
        root.put("versionNo", snapshot.getVersionNo());
        root.put("view", snapshot.getView());
        root.put("productCode", snapshot.getProductCode());
        root.put("productName", snapshot.getProductName());
        root.put("lineCount", snapshot.getLineCount());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (PdmBomSnapshotLineVO item : snapshot.getLines()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", item.getPath());
            row.put("depth", item.getDepth());
            row.put("parentPartNo", item.getParentPartNo());
            row.put("childPartNo", item.getChildPartNo());
            row.put("childPartName", item.getChildPartName());
            row.put("qty", item.getQty());
            row.put("uom", item.getUom());
            row.put("lossRate", item.getLossRate());
            row.put("material", item.getMaterial());
            row.put("spec", item.getSpec());
            row.put("remark", item.getRemark());
            lines.add(row);
        }
        root.put("lines", lines);
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "BOM 快照序列化失败");
        }
    }

    /**
     * 计算 SHA-256 十六进制校验和。
     *
     * @param json 规范化 JSON
     * @return 小写十六进制校验和
     */
    public static String checksum(String json) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "SHA-256 不可用");
        }
    }

    /**
     * 解析已发布快照 JSON。
     *
     * @param payload 快照 JSON
     * @return 快照视图
     */
    public static PdmBomSnapshotVO parse(String payload) {
        try {
            return MAPPER.readValue(payload, PdmBomSnapshotVO.class);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "BOM 快照解析失败");
        }
    }

    /**
     * 统一小数位，保证 JSON 稳定。
     *
     * @param value 原值
     * @param scale 小数位
     * @return 规范化值
     */
    private static BigDecimal scale(BigDecimal value, int scale) {
        BigDecimal effective = value == null ? BigDecimal.ZERO : value;
        return effective.setScale(scale, RoundingMode.HALF_UP);
    }
}
