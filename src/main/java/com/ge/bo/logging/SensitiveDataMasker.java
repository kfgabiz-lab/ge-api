package com.ge.bo.logging;

import java.util.regex.Pattern;

public final class SensitiveDataMasker {

  private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
      "(?i)(password|passwordHash|passwd|pwd|credentials|secret|token)"
          + "(\\s*[=:\"]+\\s*[\"']?)([^\"',&\\s}\\]]+)",
      Pattern.CASE_INSENSITIVE);

  private static final String MASK = "$1$2****";

  private SensitiveDataMasker() {
  }

  public static String mask(String message) {
    if (message == null) {
      return null;
    }
    return SENSITIVE_PATTERN.matcher(message).replaceAll(MASK);
  }

  public static String mask(Object value) {
    return mask(String.valueOf(value));
  }
}
