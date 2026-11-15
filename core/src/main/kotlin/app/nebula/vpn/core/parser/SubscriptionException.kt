package app.nebula.vpn.core.parser

sealed class SubscriptionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class HtmlWebPage(val snippet: String = "") :
        SubscriptionException("The subscription link returned an HTML web page rather than proxy configurations")

    class DeviceLimitReached(val detail: String = "") :
        SubscriptionException("Subscription device limit has been reached on this account")

    class HwidRequired(val detail: String = "") :
        SubscriptionException("The provider panel requires client hardware identification (HWID)")

    class PayloadTooLarge(val sizeBytes: Long) :
        SubscriptionException("Subscription payload exceeds 20 MB size limit ($sizeBytes bytes)")

    class EmptySubscription :
        SubscriptionException("The subscription returned 0 proxy configurations")

    class UnsupportedFormat(val detectedFormat: String) :
        SubscriptionException("Unsupported or encrypted subscription format: $detectedFormat")

    class HttpError(val statusCode: Int, val statusMessage: String) :
        SubscriptionException("HTTP error $statusCode: $statusMessage")

    class NetworkTimeout(cause: Throwable) :
        SubscriptionException("Network timeout while downloading subscription: ${cause.message}", cause)
}
