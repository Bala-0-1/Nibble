package com.nibble.app.core.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class)
actual suspend fun httpPost(url: String, body: String, contentType: String): HttpTextResponse =
    suspendCancellableCoroutine { cont ->
        val request = NSMutableURLRequest(uRL = NSURL(string = url)).apply {
            setHTTPMethod("POST")
            setValue(contentType, forHTTPHeaderField = "Content-Type")
            @Suppress("CAST_NEVER_SUCCEEDS")
            setHTTPBody((body as NSString).dataUsingEncoding(NSUTF8StringEncoding))
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data: NSData?, response, error ->
            when {
                error != null -> cont.resumeWithException(HttpUnreachable(error.localizedDescription))
                else -> {
                    val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
                    val text = data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() } ?: ""
                    cont.resume(HttpTextResponse(status, text))
                }
            }
        }
        cont.invokeOnCancellation { task.cancel() }
        task.resume()
    }
