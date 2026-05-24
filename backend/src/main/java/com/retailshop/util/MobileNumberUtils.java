package com.retailshop.util;

public final class MobileNumberUtils {

    private MobileNumberUtils() {
    }

    public static String normalizeIndianMobile(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        } else if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 13 && digits.startsWith("091")) {
            digits = digits.substring(3);
        }
        return digits.length() == 10 ? digits : "";
    }
}
