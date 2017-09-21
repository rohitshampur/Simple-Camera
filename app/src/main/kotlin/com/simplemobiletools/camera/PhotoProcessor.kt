package com.simplemobiletools.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.AsyncTask
import android.os.Environment
import com.simplemobiletools.camera.activities.MainActivity
import com.simplemobiletools.camera.extensions.compensateDeviceRotation
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.extensions.getOutputMediaFile
import com.simplemobiletools.camera.extensions.getPreviewRotation
import com.simplemobiletools.commons.extensions.*
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class PhotoProcessor(val activity: MainActivity, val uri: Uri?, val currCameraId: Int, val deviceOrientation: Int) : AsyncTask<ByteArray, Void, String>() {

    override fun doInBackground(vararg params: ByteArray): String {
        var fos: OutputStream? = null
        val path: String
        try {
            path = if (uri != null) {
                uri.path
            } else {
                activity.getOutputMediaFile(true)
            }

            if (path.isEmpty()) {
                return ""
            }

            val data = params[0]
            val photoFile = File(path)
            if (activity.needsStupidWritePermissions(path)) {
                if (activity.config.treeUri.isEmpty()) {
                    activity.toast(R.string.save_error_internal_storage)
                    activity.config.savePhotosFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()
                    return ""
                }
                var document = activity.getFileDocument(path)
                document = document?.createFile("", path.substring(path.lastIndexOf('/') + 1))
                fos = activity.contentResolver.openOutputStream(document?.uri)
            } else {
                fos = if (uri == null) {
                    FileOutputStream(photoFile)
                } else {
                    activity.contentResolver.openOutputStream(uri)
                }
            }

            var image = BitmapFactory.decodeByteArray(data, 0, data.size)
            val exif = ExifInterface(photoFile.toString())

            val deviceRot = deviceOrientation.compensateDeviceRotation(currCameraId)
            val previewRot = activity.getPreviewRotation(currCameraId)
            val imageRot = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            val totalRotation = (imageRot + deviceRot + previewRot) % 360
            val fileExif = ExifInterface(path)
            var exifOrientation = ExifInterface.ORIENTATION_NORMAL.toString()
            if (path.startsWith(activity.internalStoragePath)) {
                exifOrientation = getExifOrientation(totalRotation)
            } else {
                image = rotate(image, totalRotation)
            }

            if (image != null) {
                image.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                fos?.close()
            }

            fileExif.setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation)
            fileExif.saveAttributes()
            return photoFile.absolutePath
        } catch (e: Exception) {
            activity.showErrorToast(e)
        } finally {
            fos?.close()
        }

        return ""
    }

    private fun getExifOrientation(degrees: Int): String {
        return when (degrees) {
            90 -> ExifInterface.ORIENTATION_ROTATE_90
            180 -> ExifInterface.ORIENTATION_ROTATE_180
            270 -> ExifInterface.ORIENTATION_ROTATE_270
            else -> ExifInterface.ORIENTATION_NORMAL
        }.toString()
    }

    private fun rotate(bitmap: Bitmap, degree: Int): Bitmap? {
        if (degree == 0)
            return bitmap

        val width = bitmap.width
        val height = bitmap.height

        val matrix = Matrix()
        matrix.setRotate(degree.toFloat())

        try {
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        } catch (e: OutOfMemoryError) {
            activity.showErrorToast(e.toString())
        }
        return null
    }

    override fun onPostExecute(path: String) {
        super.onPostExecute(path)
        activity.mediaSaved(path)
    }

    interface MediaSavedListener {
        fun mediaSaved(path: String)
    }
}
