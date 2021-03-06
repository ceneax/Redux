package ceneax.app.lib.redux

import android.os.Bundle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlin.reflect.KProperty1

abstract class ReduxReducer<S : IReduxState> : ViewModel() {
    private val stateStore: IReduxStateStore<S> = ReduxStateStore(
        viewModelScope,
        MutableStateFlow(this::class.newGenericsInstance(0))
    )

    internal val stateFlow: StateFlow<S> get() = stateStore.stateFlow

    val state: S get() = stateStore.state

    protected fun setState(block: S.() -> S) {
        stateStore.setState(block)
    }
}

internal class EmptyReducer : ReduxReducer<EmptyState>()

internal fun <S : IReduxState> ReduxReducer<S>.setBeforeData(data: Bundle) = data.keySet().forEach {
    runCatching {
        this::class.java.getDeclaredField(it)
    }.onSuccess { field ->
        if (!field.isAnnotationPresent(BD::class.java)) {
            return@forEach
        }

        field.isAccessible = true
        field.set(this, data[it])
    }
}

internal inline fun <S : IReduxState> ReduxReducer<S>.observeAll(
    owner: LifecycleOwner,
    crossinline block: S.() -> Unit
) = stateFlow.onEach {
    block(stateFlow.value)
}.launchIn(owner.lifecycleScope)

internal inline fun <S : IReduxState> ReduxReducer<S>.observe(
    owner: LifecycleOwner,
    vararg props: KProperty1<IReduxState, *>,
    crossinline block: S.() -> Unit
) {
    if (props.isEmpty()) {
        return
    }

    stateFlow.map {
        getPropertiesValues(it, *props)
    }.distinctUntilChanged(
        ::arrayAllSame
    ).onEach {
        block(stateFlow.value)
    }.launchIn(owner.lifecycleScope)
}

internal fun <R> getPropertiesValues(
    receiver: R,
    vararg props: KProperty1<R, *>
): Array<*> = Array(props.size) {
    props[it].get(receiver)
}

internal fun arrayAllSame(old: Array<*>, new: Array<*>): Boolean {
    if (old.size != new.size) return false
    for (i in new.indices) {
        if (old[i] != new[i]) {
            return false
        }
    }
    return true
}