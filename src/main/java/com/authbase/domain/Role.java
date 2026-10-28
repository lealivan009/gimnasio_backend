package com.authbase.domain;

public enum Role {
    /** Alumno legado. Las cuentas nuevas usan {@link #ALUMNO}. */
    USER,
    ALUMNO,
    PROFESOR,
    ADMIN;

    public boolean isStaff() {
        return this == ADMIN || this == PROFESOR;
    }

    public boolean isStudent() {
        return this == USER || this == ALUMNO;
    }
}
