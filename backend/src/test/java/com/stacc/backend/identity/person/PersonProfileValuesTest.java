package com.stacc.backend.identity.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PersonProfileValuesTest {

    @Test
    void namesAreTidiedAndRequiredWhereTheCollegeRecordsThem() {
        assertThat(PersonProfileValues.fullName("  Aarav   Sharma ")).isEqualTo("Aarav Sharma");
        assertThat(PersonProfileValues.fullName("अनन्या  सिंह")).isEqualTo("अनन्या सिंह");
        assertThatThrownBy(() -> PersonProfileValues.fullName(" ")).hasMessage("Full name is required.");
        assertThatThrownBy(() -> PersonProfileValues.fullName("x".repeat(151))).hasMessageContaining("at most 150");
        assertThatThrownBy(() -> PersonProfileValues.fullName("Bad\u0007Name")).hasMessageContaining("not allowed");
        assertThat(PersonProfileValues.preferredName("   ")).isNull();
    }

    @Test
    void emailsAreCheckedAndStoredInLowerCase() {
        assertThat(PersonProfileValues.email(" Student1@Stacc.Example ")).isEqualTo("student1@stacc.example");
        assertThat(PersonProfileValues.email("")).isNull();
        for (String bad : new String[] {"no-at-sign", "two@@signs.example", "space in@x.example", "nodot@example"}) {
            assertThatThrownBy(() -> PersonProfileValues.email(bad)).as(bad).hasMessage("Email address is not valid.");
        }
    }

    @Test
    void phonesNeedSevenToFifteenDigits() {
        assertThat(PersonProfileValues.phone("+91 98765 43210")).isEqualTo("+91 98765 43210");
        assertThat(PersonProfileValues.phone("(080) 2345-6789")).isEqualTo("(080) 2345-6789");
        assertThat(PersonProfileValues.phone(null)).isNull();
        for (String bad : new String[] {"12345", "1234567890123456", "call me", "98765+43210"}) {
            assertThatThrownBy(() -> PersonProfileValues.phone(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
