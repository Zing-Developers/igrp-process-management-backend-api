package cv.igrp.platform.process.management.processruntime.domain.models;

import cv.igrp.platform.process.management.shared.application.constants.ProcessInstanceStatus;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.ProcessNumber;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Getter
@ToString
public class ProcessInstance {

  private cv.igrp.platform.process.management.shared.domain.models.AuditTrail audit;

  public void setAudit(cv.igrp.platform.process.management.shared.domain.models.AuditTrail audit) {
    this.audit = audit;
  }

  public static final Integer DEFAULT_PRIORITY = 0;

  private final Identifier id;
  private final Code procReleaseKey;
  private Code procReleaseId;
  private ProcessNumber number;
  private Code engineProcessNumber;
  private Code businessKey;
  private Code applicationBase;
  private String version;
  private String searchTerms;
  private LocalDateTime startedAt;
  private LocalDateTime endedAt;
  private LocalDateTime canceledAt;
  private String startedBy;
  private String endedBy;
  private String createdBy;
  private String canceledBy;
  private String obsCancel;
  private ProcessInstanceStatus status;
  private String name;
  private String progress;
  private Map<String, Object> variables;
  private List<TaskAssignmentRuleRequest> assignmentRules;
  private Integer priority;

  private boolean isArchived;

  private UserProfile userProfileStartedBy;
  private UserProfile userProfileEndedBy;
  private UserProfile userProfileCreatedBy;
  private UserProfile userProfileCanceledBy;

  @Builder
  public ProcessInstance(Identifier id,
                         Code procReleaseKey,
                         Code procReleaseId,
                         ProcessNumber number,
                         Code engineProcessNumber,
                         Code businessKey,
                         Code applicationBase,
                         String version,
                         String searchTerms,
                         LocalDateTime startedAt,
                         LocalDateTime endedAt,
                         LocalDateTime canceledAt,
                         String startedBy,
                         String endedBy,
                         String createdBy,
                         String canceledBy,
                         String obsCancel,
                         ProcessInstanceStatus status,
                         String name,
                         String progress,
                         Map<String, Object> variables,
                         List<TaskAssignmentRuleRequest> assignmentRules,
                         Integer priority,
                         boolean isArchived,
                         UserProfile userProfileStartedBy,
                         UserProfile userProfileEndedBy,
                         UserProfile userProfileCreatedBy,
                         UserProfile userProfileCanceledBy
                         ) {
    this.id = id == null ? Identifier.generate() : id;
    this.procReleaseKey = Objects.requireNonNull(procReleaseKey, "Process release key cannot be null");
    this.procReleaseId = procReleaseId;
    this.number = number;
    this.engineProcessNumber = engineProcessNumber;
    this.businessKey = businessKey == null ? Code.create(generateBusinessKey()) : businessKey;
    this.applicationBase = applicationBase;
    this.version = version;
    this.searchTerms = searchTerms;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.canceledAt = canceledAt;
    this.startedBy = startedBy;
    this.endedBy = endedBy;
    this.createdBy = createdBy;
    this.canceledBy = canceledBy;
    this.obsCancel = obsCancel;
    this.status = status == null ? ProcessInstanceStatus.CREATED : status;
    this.name = name;
    this.progress = progress;
    this.variables = variables == null ? new HashMap<>() : variables;
    this.assignmentRules = assignmentRules == null ? new ArrayList<>() : new ArrayList<>(assignmentRules);
    this.priority = priority == null ? DEFAULT_PRIORITY : priority;
    this.isArchived = isArchived;
    this.userProfileStartedBy = userProfileStartedBy;
    this.userProfileEndedBy = userProfileEndedBy;
    this.userProfileCreatedBy = userProfileCreatedBy;
    this.userProfileCanceledBy = userProfileCanceledBy;
  }

  public void create(ProcessNumber processNumber, ProcessInstance processInstance, String createdBy){
    if(createdBy == null || createdBy.isBlank()){
      throw new IllegalStateException("The createdBy (user) of the process instance cannot be null or blank");
    }
    if(processInstance.getName() == null || processInstance.getName().isBlank()){
      throw new IllegalStateException("The name of the process instance cannot be null or blank");
    }
    if(processInstance.getProcReleaseId() == null){
      throw new IllegalStateException("The process definition id cannot be null");
    }
    if(processNumber == null){
      throw new IllegalStateException("The process number cannot be null");
    }
    this.number = processNumber;
    this.procReleaseId = processInstance.getProcReleaseId();
    this.engineProcessNumber = processInstance.getEngineProcessNumber();
    this.status = ProcessInstanceStatus.CREATED;
    this.createdBy = createdBy;
    this.name = processInstance.getName();
    this.version = processInstance.getVersion() == null || processInstance.getVersion().isBlank() ? "1.0" : processInstance.getVersion();
  }

  public void start(String startedBy){
    if(this.status != ProcessInstanceStatus.CREATED && this.status != ProcessInstanceStatus.SUSPENDED){
      throw new IllegalStateException("The status of the process instance must be CREATED or SUSPENDED");
    }
    if(startedBy == null || startedBy.isBlank()){
      throw new IllegalStateException("The started by (user) of the process instance cannot be null or blank");
    }
    this.startedAt = LocalDateTime.now();
    this.startedBy = startedBy;
    this.status = ProcessInstanceStatus.RUNNING;
  }

  public void complete(LocalDateTime endedAt, String endedBy){
    if(endedBy == null || endedBy.isBlank()){
      throw new IllegalStateException("The endedBy (user) of the process instance cannot be null or blank");
    }
    this.status = ProcessInstanceStatus.COMPLETED;
    this.endedBy = endedBy;
    this.endedAt = endedAt == null ? LocalDateTime.now() : endedAt;
  }

  public void suspend(){
    this.status = ProcessInstanceStatus.SUSPENDED;
  }

  public static String generateBusinessKey(){
    return UUID.randomUUID().toString();
  }

  public void setProgress(int totalTasks, int completedTasks) {
    this.progress = String.format("%d/%d", completedTasks, totalTasks);
  }

  public void addVariables(Map<String,Object> variables) {
    this.variables.putAll(variables);
  }

  public void addAssignmentRules(List<TaskAssignmentRuleRequest> assignmentRules) {
    if (assignmentRules != null) {
      this.assignmentRules.addAll(assignmentRules);
    }
  }

  public void archive(){
    this.isArchived = true;
  }

  public void unArchive(){
    this.isArchived = false;
  }

  public void resolveUserProfileStartedBy(UserProfile userProfile) {
    if (userProfile == null) {
      throw new IllegalArgumentException("UserProfile cannot be null");
    }
    this.userProfileStartedBy = userProfile;
  }

  public void resolveUserProfileEndedBy(UserProfile userProfile) {
    if (userProfile == null) {
      throw new IllegalArgumentException("UserProfile cannot be null");
    }
    this.userProfileEndedBy = userProfile;
  }

  public void resolveUserProfileCancelledBy(UserProfile userProfile) {
    if (userProfile == null) {
      throw new IllegalArgumentException("UserProfile cannot be null");
    }
    this.userProfileCanceledBy = userProfile;
  }

  public void resolveUserProfileCreatedBy(UserProfile userProfile) {
    if (userProfile == null) {
      throw new IllegalArgumentException("UserProfile cannot be null");
    }
    this.userProfileCreatedBy = userProfile;
  }

}
