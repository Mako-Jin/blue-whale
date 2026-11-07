package com.bluewhale.crm.marketing.application.view;

import com.bluewhale.crm.marketing.domain.model.AudienceCriteria;
import com.bluewhale.crm.marketing.domain.model.AudiencePreview;
import lombok.Data;

import java.util.List;

/**
 * 活动人群预览视图。
 *
 * @author BlueWhale
 */
@Data
public class AudiencePreviewView {

    /** 命中数量 */
    private Long count;

    /** 样例客户（最多 20 条） */
    private List<SampleView> samples;

    /** 条件 JSON */
    private String criteriaJson;

    /**
     * 由领域预览构建视图。
     *
     * @param preview 预览结果
     * @param criteria 人群条件
     * @return 视图
     */
    public static AudiencePreviewView from(AudiencePreview preview, AudienceCriteria criteria) {
        AudiencePreviewView view = new AudiencePreviewView();
        view.setCount(preview.getCount());
        view.setSamples(preview.getSamples() == null ? List.of() : preview.getSamples().stream()
                .map(SampleView::from).toList());
        view.setCriteriaJson(criteria == null ? null : criteria.toJson());
        return view;
    }

    /**
     * 样例客户视图。
     *
     * @author BlueWhale
     */
    @Data
    public static class SampleView {

        /** 客户 ID */
        private Long customerId;

        /** 客户名称 */
        private String customerName;

        /** 客户等级 */
        private String level;

        /** 行业 */
        private String industry;

        /** 来源 */
        private String source;

        /**
         * 由领域样例构建视图。
         *
         * @param sample 领域样例
         * @return 视图
         */
        public static SampleView from(AudiencePreview.Sample sample) {
            SampleView view = new SampleView();
            view.setCustomerId(sample.customerId());
            view.setCustomerName(sample.customerName());
            view.setLevel(sample.level());
            view.setIndustry(sample.industry());
            view.setSource(sample.source());
            return view;
        }
    }
}
