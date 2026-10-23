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
  private static final Pattern START_TAG = Pattern.compile("<\\w[\\w:.-]*(?:[^\"'<>]|\"[^\"]*\"|'[^']*')*>");
  private static final Pattern ATTRIBUTE = Pattern.compile("\\s+(\\w+):([\\w.-]+)\\s*=\\s*([\"'])(.*?)\\3", Pattern.DOTALL);
  private static final String ASYNC_FLAG = "async(Before|After)?";

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
   * ignores (no delegateExpression, no fields). The studio frontend converts them before deploying
   * (convertCamundaToActiviti); this applies the same rules so a deployment that skipped it behaves
   * the same, and is a no-op on XML the frontend already converted. Rebinding the namespace URI is
   * enough for the parser: the prefix can stay "camunda:".
   */
  private static String toActiviti(String xml) {
    return START_TAG.matcher(xml).replaceAll(m -> Matcher.quoteReplacement(normalizeTag(m.group())))
        .replace(CAMUNDA_NS, ACTIVITI_NS);
  }

  /**
   * Per tag, two rules from the frontend, both of which would otherwise put the same attribute
   * twice in the Activiti namespace (malformed XML, the deploy fails):
   * <ul>
   *   <li>an activiti:X next to a camunda:X is dropped, the Camunda one wins;</li>
   *   <li>Activiti has a single async flag, so asyncBefore and asyncAfter collapse into async, on
   *   if any of them (or an existing async) is on.</li>
   * </ul>
   */
  private static String normalizeTag(String tag) {
    var attributes = ATTRIBUTE.matcher(tag).results().toList();
    var camundaNames = attributes.stream().filter(a -> "camunda".equals(a.group(1))).map(a -> a.group(2)).toList();
    var asyncFlags = attributes.stream().filter(a -> a.group(2).matches(ASYNC_FLAG)).toList();
    // Also when async is there twice: the frontend writes that for camunda:async + asyncBefore.
    boolean collapse = asyncFlags.size() > 1 || asyncFlags.stream().anyMatch(a -> !"async".equals(a.group(2)));

    String kept = ATTRIBUTE.matcher(tag).replaceAll(a -> {
      boolean shadowed = "activiti".equals(a.group(1)) && camundaNames.contains(a.group(2));
      boolean collapsed = collapse && a.group(2).matches(ASYNC_FLAG);
      return shadowed || collapsed ? "" : Matcher.quoteReplacement(a.group());
    });
    if (!collapse) {
      return kept;
    }
    boolean on = asyncFlags.stream().anyMatch(a -> "true".equals(a.group(4)));
    return kept.replaceFirst("(/?>)$", " " + asyncFlags.get(0).group(1) + ":async=\"" + on + "\"$1");
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
