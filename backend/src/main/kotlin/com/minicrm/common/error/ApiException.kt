package com.minicrm.common.error

import org.springframework.http.HttpStatus

/** Бізнесова помилка з машиночитаним кодом; перетворюється на problem+json у [ApiExceptionHandler]. */
open class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
    val errors: List<FieldViolation> = emptyList(),
) : RuntimeException(message)

class NotFoundException(what: String) : ApiException(HttpStatus.NOT_FOUND, "not_found", "$what not found")

class FieldValidationException(field: String, message: String) :
    ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Request validation failed", listOf(FieldViolation(field, message)))
