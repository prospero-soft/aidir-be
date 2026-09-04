package ro.prospero.aidir.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;

/**
 * Keeps the onboarding endpoints answering a bad request the same way, whichever flow it came from.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Without this the exception reaches no handler at all and the caller gets a 500 for what is a
     * client-side mistake.
     */
    @ExceptionHandler(ApiPayloadValidationException.class)
    public ProblemDetail onInvalidPayload(ApiPayloadValidationException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problemDetail.setProperty("constraintViolations", exception.getConstraintViolations());
        return problemDetail;
    }

    /**
     * A body Jackson cannot read - malformed JSON, or a value no @JsonCreator accepts, such as an unknown
     * plan key. The two flows fail differently without this: an unreadable @RequestBody is an opaque 400,
     * while ToolSubmissionEndpoint parses by hand and lets JsonProcessingException escape as a 500.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, JsonProcessingException.class})
    public ProblemDetail onUnreadablePayload(Exception exception) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);
        // getOriginalMessage() keeps Jackson's reason but drops its source snippet, which would echo the
        // raw request body - basic.password included - straight back out.
        String reason = cause instanceof JsonProcessingException jacksonFailure
                ? jacksonFailure.getOriginalMessage()
                : cause.getMessage();

        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                                                "The request payload could not be read: " + reason);
    }

    /**
     * What a payload that is well-formed but collides with the database looks like: a talent signing up on
     * an email that already has an account (409), or naming a skill, language or location that is not in
     * the reference tables (400 - the client sent something the vocabulary does not contain).
     * <p>
     * Both exception types are listed on purpose. JooqConfig builds its DSLContext by hand, so Spring Boot's
     * jOOQ auto-configuration backs off and no JooqExceptionTranslator is registered: writes made through
     * jOOQ raise jOOQ's own DataAccessException rather than Spring's DataIntegrityViolationException.
     */
    @ExceptionHandler({DataIntegrityViolationException.class, DataAccessException.class})
    public ProblemDetail onConstraintViolation(Exception exception) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);
        String sqlState = cause instanceof SQLException sqlFailure ? sqlFailure.getSQLState() : null;

        if (UNIQUE_VIOLATION.equals(sqlState)) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                                                    "That record already exists. If this was a sign-up, "
                                                    + "the email address is already registered.");
        }

        if (FOREIGN_KEY_VIOLATION.equals(sqlState)) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                                                    "The payload references a value that is not in the "
                                                    + "reference tables - check skills, languages and locations.");
        }

        // Anything else here is ours, not the caller's, so it stays a 500 and says nothing further.
        LOGGER.error("Unhandled data access failure (SQLState {})", sqlState, exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "The request could not be stored.");
    }
}
