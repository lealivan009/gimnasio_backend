package com.authbase.domain;

public enum ExerciseMeasure {
    PESO,
    TIEMPO;

    public static ExerciseMeasure orPeso(ExerciseMeasure value) {
        return value == null ? PESO : value;
    }
}
