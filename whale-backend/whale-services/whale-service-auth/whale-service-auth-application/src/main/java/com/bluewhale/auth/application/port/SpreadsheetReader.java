package com.bluewhale.auth.application.port;

import com.bluewhale.auth.application.model.importexport.UserImportRow;

import java.io.InputStream;
import java.util.List;

/**
 * 电子表格读取端口（权限中心扩展 M4）：隔离 EasyExcel 等实现细节于基础设施层。
 *
 * @author BlueWhale
 */
public interface SpreadsheetReader {

    /**
     * 读取用户导入文件（xlsx）：解析表头并逐行映射为导入行
     *
     * <p>表头按名称归一化匹配（去掉尾随 {@code *} 与括号说明）；全空行跳过；
     * 超过 {@code maxRows} 抛出受控业务异常。</p>
     *
     * @param input   上传文件输入流
     * @param maxRows 数据行数上限（预检要求全量校验，超限直接拒绝）
     * @return 导入行列表（保留 Excel 行号）
     */
    List<UserImportRow> readUsers(InputStream input, int maxRows);
}
