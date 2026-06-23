package cv.igrp.platform.process.management.processruntime.domain.service;

import cv.igrp.platform.process.management.processruntime.domain.models.*;
import cv.igrp.platform.process.management.processruntime.domain.repository.ProcessInstanceRepository;
import cv.igrp.platform.process.management.processruntime.domain.repository.RuntimeProcessEngineRepository;
import cv.igrp.platform.process.management.processruntime.domain.repository.TaskInstanceRepository;
import cv.igrp.platform.process.management.processruntime.domain.repository.UserProfileRepository;
import cv.igrp.platform.process.management.shared.application.constants.VariableTag;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ActivityInstanceService {

  private final RuntimeProcessEngineRepository runtimeProcessEngineRepository;
  private final ProcessInstanceRepository processInstanceRepository;
  private final TaskInstanceRepository taskInstanceRepository;
  private final UserProfileRepository userProfileRepository;

  public ActivityInstanceService(RuntimeProcessEngineRepository runtimeProcessEngineRepository,
                                 ProcessInstanceRepository processInstanceRepository,
                                 TaskInstanceRepository taskInstanceRepository,
                                 UserProfileRepository userProfileRepository) {
    this.runtimeProcessEngineRepository = runtimeProcessEngineRepository;
    this.processInstanceRepository = processInstanceRepository;
    this.taskInstanceRepository = taskInstanceRepository;
    this.userProfileRepository = userProfileRepository;
  }

  public ActivityData getById(String id) {
    var activity = runtimeProcessEngineRepository.getActivityById(id);
    Optional<TaskInstance> optionalTaskInstance = taskInstanceRepository.findByExternalId(id);
    if (optionalTaskInstance.isPresent()) {
      TaskInstance taskInstance = optionalTaskInstance.get();
      activity.addVariables(taskInstance.getVariables());
      activity.addVariables(taskInstance.getForms());
    }
    return activity;
  }

  public List<ActivityData> getActiveActivityInstances(String processIdentifier, ProcessArtifactEvent.ArtifactType type) {
    Optional<ProcessInstance> optionalProcessInstance = processInstanceRepository
        .findByBusinessKey(processIdentifier);
    if (optionalProcessInstance.isPresent()) {
      return getActiveActivityInstances(
          optionalProcessInstance.get(),
          type
      );
    }
    ProcessInstance processInstance = getProcessInstanceById(UUID.fromString(processIdentifier));
    return getActiveActivityInstances(processInstance, type);
  }

  private List<ActivityData> getActiveActivityInstances(ProcessInstance processInstance, ProcessArtifactEvent.ArtifactType type) {
    return runtimeProcessEngineRepository.getActiveActivityInstances(
        processInstance.getEngineProcessNumber().getValue(),
        type
    );
  }

  public List<ProcessArtifactEvent> getProcessTimelineEvents(String processIdentifier, ProcessArtifactEvent.ArtifactType type) {
    Optional<ProcessInstance> optionalProcessInstance = processInstanceRepository
        .findByBusinessKey(processIdentifier);
    if (optionalProcessInstance.isPresent()) {
      return getProcessTimelineEvents(
          optionalProcessInstance.get(),
          type
      );
    }
    ProcessInstance processInstance = getProcessInstanceById(UUID.fromString(processIdentifier));
    return getProcessTimelineEvents(processInstance, type);
  }

  private List<ProcessArtifactEvent> getProcessTimelineEvents(ProcessInstance processInstance, ProcessArtifactEvent.ArtifactType type) {

    List<ProcessArtifactEvent> timelineEvents = runtimeProcessEngineRepository.getProcessTimelineEvents(
        processInstance.getEngineProcessNumber().getValue(),
        type
    );

    // Enrich the timeline events with task variables — single batch query
    Set<String> taskIds = timelineEvents.stream()
        .map(ProcessArtifactEvent::getTaskId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<String, TaskInstance> tasksByExternalId = taskInstanceRepository.findAllByExternalIds(taskIds);
    timelineEvents.forEach(timelineEvent -> {
      if (timelineEvent.getTaskId() != null) {
        TaskInstance taskInstance = tasksByExternalId.get(timelineEvent.getTaskId());
        if (taskInstance != null) {
          Map<String, Object> variables = new HashMap<>();
          variables.put(VariableTag.VARIABLES.getCode(), taskInstance.getVariables());
          variables.put(VariableTag.FORMS.getCode(), taskInstance.getForms());
          timelineEvent.addVariables(variables);
        }
      }
    });

    // Resolve user profiles — single batch query
    resolveAllUserProfiles(timelineEvents);

    return timelineEvents;
  }

  private void resolveAllUserProfiles(List<ProcessArtifactEvent> events) {
    if (events == null || events.isEmpty()) {
      return;
    }
    Set<String> allIds = new HashSet<>();
    events.forEach(event -> {
      if (event.getAssignee() != null) {
        allIds.add(event.getAssignee());
      }
    });
    if (allIds.isEmpty()) {
      return;
    }
    List<UserProfile> profiles = userProfileRepository.findBySubjectOrEmails(allIds, allIds);
    Map<String, UserProfile> lookup = new HashMap<>();
    for (UserProfile p : profiles) {
      if (p.getSub() != null) lookup.put(p.getSub(), p);
      if (p.getEmail() != null) lookup.put(p.getEmail(), p);
    }
    events.forEach(event -> {
      if (event.getAssignee() != null) {
        UserProfile profile = lookup.get(event.getAssignee());
        if (profile != null) {
          event.resolveUserProfileAssignee(profile);
        }
      }
    });
  }

  private ProcessInstance getProcessInstanceById(UUID processInstanceId) {
    return processInstanceRepository.findById(processInstanceId)
        .orElseThrow(() -> IgrpResponseStatusException.notFound("No process instance found with id: " + processInstanceId));
  }

}
