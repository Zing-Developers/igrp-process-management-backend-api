package cv.igrp.platform.process.management.processdefinition.mappers;

import cv.igrp.framework.process.runtime.core.engine.process.model.IgrpProcessDefinitionRepresentation;
import cv.igrp.platform.process.management.processdefinition.application.dto.ProcessDeploymentDTO;
import cv.igrp.platform.process.management.processdefinition.application.dto.ProcessDeploymentListDTO;
import cv.igrp.platform.process.management.processdefinition.application.dto.ProcessDeploymentListPageDTO;
import cv.igrp.platform.process.management.processdefinition.application.dto.ProcessDeploymentRequestDTO;
import cv.igrp.platform.process.management.processdefinition.domain.models.BpmnXml;
import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessDeployment;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import cv.igrp.platform.process.management.shared.domain.models.ResourceName;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProcessDeploymentMapper {

  public ProcessDeployment toModel(ProcessDeploymentRequestDTO dto){
    return ProcessDeployment.builder()
        .bpmnXml(BpmnXml.create(dto.getBpmnXml()))
        .name(Name.create(dto.getName()))
        .description(dto.getDescription())
        .resourceName(ResourceName.create(dto.getResourceName()))
        .key(Code.create(dto.getKey()))
        .applicationBase(Code.create(dto.getApplicationBase()))
        .build();
  }

  public ProcessDeploymentDTO toDTO(ProcessDeployment model){
    ProcessDeploymentDTO dto = new ProcessDeploymentDTO();
    dto.setDeploymentId(model.getDeploymentId());
    dto.setBpmnXml(model.getBpmnXml() != null ? model.getBpmnXml().getXml() : null);
    dto.setName(model.getName().getValue());
    dto.setDeployed(model.isDeployed());
    dto.setResourceName(model.getResourceName().getValue());
    dto.setKey(model.getKey().getValue());
    dto.setDescription(model.getDescription());
    dto.setBpmnUrl(model.getBpmnUrl());
    dto.setDeployedAt(model.getDeployedAt());
    dto.setBpmnSourceType(model.getBpmnSourceType());
    dto.setApplicationBase(model.getApplicationBase().getValue());
    dto.setVersion(model.getVersion());
    return dto;
  }

  public ProcessDeploymentListDTO toListDTO(ProcessDeployment model){
    ProcessDeploymentListDTO dto = new ProcessDeploymentListDTO();
    dto.setId(model.getProcReleaseId().getValue());
    dto.setName(model.getName().getValue());
    dto.setDescription(model.getDescription());
    dto.setProcessKey(model.getKey().getValue());
    dto.setVersion(model.getVersion());
    dto.setApplicationBase(model.getApplicationBase().getValue());
    dto.setDeploymentId(model.getDeploymentId());
    dto.setCandidateGroups(
        !model.getCandidateGroups().isEmpty() ? String.join(",", model.getCandidateGroups()) : null
    );
    return dto;
  }

  public ProcessDeploymentListPageDTO toDTO(PageableLista<ProcessDeployment> deployments) {
    ProcessDeploymentListPageDTO dto = new ProcessDeploymentListPageDTO();
    dto.setTotalElements(deployments.getTotalElements());
    dto.setTotalPages(deployments.getTotalPages());
    dto.setPageNumber(deployments.getPageNumber());
    dto.setPageSize(deployments.getPageSize());
    dto.setFirst(deployments.isFirst());
    dto.setLast(deployments.isLast());
    List<ProcessDeploymentListDTO> content = deployments.getContent()
        .stream()
        .map(this::toListDTO)
        .toList();
    dto.setContent(content);
    return dto;
  }

  public ProcessDeployment toModel(IgrpProcessDefinitionRepresentation definition) {
    return ProcessDeployment.builder()
        .id(definition.releaseId())
        .key(Code.create(definition.key()))
        .name(Name.create(definition.name()))
        .resourceName(ResourceName.create(definition.resourceName()))
        .bpmnXml(BpmnXml.create(definition.bpmnXml()))
        .version(definition.version())
        .deploymentId(definition.deploymentId())
        .deployed(definition.deployed())
        .deployedAt(definition.deployedAt())
        .applicationBase(Code.create(definition.applicationBase()))
        .build();
  }


}
