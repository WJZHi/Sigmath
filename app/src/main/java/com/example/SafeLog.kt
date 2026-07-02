package com.example

object SafeLog {
    fun d(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (e: Throwable) {
            println("[$tag] [DEBUG] $msg")
        }
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        try {
            android.util.Log.e(tag, msg, tr)
        } catch (e: Throwable) {
            System.err.println("[$tag] [ERROR] $msg")
            tr?.printStackTrace(System.err)
        }
    }
}
