package cv.igrp.platform.process.management.shared.security.util;

public final class IgrpAuthorizationConstants {

  public static final String SUPER_ADMIN_ROLE = "DEPT_IGRP.superadmin";
  public static final String ROLE_PREFIX = "ROLE_";

  /**
   * Permission to search task instances beyond the caller's own tasks and their groups'.
   * Registered in the IRN System Administration; see docs/SPEC_ROUTE_AUTHORIZATION.md.
   */
  public static final String TASK_INSTANCES_SEARCH_ALL = "TASK_INSTANCES:pesquisar_todos";

  private IgrpAuthorizationConstants() {}

}
