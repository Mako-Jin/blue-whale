package com.bluewhale.auth.infrastructure.spreadsheet;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.bluewhale.auth.application.model.importexport.UserImportRow;
import com.bluewhale.auth.application.port.SpreadsheetReader;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link SpreadsheetReader} 的 EasyExcel 实现（权限中心扩展 M4）。
 *
 * <p>表头按名称归一化匹配（去尾随 {@code *} 与括号说明），列顺序无关；
 * 首行为表头，其后全空行跳过；数据行数超限直接拒绝（预检要求全量校验）。
 * 单元格一律按字符串读取，格式校验交由应用层行校验器（错误定位到行/列）。</p>
 *
 * @author BlueWhale
 */
@Component
public class EasyExcelSpreadsheetReader implements SpreadsheetReader {

    /** 归一化列名：用户名（必填） */
    static final String COL_USERNAME = "用户名";
    /** 归一化列名：昵称 */
    static final String COL_NICKNAME = "昵称";
    /** 归一化列名：手机号 */
    static final String COL_PHONE = "手机号";
    /** 归一化列名：邮箱 */
    static final String COL_EMAIL = "邮箱";
    /** 归一化列名：部门ID */
    static final String COL_DEPT_ID = "部门ID";
    /** 归一化列名：角色ID */
    static final String COL_ROLE_IDS = "角色ID";
    /** 归一化列名：状态 */
    static final String COL_STATUS = "状态";

    @Override
    public List<UserImportRow> readUsers(InputStream input, int maxRows) {
        // 带物理行号收集（EasyExcel 会跳过空行，列表下标 ≠ 物理 Excel 行号）
        List<Map.Entry<Integer, Map<Integer, String>>> rawRows = new ArrayList<>();
        try {
            EasyExcel.read(input, new AnalysisEventListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> row, AnalysisContext context) {
                    rawRows.add(Map.entry(context.readRowHolder().getRowIndex(), row));
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // 全量读入内存（上限 1 万行），无需分批回调
                }
            }).headRowNumber(0).sheet().doRead();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "auth.import.parse-failed",
                    "导入文件解析失败，请使用最新模板填写后重试: " + e.getMessage(), e.getMessage());
        }
        if (rawRows.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "auth.import.file-empty", "导入文件为空，请使用最新模板填写");
        }
        // 第一行为表头（headRowNumber(0) 使表头作为普通数据行读入）
        Map<Integer, String> header = rawRows.get(0).getValue();
        Map<Integer, String> columnMap = resolveColumns(header);
        List<UserImportRow> rows = new ArrayList<>();
        for (int i = 1; i < rawRows.size(); i++) {
            Map.Entry<Integer, Map<Integer, String>> entry = rawRows.get(i);
            int rowNo = entry.getKey() + 1;
            UserImportRow row = toRow(rowNo, entry.getValue(), columnMap);
            if (row != null) {
                rows.add(row);
            }
            if (rows.size() > maxRows) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "auth.import.rows-exceeded",
                        "导入数据超过上限 " + maxRows + " 行，请分批导入", maxRows);
            }
        }
        return rows;
    }

    /**
     * 解析表头：单元格归一化后映射「归一化列名 → 列下标」
     *
     * <p>「用户名」列必填，其余列缺失视为空值。</p>
     *
     * @param header 表头行单元格
     * @return 列名映射
     */
    private static Map<Integer, String> resolveColumns(Map<Integer, String> header) {
        Map<Integer, String> columnMap = new LinkedHashMap<>();
        boolean usernameFound = false;
        for (Map.Entry<Integer, String> entry : header.entrySet()) {
            String normalized = normalizeHeader(entry.getValue());
            if (normalized == null) {
                continue;
            }
            columnMap.put(entry.getKey(), normalized);
            if (COL_USERNAME.equals(normalized)) {
                usernameFound = true;
            }
        }
        if (!usernameFound) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "auth.import.username-column-missing",
                    "模板表头缺少「用户名」列，请下载最新模板填写");
        }
        return columnMap;
    }

    /**
     * 表头归一化：去空白、去尾随 {@code *}、去尾随括号说明
     * （如「用户名*」→「用户名」、「角色ID(逗号分隔)」→「角色ID」）
     *
     * @param raw 表头单元格原文
     * @return 归一化列名；空白单元格返回 {@code null}
     */
    static String normalizeHeader(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim();
        while (name.endsWith("*")) {
            name = name.substring(0, name.length() - 1).trim();
        }
        // 去尾随括号说明（中英文括号）
        int paren = indexOfTrailingParenStart(name);
        if (paren > 0) {
            name = name.substring(0, paren).trim();
        }
        return name.isEmpty() ? null : name;
    }

    /**
     * 定位尾随括号说明的起始下标（要求括号出现在行尾）
     */
    private static int indexOfTrailingParenStart(String name) {
        for (char paren : new char[]{'（', '('}) {
            int idx = name.lastIndexOf(paren);
            if (idx > 0 && name.charAt(name.length() - 1) == closingOf(paren)) {
                return idx;
            }
        }
        return -1;
    }

    /**
     * 括号配对
     */
    private static char closingOf(char paren) {
        return paren == '（' ? '）' : ')';
    }

    /**
     * 数据行 → 导入行；七个字段全空时返回 {@code null}（跳过全空行）
     */
    private static UserImportRow toRow(int rowNo, Map<Integer, String> cells, Map<Integer, String> columnMap) {
        String username = cellOf(cells, columnMap, COL_USERNAME);
        String nickname = cellOf(cells, columnMap, COL_NICKNAME);
        String phone = cellOf(cells, columnMap, COL_PHONE);
        String email = cellOf(cells, columnMap, COL_EMAIL);
        String deptId = cellOf(cells, columnMap, COL_DEPT_ID);
        String roleIds = cellOf(cells, columnMap, COL_ROLE_IDS);
        String status = cellOf(cells, columnMap, COL_STATUS);
        if (isBlank(username) && isBlank(nickname) && isBlank(phone) && isBlank(email)
                && isBlank(deptId) && isBlank(roleIds) && isBlank(status)) {
            return null;
        }
        return new UserImportRow(rowNo, username, nickname, phone, email, deptId, roleIds, status);
    }

    /**
     * 按归一化列名取单元格值
     */
    private static String cellOf(Map<Integer, String> cells, Map<Integer, String> columnMap, String column) {
        for (Map.Entry<Integer, String> entry : columnMap.entrySet()) {
            if (entry.getValue().equals(column)) {
                return cells.get(entry.getKey());
            }
        }
        return null;
    }

    /**
     * 空白判定
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
