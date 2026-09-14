package com.studysync.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.studysync.MainActivity
import com.studysync.R

object StudySyncNotifications {

    const val CHANNEL_CLASSES = "study_sync_classes"
    const val CHANNEL_FRIENDS = "study_sync_friends"
    const val CHANNEL_GENERAL = "study_sync_general"

    private const val NOTIFY_ID_FRIEND_OFFSET = 0x5200_0000

    private fun mayPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun postNotification(context: Context, id: Int, notification: Notification) {
        if (!mayPostNotifications(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied / disabled at runtime
        }
    }

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val classes = NotificationChannel(
            CHANNEL_CLASSES,
            context.getString(R.string.notification_channel_classes_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_classes_description)
        }
        val friends = NotificationChannel(
            CHANNEL_FRIENDS,
            context.getString(R.string.notification_channel_friends_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_friends_description)
        }
        val general = NotificationChannel(
            CHANNEL_GENERAL,
            context.getString(R.string.notification_channel_general_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_general_description)
        }
        nm.createNotificationChannel(classes)
        nm.createNotificationChannel(friends)
        nm.createNotificationChannel(general)
    }

    fun showClassCreated(context: Context, classTitle: String, classId: String) {
        val appContext = context.applicationContext
        ensureChannels(appContext)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("studysync://invite/class/$classId")).apply {
            setClass(appContext, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            appContext,
            classId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_CLASSES)
            .setSmallIcon(R.drawable.ic_stat_studysync)
            .setContentTitle(appContext.getString(R.string.notification_class_created_title))
            .setContentText(
                appContext.getString(R.string.notification_class_created_body, classTitle)
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        appContext.getString(R.string.notification_class_created_body, classTitle)
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        postNotification(appContext, classId.hashCode(), notification)
    }

    fun showFriendAddedYou(context: Context, fromName: String, inboundDocId: String) {
        val appContext = context.applicationContext
        ensureChannels(appContext)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("studysync://friends")).apply {
            setClass(appContext, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            appContext,
            NOTIFY_ID_FRIEND_OFFSET + inboundDocId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_FRIENDS)
            .setSmallIcon(R.drawable.ic_stat_studysync)
            .setContentTitle(appContext.getString(R.string.notification_friend_added_title))
            .setContentText(
                appContext.getString(R.string.notification_friend_added_body, fromName)
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        appContext.getString(R.string.notification_friend_added_body, fromName)
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        postNotification(appContext, NOTIFY_ID_FRIEND_OFFSET + inboundDocId.hashCode(), notification)
    }
}
