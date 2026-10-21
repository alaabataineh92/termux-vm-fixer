package io.github.alaabataineh92.termuxvmfixer.data.privilege.registry

import io.github.alaabataineh92.termuxvmfixer.core.model.PrivilegeMethod
import io.github.alaabataineh92.termuxvmfixer.core.model.ProviderStatus
import io.github.alaabataineh92.termuxvmfixer.core.model.RankedMethod
import io.github.alaabataineh92.termuxvmfixer.core.model.Reason
import io.github.alaabataineh92.termuxvmfixer.core.model.SessionProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class PrivilegeRegistry @Inject constructor(
    private val providers: Map<PrivilegeMethod, @JvmSuppressWildcards Provider<SessionProvider>>,
) {

    suspend fun rank(): List<RankedMethod> = coroutineScope {
        val probed = providers.values.map { provider ->
            async {
                val instance = provider.get()
                val status = runCatching { instance.probe() }
                    .getOrElse { error ->

                        ProviderStatus.Unavailable(
                            Reason.ProbeFailed(error::class.java.simpleName),
                        )
                    }
                RankedMethod(
                    method = instance.method,
                    status = status,
                    rank = instance.rank,
                )
            }
        }.awaitAll()

        probed.sorted()
    }

    fun provider(method: PrivilegeMethod): SessionProvider? =
        providers[method]?.get()
}
