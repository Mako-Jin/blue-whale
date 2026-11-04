package com.bluewhale.pdm.part.support;

import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.part.entity.PdmBom;
import com.bluewhale.pdm.part.entity.PdmBomLine;
import com.bluewhale.pdm.part.vo.PdmBomSnapshotVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * BOM 发布快照构建器测试。
 *
 * @author BlueWhale
 */
class PdmBomSnapshotBuilderTest {

    /**
     * 深度优先排序、路径与稳定 checksum。
     */
    @Test
    void snapshotShouldBeDeterministic() {
        PdmBom bom = bom("BOM-P1", "V1.0", "P1", 100L);
        PdmBomLine first = line(100L, 200L, 1, "2");
        PdmBomLine second = line(100L, 300L, 2, "1");
        PdmBomLine third = line(200L, 400L, 1, "3");
        Map<Long, PdmProduct> parts = Map.of(
                100L, part(100L, "P1", "成品"),
                200L, part(200L, "A1", "部件"),
                300L, part(300L, "S1", "标准件"),
                400L, part(400L, "R1", "原材料"));
        List<PdmBomLine> lines = List.of(first, second, third);

        PdmBomSnapshotVO snapshot = PdmBomSnapshotBuilder.build(bom, lines, parts);
        String payload1 = PdmBomSnapshotBuilder.canonicalJson(snapshot);
        String checksum1 = PdmBomSnapshotBuilder.checksum(payload1);
        String payload2 = PdmBomSnapshotBuilder.canonicalJson(
                PdmBomSnapshotBuilder.build(bom, lines, parts));
        String checksum2 = PdmBomSnapshotBuilder.checksum(payload2);

        assertEquals(payload1, payload2);
        assertEquals(checksum1, checksum2);
        assertEquals(64, checksum1.length());
        assertEquals(3, snapshot.getLineCount());
        assertEquals("1", snapshot.getLines().get(0).getPath());
        assertEquals("1.1", snapshot.getLines().get(1).getPath());
        assertEquals("2", snapshot.getLines().get(2).getPath());
        assertEquals("A1", snapshot.getLines().get(0).getChildPartNo());
    }

    /**
     * 快照 JSON 可回解析且关键字段保留。
     */
    @Test
    void parseShouldRoundTrip() {
        PdmBom bom = bom("BOM-P1", "V1.0", "P1", 100L);
        PdmBomLine first = line(100L, 200L, 1, "2");
        PdmBomSnapshotVO snapshot = PdmBomSnapshotBuilder.build(bom, List.of(first),
                Map.of(100L, part(100L, "P1", "成品"), 200L, part(200L, "A1", "部件")));
        String payload = PdmBomSnapshotBuilder.canonicalJson(snapshot);

        PdmBomSnapshotVO parsed = PdmBomSnapshotBuilder.parse(payload);

        assertNotNull(parsed);
        assertEquals("BOM-P1", parsed.getBomNo());
        assertEquals("V1.0", parsed.getVersionNo());
        assertEquals(1, parsed.getLines().size());
        assertEquals("A1", parsed.getLines().get(0).getChildPartNo());
        assertEquals(new BigDecimal("2.000000"), parsed.getLines().get(0).getQty());
    }

    /**
     * 构造 BOM 头。
     *
     * @param bomNo     编号
     * @param versionNo 版本
     * @param code      产品编码
     * @param productId 产品ID
     * @return BOM 头
     */
    private PdmBom bom(String bomNo, String versionNo, String code, Long productId) {
        PdmBom bom = new PdmBom();
        bom.setBomNo(bomNo);
        bom.setVersionNo(versionNo);
        bom.setView("EBOM");
        bom.setProductCode(code);
        bom.setProductId(productId);
        return bom;
    }

    /**
     * 构造 BOM 行。
     *
     * @param parentId 父件
     * @param childId  子件
     * @param lineNo   行号
     * @param qty      用量
     * @return BOM 行
     */
    private PdmBomLine line(Long parentId, Long childId, int lineNo, String qty) {
        PdmBomLine line = new PdmBomLine();
        line.setParentPartId(parentId);
        line.setChildPartId(childId);
        line.setLineNo(lineNo);
        line.setQty(new BigDecimal(qty));
        line.setUom("PCS");
        line.setLossRate(BigDecimal.ONE);
        return line;
    }

    /**
     * 构造零件。
     *
     * @param id   零件ID
     * @param code 编码
     * @param name 名称
     * @return 零件
     */
    private PdmProduct part(Long id, String code, String name) {
        PdmProduct product = new PdmProduct();
        product.setId(id);
        product.setCode(code);
        product.setName(name);
        product.setMaterial("45#");
        product.setSpec("Φ20");
        return product;
    }
}
