package cv.igrp.platform.process.management.processdefinition.domain.models;

import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.ServiceTask;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class BpmnXmlTest {

  @Test
  void shouldCreateBpmnXmlWithValidXml() {
    String xmlContent = """
          <definitions id="sample">...</definitions>
        """;

    BpmnXml bpmnXml = BpmnXml.create(xmlContent);

    assertNotNull(bpmnXml);
    assertEquals(xmlContent, bpmnXml.getXml());
  }

  @Test
  void shouldThrowExceptionWhenXmlIsNull() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> BpmnXml.create(null));

    assertEquals("The value of BpmnXml cannot be null or blank", exception.getMessage());
  }

  @Test
  void shouldThrowExceptionWhenXmlIsBlank() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> BpmnXml.create("   "));

    assertEquals("The value of BpmnXml cannot be null or blank", exception.getMessage());
  }

  @Test
  void shouldMarkProcessExecutableWhenAllAreNonExecutable() {
    String xml = "<bpmn:definitions><bpmn:process id=\"CHAVE\" isExecutable=\"false\"></bpmn:process></bpmn:definitions>";

    assertEquals(
        "<bpmn:definitions><bpmn:process id=\"CHAVE\" isExecutable=\"true\"></bpmn:process></bpmn:definitions>",
        BpmnXml.create(xml).getXml());
  }

  @Test
  void shouldKeepNonExecutablePoolWhenAnotherProcessIsExecutable() {
    String xml = "<definitions><process id=\"a\" isExecutable=\"true\"/><process id=\"b\" isExecutable='false'/></definitions>";

    assertEquals(xml, BpmnXml.create(xml).getXml());
  }

  @Test
  void shouldMakeCamundaModelerExtensionsReadableByActiviti() throws Exception {
    var task = deployedTask("camunda:asyncBefore=\"true\" camunda:asyncAfter=\"true\"");

    assertEquals("${igrpWebhookDelegate}", task.getImplementation());
    assertTrue(task.isAsynchronous());
    assertEquals("webhookUrl", task.getFieldExtensions().get(0).getFieldName());
  }

  @Test
  void shouldMergeAsyncBeforeIntoAnExistingAsyncInsteadOfDuplicatingIt() throws Exception {
    assertTrue(deployedTask("activiti:async=\"false\" camunda:asyncBefore=\"true\"").isAsynchronous());
    assertTrue(deployedTask("camunda:async=\"true\" camunda:asyncBefore=\"false\"").isAsynchronous());
    assertFalse(deployedTask("camunda:async='false' camunda:asyncBefore='false'").isAsynchronous());
  }

  /** Runs a Camunda-modeler service task through BpmnXml and then Activiti's own parser. */
  private static ServiceTask deployedTask(String asyncAttributes) throws Exception {
    String xml = """
        <bpmn2:definitions xmlns:bpmn2="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" xmlns:activiti="http://activiti.org/bpmn" id="d" targetNamespace="http://activiti.org/bpmn">
          <bpmn2:process id="NOTIFICAR_IRN" isExecutable="true">
            <bpmn2:serviceTask id="notify" %s camunda:delegateExpression="${igrpWebhookDelegate}">
              <bpmn2:extensionElements>
                <camunda:field name="webhookUrl"><camunda:string>https://example.org</camunda:string></camunda:field>
              </bpmn2:extensionElements>
            </bpmn2:serviceTask>
          </bpmn2:process>
        </bpmn2:definitions>
        """.formatted(asyncAttributes);

    var reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(BpmnXml.create(xml).getXml()));
    return (ServiceTask) new BpmnXMLConverter().convertToBpmnModel(reader).getMainProcess().getFlowElement("notify");
  }

}
