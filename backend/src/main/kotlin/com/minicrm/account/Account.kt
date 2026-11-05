package com.minicrm.account

import com.minicrm.auth.AppUserRepository
import com.minicrm.auth.MeResponse
import com.minicrm.common.web.currentUser
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.reflect.KClass

/** Чинні коди ISO 4217 (без історичних і металів) — той самий перелік, що на екрані Settings. */
object Currencies {
    val ACTIVE: Set<String> = linkedSetOf(
        "AED", "AFN", "ALL", "AMD", "AOA", "ARS", "AUD", "AWG", "AZN", "BAM", "BBD", "BDT", "BGN", "BHD", "BIF",
        "BMD", "BND", "BOB", "BRL", "BSD", "BTN", "BWP", "BYN", "BZD", "CAD", "CDF", "CHF", "CLP", "CNY", "COP",
        "CRC", "CUP", "CVE", "CZK", "DJF", "DKK", "DOP", "DZD", "EGP", "ERN", "ETB", "EUR", "FJD", "FKP", "GBP",
        "GEL", "GHS", "GIP", "GMD", "GNF", "GTQ", "GYD", "HKD", "HNL", "HTG", "HUF", "IDR", "ILS", "INR", "IQD",
        "IRR", "ISK", "JMD", "JOD", "JPY", "KES", "KGS", "KHR", "KMF", "KPW", "KRW", "KWD", "KYD", "KZT", "LAK",
        "LBP", "LKR", "LRD", "LSL", "LYD", "MAD", "MDL", "MGA", "MKD", "MMK", "MNT", "MOP", "MRU", "MUR", "MVR",
        "MWK", "MXN", "MYR", "MZN", "NAD", "NGN", "NIO", "NOK", "NPR", "NZD", "OMR", "PAB", "PEN", "PGK", "PHP",
        "PKR", "PLN", "PYG", "QAR", "RON", "RSD", "RUB", "RWF", "SAR", "SBD", "SCR", "SDG", "SEK", "SGD", "SHP",
        "SLE", "SOS", "SRD", "SSP", "STN", "SVC", "SYP", "SZL", "THB", "TJS", "TMT", "TND", "TOP", "TRY", "TTD",
        "TWD", "TZS", "UAH", "UGX", "USD", "UYU", "UZS", "VES", "VND", "VUV", "WST", "XAF", "XCD", "XCG", "XOF",
        "XPF", "YER", "ZAR", "ZMW", "ZWG",
    )

    fun isActive(code: String?): Boolean = code != null && code in ACTIVE
}

/** Bean Validation: значення — чинний код ISO 4217 у верхньому регістрі. */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [IsoCurrencyValidator::class])
annotation class IsoCurrency(
    val message: String = "Choose a valid currency",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class IsoCurrencyValidator : ConstraintValidator<IsoCurrency, String?> {
    // Порожнє значення обробляє @NotBlank, щоб на поле було одне повідомлення.
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean = value.isNullOrBlank() || Currencies.isActive(value)
}

data class CurrencyRequest(
    @field:NotBlank(message = "Choose a currency")
    @field:IsoCurrency
    val currency: String?,
)

interface AccountRepository : Repository<com.minicrm.auth.AppUser, Long> {
    @Modifying
    @Query("UPDATE app_user SET currency = :currency WHERE id = :id")
    fun updateCurrency(id: Long, currency: String): Int
}

@Service
class AccountService(private val accounts: AccountRepository, private val users: AppUserRepository) {
    /** Змінює лише позначення валюти власного акаунта; суми угод не конвертуються. */
    @Transactional
    fun changeCurrency(userId: Long, currency: String): String {
        accounts.updateCurrency(userId, currency)
        return users.findById(userId).orElseThrow().currency
    }
}

@RestController
@RequestMapping("/api/account")
class AccountController(private val service: AccountService) {
    @PutMapping("/currency")
    fun changeCurrency(@Valid @RequestBody body: CurrencyRequest): MeResponse {
        val me = currentUser()
        val currency = service.changeCurrency(me.id, body.currency!!)
        return MeResponse(me.email, me.demo, currency, me.demoExpiresAt)
    }
}
