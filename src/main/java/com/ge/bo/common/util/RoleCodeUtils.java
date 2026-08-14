package com.ge.bo.common.util;

import java.util.Locale;
import java.util.Set;

public final class RoleCodeUtils {

  private static final Set<String> RESERVED_ROLE_CODES = Set.of("SYSTEM_ADMIN", "SUPER_ADMIN");

  private RoleCodeUtils() {
  }

  public static boolean containsReservedCode(String code) {
    if (code == null) {
      return false;
    }
    String normalized = code.toUpperCase(Locale.ROOT);
    return RESERVED_ROLE_CODES.stream().anyMatch(normalized::contains);
  }
}
