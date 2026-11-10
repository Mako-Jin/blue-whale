package com.bluewhale.crm.customer.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * CSV 客户导入命令（M1-9）。
 *
 * @author BlueWhale
 */
@Data
public class CustomerImportCommand {

    /** CSV 文件内容（UTF-8 文本） */
    @NotBlank(message = "导入内容不能为空")
    private String content;
}
