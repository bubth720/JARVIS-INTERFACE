package com.jarvis.interfaceapp.r1

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Test-only observations at the original call site. No clicks, retries, mutation or alternate selection. */
object PickerWitness {
    private var last=0L
    fun fixture(context: Context, uri: Uri, name: String) {
        val bytes=context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val r=JSONObject().put("event","FIXTURE_BEFORE_PICKER").put("elapsed_nanos",SystemClock.elapsedRealtimeNanos())
            .put("name",name).put("uri",uri.toString()).put("bytes",bytes.size)
            .put("sha256",MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.IS_PENDING),null,null,null)?.use { c ->
            if(c.moveToFirst()) r.put("display_name",c.getString(0)).put("reported_size",c.getLong(1)).put("is_pending",c.getInt(2))
        }
        write(context,r);last=0
    }
    fun window(context: Context, root: AccessibilityNodeInfo?) {
        val now=SystemClock.elapsedRealtime();if(now-last<1000)return;last=now
        val nodes=JSONArray()
        fun visit(n: AccessibilityNodeInfo?,depth: Int) {
            if(n==null || depth>14 || nodes.length()>=100)return
            val bounds=android.graphics.Rect();n.getBoundsInScreen(bounds)
            nodes.put(JSONObject().put("package",n.packageName?.toString()).put("resource",n.viewIdResourceName)
                .put("text",n.text?.toString()?.take(160)).put("description",n.contentDescription?.toString()?.take(160))
                .put("clickable",n.isClickable).put("bounds",bounds.toShortString()))
            for(i in 0 until n.childCount)visit(n.getChild(i),depth+1)
        }
        visit(root,0);write(context,JSONObject().put("event","PICKER_WINDOW").put("elapsed_nanos",SystemClock.elapsedRealtimeNanos()).put("nodes",nodes))
    }
    private fun write(context: Context,row: JSONObject) {
        check(context.packageName in setOf("fr.jarvis.v1.lab","fr.jarvis.v1.lab.baseline"))
        val dir=File(context.filesDir,"m51-r1").apply { mkdirs() };val out=File(dir,"picker.jsonl")
        if(out.length()<131072)out.appendText(row.toString()+"\n")
    }
}
