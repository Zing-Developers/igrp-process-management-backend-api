package cv.igrp.platform.process.management.processruntime.application.commands;

import cv.igrp.platform.process.management.processruntime.application.dto.TaskAssignmentRuleListPageDTO;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRule;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRuleFilter;
import cv.igrp.platform.process.management.processruntime.domain.service.TaskAssignmentRuleService;
import cv.igrp.platform.process.management.processruntime.mappers.TaskAssignmentRuleMapper;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListTaskAssignmentRulesCommandHandlerTest {

  @Mock
  private TaskAssignmentRuleService service;

  @Mock
  private TaskAssignmentRuleMapper mapper;

  @InjectMocks
  private ListTaskAssignmentRulesCommandHandler handler;

  @Test
  void handle_shouldReturnMappedPage() {
    var command = new ListTaskAssignmentRulesCommand();
    var filter = TaskAssignmentRuleFilter.builder().build();
    var page = PageableLista.<TaskAssignmentRule>builder()
        .pageNumber(0)
        .pageSize(50)
        .totalElements(0L)
        .totalPages(0)
        .content(List.of())
        .build();
    var dto = new TaskAssignmentRuleListPageDTO();

    when(mapper.toFilter(command)).thenReturn(filter);
    when(service.getAll(filter)).thenReturn(page);
    when(mapper.toListPageDTO(page)).thenReturn(dto);

    var response = handler.handle(command);

    assertEquals(200, response.getStatusCodeValue());
    assertSame(dto, response.getBody());
    verify(mapper).toFilter(command);
    verify(service).getAll(filter);
    verify(mapper).toListPageDTO(page);
  }
}
