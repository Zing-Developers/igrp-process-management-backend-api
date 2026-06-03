package cv.igrp.platform.process.management.processruntime.mappers;

import cv.igrp.platform.process.management.processruntime.application.commands.ListTaskInstancesCommand;
import cv.igrp.platform.process.management.processruntime.application.dto.VariablesFilterDTO;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class TaskInstanceMapperTest {

  private final TaskInstanceMapper mapper = new TaskInstanceMapper(
      mock(TaskInstanceEventMapper.class),
      mock(UserProfileMapper.class)
  );

  @Test
  void toFilter_shouldParseCandidateUsersLikeCandidateGroups() {
    var command = new ListTaskInstancesCommand(
        new VariablesFilterDTO(),
        null,
        null,
        null,
        null,
        "group-a, group-b, group-a",
        "user-a@nosi.cv, user-b@nosi.cv, user-a@nosi.cv",
        null,
        null,
        null,
        null,
        0,
        10,
        null,
        null,
        false
    );

    var filter = mapper.toFilter(command);

    assertEquals(Set.of("group-a", "group-b"), filter.getCandidateGroups());
    assertEquals(Set.of("user-a@nosi.cv", "user-b@nosi.cv"), filter.getCandidateUsers());
  }
}
