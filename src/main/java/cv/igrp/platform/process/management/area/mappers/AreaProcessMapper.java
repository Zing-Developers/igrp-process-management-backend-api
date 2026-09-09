package cv.igrp.platform.process.management.area.mappers;

import cv.igrp.platform.process.management.shared.config.AuditMapping;

import cv.igrp.platform.process.management.area.application.dto.ProcessDefinitionDTO;
import cv.igrp.platform.process.management.area.application.dto.ProcessDefinitionListPageDTO;
import cv.igrp.platform.process.management.area.application.dto.ProcessDefinitionRequestDTO;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.area.domain.models.AreaProcess;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.AreaEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.AreaProcessEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AreaProcessMapper {

  public AreaProcess toModel(ProcessDefinitionRequestDTO processDefinitionRequestDTO) {
    return AreaProcess.builder()
        .processKey(Code.create(processDefinitionRequestDTO.getProcessKey()))
        .releaseId(Code.create(processDefinitionRequestDTO.getReleaseId()))
        .version(processDefinitionRequestDTO.getVersion())
        .name(processDefinitionRequestDTO.getName())
        .build();
  }

  public ProcessDefinitionDTO toDTO(AreaProcess areaProcess) {
    ProcessDefinitionDTO processDefinitionDTO = new ProcessDefinitionDTO();
    processDefinitionDTO.setId(areaProcess.getId().getValue());
    processDefinitionDTO.setAreaId(areaProcess.getAreaId() != null ? areaProcess.getAreaId().getValue() : null);
    processDefinitionDTO.setProcessKey(areaProcess.getProcessKey().getValue());
    processDefinitionDTO.setReleaseId(areaProcess.getReleaseId().getValue());
    processDefinitionDTO.setVersion(areaProcess.getVersion());
    processDefinitionDTO.setStatus(areaProcess.getStatus());
    processDefinitionDTO.setStatusDesc(areaProcess.getStatus().getDescription());
    processDefinitionDTO.setCreatedAt(areaProcess.getCreatedAt());
    processDefinitionDTO.setCreatedBy(areaProcess.getCreatedBy());
    processDefinitionDTO.setRemovedAt(areaProcess.getRemovedAt());
    processDefinitionDTO.setRemovedBy(areaProcess.getRemovedBy());
    processDefinitionDTO.setName(areaProcess.getName());
    AuditMapping.apply(processDefinitionDTO, areaProcess.getAudit());
    return processDefinitionDTO;
  }

  public AreaProcess toModel(AreaProcessEntity areaProcessEntity) {
    var model = AreaProcess.builder()
        .id(Identifier.create(areaProcessEntity.getId()))
        .areaId(Identifier.create(areaProcessEntity.getAreaId().getId()))
        .processKey(Code.create(areaProcessEntity.getProcReleaseKey()))
        .releaseId(Code.create(areaProcessEntity.getProcReleaseId()))
        .version(areaProcessEntity.getVersion())
        .createdAt(areaProcessEntity.getCreatedDate())
        .createdBy(areaProcessEntity.getCreatedBy())
        .removedAt(areaProcessEntity.getRemovedAt())
        .removedBy(areaProcessEntity.getRemovedBy())
        .status(areaProcessEntity.getStatus())
        .name(areaProcessEntity.getName())
        .build();
    model.setAudit(AuditMapping.trail(areaProcessEntity));
    return model;
  }

  public AreaProcessEntity toEntity(AreaProcess areaProcess) {
    AreaProcessEntity areaProcessEntity = new AreaProcessEntity();
    areaProcessEntity.setId(areaProcess.getId().getValue());
    areaProcessEntity.setStatus(areaProcess.getStatus());
    areaProcessEntity.setProcReleaseId(areaProcess.getReleaseId().getValue());
    areaProcessEntity.setProcReleaseKey(areaProcess.getProcessKey().getValue());
    areaProcessEntity.setRemovedAt(areaProcess.getRemovedAt());
    areaProcessEntity.setRemovedBy(areaProcess.getRemovedBy());
    areaProcessEntity.setVersion(areaProcess.getVersion());
    areaProcessEntity.setName(areaProcess.getName());
    AreaEntity areaEntity = new AreaEntity();
    areaEntity.setId(areaProcess.getAreaId().getValue());
    areaProcessEntity.setAreaId(areaEntity);
    return areaProcessEntity;
  }

  public ProcessDefinitionListPageDTO toDTO(PageableLista<AreaProcess> areaProcessPageableLista) {
    ProcessDefinitionListPageDTO dto = new ProcessDefinitionListPageDTO();
    dto.setPageNumber(areaProcessPageableLista.getPageNumber());
    dto.setPageSize(areaProcessPageableLista.getPageSize());
    dto.setTotalElements(areaProcessPageableLista.getTotalElements());
    dto.setTotalPages(areaProcessPageableLista.getTotalPages());
    dto.setFirst(areaProcessPageableLista.isFirst());
    dto.setLast(areaProcessPageableLista.isLast());
    List<ProcessDefinitionDTO> content = areaProcessPageableLista.getContent()
        .stream()
        .map(this::toDTO)
        .toList();
    dto.setContent(content);
    return dto;
  }

}
