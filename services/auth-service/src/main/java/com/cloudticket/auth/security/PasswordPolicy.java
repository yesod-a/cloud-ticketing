package com.cloudticket.auth.security;

import java.util.regex.Pattern;

/** Basic password policy shared by registration and reset flows. */
public final class PasswordPolicy {
    private static final Pattern UPPER = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWER = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT = Pattern.compile(".*\\d.*");
    private PasswordPolicy() {}
    public static boolean isValid(String password) {
        return password != null && password.length() >= 8 && password.length() <= 128
                && UPPER.matcher(password).matches() && LOWER.matcher(password).matches()
                && DIGIT.matcher(password).matches();
    }
    public static void requireValid(String password) {
        if (!isValid(password)) throw new IllegalArgumentException("Password must be 8-128 characters with upper, lower and digit");
    }
}
