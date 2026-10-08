package com.immunitech.immunecare.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AgeGroupTest {

    @ParameterizedTest
    @CsvSource({
            "0,UNDER_1", "1,AGE_1_4", "4,AGE_1_4", "5,AGE_5_11", "11,AGE_5_11", "12,AGE_12_17",
            "17,AGE_12_17", "18,AGE_18_49", "49,AGE_18_49", "50,AGE_50_64", "64,AGE_50_64",
            "65,AGE_65_PLUS", "110,AGE_65_PLUS"
    })
    void bandBoundaries(int age, AgeGroup expected) {
        assertThat(AgeGroup.fromAge(age)).isEqualTo(expected);
    }

    @Test
    void negativeAgeRejected() {
        assertThatThrownBy(() -> AgeGroup.fromAge(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ageIsCountedOnTheVaccinationDate() {
        LocalDate dob = LocalDate.of(2020, 10, 6);
        assertThat(AgeGroup.forDates(dob, LocalDate.of(2025, 10, 5))).isEqualTo(AgeGroup.AGE_1_4);   // 4 years
        assertThat(AgeGroup.forDates(dob, LocalDate.of(2025, 10, 6))).isEqualTo(AgeGroup.AGE_5_11);  // 5th birthday
    }

    @Test
    void birthDateAfterVaccinationDateRejected() {
        assertThatThrownBy(() -> AgeGroup.forDates(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
