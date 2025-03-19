package uk.gov.hmcts.reform.enforcement.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoUnitTest {
    @DisplayName("Example of Unit Test")
    @Test
    void exampleOfTest() {
        assertTrue(System.currentTimeMillis() > 0,"Example of Unit Test");
    }
}
