package cv.igrp.platform.process.management.shared.security.util;

import cv.igrp.platform.process.management.shared.domain.models.Code;

import java.util.List;

public interface UserContext {

  Code getCurrentUser();

  String getCurrentUserName();

  List<String> getCurrentGroups();

  List<String> getCurrentRoles();

  boolean isSuperAdmin();

  /**
   * Whether the current user holds the given permission, in {@code MODULE:action} form.
   *
   * @param permission the permission to check, matched exactly against the granted authorities
   * @return {@code true} when the authenticated user has that permission
   */
  boolean hasPermission(String permission);

}
