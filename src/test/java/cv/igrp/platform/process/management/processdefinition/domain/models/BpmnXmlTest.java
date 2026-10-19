package cv.igrp.platform.process.management.processdefinition.domain.models;

import org.junit.jupiter.api.Test;

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

}
