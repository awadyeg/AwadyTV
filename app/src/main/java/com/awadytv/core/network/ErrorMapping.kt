package com.awadytv.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.awadytv.core.util.AppError
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.EOFException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Maps network/decoding failures to user-presentable [AppError]s (never echoing URLs or credentials). */
object ErrorMapping {
    fun map(t: Throwable, context: Context? = null): AppError {
        if (context != null && !isOnline(context)) return AppError.Offline
        return when (t) {
            is HttpException -> when (t.code()) {
                401, 403, 884 -> AppError.WrongCredentials
                404 -> AppError.NotXtream
                else -> AppError.Http(t.code())
            }
            is SocketTimeoutException, is InterruptedIOException -> AppError.Timeout
            is UnknownHostException, is ConnectException, is SSLException -> AppError.Unreachable
            is SerializationException, is EOFException, is IllegalArgumentException -> AppError.NotXtream
            else -> AppError.Unknown(t.javaClass.simpleName)
        }
    }

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
