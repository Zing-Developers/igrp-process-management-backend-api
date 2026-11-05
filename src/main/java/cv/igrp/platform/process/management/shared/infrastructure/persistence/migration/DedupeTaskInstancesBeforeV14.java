package cv.igrp.platform.process.management.shared.infrastructure.persistence.migration;

import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * V14 makes t_task_instance.external_id unique and stops if copies exist. Before task syncs were
 * serialized per process, two syncs at the same moment could both copy the same engine task, so
 * databases from that time have them. Right before V14, in its transaction, this keeps one row per
 * external_id and moves the others' children onto it. Databases already past V14 never run it.
 * <p>
 * The row kept is the one furthest along: ended, then assigned, then the last modified, then the
 * first created. Envers history (the _aud tables) is left as it is.
 */
@Component
public class DedupeTaskInstancesBeforeV14 implements Callback {

  private static final Logger LOGGER = LoggerFactory.getLogger(DedupeTaskInstancesBeforeV14.class);

  static final MigrationVersion V14 = MigrationVersion.fromVersion("14");

  private static final String FIND_COPIES = """
      SELECT id, keep_id, external_id FROM (
        SELECT id, external_id,
               first_value(id) OVER w AS keep_id,
               row_number() OVER w AS rn
        FROM t_task_instance
        WHERE external_id IS NOT NULL
        WINDOW w AS (
          PARTITION BY external_id
          ORDER BY CASE WHEN ended_at IS NOT NULL THEN 0 ELSE 1 END,
                   CASE WHEN assigned_at IS NOT NULL THEN 0 ELSE 1 END,
                   last_modified_date DESC NULLS LAST,
                   created_date,
                   id)
      ) ranked
      WHERE rn > 1
      """;

  // Each takes (kept id, copy id); the inserts take the kept id again to skip what it already has
  private static final List<String> MOVE_CHILDREN = List.of(
      "UPDATE t_task_instance_events SET task_instance_id = ? WHERE task_instance_id = ?",
      "UPDATE t_variable SET task_instance_id = ? WHERE task_instance_id = ?",
      "UPDATE t_task_assignment_rule SET created_by_task = ? WHERE created_by_task = ?",
      """
      INSERT INTO t_task_instance_candidate_group (task_instance_id, group_id)
      SELECT CAST(? AS UUID), g.group_id FROM t_task_instance_candidate_group g
      WHERE g.task_instance_id = ?
        AND g.group_id NOT IN (SELECT group_id FROM t_task_instance_candidate_group WHERE task_instance_id = ?)
      """,
      """
      INSERT INTO t_task_instance_candidate_user (task_instance_id, user_id)
      SELECT CAST(? AS UUID), u.user_id FROM t_task_instance_candidate_user u
      WHERE u.task_instance_id = ?
        AND u.user_id NOT IN (SELECT user_id FROM t_task_instance_candidate_user WHERE task_instance_id = ?)
      """
  );

  // The copy's own candidate rows go with it (ON DELETE CASCADE)
  private static final String DELETE_COPY = "DELETE FROM t_task_instance WHERE id = ?";

  @Override
  public boolean supports(Event event, Context context) {
    return event == Event.BEFORE_EACH_MIGRATE
        && context.getMigrationInfo() != null
        && V14.equals(context.getMigrationInfo().getVersion());
  }

  @Override
  public boolean canHandleInTransaction(Event event, Context context) {
    return true;
  }

  @Override
  public void handle(Event event, Context context) {
    try {
      dedupe(context.getConnection());
    } catch (SQLException e) {
      throw new IllegalStateException("Could not remove the duplicated t_task_instance rows before V14", e);
    }
  }

  @Override
  public String getCallbackName() {
    return "dedupe-task-instances-before-v14";
  }

  void dedupe(Connection connection) throws SQLException {
    var copies = findCopies(connection);
    if (copies.isEmpty()) {
      return;
    }
    LOGGER.warn("Before V14: {} duplicated t_task_instance row(s) found; keeping one per external_id", copies.size());
    for (var copy : copies) {
      for (var sql : MOVE_CHILDREN) {
        try (var statement = connection.prepareStatement(sql)) {
          statement.setObject(1, copy.keepId());
          statement.setObject(2, copy.id());
          if (statement.getParameterMetaData().getParameterCount() == 3) {
            statement.setObject(3, copy.keepId());
          }
          statement.executeUpdate();
        }
      }
      try (var statement = connection.prepareStatement(DELETE_COPY)) {
        statement.setObject(1, copy.id());
        statement.executeUpdate();
      }
      LOGGER.warn("Before V14: external_id [{}] kept task instance [{}], removed [{}]",
          copy.externalId(), copy.keepId(), copy.id());
    }
  }

  private static List<Copy> findCopies(Connection connection) throws SQLException {
    var copies = new ArrayList<Copy>();
    try (var statement = connection.prepareStatement(FIND_COPIES);
         var rows = statement.executeQuery()) {
      while (rows.next()) {
        copies.add(new Copy(
            rows.getObject("id", UUID.class),
            rows.getObject("keep_id", UUID.class),
            rows.getString("external_id")));
      }
    }
    return copies;
  }

  private record Copy(UUID id, UUID keepId, String externalId) {
  }
}
