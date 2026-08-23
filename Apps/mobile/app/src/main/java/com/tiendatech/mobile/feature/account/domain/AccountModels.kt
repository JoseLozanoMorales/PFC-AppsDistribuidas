package com.tiendatech.mobile.feature.account.domain

data class Profile(val id: Long, val username: String, val name: String, val document: String, val email: String, val phone: String)
data class Address(val id: Long, val street: String, val reference: String?, val cityId: Long, val city: String?, val province: String?, val enabled: Boolean)
data class City(val id: Long, val name: String, val provinceName: String)
data class PaymentMethod(val id: Long, val mask: String, val expiration: String, val enabled: Boolean, val typeId: Long, val typeName: String)
data class PaymentType(val id: Long, val name: String)
data class AccountData(val profile: Profile, val addresses: List<Address>, val cities: List<City>, val paymentMethods: List<PaymentMethod>, val paymentTypes: List<PaymentType>)
data class OrderConfirmation(val id: Long, val total: Double, val date: String)

sealed interface AccountResult<out T> {
    data class Success<T>(val value: T) : AccountResult<T>
    data class Failure(val message: String) : AccountResult<Nothing>
    data class Ambiguous(val message: String) : AccountResult<Nothing>
    data object Unauthorized : AccountResult<Nothing>
}
