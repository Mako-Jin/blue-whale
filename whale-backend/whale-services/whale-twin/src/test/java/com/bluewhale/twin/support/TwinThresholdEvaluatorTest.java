package com.bluewhale.twin.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阈值评估器单测：三档判定、单阈值、非数值跳过、thresholds 缺失与 stateKey 映射。
 *
 * @author BlueWhale
 */
class TwinThresholdEvaluatorTest {

    @Test
    void classifiesThreeBandsWithTwoThresholds() {
        String propertyMap = "{\"temperature\":{\"stateKey\":\"temp\",\"thresholds\":[50,80]}}";

        var below = TwinThresholdEvaluator.evaluate(propertyMap, Map.of("temp", 49));
        var between = TwinThresholdEvaluator.evaluate(propertyMap, Map.of("temp", 50));
        var over = TwinThresholdEvaluator.evaluate(propertyMap, Map.of("temp", 81));

        assertEquals(TwinThresholdEvaluator.LEVEL_NORMAL, below.get("temp").level());
        assertEquals(TwinThresholdEvaluator.LEVEL_WARN, between.get("temp").level());
        assertEquals(TwinThresholdEvaluator.LEVEL_CRITICAL, over.get("temp").level());
        assertEquals(2, over.get("temp").thresholds().size());
        assertEquals(81, over.get("temp").value());
    }

    @Test
    void singleThresholdMeansWarnLine() {
        String propertyMap = "{\"load\":{\"thresholds\":[90]}}";

        var under = TwinThresholdEvaluator.evaluate(propertyMap, Map.of("load", 89));
        var over = TwinThresholdEvaluator.evaluate(propertyMap, Map.of("load", 90));

        assertEquals(TwinThresholdEvaluator.LEVEL_NORMAL, under.get("load").level());
        assertEquals(TwinThresholdEvaluator.LEVEL_WARN, over.get("load").level());
    }

    @Test
    void nonNumericValuesAreSkipped() {
        String propertyMap = "{\"temperature\":{\"thresholds\":[50,80]}}";

        var result = TwinThresholdEvaluator.evaluate(propertyMap,
                Map.of("temperature", "high"));

        assertTrue(result.isEmpty());
    }

    @Test
    void numericStringIsAccepted() {
        String propertyMap = "{\"temperature\":{\"thresholds\":[50,80]}}";

        var result = TwinThresholdEvaluator.evaluate(propertyMap,
                Map.of("temperature", "82.5"));

        assertEquals(TwinThresholdEvaluator.LEVEL_CRITICAL, result.get("temperature").level());
    }

    @Test
    void negativeValuesSupportNormalLevel() {
        String propertyMap = "{\"temperature\":{\"thresholds\":[50,80]}}";

        var result = TwinThresholdEvaluator.evaluate(propertyMap, Map.of("temperature", -12));

        assertEquals(TwinThresholdEvaluator.LEVEL_NORMAL, result.get("temperature").level());
    }

    @Test
    void missingThresholdsProducesNoLevel() {
        String propertyMap = "{\"temperature\":{\"stateKey\":\"temp\"}}";

        var result = TwinThresholdEvaluator.evaluate(propertyMap, Map.of("temp", 82.5));

        assertTrue(result.isEmpty());
    }

    @Test
    void plainStringMappingKeepsSourceFieldAsStateKey() {
        String propertyMap = "{\"pressure\":{\"stateKey\":\"psi\",\"thresholds\":[10,20]}}";

        var result = TwinThresholdEvaluator.evaluate(propertyMap, Map.of("psi", 15));

        assertEquals("psi", result.get("psi").stateKey());
        assertEquals(List.of(new java.math.BigDecimal("10"), new java.math.BigDecimal("20")),
                result.get("psi").thresholds());
    }

    @Test
    void brokenPropertyMapYieldsEmptyWithoutThrowing() {
        var result = TwinThresholdEvaluator.evaluate("not-json", Map.of("temperature", 82.5));

        assertTrue(result.isEmpty());
    }
}
