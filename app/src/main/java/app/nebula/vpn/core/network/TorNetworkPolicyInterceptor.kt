package app.nebula.vpn.core.network

import app.nebula.vpn.core.tor.NetworkPolicy
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor enforcing the central Tor Fail-Closed NetworkPolicy in the Android app.
 */
class TorNetworkPolicyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val port = request.url.port

        NetworkPolicy.checkAllowed(host, port)

        return chain.proceed(request)
    }
}
