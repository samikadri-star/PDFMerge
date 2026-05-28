package com.example.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.MergeHistory
import com.example.data.MergeHistoryRepository
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class SelectedPdf(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val size: Long,
    val isImage: Boolean = false
)

class MergeViewModel(private val repository: MergeHistoryRepository) : ViewModel() {

    private val _selectedPdfs = MutableStateFlow<List<SelectedPdf>>(emptyList())
    val selectedPdfs: StateFlow<List<SelectedPdf>> = _selectedPdfs

    private val _isMerging = MutableStateFlow(false)
    val isMerging: StateFlow<Boolean> = _isMerging

    private val _mergeSuccess = MutableStateFlow<MergeHistory?>(null)
    val mergeSuccess: StateFlow<MergeHistory?> = _mergeSuccess

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val mergeHistory: StateFlow<List<MergeHistory>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addPdfs(uris: List<Uri>, context: Context) {
        viewModelScope.launch {
            val newList = _selectedPdfs.value.toMutableList()
            for (uri in uris) {
                val metadata = getPdfMetadata(context, uri)
                newList.add(metadata)
            }
            _selectedPdfs.value = newList
        }
    }

    fun removePdf(id: String) {
        _selectedPdfs.value = _selectedPdfs.value.filter { it.id != id }
    }

    fun moveUp(index: Int) {
        if (index <= 0 || index >= _selectedPdfs.value.size) return
        val list = _selectedPdfs.value.toMutableList()
        val temp = list[index]
        list[index] = list[index - 1]
        list[index - 1] = temp
        _selectedPdfs.value = list
    }

    fun moveDown(index: Int) {
        if (index < 0 || index >= _selectedPdfs.value.size - 1) return
        val list = _selectedPdfs.value.toMutableList()
        val temp = list[index]
        list[index] = list[index + 1]
        list[index + 1] = temp
        _selectedPdfs.value = list
    }

    fun clearSelected() {
        _selectedPdfs.value = emptyList()
    }

    fun clearSuccessState() {
        _mergeSuccess.value = null
    }

    fun clearErrorState() {
        _errorMessage.value = null
    }

