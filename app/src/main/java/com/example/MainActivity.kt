package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.MergeHistory
import com.example.data.MergeHistoryRepository
import com.example.ui.MergeViewModel
import com.example.ui.MergeViewModelFactory
import com.example.ui.SelectedPdf
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(this)
        val repository = MergeHistoryRepository(database.mergeHistoryDao())
        val factory = MergeViewModelFactory(repository)

        setContent {
            MyApplicationTheme {
                val viewModel: MergeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
                PdfMergerApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfMergerApp(viewModel: MergeViewModel) {
    val context = LocalContext.current
    
    // Force RTL layout direction for local Arabic aesthetic consistency
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val selectedPdfs by viewModel.selectedPdfs.collectAsStateWithLifecycle()
        val isMerging by viewModel.isMerging.collectAsStateWithLifecycle()
        val mergeSuccess by viewModel.mergeSuccess.collectAsStateWithLifecycle()
        val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
        val mergeHistory by viewModel.mergeHistory.collectAsStateWithLifecycle()

        var customFileName by remember { mutableStateOf("") }

        val pdfPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
            onResult = { uris ->
                if (uris.isNotEmpty()) {
                    viewModel.addPdfs(uris, context)
                }
            }
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { /* App Info or menu */ }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "معلومات عن التطبيق",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        // Main Welcome Header
                        HeroBanner()
                    }

                    // Selected Documents Workspace Label
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "ملفات الدمج الحالية (${selectedPdfs.size})",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (selectedPdfs.isEmpty()) 
                                        stringResource(id = R.string.no_files_selected)
                                    else 
                                        "رتب ملفات الـ PDF بالترتيب المرغوب ثم اضغط دمج",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (selectedPdfs.isEmpty()) {
                        // Empty workspace state
                        item {
                            EmptyWorkspaceCard(
                                onSelectClick = {
                                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                }
                            )
                        }
                    } else {
                        // Selected files list
                        itemsIndexed(
                            items = selectedPdfs,
                            key = { _, item -> item.id }
                        ) { index, pdfItem ->
                            SelectedPdfItemRow(
                                item = pdfItem,
                                index = index,
                                totalCount = selectedPdfs.size,
                                onMoveUp = { viewModel.moveUp(index) },
                                onMoveDown = { viewModel.moveDown(index) },
                                onRemove = { viewModel.removePdf(pdfItem.id) }
                            )
                        }

                        // Add More / Merge action configuration controls
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 600.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Mini button to add more
                                Button(
                                    onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("add_more_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringResource(id = R.string.add_pdf_files)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(id = R.string.add_pdf_files),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Options Form card
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "خيارات الملف المدمج",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        OutlinedTextField(
                                            value = customFileName,
                                            onValueChange = { customFileName = it },
                                            label = { Text("اسم الملف الناتج (مثال: مستنداتي_المدمجة)") },
                                            placeholder = { Text("تلقائي: merged_${System.currentTimeMillis()}.pdf") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("custom_filename_input"),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                cursorColor = MaterialTheme.colorScheme.primary,
                                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        )

                                        Button(
                                            onClick = {
                                                viewModel.mergeSelected(context, customFileName)
                                            },
                                            enabled = selectedPdfs.size >= 2,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .testTag("merge_button"),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Done,
                                                contentDescription = stringResource(id = R.string.merge_files)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(id = R.string.merge_files),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // History Section Header
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.history_title),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (mergeHistory.isEmpty()) {
                        item {
                            EmptyHistoryCard()
                        }
                    } else {
                        itemsIndexed(
                            items = mergeHistory,
                            key = { _, item -> item.id }
                        ) { _, historyItem ->
                            HistoryItemRow(
                                history = historyItem,
                                onOpen = { openPdf(context, historyItem.filePath) },
                                onShare = { sharePdf(context, historyItem.filePath) },
                                onDelete = { viewModel.deleteHistoryItem(historyItem) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }

                // Global merging progress overlay
                if (isMerging) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            modifier = Modifier
                                .padding(32.dp)
                                .fillMaxWidth()
                                .widthIn(max = 300.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 4.dp
                                )
                                Text(
                                    text = "جاري دمج الملفات الآن...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "يرجى الانتظار، نقوم بجمع وتنظيم صفحات الـ PDF معاً.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Success Overlay Dialog
                mergeSuccess?.let { successItem ->
                    Dialog(onDismissRequest = { viewModel.clearSuccessState() }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "تم بنجاح",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Text(
                                    text = stringResource(id = R.string.success_merged),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SuccessDetailRow(label = "اسم الملف :", value = successItem.fileName)
                                    SuccessDetailRow(label = "الحجم الكلي :", value = formatFileSize(successItem.fileSize))
                                    SuccessDetailRow(label = "عدد الملفات المدمجة :", value = "${successItem.filesCount} ملفات")
                                    SuccessDetailRow(label = "وقت وتاريخ الدمج :", value = formatTimestamp(successItem.timestamp))
                                }

                                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Open file
                                    Button(
                                        onClick = {
                                            openPdf(context, successItem.filePath)
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text("فتح الملف")
                                    }
                                    
                                    // Share file
                                    OutlinedButton(
                                        onClick = {
                                            sharePdf(context, successItem.filePath)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("مشاركة")
                                    }
                                }

                                TextButton(
                                    onClick = { viewModel.clearSuccessState() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("إغلاق", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }

                // Error Message Dialog
                errorMessage?.let { errorText ->
                    Dialog(onDismissRequest = { viewModel.clearErrorState() }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete, // Error icon fallback
                                    contentDescription = "تنبيه خطأ",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(36.dp)
                                )

                                Text(
                                    text = "تنبيه",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )

                                Text(
                                    text = errorText,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )

                                Button(
                                    onClick = { viewModel.clearErrorState() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text("حسناً", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeroBanner() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "دامج المستندات",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "أداة ذكية وسريعة لجمع ملفات الـ PDF معاً في ملف واحد دون انترنت وبخصوصية تامة.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add, // Decorative representation of adding together
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun EmptyWorkspaceCard(onSelectClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "ابدأ بدمج ملفاتك",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(id = R.string.select_instructions),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Button(
                onClick = onSelectClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("select_pdf_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.add_pdf_files)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.add_pdf_files),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SelectedPdfItemRow(
    item: SelectedPdf,
    index: Int,
    totalCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stylized container with index
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // File Info details text wrapper
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(item.size),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Controls (Up, Down, Delete buttons)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Move Up arrow
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "تحريك للأعلى",
                        tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                }

                // Move Down arrow
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < totalCount - 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "تحريك للأسفل",
                        tint = if (index < totalCount - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Delete entry
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف الملف",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.history_empty),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun HistoryItemRow(
    history: MergeHistory,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.outline),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share, // PDF history item representation
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = history.fileName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatFileSize(history.fileSize),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "${history.filesCount} ملفات مدمجة",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = formatTimestamp(history.timestamp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // History Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onOpen,
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Done, // represents showing file
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("فتح الملف", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = onShare,
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مشاركة", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف من السجل",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SuccessDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp)
        )
    }
}

// Utility formatting functions
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 ب"
    val units = arrayOf("بايت", "كيلوبايت", "ميغابايت", "غيغابايت")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val formattedValue = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", formattedValue, units[digitGroups])
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale("ar"))
    return sdf.format(Date(timestamp))
}

private fun openPdf(context: Context, filePath: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "الملف لم يعد موجوداً في المساحة التخزينية", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "com.example.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "لا يتوفر تطبيق لعرض مستندات PDF على هذا الهاتف", Toast.LENGTH_LONG).show()
    }
}

private fun sharePdf(context: Context, filePath: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "الملف غير موجود لمشاركته", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "com.example.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الملف عبر:"))
    } catch (e: Exception) {
        Toast.makeText(context, "فشل في مشاركة المستند", Toast.LENGTH_LONG).show()
    }
}
