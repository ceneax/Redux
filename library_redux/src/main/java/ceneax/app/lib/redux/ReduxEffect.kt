package ceneax.app.lib.redux

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

abstract class ReduxEffect<VM : ReduxViewModel<*>> {
    private lateinit var mReduxView: IReduxView<*, *>

    protected val ctx: EffectContext by lazy(LazyThreadSafetyMode.NONE) {
        EffectContext(
            activity = mReduxView.activity,
            lifecycleOwner = mReduxView.lifecycleOwner,
            fragmentManager = mReduxView.fragmentManager
        )
    }

    @Suppress("UNCHECKED_CAST")
    internal val _stateManager: VM by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(
            mReduxView.viewModelStoreOwner,
            Redux.viewModelFactory
        )[this::class.getGenericsClass(0)]
    }
    val stateManager: VM get() = _stateManager

    internal fun setBeforeData(data: Bundle) = data.keySet().forEach {
        runCatching {
            _stateManager::class.java.getDeclaredField(it)
        }.onSuccess { field ->
            if (!field.isAnnotationPresent(BD::class.java)) {
                return@forEach
            }

            field.isAccessible = true
            field.set(_stateManager, data[it])
        }
    }

    protected inline fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        crossinline block: suspend CoroutineScope.() -> Unit
    ): Job = ctx.lifecycleOwner.lifecycleScope.launch(context) {
        block()
    }

    suspend fun <T> loadingScope(
        block: suspend IReduxLoadingDialog<*>.() -> T
    ): T {
        Redux.loadingDialog.dialog?.show(ctx.fragmentManager, this@ReduxEffect::class.java.simpleName)
        Redux.loadingDialog.setLoadingContent(Redux.loadingDialog.defaultContent)
        val res = block(Redux.loadingDialog)
        Redux.loadingDialog.dialog?.dismiss()
        return res
    }

    data class EffectContext(
        val activity: Activity,
        val lifecycleOwner: LifecycleOwner,
        val fragmentManager: FragmentManager
    )
}

suspend fun <T> ReduxEffect.EffectContext.loadingScope(
    block: suspend IReduxLoadingDialog<*>.() -> T
): T {
    Redux.loadingDialog.dialog?.show(fragmentManager, this::class.java.simpleName)
    Redux.loadingDialog.setLoadingContent(Redux.loadingDialog.defaultContent)
    val res = block(Redux.loadingDialog)
    Redux.loadingDialog.dialog?.dismiss()
    return res
}