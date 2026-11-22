package github.franco.chat.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import github.franco.chat.ai.MainActivity
import github.franco.chat.ai.data.Chat
import github.franco.chat.ai.data.ChatRepository
import github.franco.chat.ai.data.Message
import github.franco.chat.ai.net.ChatClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Servicio en primer plano que ejecuta las peticiones al proveedor.
 * La respuesta sigue llegando aunque la app salga de pantalla o pase a Recientes,
 * y se guarda en el chat aunque la interfaz ya no esté visible.
 */
class ResponseService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = ChatClient()
    private val active = AtomicInteger(0)
    private lateinit var repository: ChatRepository

    override fun onCreate() {
        super.onCreate()
        repository = ChatRepository(applicationContext)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.coroutineContext.cancelChildren()
            client.cancel()
            if (active.get() == 0) stopSelf()
            return START_NOT_STICKY
        }

        val job = intent?.getStringExtra(EXTRA_JOB_ID)?.let { ResponseJobs.take(it) }
        // Android exige entrar en primer plano poco después de startForegroundService.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(job?.chat?.title ?: "Respuesta"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        if (job == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        active.incrementAndGet()
        scope.launch { run(job) }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun run(job: ResponseJob) {
        val text = StringBuilder()
        var error: String? = null
        ResponseBus.publish(ResponseUpdate(job.chat.id, "", streaming = true, error = null))
        try {
            client.stream(job.request).collect { delta ->
                text.append(delta)
                ResponseBus.publish(
                    ResponseUpdate(job.chat.id, text.toString(), streaming = true, error = null)
                )
            }
        } catch (e: Exception) {
            // Si cancelamos nosotros (botón Detener) no es un error que mostrar.
            if (currentCoroutineContext().isActive) error = friendlyError(e)
        } finally {
            val result = text.toString()
            withContext(NonCancellable) { save(job.chat, result) }
            ResponseBus.publish(ResponseUpdate(job.chat.id, result, streaming = false, error = error))
            finishOne()
        }
    }

    /** Guarda el chat con la respuesta. */
    private suspend fun save(chat: Chat, text: String) {
        val saved = chat.copy(
            messages = chat.messages + Message("assistant", text),
            updatedAt = System.currentTimeMillis()
        )
        repository.upsertChat(saved)
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is UnknownHostException -> "Sin conexión con el proveedor. Revisa tu red y reintenta."
        else -> e.message ?: "Error desconocido"
    }

    private fun finishOne() {
        if (active.decrementAndGet() <= 0) stopIfIdle()
    }

    private fun stopIfIdle() {
        if (active.get() <= 0) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Respuestas en curso", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(title: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ResponseService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Generando respuesta")
            .setContentText(title)
            .setContentIntent(open)
            .addAction(0, "Detener", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_STOP = "github.franco.chat.ai.action.STOP_RESPONSE"
        private const val EXTRA_JOB_ID = "job_id"
        private const val NOTIFICATION_ID = 4201
        private const val CHANNEL_ID = "respuestas_en_curso"

        fun start(context: Context, job: ResponseJob) {
            val id = ResponseJobs.put(job)
            val intent = Intent(context, ResponseService::class.java).putExtra(EXTRA_JOB_ID, id)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(Intent(context, ResponseService::class.java).setAction(ACTION_STOP))
            }
        }
    }
}
