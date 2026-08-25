package cv.igrp.platform.process.management.processdefinition.domain.service;

import cv.igrp.platform.process.management.processdefinition.domain.filter.ProcessDeploymentFilter;
import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessArtifact;
import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessDeployment;
import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessPackage;
import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessSequence;
import cv.igrp.platform.process.management.processdefinition.domain.repository.ProcessDefinitionRepository;
import cv.igrp.platform.process.management.processdefinition.domain.repository.ProcessDeploymentRepository;
import cv.igrp.platform.process.management.processdefinition.domain.repository.ProcessSequenceRepository;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessInstance;
import cv.igrp.platform.process.management.processruntime.domain.repository.ProcessInstanceRepository;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import cv.igrp.platform.process.management.shared.domain.models.ResourceName;
import cv.igrp.platform.process.management.shared.security.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
public class ProcessDeploymentService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessDeploymentService.class);


  private final ProcessDeploymentRepository processDeploymentRepository;
  private final UserContext userContext;

  private final ProcessDefinitionRepository processDefinitionRepository;
  private final ProcessSequenceRepository processSequenceRepository;
  private final ProcessInstanceRepository processInstanceRepository;

  public ProcessDeploymentService(ProcessDeploymentRepository processDeploymentRepository,
                                  UserContext userContext,
                                  ProcessDefinitionRepository processDefinitionRepository,
                                  ProcessSequenceRepository processSequenceRepository,
                                  ProcessInstanceRepository processInstanceRepository) {
    this.processDeploymentRepository = processDeploymentRepository;
    this.userContext = userContext;
    this.processDefinitionRepository = processDefinitionRepository;
    this.processSequenceRepository = processSequenceRepository;
    this.processInstanceRepository = processInstanceRepository;
  }

  public ProcessDeployment deployProcess(ProcessDeployment processDeployment) {
    processDeployment.deploy();
    return processDeploymentRepository.deploy(processDeployment);
  }

  public PageableLista<ProcessDeployment> getAllDeployments(ProcessDeploymentFilter processDeploymentFilter) {
    if (processDeploymentFilter.isFilterByCurrentUser()) {
      userContext.getCurrentGroups()
          .forEach(processDeploymentFilter::addContextGroup);
    }
    PageableLista<ProcessDeployment> pageableLista = processDeploymentRepository.findAll(processDeploymentFilter);
    // Enrich with candidate groups — single batch call
    List<String> ids = pageableLista.getContent().stream()
        .map(ProcessDeployment::getId)
        .toList();
    Map<String, Set<String>> groupsMap = processDeploymentRepository.getCandidateStarterGroupsBatch(ids);
    pageableLista.getContent().forEach(deployment ->
        groupsMap.getOrDefault(deployment.getId(), Set.of())
            .forEach(deployment::addCandidateGroups));
    return pageableLista;
  }

  public List<ProcessArtifact> getDeployedArtifactsByProcessDefinitionId(String processDefinitionId) {
    return processDeploymentRepository.findAllArtifacts(processDefinitionId);
  }

  public String findLastProcessDefinitionIdByKey(String processDefinitionKey) {
    return processDeploymentRepository.findLastProcessDefinitionIdByKey(processDefinitionKey)
        .orElseThrow(() -> IgrpResponseStatusException.notFound("No last process definition founded by key: " +  processDefinitionKey));
  }

  public void assignProcessDefinition(String id, String groups) {
    updateProcessDefinitionAssignment(id, groups, true);
  }

  public void unAssignProcessDefinition(String id, String groups) {
    updateProcessDefinitionAssignment(id, groups, false);
  }

  public void updateProcessDefinitionAssignment(String processDefinitionId, String groups, boolean assign) {

    if (processDefinitionId == null || processDefinitionId.isBlank()) {
      throw IgrpResponseStatusException.badRequest(
          "Process definition ID cannot be null or blank"
      );
    }
    if (groups == null || groups.isBlank()) {
      throw IgrpResponseStatusException.badRequest(
          "Groups cannot be null or blank"
      );
    }
    Arrays.stream(groups.split(","))
        .map(String::trim)
        .filter(g -> !g.isEmpty())
        .distinct()
        .forEach(group -> {
          if (assign) {
            processDeploymentRepository
                .addCandidateStarterGroup(processDefinitionId, group);
          } else {
            processDeploymentRepository
                .removeCandidateStarterGroup(processDefinitionId, group);
          }
        });
  }

  public ProcessPackage exportProcessDefinition(String processDefinitionId) {
    ProcessDeployment processDeployment = getProcessDeploymentById(processDefinitionId);
    ProcessPackage processPackage = ProcessPackage.builder()
        .processName(processDeployment.getName())
        .processId(Code.create(processDefinitionId))
        .processDescription(processDeployment.getDescription())
        .processKey(Code.create(processDeployment.getKey().getValue()))
        .applicationBase(processDeployment.getApplicationBase())
        .bpmnXml(processDeployment.getBpmnXml())
        .build();

    // Add the sequence
    Optional<ProcessSequence> optProcessSequence = processSequenceRepository.findByProcessDefinitionKey(
        processPackage.getProcessKey().getValue()
    );
    processPackage.addSequence(
        optProcessSequence.orElseGet(() -> ProcessSequence.defaultSequence(processPackage.getProcessKey()))
    );

    // Add artifacts
    processDefinitionRepository.findAllArtifacts(processPackage.getProcessId())
        .forEach(processPackage::addArtifact);

    // Add candidate groups
    processDeploymentRepository.getCandidateStarterGroups(processPackage.getProcessId().getValue())
        .forEach(processPackage::addGroup);

    return processPackage;
  }

  private ProcessDeployment getProcessDeploymentById(String processDefinitionId) {
    return processDeploymentRepository.findById(processDefinitionId)
        .orElseThrow(() -> IgrpResponseStatusException.notFound("No process definition found with ID: " + processDefinitionId));
  }

  public void importProcessDefinition(ProcessPackage processPackage) {

    LOGGER.debug("Importing process definition: {}", processPackage.getProcessKey());

    // Deploy Process
    ProcessDeployment processDeployment = deployProcess(
        ProcessDeployment.builder()
            .bpmnXml(processPackage.getBpmnXml())
            .name(processPackage.getProcessName())
            .description(processPackage.getProcessDescription())
            .resourceName(ResourceName.create(processPackage.getProcessVersion() + ".bpmn20.xml"))
            .key(processPackage.getProcessKey())
            .applicationBase(processPackage.getApplicationBase())
            .build()
    );

    // Save Process Sequence
    Optional<ProcessSequence> processSequence = processSequenceRepository.findByProcessDefinitionKey(
        processDeployment.getKey().getValue()
    );
    if (processSequence.isEmpty()) {
      processPackage.getSequence().resetNextNumber();
      processSequenceRepository.save(processPackage.getSequence());
    }

    // Save Process Artifacts
    processPackage.getArtifacts().forEach(processArtifact -> {
      ProcessArtifact newProcessArtifact = ProcessArtifact.builder()
          .key(processArtifact.getKey())
          .name(processArtifact.getName())
          .formKey(processArtifact.getFormKey())
          .candidateGroups(processArtifact.getCandidateGroups())
          .priority(processArtifact.getPriority())
          .dueDate(processArtifact.getDueDate())
          .processDefinitionId(Code.create(processDeployment.getId()))
          .build();
      processDefinitionRepository.saveArtifact(newProcessArtifact);
    });

    // Candidate Groups
    for (String group : processPackage.getCandidateGroups()) {
      processDeploymentRepository.addCandidateStarterGroup(processDeployment.getId(), group);
    }

    LOGGER.info("Process definition imported successfully: ID '{}' Key '{}'",
        processDeployment.getId(), processDeployment.getKey().getValue());

  }

  public ProcessDeployment deployProcessAndConfigure(ProcessDeployment processDeployment) {

    Optional<String> optionalProcessId = processDeploymentRepository.findLastProcessDefinitionIdByKey(
        processDeployment.getKey().getValue()
    );

    // Deploy
    ProcessDeployment deployedProcess = deployProcess(processDeployment);

    if (optionalProcessId.isPresent()) {
      // Groups
      processDeploymentRepository.getCandidateStarterGroups(optionalProcessId.get())
          .forEach(group -> processDeploymentRepository.addCandidateStarterGroup(
              deployedProcess.getId(),
              group
          ));
      // Artifacts
      Map<String, ProcessArtifact> previousByKey =
          processDefinitionRepository
              .findAllArtifacts(Code.create(optionalProcessId.get()))
              .stream()
              .collect(Collectors.toMap(
                  a -> a.getKey().getValue(),
                  Function.identity()
              ));

      List<ProcessArtifact> deployedArtifacts = getDeployedArtifactsByProcessDefinitionId(deployedProcess.getId());

      deployedArtifacts.forEach(deployedArtifact -> {

        ProcessArtifact previous = previousByKey.get(deployedArtifact.getKey().getValue());

        ProcessArtifact toPersist = ProcessArtifact.builder()
            .key(deployedArtifact.getKey())
            .name(deployedArtifact.getName())
            .processDefinitionId(Code.create(deployedProcess.getId()))
            .candidateGroups(previous != null ? previous.getCandidateGroups() : deployedArtifact.getCandidateGroups())
            .priority(previous != null ? previous.getPriority() : deployedArtifact.getPriority())
            .dueDate(previous != null ? previous.getDueDate() : deployedArtifact.getDueDate())
            .formKey(previous != null && previous.isFormKeySet() ? previous.getFormKey() : deployedArtifact.getFormKey())
            .build();

        processDefinitionRepository.saveArtifact(toPersist);

      });
    }

    return deployedProcess;
  }

  public void archiveProcess(String processDefinitionId) {

    // Process Definition
    processDeploymentRepository.archiveProcessDefinitionById(processDefinitionId);

    // Process Instances
    List<ProcessInstance> processInstances = processInstanceRepository.findAllByProcessReleaseId(processDefinitionId);
    for (ProcessInstance processInstance : processInstances) {
      processInstance.archive();
      processInstanceRepository.save(processInstance);
    }

  }

  public void unArchiveProcess(String processDefinitionId) {

    // Process Definition
    processDeploymentRepository.unArchiveProcessDefinitionById(processDefinitionId);

    // Process Instances
    List<ProcessInstance> processInstances = processInstanceRepository.findAllByProcessReleaseId(processDefinitionId);
    for (ProcessInstance processInstance : processInstances) {
      processInstance.unArchive();
      processInstanceRepository.save(processInstance);
    }

  }

}
