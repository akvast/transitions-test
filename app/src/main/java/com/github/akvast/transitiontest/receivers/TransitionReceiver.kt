package com.github.akvast.transitiontest.receivers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.support.v4.app.NotificationCompat
import com.github.akvast.transitiontest.R
import com.github.akvast.transitiontest.database.Database
import com.github.akvast.transitiontest.database.entities.UserActivity
import com.github.akvast.transitiontest.database.entities.UserActivityTransition
import com.github.akvast.transitiontest.ui.vm.UserActivityTransitionViewModel
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers


class TransitionReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "default"
        const val CHANNEL_NAME = "Notifications"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val notificationManager = context?.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = CHANNEL_NAME
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            notificationManager.createNotificationChannel(channel)
        }

        val recognitionResult = ActivityRecognitionResult.extractResult(intent)
        recognitionResult?.mostProbableActivity?.let {
            if (it.type != DetectedActivity.STILL) {
                Completable.fromAction {
                    Database.getUserActivityDao()
                            .insert(UserActivity().apply {
                                type = it.type
                                confidence = it.confidence
                            })
                }.subscribeOn(Schedulers.io()).subscribe()
            }
        }

        val transitionResult = ActivityTransitionResult.extractResult(intent)
        Completable.fromAction {
            transitionResult?.transitionEvents?.forEach {
                Database.getUserActivityTransitionDao()
                        .insert(UserActivityTransition().apply {
                            activityType = it.activityType
                            transitionType = it.transitionType
                        })
            }
        }.subscribeOn(Schedulers.io()).subscribe()

        transitionResult?.transitionEvents?.forEach {
            val viewModel = UserActivityTransitionViewModel(UserActivityTransition().apply {
                activityType = it.activityType
                transitionType = it.transitionType
            })
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setColor(context.resources.getColor(R.color.colorPrimary))
                    .setContentTitle(context.getString(R.string.app_name))
                    .setDefaults(Notification.DEFAULT_LIGHTS or Notification.DEFAULT_VIBRATE)
                    .setLights(Color.RED, 500, 2000)
                    .setContentText("${viewModel.activityType()} ${viewModel.transitionType()} at ${viewModel.date()}")
                    .setAutoCancel(true)

            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

}