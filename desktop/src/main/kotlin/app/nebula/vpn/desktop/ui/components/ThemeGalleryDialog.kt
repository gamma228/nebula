package app.nebula.vpn.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.nebula.vpn.core.model.SurfaceStyle
import app.nebula.vpn.core.model.ThemeMode
import app.nebula.vpn.desktop.theme.DesktopThemePreset
import app.nebula.vpn.desktop.theme.NebulaThemePack
import app.nebula.vpn.desktop.theme.ThemePackManager
import app.nebula.vpn.desktop.theme.parseHexColor
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun ThemeGalleryDialog(
    currentPresetId: String,
    onDismiss: () -> Unit,
    onApplyTheme: (NebulaThemePack) -> Unit
) {
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(720.dp).height(560.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Галерея тем Nebula",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Выберите готовый стиль оформления или настройте свой",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val chooser = JFileChooser()
                                chooser.dialogTitle = "Импорт .nebulatheme"
                                chooser.fileFilter = FileNameExtensionFilter("Nebula Theme Files (*.nebulatheme)", "nebulatheme", "json")
                                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    val pack = ThemePackManager.importFromFile(chooser.selectedFile)
                                    onApplyTheme(pack)
                                    statusMessage = "Тема '${pack.name}' успешно импортирована!"
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Импорт", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val chooser = JFileChooser()
                                chooser.dialogTitle = "Экспорт текущей темы"
                                chooser.selectedFile = File("custom.nebulatheme")
                                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    val currentPack = ThemePackManager.findById(currentPresetId)
                                    ThemePackManager.exportToFile(currentPack, chooser.selectedFile)
                                    statusMessage = "Файл сохранен: ${chooser.selectedFile.name}"
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Экспорт", fontSize = 12.sp)
                        }
                    }
                }

                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusMessage!!,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Grid of 8 Built-in Themes
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(ThemePackManager.BUILTIN_PACKS) { pack: NebulaThemePack ->
                        val isSelected = currentPresetId.equals(pack.id, ignoreCase = true)
                        val primaryColor = parseHexColor(pack.seedHex)
                        val secondaryColor = parseHexColor(pack.secondaryHex)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onApplyTheme(pack) },
                            shape = RoundedCornerShape(pack.cornerRadiusDp.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (pack.mode == "AMOLED") Color(0xFF0A0A0A)
                                else if (pack.mode == "LIGHT") Color(0xFFF1F5F9)
                                else Color(0xFF1E232E)
                            ),
                            border = if (isSelected) {
                                CardDefaults.outlinedCardBorder().copy(
                                    width = 2.dp,
                                    brush = androidx.compose.ui.graphics.SolidColor(primaryColor)
                                )
                            } else CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = pack.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (pack.mode == "LIGHT") Color(0xFF0F172A) else Color.White
                                    )

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(primaryColor, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = pack.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (pack.mode == "LIGHT") Color(0xFF475569) else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Chips row (Primary color + Secondary color + Style tags)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(primaryColor, CircleShape)
                                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(secondaryColor, CircleShape)
                                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (pack.mode == "LIGHT") Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = pack.mode,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (pack.mode == "LIGHT") Color(0xFF0F172A) else Color(0xFFF1F5F9),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (pack.mode == "LIGHT") Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = pack.surfaceStyle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (pack.mode == "LIGHT") Color(0xFF0F172A) else Color(0xFFF1F5F9),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }
}
