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
    val size: Long
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
            _errorMessage.value = "الرجاء اختيار ملفي بي دي اف على الأقل للدمج"
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
                    
                    val outputDir = File(context.filesDir, "merged_pdfs")
                    if (!outputDir.exists()) {
                        outputDir.mkdirs()
                    }
                    val outputFile = File(outputDir, formattedName)
                    merger.destinationFileName = outputFile.absolutePath
                    
                    val tempFiles = mutableListOf<File>()
                    
                    // Copy content streams to local cache files to avoid permission lifetime issues
                    for (item in pdfs) {
                        try {
                            val tempFile = File(context.cacheDir, "temp_merge_${UUID.randomUUID()}_${item.name}")
                            context.contentResolver.openInputStream(item.uri)?.use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (tempFile.exists() && tempFile.length() > 0) {
                                tempFiles.add(tempFile)
                                merger.addSource(tempFile)
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }

                    if (tempFiles.size < 2) {
                        throw Exception("فشل في قراءة ملفات الـ PDF المحددة")
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
                _errorMessage.value = "حدث خطأ أثناء دمج الملفات. يرجى التحقق من صحة ملفات الـ PDF."
            }
        }
    }

    private fun getPdfMetadata(context: Context, uri: Uri): SelectedPdf {
        var name = "file_${System.currentTimeMillis()}.pdf"
        var size = 0L
        try {
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
        if (!name.lowercase().endsWith(".pdf")) {
            name = "$name.pdf"
        }
        return SelectedPdf(uri = uri, name = name, size = size)
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
