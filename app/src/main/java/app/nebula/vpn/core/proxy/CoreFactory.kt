package app.nebula.vpn.core.proxy

import android.content.Context

object CoreFactory {
    fun create(
        context: Context,
        coreType: CoreType = CoreType.SING_BOX,
        useStub: Boolean = false
    ): ProxyCore {
        return when {
            useStub -> StubProxyCore()
            coreType == CoreType.XRAY -> XrayCore()
            else -> LibboxCore(context)
        }
    }
}
