package uk.gov.hmcts.reform.enforcement.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoUnitTest {
    @DisplayName("Example of Unit Test")
    @Test
    void exampleOfTest() {
        assertTrue(System.currentTimeMillis() > 0,"Example of Unit Test");
    }

    @DisplayName("should demonstrate to write a test with hamcrest")
    @Test
    void shouldDemonstrateHamcrest() {
        assertThat("Hello World", is("Hello World"));
        assertThat(1 + 2 + 3 + 4 + 5, is(15));
    }
}
