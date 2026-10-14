package cv.igrp.platform.process.management.processruntime.mappers;

import cv.igrp.platform.process.management.processruntime.application.dto.ActivityDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.ActivityProgressDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskVariableDTO;
import cv.igrp.platform.process.management.processruntime.domain.models.ActivityData;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessArtifactEvent;
import cv.igrp.platform.process.management.shared.application.constants.VariableTag;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ActivityMapper {

  private final UserProfileMapper userProfileMapper;

  public ActivityMapper(UserProfileMapper userProfileMapper) {
    this.userProfileMapper = userProfileMapper;
  }

  public ActivityDTO toDto(ActivityData activityData) {

    var activityDto = new ActivityDTO();

    activityDto.setId(activityData.getId().getValue());
    activityDto.setName(activityData.getName().getValue());
    activityDto.setDescription(activityData.getDescription());
    activityDto.setStatus(activityData.getStatus().name());
    activityDto.setType(activityData.getType().name());
    activityDto.setParentId(activityData.getParentId().getValue());
    activityDto.setParentProcessInstanceId(activityData.getParentProcessInstanceId().getValue());
    activityDto.setProcessInstanceId(activityData.getProcessInstanceId().getValue());
    activityDto.setVariables(toActivityVariableDTO(activityData.getVariables(), VariableTag.VARIABLES));

    return activityDto;

  }

  public List<TaskVariableDTO> toActivityVariableDTO(Map<String,Object> variables, VariableTag tag){
    Object group = variables.get(tag.getCode());

    if (!(group instanceof Map<?, ?> map))
      return List.of();

    return map.entrySet().stream()
        .map(e -> new TaskVariableDTO(e.getKey().toString(), e.getValue()))
        .toList();
  }

  public ActivityProgressDTO toProgressDto(ProcessArtifactEvent processArtifactEvent) {

    var activityProgressDTO = new ActivityProgressDTO();

    activityProgressDTO.setActivityId(processArtifactEvent.getArtifactId());
    activityProgressDTO.setActivityName(processArtifactEvent.getArtifactName());
    activityProgressDTO.setType(processArtifactEvent.getType().name());
    activityProgressDTO.setStatus(processArtifactEvent.getStatus().name());
    activityProgressDTO.setProcessInstanceId(processArtifactEvent.getProcessInstanceId());
    activityProgressDTO.setAssignee(processArtifactEvent.getAssignee());
    activityProgressDTO.setDurationMillis(processArtifactEvent.getDuration());
    activityProgressDTO.setAssignee(processArtifactEvent.getAssignee());
    activityProgressDTO.setExecutionId(processArtifactEvent.getExecutionId());
    activityProgressDTO.setTaskId(processArtifactEvent.getTaskId());
    activityProgressDTO.setActivityInstanceId(processArtifactEvent.getArtifactInstanceId());
    activityProgressDTO.setTreeNumber(processArtifactEvent.getTreeNumber());

    ZoneId cvZone = ZoneId.of("Atlantic/Cape_Verde");
    activityProgressDTO.setStartTime(
        processArtifactEvent.getStartTime() != null ? LocalDateTime.ofInstant(processArtifactEvent.getStartTime(), cvZone) : null
    );
    activityProgressDTO.setEndTime(
        processArtifactEvent.getEndTime() != null ? LocalDateTime.ofInstant(processArtifactEvent.getEndTime(), cvZone) : null
    );

    activityProgressDTO.setVariables(toActivityVariableDTO(processArtifactEvent.getVariables(), VariableTag.VARIABLES));
    activityProgressDTO.setForms(toActivityVariableDTO(processArtifactEvent.getVariables(), VariableTag.FORMS));

    activityProgressDTO.setUserProfileAssignee(userProfileMapper.toDTO(processArtifactEvent.getUserProfileAssignee()));

    var failure = processArtifactEvent.getFailure();
    if (failure != null) {
      activityProgressDTO.setJobId(failure.jobId());
      activityProgressDTO.setErrorMessage(failure.errorMessage());
      activityProgressDTO.setAttempts(failure.attempts());
      activityProgressDTO.setFirstFailedAt(failure.firstFailedAt() != null ? LocalDateTime.ofInstant(failure.firstFailedAt(), cvZone) : null);
      activityProgressDTO.setLastFailedAt(failure.lastFailedAt() != null ? LocalDateTime.ofInstant(failure.lastFailedAt(), cvZone) : null);
    }

    return activityProgressDTO;

  }

  public List<ActivityDTO> toInstancesDto(List<ActivityData> instances) {
    return instances.stream().map(this::toDto).collect(Collectors.toList());
  }

  public List<ActivityProgressDTO> toProgressesDto(List<ProcessArtifactEvent> progress) {
    return progress.stream().map(this::toProgressDto).collect(Collectors.toList());
  }

}
