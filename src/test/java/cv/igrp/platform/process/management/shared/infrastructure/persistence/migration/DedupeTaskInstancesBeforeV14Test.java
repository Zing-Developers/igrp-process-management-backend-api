package cv.igrp.platform.process.management.shared.infrastructure.persistence.migration;

import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Databases from before task syncs were serialized can hold two app tasks for one engine task, and
 * V14 refuses to make external_id unique over them. The callback keeps one and the children follow.
 */
class DedupeTaskInstancesBeforeV14Test {

  private static final UUID OPEN = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID ENDED = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000003");

  private final DedupeTaskInstancesBeforeV14 callback = new DedupeTaskInstancesBeforeV14();
  private JdbcTemplate jdbc;

  @BeforeEach
  void schema() {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:dedupe-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("""
        CREATE TABLE t_task_instance (id UUID PRIMARY KEY, external_id VARCHAR(255),
          assigned_at TIMESTAMP, ended_at TIMESTAMP, last_modified_date TIMESTAMP, created_date TIMESTAMP NOT NULL)""");
    jdbc.execute("""
        CREATE TABLE t_task_instance_candidate_group (task_instance_id UUID NOT NULL, group_id VARCHAR(255) NOT NULL,
          PRIMARY KEY (task_instance_id, group_id),
          FOREIGN KEY (task_instance_id) REFERENCES t_task_instance (id) ON DELETE CASCADE)""");
    jdbc.execute("""
        CREATE TABLE t_task_instance_candidate_user (task_instance_id UUID NOT NULL, user_id VARCHAR(255) NOT NULL,
          PRIMARY KEY (task_instance_id, user_id),
          FOREIGN KEY (task_instance_id) REFERENCES t_task_instance (id) ON DELETE CASCADE)""");
    jdbc.execute("""
        CREATE TABLE t_task_instance_events (id UUID PRIMARY KEY, task_instance_id UUID,
          FOREIGN KEY (task_instance_id) REFERENCES t_task_instance (id))""");
    jdbc.execute("""
        CREATE TABLE t_variable (id UUID PRIMARY KEY, task_instance_id UUID,
          FOREIGN KEY (task_instance_id) REFERENCES t_task_instance (id))""");
    jdbc.execute("""
        CREATE TABLE t_task_assignment_rule (id UUID PRIMARY KEY, created_by_task UUID,
          FOREIGN KEY (created_by_task) REFERENCES t_task_instance (id) ON DELETE SET NULL)""");
  }

  @Test
  void keepsTheCopyFurthestAlongAndMovesTheOthersChildrenOntoIt() throws Exception {
    jdbc.update("INSERT INTO t_task_instance VALUES (?, 'engine-1', NULL, NULL, TIMESTAMP '2026-06-11 17:51:33', TIMESTAMP '2026-06-11 17:51:33')", OPEN);
    jdbc.update("INSERT INTO t_task_instance VALUES (?, 'engine-1', TIMESTAMP '2026-06-12 09:00:00', TIMESTAMP '2026-06-12 10:00:00', TIMESTAMP '2026-06-12 10:00:00', TIMESTAMP '2026-06-11 17:51:34')", ENDED);
    jdbc.update("INSERT INTO t_task_instance VALUES (?, 'engine-2', NULL, NULL, NULL, TIMESTAMP '2026-06-11 18:00:00')", OTHER);
    jdbc.update("INSERT INTO t_task_instance_candidate_group VALUES (?, 'g1'), (?, 'g2'), (?, 'g1')", OPEN, OPEN, ENDED);
    jdbc.update("INSERT INTO t_task_instance_candidate_user VALUES (?, 'ana@irn')", OPEN);
    jdbc.update("INSERT INTO t_task_instance_events VALUES (?, ?)", UUID.randomUUID(), OPEN);
    jdbc.update("INSERT INTO t_variable VALUES (?, ?)", UUID.randomUUID(), OPEN);
    jdbc.update("INSERT INTO t_task_assignment_rule VALUES (?, ?)", UUID.randomUUID(), OPEN);

    run();

    assertThat(jdbc.queryForList("SELECT id FROM t_task_instance ORDER BY id", UUID.class))
        .containsExactly(ENDED, OTHER);
    assertThat(jdbc.queryForList(
        "SELECT group_id FROM t_task_instance_candidate_group WHERE task_instance_id = ? ORDER BY group_id", String.class, ENDED))
        .containsExactly("g1", "g2");
    assertThat(jdbc.queryForList(
        "SELECT user_id FROM t_task_instance_candidate_user WHERE task_instance_id = ?", String.class, ENDED))
        .containsExactly("ana@irn");
    assertThat(jdbc.queryForList("SELECT task_instance_id FROM t_task_instance_events", UUID.class)).containsExactly(ENDED);
    assertThat(jdbc.queryForList("SELECT task_instance_id FROM t_variable", UUID.class)).containsExactly(ENDED);
    assertThat(jdbc.queryForList("SELECT created_by_task FROM t_task_assignment_rule", UUID.class)).containsExactly(ENDED);
  }

  @Test
  void withoutCopiesLeavesTheTableAsItIs() throws Exception {
    jdbc.update("INSERT INTO t_task_instance VALUES (?, 'engine-1', NULL, NULL, NULL, TIMESTAMP '2026-06-11 17:51:33')", OPEN);
    jdbc.update("INSERT INTO t_task_instance VALUES (?, NULL, NULL, NULL, NULL, TIMESTAMP '2026-06-11 17:51:33')", ENDED);
    jdbc.update("INSERT INTO t_task_instance VALUES (?, NULL, NULL, NULL, NULL, TIMESTAMP '2026-06-11 17:51:33')", OTHER);

    run();

    assertThat(jdbc.queryForObject("SELECT count(*) FROM t_task_instance", Integer.class)).isEqualTo(3);
  }

  @Test
  void runsOnlyRightBeforeV14() {
    assertThat(callback.supports(Event.BEFORE_EACH_MIGRATE, contextAt("14"))).isTrue();
    assertThat(callback.supports(Event.BEFORE_EACH_MIGRATE, contextAt("13"))).isFalse();
    assertThat(callback.supports(Event.BEFORE_EACH_MIGRATE, contextAt("15"))).isFalse();
    assertThat(callback.supports(Event.BEFORE_MIGRATE, contextAt("14"))).isFalse();
    assertThat(callback.canHandleInTransaction(Event.BEFORE_EACH_MIGRATE, contextAt("14"))).isTrue();
  }

  private void run() throws Exception {
    try (Connection connection = jdbc.getDataSource().getConnection()) {
      callback.dedupe(connection);
    }
  }

  private static Context contextAt(String version) {
    var info = mock(MigrationInfo.class);
    when(info.getVersion()).thenReturn(MigrationVersion.fromVersion(version));
    var context = mock(Context.class);
    when(context.getMigrationInfo()).thenReturn(info);
    return context;
  }
}
