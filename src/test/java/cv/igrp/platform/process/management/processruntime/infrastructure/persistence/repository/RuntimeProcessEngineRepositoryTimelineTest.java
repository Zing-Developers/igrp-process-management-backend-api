package cv.igrp.platform.process.management.processruntime.infrastructure.persistence.repository;

import cv.igrp.framework.process.runtime.core.engine.activity.ActivityQueryService;
import cv.igrp.framework.process.runtime.core.engine.activity.model.IGRPActivityStatus;
import cv.igrp.framework.process.runtime.core.engine.activity.model.IGRPActivityType;
import cv.igrp.framework.process.runtime.core.engine.activity.model.ProcessTimelineEvent;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessArtifactEvent;
import cv.igrp.platform.process.management.shared.jobs.FailedJobService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The activity list: history from the framework, plus the activities whose job failed, once each. */
class RuntimeProcessEngineRepositoryTimelineTest {

  private static ProcessTimelineEvent event(String activityId, IGRPActivityStatus status) {
    var event = new ProcessTimelineEvent();
    event.setActivityId(activityId);
    event.setActivityName(activityId);
    event.setProcessInstanceId("eng-1");
    event.setType(IGRPActivityType.SERVICE_TASK);
    event.setStatus(status);
    return event;
  }

  @Test
  void aStuckActivityReplacesThePendingRowTheFrameworkListsForIt() {
    var activities = mock(ActivityQueryService.class);
    var failedJobs = mock(FailedJobService.class);
    when(activities.getActivityTimelineEvents("eng-1")).thenReturn(List.of(
        event("validarPedido", IGRPActivityStatus.COMPLETED),
        event("notifyIrn", IGRPActivityStatus.PENDING),   // never reached history: its job failed
        event("emitirCertidao", IGRPActivityStatus.PENDING)));
    when(failedJobs.stuckActivities("eng-1")).thenReturn(List.of(ProcessArtifactEvent.builder()
        .processInstanceId("eng-1").artifactId("notifyIrn").type(ProcessArtifactEvent.ArtifactType.SERVICE_TASK)
        .status(ProcessArtifactEvent.ArtifactStatus.FAILED).build()));
    var repository = new RuntimeProcessEngineRepositoryImpl(null, null, null, null, null, null, null,
        activities, null, failedJobs);

    var timeline = repository.getProcessTimelineEvents("eng-1", null);

    assertThat(timeline).extracting(e -> e.getArtifactId() + ":" + e.getStatus())
        .containsExactly("validarPedido:COMPLETED", "emitirCertidao:PENDING", "notifyIrn:FAILED");
  }

}
