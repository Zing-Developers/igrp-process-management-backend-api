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
  private static final String CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn";
  private static final String ACTIVITI_NS = "http://activiti.org/bpmn";
  private static final Pattern TAG_WITH_ASYNC_BEFORE = Pattern.compile("<[^>]*:asyncBefore\\s*=[^>]*>");
  private static final Pattern ASYNC_BEFORE = Pattern.compile("\\s+(\\w+):asyncBefore\\s*=\\s*([\"'])(\\w*)\\2");
  private static final Pattern ASYNC = Pattern.compile("(\\w+:async\\s*=\\s*)([\"'])(\\w*)\\2");

  private final String xml;

  @Builder
  private BpmnXml(String xml) {
    if (xml == null || xml.isBlank()) {
      throw new IllegalArgumentException("The value of BpmnXml cannot be null or blank");
    }
    this.xml = makeExecutable(toActiviti(xml));
  }

  public static BpmnXml create(String xml) {
    return new BpmnXml(xml);
  }

  /**
   * The modeler is Camunda's, so its extensions come in the Camunda namespace, which Activiti
   * ignores (no delegateExpression, no fields). Rebinding the namespace URI is enough: the prefix
   * can stay "camunda:". Its "Asynchronous before" checkbox writes asyncBefore, which Activiti calls
   * async. asyncAfter has no Activiti equivalent and stays ignored.
   */
  private static String toActiviti(String xml) {
    return TAG_WITH_ASYNC_BEFORE.matcher(xml.replace(CAMUNDA_NS, ACTIVITI_NS))
        .replaceAll(m -> Matcher.quoteReplacement(asyncBeforeToAsync(m.group())));
  }

  /**
   * A tag that already has async (the deprecated camunda:async, or activiti:async) would end up with
   * the attribute twice, which is not well-formed XML. Merge instead: async is on if either was.
   */
  private static String asyncBeforeToAsync(String tag) {
    var before = ASYNC_BEFORE.matcher(tag);
    if (!before.find()) {
      return tag;
    }
    var async = ASYNC.matcher(tag);
    if (!async.find()) {
      return before.replaceFirst(" $1:async=$2$3$2");
    }
    boolean on = "true".equals(before.group(3)) || "true".equals(async.group(3));
    return ASYNC.matcher(before.replaceFirst("")).replaceFirst("$1\"" + on + "\"");
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
