package app.nebula.vpn.core.proxy

import android.content.Context

object CoreFactory {
    fun create(context: Context, useStub: Boolean = false): ProxyCore {
        return if (useStub) {
            StubProxyCore()
        } else {
            LibboxCore(context)
        }
    }
}
