package app.nebula.vpn.ui.servers

import android.content.Context
import app.nebula.vpn.R
import app.nebula.vpn.core.parser.SubscriptionException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object SubscriptionErrorFormatter {

    fun getErrorResId(error: Throwable): Int {
        return when (error) {
            is SubscriptionException.NetworkTimeout -> R.string.sub_err_network_timeout
            is SocketTimeoutException, is UnknownHostException -> R.string.sub_err_network_timeout
            is SubscriptionException.HttpError -> {
                when (error.statusCode) {
                    401, 403 -> R.string.sub_err_auth
                    404 -> R.string.sub_err_not_found
                    429 -> R.string.sub_err_rate_limited
                    in 500..599 -> R.string.sub_err_server_error
                    else -> R.string.sub_err_http_generic
                }
            }
            is SubscriptionException.HtmlWebPage -> R.string.sub_err_html_web_page
            is SubscriptionException.DeviceLimitReached -> R.string.sub_err_device_limit
            is SubscriptionException.HwidRequired -> R.string.sub_err_hwid_required
            is SubscriptionException.PayloadTooLarge -> R.string.sub_err_payload_too_large
            is SubscriptionException.EmptySubscription -> R.string.sub_err_empty
            is SubscriptionException.UnsupportedFormat -> R.string.sub_err_unsupported_format
            else -> R.string.sub_err_generic
        }
    }

    fun formatError(context: Context, error: Throwable): String {
        return when (error) {
            is SubscriptionException.NetworkTimeout -> {
                context.getString(R.string.sub_err_network_timeout)
            }
            is SocketTimeoutException, is UnknownHostException -> {
                context.getString(R.string.sub_err_network_timeout)
            }
            is SubscriptionException.HttpError -> {
                when (error.statusCode) {
                    401, 403 -> context.getString(R.string.sub_err_auth, error.statusCode)
                    404 -> context.getString(R.string.sub_err_not_found)
                    429 -> context.getString(R.string.sub_err_rate_limited)
                    in 500..599 -> context.getString(R.string.sub_err_server_error, error.statusCode)
                    else -> context.getString(R.string.sub_err_http_generic, error.statusCode, error.statusMessage)
                }
            }
            is SubscriptionException.HtmlWebPage -> {
                context.getString(R.string.sub_err_html_web_page)
            }
            is SubscriptionException.DeviceLimitReached -> {
                context.getString(R.string.sub_err_device_limit)
            }
            is SubscriptionException.HwidRequired -> {
                context.getString(R.string.sub_err_hwid_required)
            }
            is SubscriptionException.PayloadTooLarge -> {
                context.getString(R.string.sub_err_payload_too_large)
            }
            is SubscriptionException.EmptySubscription -> {
                context.getString(R.string.sub_err_empty)
            }
            is SubscriptionException.UnsupportedFormat -> {
                context.getString(R.string.sub_err_unsupported_format, error.detectedFormat)
            }
            else -> {
                val msg = error.message ?: error.javaClass.simpleName
                context.getString(R.string.sub_err_generic, msg)
            }
        }
    }
}
