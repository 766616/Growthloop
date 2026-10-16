package com.growthloop.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure unit tests (no Spring context). */
class AcademicServiceTest {

    @Test
    void parsesSimpleCsvLine() {
        assertEquals(List.of("GL24001", "Java", "78", "100", "85", "16"), AcademicService.parseLine("GL24001,Java,78,100,85,16"));
    }

    @Test
    void parsesQuotedFieldsWithCommas() {
        List<String> row = AcademicService.parseLine("GL24001,\"Data Structures, Advanced\",70,100,90,18");
        assertEquals(6, row.size());
        assertEquals("Data Structures, Advanced", row.get(1));
    }

    @Test
    void keepsEmptyTrailingFields() {
        List<String> row = AcademicService.parseLine("GL24001,Java,78,100,,");
        assertEquals(6, row.size());
        assertEquals("", row.get(4));
    }

    @Test
    void roundsToOneDecimal() {
        assertEquals(66.7, AcademicService.round(66.666));
        assertEquals(50.0, AcademicService.round(50.0));
    }

    @Test
    void defaultAcademicYearLooksLikeYyyyYy() {
        assertTrue(AcademicService.defaultYear().matches("\\d{4}-\\d{2}"));
    }
}
