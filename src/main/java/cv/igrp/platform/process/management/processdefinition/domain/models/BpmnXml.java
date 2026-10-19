package cv.igrp.platform.process.management.processdefinition.domain.models;

import lombok.Builder;
import lombok.Getter;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class BpmnXml {

  private static final Pattern PROCESS_TAG = Pattern.compile("<(?:\\w+:)?process\\b[^>]*>");
  private static final Pattern NOT_EXECUTABLE = Pattern.compile("isExecutable\\s*=\\s*([\"'])false\\1");

  private final String xml;

  @Builder
  private BpmnXml(String xml) {
    if (xml == null || xml.isBlank()) {
      throw new IllegalArgumentException("The value of BpmnXml cannot be null or blank");
    }
    this.xml = makeExecutable(xml);
  }

  public static BpmnXml create(String xml) {
    return new BpmnXml(xml);
  }

  /**
   * Activiti refuses a deployment when every process is marked isExecutable="false",
   * which is what the modeler writes when nobody ticks "Executable". In that case
   * flip them all to true. A collaboration with at least one executable process is
   * left alone, so documentation-only pools stay non-executable.
   */
  private static String makeExecutable(String xml) {
    var tags = PROCESS_TAG.matcher(xml).results().map(MatchResult::group).toList();
    if (tags.isEmpty() || !tags.stream().allMatch(t -> NOT_EXECUTABLE.matcher(t).find())) {
      return xml;
    }
    return PROCESS_TAG.matcher(xml).replaceAll(m ->
        Matcher.quoteReplacement(NOT_EXECUTABLE.matcher(m.group()).replaceFirst("isExecutable=$1true$1")));
  }

}
