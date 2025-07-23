package io.agora.examples.karaoke_view_ex.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.UUID

object Utils {
    fun copyAssetsToCreateNewFile(context: Context, name: String): File? {
        val assets = context.assets

        if (assets == null || name == null || name.isEmpty()) {
            return null
        }

        val target = File(context.cacheDir, name)
        if (target.exists() && target.isFile) {
            target.delete()
        }

        try {
            var input: InputStream? = null
            var output: OutputStream? = null
            try {
                output = FileOutputStream(target)
                input = assets.open(name)
                copyFile(input, output)
                output.flush()
            } finally {
                input?.close()
                output?.close()
            }
        } catch (e: IOException) {
            return null
        }

        return target
    }

    @Throws(IOException::class)
    private fun copyFile(`in`: InputStream, out: OutputStream) {
        val buffer = ByteArray(1024)
        var read: Int
        while ((`in`.read(buffer).also { read = it }) != -1) {
            out.write(buffer, 0, read)
        }
    }

    fun loadAsString(context: Context, name: String): String? {
        val assets = context.assets ?: return null

        try {
            assets.open(name).use { input ->
                return loadAsString(input)
            }
        } catch (e: IOException) {
            return null
        }
    }

    fun loadAsString(inputStream: InputStream?): String? {
        if (inputStream == null) {
            return null
        }

        try {
            ByteArrayOutputStream().use { result ->
                val buffer = ByteArray(4096)
                var length: Int

                while ((inputStream.read(buffer).also { length = it }) > 0) {
                    result.write(buffer, 0, length)
                }
                return result.toString("UTF-8")
            }
        } catch (e: IOException) {
            return null
        }
    }

    @JvmStatic
    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    @JvmStatic
    fun dp2pix(context: Context, dp: Float): Int {
        val density = context.resources.displayMetrics.scaledDensity
        return (dp * density).toInt()
    }

    @JvmStatic
    fun sp2pix(context: Context, sp: Float): Int {
        val density = context.resources.displayMetrics.scaledDensity
        return (sp * density + 0.5).toInt()
    }

    @JvmStatic
    fun colorInStringToDex(color: String): Int {
        var colorInDex = 0
        colorInDex = when (color) {
            "Yellow" -> Color.YELLOW
            "White" -> Color.WHITE
            "Red" -> Color.RED
            "Gray" -> Color.parseColor("#9E9E9E")
            "Orange" -> Color.parseColor("#FFA500")
            "Blue" -> Color.BLUE
            "Brown" -> Color.parseColor("#654321")
            "Green" -> Color.GREEN
            else -> 0
        }
        return colorInDex
    }

    fun getCurrentDateStr(pattern: String?): String {
        val format = SimpleDateFormat(pattern, Locale.getDefault())
        return format.format(Date())
    }

    fun getRandomString(length: Int): String {
        val str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = Random()
        val sb = StringBuffer()
        for (i in 0 until length) {
            val number = random.nextInt(62)
            sb.append(str[number])
        }
        return sb.toString()
    }

    fun getUuid(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }

}
