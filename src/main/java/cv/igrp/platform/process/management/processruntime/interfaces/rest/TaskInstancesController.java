/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.process.management.processruntime.interfaces.rest;

import cv.igrp.framework.stereotype.IgrpController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;

import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.process.management.processruntime.application.queries.*;
import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.platform.process.management.processruntime.application.commands.*;
import cv.igrp.platform.process.management.processruntime.application.dto.VariablesFilterDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskAssignmentRuleListDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskAssignmentRuleListPageDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskAssignmentRuleUpdateDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskInstanceListPageDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskInstanceDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.UnclaimTaskDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.AssignTaskDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskDataDTO;
import java.util.List;
import cv.igrp.platform.process.management.shared.application.dto.ConfigParameterDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskVariablesFormsDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskInstanceStatsDTO;
import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;

@IgrpController
@RestController
@RequestMapping(path = "tasks-instances")
@Tag(name = "TaskInstances", description = "Task Instances Management")
public class TaskInstancesController {

  
  private final QueryBus queryBus;
  private final CommandBus commandBus;

  public TaskInstancesController(QueryBus queryBus, CommandBus commandBus) {
          this.queryBus = queryBus;
          this.commandBus = commandBus;
  }
      @PostMapping(
   value = "search"
  )
  @Operation(
    summary = "POST method to handle operations for List task instances",
    description = "POST method to handle operations for List task instances",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "List of task instances",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = TaskInstanceListPageDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<TaskInstanceListPageDTO> listTaskInstances(@Valid @RequestBody VariablesFilterDTO listTaskInstancesRequest
    , @RequestParam(value = "processInstanceId", required = false) String processInstanceId,
    @RequestParam(value = "processNumber", required = false) String processNumber,
    @RequestParam(value = "processReleaseKey", required = false) String processReleaseKey,
    @RequestParam(value = "applicationBase", required = false) String applicationBase,
    @RequestParam(value = "candidateGroups", required = false) String candidateGroups,
    @RequestParam(value = "candidateUsers", required = false) String candidateUsers,
    @RequestParam(value = "user", required = false) String user,
    @RequestParam(value = "status", required = false) String status,
    @RequestParam(value = "dateFrom", required = false) String dateFrom,
    @RequestParam(value = "dateTo", required = false) String dateTo,
    @RequestParam(value = "page", required = false) Integer page,
    @RequestParam(value = "size", required = false) Integer size,
    @RequestParam(value = "name", required = false) String name,
    @RequestParam(value = "processName", required = false) String processName,
    @RequestParam(value = "filterByCurrentUser", required = false) boolean filterByCurrentUser,
    @RequestParam(value = "priority", required = false) Integer priority)
  {

      final var command = new ListTaskInstancesCommand(listTaskInstancesRequest, processInstanceId, processNumber, processReleaseKey, applicationBase, candidateGroups, candidateUsers, user, status, dateFrom, dateTo, page, size, name, processName, filterByCurrentUser, priority);

       ResponseEntity<TaskInstanceListPageDTO> response = commandBus.send(command);

       return response;
  }

      @GetMapping(
   value = "{id}"
  )
  @Operation(
    summary = "GET method to handle operations for Get task instance by id",
    description = "GET method to handle operations for Get task instance by id",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "Task Instance Info",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = TaskInstanceDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<TaskInstanceDTO> getTaskInstanceById(
    @PathVariable(value = "id") String id)
  {

      final var query = new GetTaskInstanceByIdQuery(id);

      ResponseEntity<TaskInstanceDTO> response = queryBus.handle(query);

      return response;
  }

      @PostMapping(
   value = "{id}/claim"
  )
  @Operation(
    summary = "POST method to handle operations for Claim task",
    description = "POST method to handle operations for Claim task",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "No Content",
          content = @Content(
              mediaType = "",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<?> claimTask(
    @PathVariable(value = "id") String id)
  {

      final var command = new ClaimTaskCommand(id);

       ResponseEntity<?> response = commandBus.send(command);

       return response;
  }

      @PostMapping(
   value = "{id}/unclaim"
  )
  @Operation(
    summary = "POST method to handle operations for Un claim task",
    description = "POST method to handle operations for Un claim task",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "No content",
          content = @Content(
              mediaType = "",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<?> unClaimTask(@Valid @RequestBody UnclaimTaskDTO unClaimTaskRequest
    , @PathVariable(value = "id") String id)
  {

      final var command = new UnClaimTaskCommand(unClaimTaskRequest, id);

       ResponseEntity<?> response = commandBus.send(command);

       return response;
  }

      @PostMapping(
   value = "{id}/assign"
  )
  @Operation(
    summary = "POST method to handle operations for Assign task",
    description = "POST method to handle operations for Assign task",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "No content",
          content = @Content(
              mediaType = "",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )
  
  public ResponseEntity<?> assignTask(@Valid @RequestBody AssignTaskDTO assignTaskRequest
    , @PathVariable(value = "id") String id)
  {

      final var command = new AssignTaskCommand(assignTaskRequest, id);

       ResponseEntity<?> response = commandBus.send(command);

       return response;
  }

      @PostMapping(
   value = "{id}/complete"
  )
  @Operation(
    summary = "POST method to handle operations for Complete task",
    description = "POST method to handle operations for Complete task",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = TaskInstanceDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<TaskInstanceDTO> completeTask(@Valid @RequestBody TaskDataDTO completeTaskRequest
    , @PathVariable(value = "id") String id)
  {

      final var command = new CompleteTaskCommand(completeTaskRequest, id);

       ResponseEntity<TaskInstanceDTO> response = commandBus.send(command);

       return response;
  }

      @PostMapping(
   value = "me"
  )
  @Operation(
    summary = "POST method to handle operations for Get all my tasks",
    description = "POST method to handle operations for Get all my tasks",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "List All My Tasks",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = TaskInstanceListPageDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<TaskInstanceListPageDTO> getAllMyTasks(@Valid @RequestBody VariablesFilterDTO getAllMyTasksRequest
    , @RequestParam(value = "processInstanceId", required = false) String processInstanceId,
    @RequestParam(value = "processNumber", required = false) String processNumber,
    @RequestParam(value = "applicationBase", required = false) String applicationBase,
    @RequestParam(value = "processName", required = false) String processName,
    @RequestParam(value = "status", required = false) String status,
    @RequestParam(value = "dateFrom", required = false) String dateFrom,
    @RequestParam(value = "dateTo", required = false) String dateTo,
    @RequestParam(value = "page", required = false) Integer page,
    @RequestParam(value = "size", required = false) Integer size,
    @RequestParam(value = "processReleaseKey", required = false) String processReleaseKey,
    @RequestParam(value = "name", required = false) String name,
    @RequestParam(value = "priority", required = false) Integer priority)
  {

      final var command = new GetAllMyTasksCommand(getAllMyTasksRequest, processInstanceId, processNumber, applicationBase, processName, status, dateFrom, dateTo, page, size, processReleaseKey, name, priority);

       ResponseEntity<TaskInstanceListPageDTO> response = commandBus.send(command);

       return response;
  }

      @GetMapping(
   value = "status"
  )
  @Operation(
    summary = "GET method to handle operations for List task instance status",
    description = "GET method to handle operations for List task instance status",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ConfigParameterDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<ConfigParameterDTO>> listTaskInstanceStatus(
    )
  {

      final var query = new ListTaskInstanceStatusQuery();

      ResponseEntity<List<ConfigParameterDTO>> response = queryBus.handle(query);

      return response;
  }

      @GetMapping(
   value = "event_type"
  )
  @Operation(
    summary = "GET method to handle operations for List task instance event type",
    description = "GET method to handle operations for List task instance event type",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = ConfigParameterDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<List<ConfigParameterDTO>> listTaskInstanceEventType(
    )
  {

      final var query = new ListTaskInstanceEventTypeQuery();

      ResponseEntity<List<ConfigParameterDTO>> response = queryBus.handle(query);

      return response;
  }

      @GetMapping(
   value = "{id}/variables"
  )
  @Operation(
    summary = "GET method to handle operations for Get task variables by id",
    description = "GET method to handle operations for Get task variables by id",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = TaskVariablesFormsDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<TaskVariablesFormsDTO> getTaskVariablesById(
    @PathVariable(value = "id") String id)
  {

      final var query = new GetTaskVariablesByIdQuery(id);

      ResponseEntity<TaskVariablesFormsDTO> response = queryBus.handle(query);

      return response;
  }

      @GetMapping(
   value = "stats"
  )
  @Operation(
    summary = "GET method to handle operations for Get task instance statistics",
    description = "GET method to handle operations for Get task instance statistics",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = TaskInstanceStatsDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<TaskInstanceStatsDTO> getTaskInstanceStatistics(
    )
  {

      final var query = new GetTaskInstanceStatisticsQuery();

      ResponseEntity<TaskInstanceStatsDTO> response = queryBus.handle(query);

      return response;
  }

      @GetMapping(
   value = "stats/me"
  )
  @Operation(
    summary = "GET method to handle operations for Get my task instance statistics",
    description = "GET method to handle operations for Get my task instance statistics",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = TaskInstanceStatsDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<TaskInstanceStatsDTO> getMyTaskInstanceStatistics(
    )
  {

      final var query = new GetMyTaskInstanceStatisticsQuery();

      ResponseEntity<TaskInstanceStatsDTO> response = queryBus.handle(query);

      return response;
  }

      @GetMapping(
   value = "assignment-rules"
  )
  @Operation(
    summary = "GET method to handle operations for List task assignment rules",
    description = "GET method to handle operations for List task assignment rules",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "List task assignment rules",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = TaskAssignmentRuleListPageDTO.class,
                  type = "object")
          )
      )
    }
  )

  public ResponseEntity<TaskAssignmentRuleListPageDTO> listTaskAssignmentRules(
    @RequestParam(value = "processInstanceId", required = false) String processInstanceId,
    @RequestParam(value = "processDefinitionKey", required = false) String processDefinitionKey,
    @RequestParam(value = "taskDefinitionKey", required = false) String taskDefinitionKey,
    @RequestParam(value = "assignee", required = false) String assignee,
    @RequestParam(value = "candidateUsers", required = false) String candidateUsers,
    @RequestParam(value = "candidateGroups", required = false) String candidateGroups,
    @RequestParam(value = "assignmentMode", required = false) TaskAssignmentMode assignmentMode,
    @RequestParam(value = "consumed", required = false) Boolean consumed,
    @RequestParam(value = "active", required = false) Boolean active,
    @RequestParam(value = "createdByTask", required = false) String createdByTask,
    @RequestParam(value = "page", required = false) Integer page,
    @RequestParam(value = "size", required = false) Integer size)
  {

      final var command = new ListTaskAssignmentRulesCommand(processInstanceId, processDefinitionKey, taskDefinitionKey, assignee, candidateUsers, candidateGroups, assignmentMode, consumed, active, createdByTask, page, size);

       ResponseEntity<TaskAssignmentRuleListPageDTO> response = commandBus.send(command);

       return response;
  }

      @PutMapping(
   value = "assignment-rules/{id}"
  )
  @Operation(
    summary = "PUT method to handle operations for Update task assignment rule",
    description = "PUT method to handle operations for Update task assignment rule",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "Task assignment rule updated",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = TaskAssignmentRuleListDTO.class,
                  type = "object")
          )
      )
    }
  )

  public ResponseEntity<TaskAssignmentRuleListDTO> updateTaskAssignmentRule(@Valid @RequestBody TaskAssignmentRuleUpdateDTO updateTaskAssignmentRuleRequest
    , @PathVariable(value = "id") String id)
  {

      final var command = new UpdateTaskAssignmentRuleCommand(updateTaskAssignmentRuleRequest, id);

       ResponseEntity<TaskAssignmentRuleListDTO> response = commandBus.send(command);

       return response;
  }

      @DeleteMapping(
   value = "assignment-rules/{id}"
  )
  @Operation(
    summary = "DELETE method to handle operations for Delete task assignment rule",
    description = "DELETE method to handle operations for Delete task assignment rule",
    responses = {
      @ApiResponse(
          responseCode = "204",
          description = "No content",
          content = @Content(
              mediaType = "",
              schema = @Schema(
                  implementation = String.class,
                  type = "String")
          )
      )
    }
  )

  public ResponseEntity<?> deleteTaskAssignmentRule(
    @PathVariable(value = "id") String id)
  {

      final var command = new DeleteTaskAssignmentRuleCommand(id);

       ResponseEntity<?> response = commandBus.send(command);

       return response;
  }

      @PostMapping(
   value = "{id}/save"
  )
  @Operation(
    summary = "POST method to handle operations for Save task",
    description = "POST method to handle operations for Save task",
    responses = {
      @ApiResponse(
          responseCode = "200",
          description = "",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(
                  implementation = TaskInstanceDTO.class,
                  type = "object")
          )
      )
    }
  )
  
  public ResponseEntity<TaskInstanceDTO> saveTask(@Valid @RequestBody TaskDataDTO saveTaskRequest
    , @PathVariable(value = "id") String id)
  {

      final var command = new SaveTaskCommand(saveTaskRequest, id);

       ResponseEntity<TaskInstanceDTO> response = commandBus.send(command);

       return response;
  }

}
