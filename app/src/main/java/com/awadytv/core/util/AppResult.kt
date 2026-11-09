package com.awadytv.core.util

/** Minimal result wrapper used by repositories; [AppError] carries a user-presentable message. */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

/** Errors the UI knows how to explain. Messages never include credentials or full URLs. */
sealed class AppError(val title: String, val message: String) {
    data object InvalidUrl : AppError("Check the server URL", "Enter the address your provider gave you, for example http://example.com:8080")
    data object WrongCredentials : AppError("Sign-in failed", "The username or password was not accepted by the server.")
    data class Expired(val expiry: String?) : AppError("Subscription expired", "This account expired${expiry?.let { " on $it" } ?: ""}. Contact your provider to renew it.")
    data object Banned : AppError("Account banned", "The provider has banned this account.")
    data object Disabled : AppError("Account disabled", "The provider has disabled this account.")
    data object Offline : AppError("You're offline", "Connect this TV to the internet and try again.")
    data object Unreachable : AppError("Server unreachable", "The server could not be reached. Check the URL and port, or try again later.")
    data object Timeout : AppError("Server is slow", "The server took too long to answer. Try again in a moment.")
    data object NotXtream : AppError("Unexpected response", "This doesn't look like an Xtream Codes server. Check the URL.")
    data class Http(val code: Int) : AppError("Server error", "The server answered with error $code.")
    data object NoProfile : AppError("Not signed in", "Sign in with your Xtream Codes account first.")
    data class Unknown(val detail: String?) : AppError("Something went wrong", detail ?: "Please try again.")
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}
