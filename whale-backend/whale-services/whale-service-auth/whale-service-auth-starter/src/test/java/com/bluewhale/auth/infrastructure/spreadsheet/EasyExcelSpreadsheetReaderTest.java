package com.bluewhale.auth.infrastructure.spreadsheet;

import com.alibaba.excel.EasyExcel;
import com.bluewhale.auth.application.model.importexport.UserImportRow;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * {@link EasyExcelSpreadsheetReader} 适配器用例（权限中心扩展 M4）：
 * 表头归一化匹配、行号保留、全空行跳过、超限拒绝、缺列拒绝。
 *
 * @author BlueWhale
 */
class EasyExcelSpreadsheetReaderTest {

    private final EasyExcelSpreadsheetReader reader = new EasyExcelSpreadsheetReader();

    /**
     * 构造测试 xlsx：首行表头 + 数据行
     */
    private static byte[] toXlsx(List<List<String>> header, List<List<Object>> dataRows) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        EasyExcel.write(output).head(header).sheet("用户导入").doWrite(dataRows);
        return output.toByteArray();
    }

    @Test
    void 读取正常模板_行号与字段映射正确() {
        byte[] bytes = toXlsx(
                List.of(List.of("用户名*"), List.of("昵称"), List.of("手机号"), List.of("邮箱"),
                        List.of("部门ID"), List.of("角色ID(逗号分隔)"), List.of("状态(1启用/0停用)")),
                List.of(Arrays.asList("zhangsan", "张三", "13800138000", "z@example.com", "100", "10,11", "1")));
        List<UserImportRow> rows = reader.readUsers(new ByteArrayInputStream(bytes), 10_000);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).rowNo()).isEqualTo(2);
        assertThat(rows.get(0).username()).isEqualTo("zhangsan");
        assertThat(rows.get(0).roleIds()).isEqualTo("10,11");
        assertThat(rows.get(0).status()).isEqualTo("1");
    }

    @Test
    void 列顺序无关_仍可正确映射() {
        byte[] bytes = toXlsx(
                List.of(List.of("状态(1启用/0停用)"), List.of("用户名*"), List.of("邮箱")),
                List.of(Arrays.asList("0", "zhangsan", "z@example.com")));
        List<UserImportRow> rows = reader.readUsers(new ByteArrayInputStream(bytes), 10_000);
        assertThat(rows.get(0).username()).isEqualTo("zhangsan");
        assertThat(rows.get(0).email()).isEqualTo("z@example.com");
        assertThat(rows.get(0).status()).isEqualTo("0");
    }

    @Test
    void 全空行跳过_后续行号不重排() {
        byte[] bytes = toXlsx(
                List.of(List.of("用户名*"), List.of("手机号")),
                List.of(
                        Arrays.asList("zhangsan", "13800138000"),
                        Arrays.asList("", "", ""),
                        Arrays.asList("lisi", "13800138001")));
        List<UserImportRow> rows = reader.readUsers(new ByteArrayInputStream(bytes), 10_000);
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(UserImportRow::rowNo, UserImportRow::username)
                .containsExactly(tuple(2, "zhangsan"), tuple(4, "lisi"));
    }

    @Test
    void 缺少用户名列_拒绝并提示() {
        byte[] bytes = toXlsx(
                List.of(List.of("昵称"), List.of("手机号")),
                List.of(Arrays.asList("张三", "13800138000")));
        assertThatThrownBy(() -> reader.readUsers(new ByteArrayInputStream(bytes), 10_000))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名");
    }

    @Test
    void 超过行数上限_拒绝() {
        List<List<Object>> dataRows = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            dataRows.add(Arrays.asList("user" + i, "", ""));
        }
        byte[] bytes = toXlsx(List.of(List.of("用户名*"), List.of("手机号")), dataRows);
        assertThatThrownBy(() -> reader.readUsers(new ByteArrayInputStream(bytes), 10))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超过上限");
    }

    @Test
    void 空文件_拒绝() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        EasyExcel.write(output).sheet("empty").doWrite(new ArrayList<>());
        assertThatThrownBy(() -> reader.readUsers(
                new ByteArrayInputStream(output.toByteArray()), 10_000))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("导入文件为空");
    }

    @Test
    void 表头归一化_去星号与括号说明() {
        assertThat(EasyExcelSpreadsheetReader.normalizeHeader("用户名*")).isEqualTo("用户名");
        assertThat(EasyExcelSpreadsheetReader.normalizeHeader("角色ID(逗号分隔)")).isEqualTo("角色ID");
        assertThat(EasyExcelSpreadsheetReader.normalizeHeader("状态(1启用/0停用)")).isEqualTo("状态");
        assertThat(EasyExcelSpreadsheetReader.normalizeHeader(" 部门ID ")).isEqualTo("部门ID");
        assertThat(EasyExcelSpreadsheetReader.normalizeHeader(null)).isNull();
    }

    @Test
    void 非法文件_转为业务异常() {
        // 任意非 xlsx 字节流：EasyExcel 解析失败或表头缺失，最终须转为受控业务异常
        assertThatThrownBy(() -> reader.readUsers(new ByteArrayInputStream(
                "not-an-excel-file".getBytes(java.nio.charset.StandardCharsets.UTF_8)), 10))
                .isInstanceOf(BizException.class);
    }
}
