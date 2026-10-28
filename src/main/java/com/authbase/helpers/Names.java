package com.authbase.helpers;

public final class Names {

    private Names() {
    }

    public static String display(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        return (first + " " + last).trim();
    }

    public static String[] split(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[] {"", ""};
        }
        String trimmed = fullName.trim().replaceAll("\\s+", " ");
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[] {trimmed, ""};
        }
        return new String[] {trimmed.substring(0, space), trimmed.substring(space + 1)};
    }
}

