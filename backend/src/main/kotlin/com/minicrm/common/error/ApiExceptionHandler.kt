package com.minicrm.common.error

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

/** Помилка конкретного поля у відповіді валідації. */
data class FieldViolation(val field: String, val message: String)

/**
 * Єдиний формат помилок API — RFC 9457 `application/problem+json`:
 * `type`, `title`, `status`, `detail`, `instance`, додатково `code` і (для валідації) `errors[]`.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        // Одне повідомлення на поле, як у формах макетів: «обов'язкове» має пріоритет над перевіркою формату.
        val violations = ex.bindingResult.fieldErrors
            .groupBy { it.field }
            .map { (field, errors) ->
                val primary = errors.firstOrNull { it.code in REQUIRED_CODES } ?: errors.minBy { it.defaultMessage.orEmpty() }
                FieldViolation(field, primary.defaultMessage ?: "Invalid value")
            }
            .sortedBy { it.field }
        return handleExceptionInternal(ex, validationProblem(violations), headers, HttpStatus.BAD_REQUEST, request)
    }

    override fun createResponseEntity(
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        // Доповнюємо стандартні помилки Spring MVC (400 malformed JSON, 404, 405, 415 ...) машиночитаним кодом.
        if (body is ProblemDetail && body.properties?.containsKey("code") != true) {
            body.setProperty("code", ErrorCodes.forStatus(statusCode.value()))
        }
        return super.createResponseEntity(body, headers, statusCode, request)
    }

    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException): ResponseEntity<ProblemDetail> {
        val problem = if (ex.code == ErrorCodes.VALIDATION_FAILED) {
            validationProblem(ex.errors)
        } else {
            problem(ex.status, ex.code, ex.message)
        }
        return ResponseEntity.status(ex.status).body(problem)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ProblemDetail> {
        log.error("Unhandled exception", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "Unexpected server error"))
    }

    companion object {
        private val REQUIRED_CODES = setOf("NotBlank", "NotNull", "NotEmpty")

        fun problem(status: HttpStatus, code: String, detail: String): ProblemDetail =
            ProblemDetail.forStatusAndDetail(status, detail).apply {
                type = URI.create(ErrorCodes.TYPE_PREFIX + code)
                setProperty("code", code)
            }

        fun validationProblem(violations: List<FieldViolation>): ProblemDetail =
            problem(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Request validation failed").apply {
                title = "Validation failed"
                setProperty("errors", violations)
            }
    }
}

object ErrorCodes {
    const val TYPE_PREFIX = "urn:minicrm:problem:"
    const val VALIDATION_FAILED = "validation_failed"
    const val INTERNAL_ERROR = "internal_error"
    const val UNAUTHENTICATED = "unauthenticated"
    const val DEMO_EXPIRED = "demo_expired"
    const val FORBIDDEN = "forbidden"

    fun forStatus(status: Int): String = when (status) {
        400 -> "bad_request"
        401 -> UNAUTHENTICATED
        403 -> FORBIDDEN
        404 -> "not_found"
        405 -> "method_not_allowed"
        406 -> "not_acceptable"
        409 -> "conflict"
        415 -> "unsupported_media_type"
        429 -> "too_many_requests"
        in 500..599 -> INTERNAL_ERROR
        else -> "http_$status"
    }
}
