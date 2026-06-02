package cv.igrp.platform.process.management.shared.application.constants;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TaskAssignmentMode {
  ALWAYS,
  ONE_TIME;

  @JsonCreator
  public static TaskAssignmentMode fromValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return TaskAssignmentMode.valueOf(value.trim().toUpperCase());
  }
}
