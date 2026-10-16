package com.growthloop.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcademicServiceParseTest {

    @Test
    void parsesSimpleLine() {
        assertEquals(List.of("GL24001", "Maths", "78", "100", "85", "90"),
                AcademicService.parseLine("GL24001,Maths,78,100,85,90"));
    }

    @Test
    void keepsCommasInsideQuotes() {
        List<String> row = AcademicService.parseLine("GL24001,\"Data Structures, Lab\",40,50,,");
        assertEquals(6, row.size());
        assertEquals("Data Structures, Lab", row.get(1));
        assertEquals("", row.get(4));
    }

    @Test
    void roundsToOneDecimal() {
        assertEquals(66.7, AcademicService.round(66.666));
    }
}
