package ch.admin.bit.jeap.reaction.observer.header;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OperationNameRuleTest {

    @ParameterizedTest
    @ValueSource(strings = {"svc GET /api/items", "svc GET /api/{} ;v=1.2+", "svc GET /api/café", "svc GET /😀"})
    void violation_acceptedName_none(String name) {
        assertThat(OperationNameRule.violation(name)).isEmpty();
    }

    @Test
    void violation_nameAtTheLengthLimit_none() {
        assertThat(OperationNameRule.violation("x".repeat(OperationNameRule.MAX_LENGTH))).isEmpty();
    }

    @Test
    void violation_nameOverTheLengthLimit_tooLong() {
        assertThat(OperationNameRule.violation("x".repeat(OperationNameRule.MAX_LENGTH + 1)))
                .contains("is longer than 600 characters");
    }

    @Test
    void violation_emptyName_empty() {
        assertThat(OperationNameRule.violation("")).contains("is empty");
    }

    @Test
    void violation_nameOfACoarseAction_coarseActionPrefix() {
        assertThat(OperationNameRule.violation("//svc GET /api/items"))
                .contains("starts with '//', the mark of a coarse action");
    }

    @ParameterizedTest
    @ValueSource(strings = {"‌", "​", "‮", "﻿", "\n", "\t", "\u0000", "\u0085", " ", " ",
            "󠀁"})
    void violation_nameWithAControlOrInvisibleCharacter_invisibleCharacter(String character) {
        assertThat(OperationNameRule.violation("svc GET /api/zero" + character + "width"))
                .contains("contains a control character, a line or paragraph separator or an invisible format character");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "' svc GET /x'|svc GET /x",
            "'svc GET /x '|svc GET /x",
            "' svc GET /x '|svc GET /x",
            "'svc GET /x\t'|'svc GET /x\t'",
            "'   '|''"})
    void stripSpaces_dropsTheSpaceSeparatorsAtTheEdgesOnly(String name, String expected) {
        assertThat(OperationNameRule.stripSpaces(name)).isEqualTo(expected);
    }

    @Test
    void escapeForLog_replacesControlAndInvisibleCharactersByTheirCodePoints() {
        assertThat(OperationNameRule.escapeForLog("svc GET /a‌b\nc‮d󠀁 café"))
                .isEqualTo("svc GET /a[U+200C]b[U+000A]c[U+202E]d[U+E0001] café");
    }
}
