package ceneax.app.lib.redux

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import ceneax.app.lib.redux.annotation.ReduxModule
import kotlin.random.Random

object ReduxRouterCore {
    private val mModuleController by lazy { ReduxRouterModuleController() }

    fun addModuleName(name: String) {
        try {
            val cls = Class.forName(name)
            if (ReduxModule::class.java.isAssignableFrom(cls)) {
                addModule(cls.newInstance() as ReduxModule)
            }
        } catch (e: Exception) {
            // 忽略
        }
    }

    fun addModule(module: ReduxModule) = mModuleController.addModule(module)

    fun removeModule(module: ReduxModule) = mModuleController.removeModule(module)

    fun queryPageRoute(path: String): String = mModuleController.queryPageRoute(path)
}

class ReduxRouterModuleController {
    private val mModules = mutableListOf<ReduxModule>()

    fun addModule(vararg module: ReduxModule) {
        module.forEach {
            mModules.add(it)
        }
    }

    fun removeModule(module: ReduxModule) {
        mModules.remove(module)
    }

    fun queryPageRoute(path: String): String {
        var className = ""
        for (module in mModules) {
            val find = module.getPageRoute()[path]
            if (find != null) {
                className = find.name
                break
            }
        }
        return className
    }
}

class ReduxRouter private constructor() {
    companion object {
        val instance by lazy { ReduxRouter() }
    }

    fun <A : Activity> build(targetActivity: Class<A>): RouterParams {
        return RouterParams.Builder(
            targetActivity = targetActivity.name
        ).build()
    }

    fun <A : Activity> build(activity: ComponentActivity, targetActivity: Class<A>): RouterParams {
        return RouterParams.Builder(
            activity = activity,
            targetActivity = targetActivity.name
        ).build()
    }

    fun build(targetPath: String): RouterParams {
        return RouterParams.Builder(
            targetActivity = ReduxRouterCore.queryPageRoute(targetPath)
        ).build()
    }

    fun build(activity: ComponentActivity, targetPath: String): RouterParams {
        return RouterParams.Builder(
            activity = activity,
            targetActivity = ReduxRouterCore.queryPageRoute(targetPath)
        ).build()
    }
}

class RouterParams private constructor(private val builder: Builder) {
    data class Builder(
        val activity: ComponentActivity? = null,
        val targetActivity: String = ""
    ) {
        var bundle: Bundle? = null
        var onResult: ((ActivityResult) -> Unit)? = null

        fun build() = RouterParams(this)
    }

    fun with(bundle: Bundle): RouterParams {
        builder.bundle = bundle
        return this
    }

    fun onResult(onResult: (ActivityResult) -> Unit): RouterParams {
        builder.onResult = onResult
        return this
    }

    fun navigation() = RouterExecutor(builder).execute()
}

class RouterExecutor(private val params: RouterParams.Builder) {
    fun execute() {
        if (params.targetActivity.isEmpty()) {
            RLog.e("跳转失败，未找到目标Activity")
            return
        }

        if (params.activity == null || params.onResult == null) {
            navigationNoResult()
            return
        }

        navigationWithResult()
    }

    private fun navigationNoResult() {
        Redux.application.startActivity(Intent().also {
            it.setClassName(Redux.application, params.targetActivity)
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (params.bundle != null) {
                it.putExtras(params.bundle!!)
            }
        })
    }

    private fun navigationWithResult() {
        val launcher = params.activity!!.activityResultRegistry.register(
            "activity_rq#${Random(Int.MAX_VALUE).nextInt()}",
            ActivityResultContracts.StartActivityForResult(),
            params.onResult!!
        )

        params.activity.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    launcher.unregister()
                }
            }
        })

        launcher.launch(Intent().also {
            it.setClassName(params.activity, params.targetActivity)
            if (params.bundle != null) {
                it.putExtras(params.bundle!!)
            }
        })
    }
}