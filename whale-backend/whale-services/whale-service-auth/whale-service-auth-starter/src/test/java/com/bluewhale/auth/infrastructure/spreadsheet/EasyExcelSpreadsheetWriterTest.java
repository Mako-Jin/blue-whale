package com.bluewhale.auth.infrastructure.spreadsheet;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.bluewhale.auth.application.model.importexport.UserExportRow;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EasyExcelSpreadsheetWriter} 适配器用例（权限中心扩展 M4）：
 * 模板生成、流式分页导出（跨页数据行数完整）、表头与模板一致。
 *
 * @author BlueWhale
 */
class EasyExcelSpreadsheetWriterTest {

    private final EasyExcelSpreadsheetWriter writer = new EasyExcelSpreadsheetWriter();

    /**
     * 读取 xlsx 全部数据行（含表头行，headRowNumber(0)）
     */
    private static List<List<String>> readAllRows(byte[] bytes) {
        List<List<String>> rows = new ArrayList<>();
        EasyExcel.read(new ByteArrayInputStream(bytes), new AnalysisEventListener<java.util.Map<Integer, String>>() {
            @Override
            public void invoke(java.util.Map<Integer, String> row, AnalysisContext context) {
                rows.add(new ArrayList<>(row.values()));
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // 单测一次性读取
            }
        }).headRowNumber(0).sheet().doRead();
        return rows;
    }

    @Test
    void 模板_含表头与示例行() {
        byte[] bytes = writer.writeTemplate();
        List<List<String>> rows = readAllRows(bytes);
        // 表头 7 列 + 示例行
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly("用户名*", "昵称", "手机号", "邮箱",
                "部门ID", "角色ID(逗号分隔)", "状态(1启用/0停用)");
        assertThat(rows.get(1).get(0)).isEqualTo("zhangsan");
    }

    @Test
    void 流式导出_跨页完整写出() {
        // 页大小 1000：第一页 1000 行、第二页 5 行、第三页空 → 共 1005 行
        int pageSize = 1_000;
        List<UserExportRow> pageOne = new ArrayList<>();
        for (int i = 0; i < pageSize; i++) {
            pageOne.add(new UserExportRow("user" + i, "昵称", "13800138000", null, 100L, "10,11", 1));
        }
        List<UserExportRow> pageTwo = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            pageTwo.add(new UserExportRow("tail" + i, "昵称", null, "t@example.com", null, null, 0));
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long written = writer.writeUsers(output, (pageIndex, size) -> {
            assertThat(size).isEqualTo(pageSize);
            return switch ((int) pageIndex) {
                case 0 -> pageOne;
                case 1 -> pageTwo;
                default -> List.of();
            };
        });
        assertThat(written).isEqualTo(1005);
        List<List<String>> rows = readAllRows(output.toByteArray());
        assertThat(rows).hasSize(1006); // 表头 + 1005 数据行
        assertThat(rows.get(0)).containsExactly("用户名*", "昵称", "手机号", "邮箱",
                "部门ID", "角色ID(逗号分隔)", "状态(1启用/0停用)");
        assertThat(rows.get(1).get(0)).isEqualTo("user0");
        assertThat(rows.get(1005).get(0)).isEqualTo("tail4");
    }

    @Test
    void 导出空数据_仅写出零行() {
        // 零行导出在实际业务中不会发生（服务侧零数据直接抛业务异常），此处仅断言行数
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long written = writer.writeUsers(output, (pageIndex, size) -> List.of());
        assertThat(written).isZero();
    }
}
