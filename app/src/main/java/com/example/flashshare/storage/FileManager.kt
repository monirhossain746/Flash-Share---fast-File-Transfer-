package com.example.flashshare.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.flashshare.model.DuplicateAction
import com.example.flashshare.model.FileCategory
import com.example.flashshare.model.ShareableFile
import com.example.flashshare.util.Formatters
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DestinationTarget(
  val outputStream: OutputStream,
  val displayUri: String,
  val finalFileName: String,
  val finalizeAction: () -> Unit = {},
  val abortAction: () -> Unit = {},
)

class FileManager(private val context: Context) {

  fun categorizeFile(name: String, mimeType: String?): FileCategory {
    val ext = name.substringAfterLast('.', "").lowercase()
    val mime = (mimeType ?: "").lowercase()
    return when {
      ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg") || mime.startsWith("image/") ->
        FileCategory.IMAGES
      ext in setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "m4v") || mime.startsWith("video/") ->
        FileCategory.VIDEOS
      ext in setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus") || mime.startsWith("audio/") ->
        FileCategory.AUDIO
      ext in setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "csv", "md", "rtf", "odt") ||
        mime.startsWith("text/") || mime == "application/pdf" ->
        FileCategory.DOCUMENTS
      ext in setOf("apk", "xapk", "apks") || mime == "application/vnd.android.package-archive" ->
        FileCategory.APK
      ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso") ->
        FileCategory.ARCHIVES
      else -> FileCategory.OTHER
    }
  }

  suspend fun inspectUri(uri: Uri, relativePathOverride: String? = null): ShareableFile? = withContext(Dispatchers.IO) {
    runCatching {
      var name = "file_${System.currentTimeMillis()}"
      var size = 0L
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (nameIdx >= 0) {
            cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
          }
          if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
            size = cursor.getLong(sizeIdx)
          }
        }
      }
      if (size <= 0L) {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
          if (afd.length > 0) size = afd.length
        }
      }
      val ext = name.substringAfterLast('.', "")
      val mime = context.contentResolver.getType(uri)
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
        ?: "application/octet-stream"
      ShareableFile(
        uri = uri,
        name = Formatters.sanitizeFilename(name),
        extension = ext.uppercase().ifBlank { "FILE" },
        sizeBytes = size,
        mimeType = mime,
        relativePath = Formatters.sanitizeRelativePath(relativePathOverride ?: name),
        category = categorizeFile(name, mime),
      )
    }.getOrNull()
  }

  suspend fun collectFolderFiles(treeUri: Uri): List<ShareableFile> = withContext(Dispatchers.IO) {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
    val rootName = root.name ?: "Folder"
    val results = mutableListOf<ShareableFile>()

    fun walk(dir: DocumentFile, prefix: String) {
      for (child in dir.listFiles()) {
        val childName = child.name ?: continue
        val rel = "$prefix/$childName"
        if (child.isDirectory) {
          walk(child, rel)
        } else if (child.isFile) {
          val size = child.length()
          val ext = childName.substringAfterLast('.', "")
          val mime = child.type
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            ?: "application/octet-stream"
          results += ShareableFile(
            uri = child.uri,
            name = Formatters.sanitizeFilename(childName),
            extension = ext.uppercase().ifBlank { "FILE" },
            sizeBytes = size,
            mimeType = mime,
            relativePath = Formatters.sanitizeRelativePath(rel),
            category = categorizeFile(childName, mime),
          )
        }
      }
    }
    walk(root, rootName)
    results
  }

  suspend fun listReceivedFiles(treeUriString: String?): List<ShareableFile> = withContext(Dispatchers.IO) {
    val result = mutableListOf<ShareableFile>()
    if (!treeUriString.isNullOrBlank()) {
      runCatching {
        val docRoot = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
        docRoot?.listFiles()?.filter { it.isFile }?.forEach { file ->
          val name = file.name ?: return@forEach
          val ext = name.substringAfterLast('.', "")
          val mime = file.type ?: "application/octet-stream"
          result += ShareableFile(
            uri = file.uri,
            name = name,
            extension = ext.uppercase().ifBlank { "FILE" },
            sizeBytes = file.length(),
            mimeType = mime,
            relativePath = name,
            category = categorizeFile(name, mime),
            lastModified = file.lastModified(),
          )
        }
      }
      if (result.isNotEmpty()) return@withContext result.sortedByDescending { it.lastModified }
    }

    // Fallback: inspect app FlashShare directory
    val appDir = getFallbackReceiveDir()
    appDir.listFiles()?.filter { it.isFile }?.forEach { file ->
      val ext = file.name.substringAfterLast('.', "")
      val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
        ?: "application/octet-stream"
      result += ShareableFile(
        uri = Uri.fromFile(file),
        name = file.name,
        extension = ext.uppercase().ifBlank { "FILE" },
        sizeBytes = file.length(),
        mimeType = mime,
        relativePath = file.name,
        category = categorizeFile(file.name, mime),
        lastModified = file.lastModified(),
      )
    }
    result.sortedByDescending { it.lastModified }
  }

  fun getFallbackReceiveDir(): File {
    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "FlashShare")
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  fun getAvailableStorageBytes(): Long {
    val path = context.getExternalFilesDir(null) ?: context.filesDir
    return runCatching {
      val stat = StatFs(path.absolutePath)
      if (stat.totalBytes > 0L) stat.availableBytes
      else path.usableSpace.takeIf { it > 0L } ?: Long.MAX_VALUE
    }.getOrDefault(Long.MAX_VALUE)
  }

  fun hasEnoughStorage(requiredBytes: Long): Boolean {
    if (requiredBytes <= 0L) return true
    val available = getAvailableStorageBytes()
    return available > requiredBytes + (10L * 1024L * 1024L)
  }

  fun findDuplicateNames(treeUriString: String?, fileNames: List<String>): List<String> {
    if (fileNames.isEmpty()) return emptyList()
    val existing = mutableSetOf<String>()
    if (!treeUriString.isNullOrBlank()) {
      runCatching {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
        root?.listFiles()?.mapNotNull { it.name }?.let { existing.addAll(it) }
      }
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      getFallbackReceiveDir().listFiles()?.map { it.name }?.let { existing.addAll(it) }
    }
    return fileNames.filter { Formatters.sanitizeFilename(it) in existing }
  }

  fun openDestinationOutput(
    treeUriString: String?,
    relativePath: String,
    mimeType: String,
    duplicateAction: DuplicateAction,
  ): Result<DestinationTarget> {
    val safeRel = Formatters.sanitizeRelativePath(relativePath)
    val segments = safeRel.split('/')
    val baseName = Formatters.sanitizeFilename(segments.last())

    if (!treeUriString.isNullOrBlank()) {
      val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
      if (root != null && root.canWrite()) {
        var targetDir: DocumentFile = root
        for (dirName in segments.dropLast(1)) {
          val existingDir = targetDir.findFile(dirName)
          targetDir = if (existingDir != null && existingDir.isDirectory) {
            existingDir
          } else {
            targetDir.createDirectory(dirName)
              ?: return Result.failure(IllegalStateException("Unable to create folder $dirName in destination."))
          }
        }
        val existingFile = targetDir.findFile(baseName)
        val resolvedName = when {
          existingFile == null -> baseName
          duplicateAction == DuplicateAction.OVERWRITE -> {
            existingFile.delete()
            baseName
          }
          duplicateAction == DuplicateAction.SKIP ->
            return Result.failure(IllegalStateException("Skipped duplicate file: $baseName"))
          else -> uniqueName(baseName) { candidate -> targetDir.findFile(candidate) != null }
        }
        val docFile = targetDir.createFile(mimeType.ifBlank { "application/octet-stream" }, resolvedName)
          ?: return Result.failure(IllegalStateException("Storage permission denied or cannot create $resolvedName"))
        val out = context.contentResolver.openOutputStream(docFile.uri)
          ?: return Result.failure(IllegalStateException("Failed to open output stream for $resolvedName"))
        return Result.success(
          DestinationTarget(
            outputStream = out,
            displayUri = docFile.uri.toString(),
            finalFileName = resolvedName,
            abortAction = { runCatching { docFile.delete() } },
          ),
        )
      }
    }

    // MediaStore.Downloads on Android 10+ (zero broad storage permissions required)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val subDir = if (segments.size > 1) {
        "${Environment.DIRECTORY_DOWNLOADS}/FlashShare/${segments.dropLast(1).joinToString("/")}"
      } else {
        "${Environment.DIRECTORY_DOWNLOADS}/FlashShare"
      }
      val resolvedName = if (duplicateAction == DuplicateAction.RENAME || duplicateAction == DuplicateAction.ASK) {
        baseName
      } else {
        baseName
      }
      val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, resolvedName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
        put(MediaStore.MediaColumns.RELATIVE_PATH, subDir)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
      }
      val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
      if (uri != null) {
        val out = context.contentResolver.openOutputStream(uri)
        if (out != null) {
          return Result.success(
            DestinationTarget(
              outputStream = out,
              displayUri = uri.toString(),
              finalFileName = resolvedName,
              finalizeAction = {
                val doneValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                runCatching { context.contentResolver.update(uri, doneValues, null, null) }
              },
              abortAction = {
                runCatching { context.contentResolver.delete(uri, null, null) }
              },
            ),
          )
        }
      }
    }

    // Fallback to app-specific external downloads folder
    val baseDir = getFallbackReceiveDir()
    var dir = baseDir
    for (sub in segments.dropLast(1)) {
      dir = File(dir, sub)
      if (!dir.exists()) dir.mkdirs()
    }
    var targetFile = File(dir, baseName)
    if (targetFile.exists()) {
      when (duplicateAction) {
        DuplicateAction.OVERWRITE -> targetFile.delete()
        DuplicateAction.SKIP -> return Result.failure(IllegalStateException("Skipped duplicate file: $baseName"))
        else -> {
          val renamed = uniqueName(baseName) { candidate -> File(dir, candidate).exists() }
          targetFile = File(dir, renamed)
        }
      }
    }
    return Result.success(
      DestinationTarget(
        outputStream = FileOutputStream(targetFile),
        displayUri = Uri.fromFile(targetFile).toString(),
        finalFileName = targetFile.name,
        abortAction = { runCatching { targetFile.delete() } },
      ),
    )
  }

  private fun uniqueName(original: String, exists: (String) -> Boolean): String {
    val dot = original.lastIndexOf('.')
    val stem = if (dot > 0) original.substring(0, dot) else original
    val ext = if (dot > 0) original.substring(dot) else ""
    var idx = 1
    var candidate = "$stem ($idx)$ext"
    while (exists(candidate)) {
      idx++
      candidate = "$stem ($idx)$ext"
    }
    return candidate
  }

  fun openInputStream(uri: Uri): InputStream? {
    return if (uri.scheme == "file") {
      uri.path?.let { File(it).inputStream() }
    } else {
      context.contentResolver.openInputStream(uri)
    }
  }

  suspend fun computeSha256(uri: Uri, bufferBytes: Int = 256 * 1024): String? = withContext(Dispatchers.IO) {
    runCatching {
      val digest = MessageDigest.getInstance("SHA-256")
      val buf = ByteArray(bufferBytes)
      openInputStream(uri)?.use { input ->
        while (true) {
          val read = input.read(buf)
          if (read <= 0) break
          digest.update(buf, 0, read)
        }
      } ?: return@withContext null
      digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
  }

  suspend fun loadThumbnail(file: ShareableFile): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
      when (file.category) {
        FileCategory.IMAGES -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && file.uri.scheme == "content") {
            runCatching { context.contentResolver.loadThumbnail(file.uri, Size(140, 140), null) }.getOrNull()
          } else {
            null
          } ?: run {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val maxDim = maxOf(opts.outWidth, opts.outHeight).coerceAtLeast(1)
            val sample = (maxDim / 140).coerceAtLeast(1)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
          }
        }
        FileCategory.VIDEOS -> {
          val retriever = MediaMetadataRetriever()
          try {
            retriever.setDataSource(context, file.uri)
            retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
          } finally {
            runCatching { retriever.release() }
          }
        }
        else -> null
      }
    }.getOrNull()
  }
}
