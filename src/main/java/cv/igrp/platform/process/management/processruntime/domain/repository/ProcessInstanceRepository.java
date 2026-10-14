package cv.igrp.platform.process.management.processruntime.domain.repository;


import cv.igrp.platform.process.management.processruntime.domain.models.ProcessInstance;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessInstanceFilter;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessStatistics;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessInstanceRepository {

  ProcessInstance update(ProcessInstance processInstance);

  ProcessInstance save(ProcessInstance processInstance);

  PageableLista<ProcessInstance> findAll(ProcessInstanceFilter filter);

  Optional<ProcessInstance> findById(UUID id);

  ProcessStatistics getProcessInstanceStatistics();

  Optional<ProcessInstance> findByBusinessKey(String businessKey);

  Optional<ProcessInstance> findByEngineProcessNumber(String engineProcessNumber);

  List<ProcessInstance> findAllByProcessReleaseId(String processReleaseId);

}
