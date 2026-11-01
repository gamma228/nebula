package app.nebula.vpn.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.desktop.i18n.AppLanguage
import app.nebula.vpn.desktop.i18n.DesktopStrings
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun QrCodeDialog(
    profile: ProxyProfile,
    language: AppLanguage,
    onDismiss: () -> Unit
) {
    val shareLink = remember(profile) {
        profile.toShareLink()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = DesktopStrings.get("share_qr_title", language),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = profile.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = DesktopStrings.get("share_qr_sub", language),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))

                // QR Code Canvas with white background
                Box(
                    modifier = Modifier
                        .size(230.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    QrCodeRenderer(text = shareLink, size = 210.dp)
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                StringSelection(shareLink),
                                null
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(DesktopStrings.get("copy_link", language), fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            DesktopStrings.get("btn_cancel", language),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCodeRenderer(text: String, size: Dp) {
    val bitMatrix = remember(text) {
        try {
            QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                256,
                256,
                mapOf(EncodeHintType.MARGIN to 1)
            )
        } catch (_: Exception) {
            null
        }
    }

    Canvas(modifier = Modifier.size(size)) {
        if (bitMatrix != null) {
            val cellW = this.size.width / bitMatrix.width
            val cellH = this.size.height / bitMatrix.height
            for (y in 0 until bitMatrix.height) {
                for (x in 0 until bitMatrix.width) {
                    if (bitMatrix.get(x, y)) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(x * cellW, y * cellH),
                            size = Size(cellW, cellH)
                        )
                    }
                }
            }
        }
    }
}
