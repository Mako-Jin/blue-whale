package com.bluewhale.auth.infrastructure.spreadsheet;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.bluewhale.auth.application.model.importexport.UserExportRow;
import com.bluewhale.auth.application.port.SpreadsheetWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link SpreadsheetWriter} 的 EasyExcel 实现（权限中心扩展 M4）。
 *
 * <p>导入模板与导出文件共用同一套表头（保证「导出文件表头与模板一致」验收项）；
 * 导出按页拉取数据流式写出，内存中只保留当前页。</p>
 *
 * @author BlueWhale
 */
@Component
public class EasyExcelSpreadsheetWriter implements SpreadsheetWriter {

    /** 表头（模板与导出共用） */
    private static final List<List<String>> HEAD = Arrays.asList(
            List.of("用户名*"),
            List.of("昵称"),
            List.of("手机号"),
            List.of("邮箱"),
            List.of("部门ID"),
            List.of("角色ID(逗号分隔)"),
            List.of("状态(1启用/0停用)"));

    /** 模板示例行 */
    private static final List<List<Object>> SAMPLE_ROW = List.of(Arrays.asList(
            "zhangsan", "张三", "13800138000", "zhangsan@example.com", "", "", "1"));

    /** 流式导出分页页大小 */
    private static final int PAGE_SIZE = 1_000;

    @Override
    public byte[] writeTemplate() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        EasyExcel.write(output).head(HEAD).sheet("用户导入").doWrite(SAMPLE_ROW);
        return output.toByteArray();
    }

    @Override
    public long writeUsers(OutputStream output, UserRowFetcher fetcher) {
        long written = 0;
        try (ExcelWriter writer = EasyExcel.write(output).build()) {
            WriteSheet sheet = EasyExcel.writerSheet(0, "用户导出").head(HEAD).build();
            for (long pageIndex = 0; ; pageIndex++) {
                List<UserExportRow> rows = fetcher.fetch(pageIndex, PAGE_SIZE);
                if (rows.isEmpty()) {
                    break;
                }
                writer.write(toSheetRows(rows), sheet);
                written += rows.size();
                if (rows.size() < PAGE_SIZE) {
                    break;
                }
            }
        }
        return written;
    }

    /**
     * 导出行 → EasyExcel 行载荷（列顺序与表头一致）
     */
    private static List<List<Object>> toSheetRows(List<UserExportRow> rows) {
        List<List<Object>> sheetRows = new ArrayList<>(rows.size());
        for (UserExportRow row : rows) {
            sheetRows.add(Arrays.asList(
                    row.username(),
                    row.nickname() == null ? "" : row.nickname(),
                    row.phone() == null ? "" : row.phone(),
                    row.email() == null ? "" : row.email(),
                    row.deptId() == null ? "" : row.deptId(),
                    row.roleIds() == null ? "" : row.roleIds(),
                    row.status() == null ? "" : row.status()));
        }
        return sheetRows;
    }
}