    fun deleteHistoryItem(history: MergeHistory) {
        viewModelScope.launch {
            repository.deleteById(history.id)
            // Also try to delete the filesystem file internally to free up storage
            try {
                val file = File(history.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun mergeSelected(context: Context, customName: String) {
        val pdfs = _selectedPdfs.value
        if (pdfs.size < 2) {
            _errorMessage.value = "الرجاء اختيار ملفين على الأقل للدمج"
            return
        }

        val formattedName = if (customName.trim().isEmpty()) {
            "merged_${System.currentTimeMillis()}.pdf"
        } else {
            val sanitized = customName.trim().replace(Regex("[^a-zA-Z0-9_\\-\\s\\u0600-\\u06FF]"), "")
            if (sanitized.lowercase().endsWith(".pdf")) sanitized else "$sanitized.pdf"
        }

        _isMerging.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val resultFile = withContext(Dispatchers.IO) {
                try {
                    val merger = PDFMergerUtility()
                    
                    // Choose the user-accessible storage folder named "الملفات_المدموجه" in the primary storage
                    var outputDir: File? = null
                    val candidates = mutableListOf<File>()

                    // Candidate 1: Main internal storage root "الملفات_المدموجه" directly (works on Android 9/Legacy or if system permits)
                    try {
                        val externalStorage = android.os.Environment.getExternalStorageDirectory()
                        if (externalStorage != null) {
                            candidates.add(File(externalStorage, "الملفات_المدموجه"))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Candidate 2: "الملفات_المدموجه" inside Documents (the standard modern accessible location in main storage)
                    try {
                        val docsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
                        if (docsDir != null) {
                            candidates.add(File(docsDir, "الملفات_المدموجه"))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Candidate 3: "الملفات_المدموجه" inside Downloads (standard modern accessible location in main storage)
                    try {
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        if (downloadsDir != null) {
                            candidates.add(File(downloadsDir, "الملفات_المدموجه"))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Candidate 4: External files folder of the app
                    try {
                        val extFilesDir = context.getExternalFilesDir(null)
                        if (extFilesDir != null) {
                            candidates.add(File(extFilesDir, "الملفات_المدموجه"))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Find first candidate we can create and write to
                    for (candidate in candidates) {
                        try {
                            var canUse = false
                            if (!candidate.exists()) {
                                val success = candidate.mkdirs()
                                if (success || candidate.exists()) {
                                    canUse = true
                                }
                            } else {
                                canUse = true
                            }
                            if (canUse) {
                                // Try creating a dummy test file to verify write access
                                val testFile = File(candidate, ".test_write_" + System.currentTimeMillis())
                                if (testFile.createNewFile()) {
                                    testFile.delete()
                                    outputDir = candidate
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (outputDir == null) {
                        // Fallback to app's internal filesDir
                        outputDir = File(context.filesDir, "الملفات_المدموجه")
                        if (!outputDir.exists()) {
                            outputDir.mkdirs()
                        }
                    }

                    val outputFile = File(outputDir, formattedName)
                    merger.destinationFileName = outputFile.absolutePath
                    
                    val tempFiles = mutableListOf<File>()
                    
                    // Copy content streams or convert image to local cache files
                    for (item in pdfs) {
                        try {
                            val tempFile = File(context.cacheDir, "temp_merge_${UUID.randomUUID()}_${item.name}")
                            if (item.isImage) {
                                val success = convertImageToPdf(context, item.uri, tempFile)
                                if (success && tempFile.exists() && tempFile.length() > 0) {
                                    tempFiles.add(tempFile)
                                    merger.addSource(tempFile)
                                }
                            } else {
                                context.contentResolver.openInputStream(item.uri)?.use { input ->
                                    tempFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                if (tempFile.exists() && tempFile.length() > 0) {
                                    tempFiles.add(tempFile)
                                    merger.addSource(tempFile)
                                }
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }

                    if (tempFiles.size < 2) {
                        throw Exception("فشل في معالجة الملفات المحددة")
                    }

                    // Perform pdfBox merge
                    merger.mergeDocuments(null)

                    // Clean up temp cache files
                    for (tempFile in tempFiles) {
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }
                    }

                    if (outputFile.exists() && outputFile.length() > 0) {
                        outputFile
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            _isMerging.value = false

            if (resultFile != null) {
                val historyRecord = MergeHistory(
                    fileName = resultFile.name,
                    filePath = resultFile.absolutePath,
                    fileSize = resultFile.length(),
                    filesCount = pdfs.size
                )
                
                val insertedId = repository.insert(historyRecord)
                _mergeSuccess.value = historyRecord.copy(id = insertedId.toInt())
                _selectedPdfs.value = emptyList() // clear draft on success
            } else {
                _errorMessage.value = "حدث خطأ أثناء دمج الملفات. يرجى التحقق من صحة الملفات أو الصور المحددة."
            }
        }
    }

    private fun convertImageToPdf(context: Context, uri: Uri, outputFile: File): Boolean {
        var inputStream: java.io.InputStream? = null
        var bitmap: android.graphics.Bitmap? = null
        var pdfDoc: android.graphics.pdf.PdfDocument? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            if (bitmap == null) return false
            
            pdfDoc = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDoc.finishPage(page)
            
            outputFile.outputStream().use { fos ->
                pdfDoc.writeTo(fos)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try { inputStream?.close() } catch (ex: Exception) {}
            try { bitmap?.recycle() } catch (ex: Exception) {}
            try { pdfDoc?.close() } catch (ex: Exception) {}
        }
    }

    private fun getPdfMetadata(context: Context, uri: Uri): SelectedPdf {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L
        var isImg = false
        try {
            val mimeType = context.contentResolver.getType(uri)
            isImg = mimeType?.startsWith("image/") ?: false
            
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        val retrievedName = cursor.getString(nameIndex)
                        if (!retrievedName.isNullOrEmpty()) {
                            name = retrievedName
                        }
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (!isImg && !name.lowercase().endsWith(".pdf")) {
            name = "$name.pdf"
        }
        return SelectedPdf(uri = uri, name = name, size = size, isImage = isImg)
    }
}

class MergeViewModelFactory(private val repository: MergeHistoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MergeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MergeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
