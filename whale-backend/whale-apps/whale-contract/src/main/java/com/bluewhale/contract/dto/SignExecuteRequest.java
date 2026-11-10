package com.bluewhale.contract.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 本地签署请求：印章 + 落章坐标。
 *
 * @author BlueWhale
 */
@Data
public class SignExecuteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用印章ID */
    @NotNull(message = "请选择印章")
    private Long sealId;

    /** 页码（从 1 起；骑缝章忽略） */
    private Integer page;

    /** 落章 X 坐标（PDF 点，左下角为原点） */
    private Float x;

    /** 落章 Y 坐标（PDF 点，左下角为原点） */
    private Float y;

    /** 印章宽度（PDF 点） */
    private Float width;

    /** 印章高度（PDF 点） */
    private Float height;

    /** 旋转角度（度，逆时针） */
    private Float rotation;

    /** 是否骑缝章：按页数均分印章图并加盖到每页切边 */
    private Boolean crossPage;

    /**
     * 签署意愿确认（必填 true）。
     * <p>本地签署以此作为意愿认证证据：未勾选直接拒绝，避免「无意愿证据的静默签署」。</p>
     */
    @NotNull(message = "请确认签署意愿")
    @AssertTrue(message = "请确认签署意愿")
    private Boolean confirmWillingness;
}
