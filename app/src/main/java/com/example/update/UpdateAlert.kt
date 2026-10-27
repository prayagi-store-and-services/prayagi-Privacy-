package com.example.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Kept only so an already-persisted old worker exits without network, APK or notification work. */
class UpdateAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = Result.success()
}
