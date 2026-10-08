package com.immunitech.immunecare.report;

import java.time.LocalDate;
import java.time.Period;

/** Age bands used by the daily summary report (FR16). */
public enum AgeGroup {
    UNDER_1("Under 1 year"),
    AGE_1_4("1-4 years"),
    AGE_5_11("5-11 years"),
    AGE_12_17("12-17 years"),
    AGE_18_49("18-49 years"),
    AGE_50_64("50-64 years"),
    AGE_65_PLUS("65+ years");

    private final String label;

    AgeGroup(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static AgeGroup fromAge(int ageInYears) {
        if (ageInYears < 0) {
            throw new IllegalArgumentException("Age cannot be negative: " + ageInYears);
        }
        if (ageInYears < 1) return UNDER_1;
        if (ageInYears <= 4) return AGE_1_4;
        if (ageInYears <= 11) return AGE_5_11;
        if (ageInYears <= 17) return AGE_12_17;
        if (ageInYears <= 49) return AGE_18_49;
        if (ageInYears <= 64) return AGE_50_64;
        return AGE_65_PLUS;
    }

    /** Age band of a person on a given date (age on the day the dose was given). */
    public static AgeGroup forDates(LocalDate dateOfBirth, LocalDate onDate) {
        if (dateOfBirth.isAfter(onDate)) {
            throw new IllegalArgumentException(
                    "Date of birth " + dateOfBirth + " is after the vaccination date " + onDate);
        }
        return fromAge(Period.between(dateOfBirth, onDate).getYears());
    }
}
