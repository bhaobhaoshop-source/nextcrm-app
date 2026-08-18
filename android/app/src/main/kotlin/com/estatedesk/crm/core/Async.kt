package com.estatedesk.crm.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Lightweight async helpers. DB work is serialized on one thread to keep
 *  SQLite access safe and consistent without coroutines. */
object Async {
    val db: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "estatedesk-db").apply { priority = Thread.NORM_PRIORITY }
    }
    val io: ExecutorService = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "estatedesk-io").apply { priority = Thread.NORM_PRIORITY }
    }
    private val main = Handler(Looper.getMainLooper())

    /** Run [call] on the DB thread, deliver result on the main thread. */
    fun <T> db(call: () -> T, then: (T) -> Unit) {
        db.execute {
            val r = try {
                call()
            } catch (t: Throwable) {
                null
            }
            main.post { then(r as T) }
        }
    }

    /** Run [call] on the DB thread and post [onOk] on success. */
    fun write(call: () -> Unit, onOk: (() -> Unit)? = null, onErr: (() -> Unit)? = null) {
        db.execute {
            var ok = false
            try {
                call(); ok = true
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            main.post { if (ok) onOk?.invoke() else onErr?.invoke() }
        }
    }

    fun <T> io(call: () -> T, then: (T) -> Unit) {
        io.execute {
            val r = try {
                call()
            } catch (t: Throwable) {
                null
            }
            main.post { then(r as T) }
        }
    }

    fun ui(delayMs: Long = 0, fn: () -> Unit) {
        if (delayMs > 0) main.postDelayed(fn, delayMs) else main.post(fn)
    }
}
