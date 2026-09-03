package cv.igrp.platform.process.management.processdefinition.domain.models;

import cv.igrp.platform.process.management.shared.application.constants.Status;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import cv.igrp.platform.process.management.shared.domain.models.ProcessNumber;
import cv.igrp.platform.process.management.shared.util.DateUtil;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Getter
public class ProcessSequence {

  private cv.igrp.platform.process.management.shared.domain.models.AuditTrail audit;

  public void setAudit(cv.igrp.platform.process.management.shared.domain.models.AuditTrail audit) {
    this.audit = audit;
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessSequence.class);

  private final Identifier id;
  private final Name name;
  private final Code prefix;
  private final Short checkDigitSize;
  private final Short padding;
  private final String dateFormat;
  private Long nextNumber;
  private final Short numberIncrement;
  private final Code processDefinitionKey;
  private String separator;

  @Builder
  public ProcessSequence(Identifier id,
                         Name name,
                         Code prefix,
                         Short checkDigitSize,
                         Short padding,
                         String dateFormat,
                         Long nextNumber,
                         Short numberIncrement,
                         Code processDefinitionKey,
                         String separator
  ){
    this.id = id == null ? Identifier.generate() : id;
    this.name = Objects.requireNonNull(name, "Name cannot be null");
    this.prefix = Objects.requireNonNull(prefix, "Prefix cannot be null");
    this.checkDigitSize = Objects.requireNonNull(checkDigitSize, "CheckDigitSize cannot be null");
    this.padding = padding;
    this.dateFormat = Objects.requireNonNull(dateFormat, "DateFormat cannot be null");
    this.nextNumber = nextNumber;
    this.numberIncrement = Objects.requireNonNull(numberIncrement, "NumberIncrement cannot be null");
    this.processDefinitionKey = Objects.requireNonNull(processDefinitionKey, "ProcessDefinitionKey cannot be null");
    this.separator = separator;
  }


  public void validate() {
    if (name == null || name.getValue().trim().isEmpty()) {
      throw IgrpResponseStatusException.badRequest("Sequence name must not be blank.");
    }

    if (prefix == null || !prefix.getValue().matches("[A-Za-z0-9/_-]+")) {
      throw IgrpResponseStatusException.badRequest("Prefix must be alphanumeric and not null.");
    }

    if (checkDigitSize != null && (checkDigitSize < 0 || checkDigitSize > 10)) {
      throw IgrpResponseStatusException.badRequest("Check digit size must be between 0 and 10.");
    }

    if (padding != null && (padding < 0 || padding > 20)) {
      throw IgrpResponseStatusException.badRequest("Padding must be between 0 and 20.");
    }

    if (dateFormat != null && !dateFormat.isEmpty()) {
      try {
        DateTimeFormatter.ofPattern(dateFormat);
      } catch (IllegalArgumentException e) {
        throw IgrpResponseStatusException.badRequest("Invalid date format: " + dateFormat);
      }
    }

    if (nextNumber != null && nextNumber < 1) {
      throw new IllegalArgumentException("Next number must be >= 1.");
    }

    if (numberIncrement == null || numberIncrement <= 0) {
      throw IgrpResponseStatusException.badRequest("Number increment must be > 0.");
    }

    if (processDefinitionKey == null) {
      throw IgrpResponseStatusException.badRequest("processDefinitionKey must not be null.");
    }

  }


  public ProcessSequence newInstance() {
    return buildWith(Identifier.generate());
  }


  public ProcessSequence copyWithId(Identifier id) {
    return buildWith(id);
  }


  private ProcessSequence buildWith(Identifier id) {
    return ProcessSequence.builder()
        .id(id)
        .name(this.name)
        .prefix(this.prefix)
        .checkDigitSize(this.checkDigitSize)
        .padding(this.padding)
        .dateFormat(this.dateFormat)
        .nextNumber(this.nextNumber == null ? 1L : this.nextNumber)
        .numberIncrement(this.numberIncrement)
        .processDefinitionKey(this.processDefinitionKey)
        .separator(this.separator)
        .build();
  }


  public ProcessNumber generateNextProcessNumberAndIncrement() {
    String processNumber = generateNumber(this.nextNumber == null ? 1L : this.nextNumber);
    this.nextNumber = (this.nextNumber == null ? 1L : this.nextNumber) + this.numberIncrement;
    return ProcessNumber.create(processNumber);
  }


  private String generateNumber(Long currentNumber) {

    LOGGER.debug("Generation of process sequence started");

    StringBuilder sb = new StringBuilder();

    LOGGER.debug("PREFIX is [{}]", prefix.getValue());
    sb.append(prefix.getValue());

    if (dateFormat != null && !dateFormat.isEmpty()) {
      var now = LocalDate.now();
      var dateStr = DateUtil.biLocalDateToString.apply(now, DateTimeFormatter.ofPattern(dateFormat));
      LOGGER.debug("CURRENT_DATE [{}] with FORMAT [{}] is [{}]", now, dateFormat, dateStr);
      sb.append(separator != null ? separator : "");
      sb.append(dateStr);
    }

    if (padding != null && padding > 0) {
      var number = String.format("%0" + padding + "d", currentNumber); //do not edit
      LOGGER.debug("CURRENT_NUMBER [{}] with PADDING [{}] is [{}]", currentNumber, padding, number);
      sb.append(separator != null ? separator : "");
      sb.append(number);
    } else {
      LOGGER.debug("CURRENT_NUMBER [{}] with NO PADDING is [{}]", currentNumber, currentNumber);
      sb.append(separator != null ? separator : "");
      sb.append(currentNumber);
    }

    if (checkDigitSize != null && checkDigitSize > 0) {
      long checkDigit = currentNumber % (long) Math.pow(10, checkDigitSize);
      LOGGER.debug("CURRENT_NUMBER [{}] with CHECK_DIGIT_SIZE [{}] is [{}]", currentNumber, checkDigitSize, checkDigit);
      sb.append(checkDigit);
    } else {
      LOGGER.debug("CHECK_DIGIT_SIZE is NULL.");
    }

    var sequence = sb.toString();

    LOGGER.debug("Generation of process sequence finished");

    return sequence;
  }

  public static ProcessSequence defaultSequence(Code processDefinitionKey) {
    return ProcessSequence.builder()
        .id(Identifier.generate())
        .name(Name.create("Default_Sequence_" + processDefinitionKey.getValue()))
        .prefix(Code.create("PROC_"))
        .checkDigitSize((short) 0)
        .padding((short) 4)
        .dateFormat("yyyyMMdd")
        .nextNumber(1L)
        .numberIncrement((short) 1)
        .processDefinitionKey(processDefinitionKey)
        .build();
  }

  public void resetNextNumber() {
    this.nextNumber = 1L;
  }

  public ProcessSequence update(ProcessSequence sequence) {
    Long nextNumber = sequence.getNextNumber() != null
        ? sequence.getNextNumber()
        : this.nextNumber != null ? this.nextNumber : 1L;
    return ProcessSequence.builder()
        .id(this.id)
        .name(sequence.getName())
        .prefix(sequence.getPrefix())
        .checkDigitSize(sequence.getCheckDigitSize())
        .padding(sequence.getPadding() != null ? sequence.getPadding() : this.padding)
        .dateFormat(sequence.dateFormat)
        .nextNumber(nextNumber)
        .numberIncrement(sequence.numberIncrement)
        .processDefinitionKey(this.processDefinitionKey)
        .separator(sequence.getSeparator() != null ? sequence.getSeparator() : this.separator)
        .build();
  }

}
