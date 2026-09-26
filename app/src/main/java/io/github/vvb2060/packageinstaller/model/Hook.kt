package io.github.vvb2060.packageinstaller.model

import android.app.ActivityThread
import android.app.IActivityManager
import android.content.AttributionSource
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageInstallerSession
import android.content.pm.IPackageManager
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.RemoteException
import android.provider.Settings
import android.system.Os
import androidx.annotation.RequiresApi
import org.lsposed.hiddenapibypass.LSPass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

private const val EXTENSION_TRANSACTION =
    ('_'.code shl 24) or ('E'.code shl 16) or ('X'.code shl 8) or 'T'.code

private class ExtBinderWrapper(original: IBinder) : ShizukuBinderWrapper(original) {
    @Throws(RemoteException::class)
    fun getExtension(): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            transact(EXTENSION_TRANSACTION, data, reply, 0)
            val extension = reply.readStrongBinder()
            return extension?.let { ExtBinderWrapper(it) }
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}

object Hook {
    private var hooked = false
    private lateinit var am: IActivityManager
    lateinit var pm: PackageManager
    lateinit var installer: PackageInstaller

    init {
        LSPass.setHiddenApiExemptions("")
    }

    fun init(context: Context) {
        if (hooked) return

        val ibinder = SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)
        am = IActivityManager.Stub.asInterface(ExtBinderWrapper(ibinder))

        pm = context.packageManager
        var contextImpl = context
        while (contextImpl is ContextWrapper) {
            contextImpl = contextImpl.baseContext
        }
        contextImpl::class.java.getDeclaredField("mPackageManager").apply {
            isAccessible = true
            set(contextImpl, null)
        }

        installer = pm.packageInstaller
        pm::class.java.getDeclaredField("mInstaller").apply {
            isAccessible = true
            set(pm, null)
        }

        ActivityThread::class.java.getDeclaredField("sPackageManager").apply {
            isAccessible = true
            val sPackageManager = get(null) as IPackageManager
            val wrapper = ExtBinderWrapper(sPackageManager.asBinder())
            set(null, IPackageManager.Stub.asInterface(wrapper))
        }
        pm::class.java.getDeclaredField("mPM").apply {
            isAccessible = true
            val mPM = get(pm) as IPackageManager
            val wrapper = ExtBinderWrapper(mPM.asBinder())
            set(pm, IPackageManager.Stub.asInterface(wrapper))
        }
        installer::class.java.getDeclaredField("mInstaller").apply {
            isAccessible = true
            val mInstaller = get(installer) as IPackageInstaller
            val wrapper = ExtBinderWrapper(mInstaller.asBinder())
            set(installer, IPackageInstaller.Stub.asInterface(wrapper))
        }
        hooked = true
    }

    private fun wrapGlobalSettings(callback: Runnable) {
        val holder = Settings.Global::class.java.getDeclaredField("sProviderHolder").run {
            isAccessible = true
            get(null)
        }
        val provider = holder::class.java.getDeclaredField("mContentProvider").run {
            isAccessible = true
            get(holder)
        }
        provider::class.java.getDeclaredField("mRemote").run {
            isAccessible = true
            val binder = get(provider) as IBinder
            val wrapper = ExtBinderWrapper(binder)
            set(provider, wrapper)
            callback.run()
            set(provider, binder)
        }
    }

    fun PackageInstaller.Session.wrap() {
        return this::class.java.getDeclaredField("mSession").run {
            isAccessible = true
            val mSession = get(this@wrap) as IPackageInstallerSession
            val wrapper = ExtBinderWrapper(mSession.asBinder())
            set(this@wrap, IPackageInstallerSession.Stub.asInterface(wrapper))
        }
    }

    fun disableAdbVerify(context: Context) {
        val name = "verifier_verify_adb_installs"
        val verifierIncludeAdb = Settings.Global.getInt(context.contentResolver, name, 1) != 0
        if (verifierIncludeAdb) {
            wrapGlobalSettings {
                val contextWrapper = ShizukuContext(context)
                val cr = object : ContentResolver(contextWrapper) {}
                Settings.Global.putInt(cr, name, 0)
            }
        }
    }

    fun startActivity(intent: Intent) {
        val userId = Os.getuid() / 100000
        am.startActivityAsUser(
            null, "com.android.shell", intent, intent.type,
            null, null, 0, 0, null, null, userId
        )
    }
}

private class ShizukuContext(context: Context) : ContextWrapper(context) {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun getAttributionSource(): AttributionSource {
        val builder = AttributionSource.Builder(Shizuku.getUid())
            .setPackageName("com.android.shell")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setPid(Process.INVALID_PID)
        }
        return builder.build()
    }

    override fun getOpPackageName(): String {
        return "com.android.shell"
    }
}
