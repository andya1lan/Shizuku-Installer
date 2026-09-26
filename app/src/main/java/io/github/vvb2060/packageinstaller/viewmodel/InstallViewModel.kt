package io.github.vvb2060.packageinstaller.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Handler
import android.os.Looper
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import io.github.vvb2060.packageinstaller.model.InstallRepository
import io.github.vvb2060.packageinstaller.model.InstallStage
import rikka.shizuku.Shizuku

@SuppressLint("RestrictedApi")
class InstallViewModel(application: Application) : AndroidViewModel(application) {
    private val executor = ArchTaskExecutor.getInstance()
    private val repository = InstallRepository(getApplication())
    private val handler = Handler(Looper.getMainLooper())
    private val binderListener = Shizuku.OnBinderReceivedListener { runPreCheck() }
    private var intentToCheck: Intent? = null
    val currentInstallStage: LiveData<InstallStage>
        get() = repository.installResult
    val stagingProgress: LiveData<Int>
        get() = repository.stagingProgress

    fun preprocessIntent(intent: Intent) {
        // Shizuku pushes its binder when our process starts, which can land after
        // onCreate. Wait up to 1 s for it instead of reporting Shizuku as unavailable.
        // The listener runs only after attachApplication has finished.
        intentToCheck = intent
        handler.postDelayed({ runPreCheck() }, 1000)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
    }

    private fun runPreCheck() {
        val intent = intentToCheck ?: return
        intentToCheck = null
        handler.removeCallbacksAndMessages(null)
        if (repository.preCheck(intent)) {
            executor.executeOnDiskIO {
                repository.parseUri()
            }
        }
    }

    override fun onCleared() {
        handler.removeCallbacksAndMessages(null)
        // Removed here rather than in the callback: Shizuku may be iterating its
        // listener list when it calls us.
        Shizuku.removeBinderReceivedListener(binderListener)
    }

    fun cleanupInstall() {
        repository.cleanupInstall()
    }

    fun initiateInstall(
        setInstaller: Boolean,
        commit: Boolean,
        full: Boolean,
        removeSplit: Boolean
    ) {
        executor.executeOnDiskIO {
            repository.install(setInstaller, commit, full, removeSplit)
        }
    }

    fun archivePackage(info: PackageInfo, uninstall: Boolean) {
        executor.executeOnDiskIO {
            repository.archivePackage(info, uninstall)
        }
    }

    fun setPackageEnabled(packageName: String, enabled: Boolean) {
        executor.executeOnDiskIO {
            repository.setPackageEnabled(packageName, enabled)
        }
    }
}
