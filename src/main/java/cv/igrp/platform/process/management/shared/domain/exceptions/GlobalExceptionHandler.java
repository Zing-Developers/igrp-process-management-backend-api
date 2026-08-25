package cv.igrp.platform.process.management.shared.domain.exceptions;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import cv.igrp.platform.process.management.processdefinition.domain.exception.ProcessDeploymentException;
import cv.igrp.platform.process.management.processruntime.domain.exception.RuntimeProcessEngineException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Arrays;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IgrpResponseStatusException.class)
    public ProblemDetail handleIgrpResponseStatusException(IgrpResponseStatusException ex) {

        LOGGER.error(ex.getMessage(), ex);

        return ex.getBody();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {

        LOGGER.error(ex.getMessage(), ex);

        var problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        problemDetail.setTitle(ex.getMessage());

        return problemDetail;
    }

    @ExceptionHandler(ClassCastException.class)
    public ProblemDetail handleClassCastException(ClassCastException ex) {

        var stackTrace = ex.getStackTrace();

        var origin = stackTrace.length > 0 ? stackTrace[0] : null;

        var detailedMessage = ex.getMessage();

        if (origin != null) {
            detailedMessage += " at " + origin.getClassName() + "." + origin.getMethodName() +
                               "(" + origin.getFileName() + ":" + origin.getLineNumber() + ")";
        }

        LOGGER.error("CLASS CAST EXCEPTION: {}", detailedMessage, ex);

        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(NullPointerException.class)
    public ProblemDetail handleIllegalArgumentException(NullPointerException ex) {

        LOGGER.error(ex.getMessage(), ex);

        var problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        // Helpful NPE messages carry internal field/method/class names — server log only
        problemDetail.setTitle("Unexpected server error");

        return problemDetail;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalStateException(IllegalStateException ex) {

        LOGGER.error(ex.getMessage(), ex);

        var problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        // Internal-state details stay in the server log
        problemDetail.setTitle("Unexpected server state");

        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {

        var errors = ex.getBindingResult().getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField, fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value")
                );

        var problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Validation Errors");
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolationException(ConstraintViolationException ex) {

        var errors = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage));

        var problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Constraint Violation Errors");
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {

        LOGGER.error("HTTP MESSAGE NOT READABLE EXCEPTION", ex);

        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        if (ex.getCause() instanceof InvalidFormatException ife && ife.getTargetType().isEnum()) {

            var targetType = ife.getTargetType();

            var allowedValues = Arrays.stream(targetType.getEnumConstants())
                    .map(Object::toString)
                    .toArray(String[]::new);

            problem.setTitle("Invalid value for enum type: " + targetType.getSimpleName());
            problem.setProperty("CurrentValue", ife.getValue());
            problem.setProperty("AllowedValues", allowedValues);
            return problem;
        }

        // Jackson parser internals (class paths, locations) stay in the server log
        problem.setTitle("Malformed JSON request");

        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {

        var rootCause = getRootCause(ex);

        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        if (rootCause instanceof PSQLException psqlEx) {

            var sqlState = psqlEx.getSQLState();

            if ("23503".equals(sqlState)) {

                var detail = extractForeignKeyField(psqlEx);

                problem.setTitle("Foreign Key Constraint Violation");
                problem.setDetail(detail != null
                        ? "Foreign key constraint violated on field: '" + detail + "'."
                        : "A foreign key constraint was violated.");
                return problem;
            }
        }

        // Raw PSQL messages leak table/column/constraint names and values — server log only
        LOGGER.error("Data integrity violation", ex);
        problem.setTitle("Data Integrity Violation");
        problem.setDetail("A data integrity constraint was violated.");

        return problem;
    }

    private String extractForeignKeyField(org.postgresql.util.PSQLException ex) {

        var message = ex.getServerErrorMessage() != null
                ? ex.getServerErrorMessage().getDetail()
                : ex.getMessage();

        if (message != null) {
            int keyStart = message.indexOf("Key (");
            int keyEnd = message.indexOf(")=");
            if (keyStart != -1 && keyEnd != -1 && keyEnd > keyStart + 5) {
                return message.substring(keyStart + 5, keyEnd);
            }
        }
        return null;
    }

    private Throwable getRootCause(Throwable throwable) {
        var cause = throwable.getCause();
        return (cause == null || cause == throwable) ? throwable : getRootCause(cause);
    }

  @ExceptionHandler(RuntimeProcessEngineException.class)
  public ProblemDetail handleRuntimeProcessEngineException(RuntimeProcessEngineException ex) {

    var problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);

    problemDetail.setTitle(ex.getMessage());

    return problemDetail;
  }

  @ExceptionHandler(ProcessDeploymentException.class)
  public ProblemDetail handleProcessDeploymentException(ProcessDeploymentException ex) {

    var problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);

    problemDetail.setTitle(ex.getMessage());

    return problemDetail;
  }

}
