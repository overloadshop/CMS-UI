package com.lindicra.cms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lindicra.cms.ui.theme.*

enum class CMSTextFieldSize(val height: androidx.compose.ui.unit.Dp, val horizontalPadding: androidx.compose.ui.unit.Dp,
                            val fontSize: androidx.compose.ui.unit.TextUnit) {
    Large(52.dp, 16.dp, 16.sp),
    Medium(44.dp, 16.dp, 15.sp),
    Small(36.dp, 12.dp, 14.sp)
}

enum class CMSTextFieldVariant {
    Plain, Search, Numeric, Password, Code, Stepper
}

/**
 * CLEAR 标准输入框：默认、聚焦、已填、错误、禁用、成功六种状态。
 * 由 BasicTextField 承载实际输入，避免外层状态回写时丢失焦点或光标。
 */
@Composable
fun CMSTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    isSuccess: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    size: CMSTextFieldSize = CMSTextFieldSize.Medium,
    variant: CMSTextFieldVariant = CMSTextFieldVariant.Plain,
    prefixText: String? = null,
    suffixText: String? = null,
    showClearButton: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingContent: (@Composable (() -> Unit))? = null,
    trailingContent: (@Composable (() -> Unit))? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (size == CMSTextFieldSize.Large) 16.dp else 12.dp)
    val accent = LocalCMSAccentColor.current
    val borderColor = when {
        !enabled -> Color.Transparent
        isError -> Color(0xFFFF5B68)
        isSuccess -> Color(0xFF50C878)
        focused -> accent
        else -> Color.Transparent
    }
    val background = when {
        !enabled -> TertiaryFill.copy(alpha = 0.45f)
        isError -> TertiaryFill.copy(alpha = 0.92f)
        isSuccess -> TertiaryFill.copy(alpha = 0.92f)
        else -> TertiaryFill
    }
    val resolvedKeyboardOptions = when (variant) {
        CMSTextFieldVariant.Search -> keyboardOptions.copy(keyboardType = KeyboardType.Text)
        CMSTextFieldVariant.Numeric, CMSTextFieldVariant.Stepper ->
            keyboardOptions.copy(keyboardType = KeyboardType.Decimal)
        CMSTextFieldVariant.Password ->
            keyboardOptions.copy(keyboardType = KeyboardType.Password)
        CMSTextFieldVariant.Code ->
            keyboardOptions.copy(keyboardType = KeyboardType.Ascii)
        CMSTextFieldVariant.Plain -> keyboardOptions
    }
    val resolvedVisualTransformation =
        if (variant == CMSTextFieldVariant.Password &&
            visualTransformation == VisualTransformation.None
        ) PasswordVisualTransformation() else visualTransformation
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        label?.let {
            Text(it, color = if (enabled) SecondaryText else TertiaryText,
                fontSize = 12.sp, fontFamily = HarmonySansSC, style = CMSNoFontPadding)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            maxLines = maxLines.coerceAtLeast(1),
            keyboardOptions = resolvedKeyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = resolvedVisualTransformation,
            textStyle = TextStyle(
                color = if (enabled) PrimaryText else TertiaryText,
                fontSize = size.fontSize,
                fontFamily = HarmonySansSC,
                fontWeight = FontWeight.Normal
            ),
            cursorBrush = SolidColor(accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = size.height)
                .shadow(
                    elevation = if (focused && enabled && !isError) 3.dp else 0.dp,
                    shape = shape,
                    ambientColor = accent.copy(alpha = 0.22f),
                    spotColor = accent.copy(alpha = 0.18f)
                )
                .background(background, shape)
                .border(1.dp, borderColor, shape)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = size.height)
                        .padding(horizontal = size.horizontalPadding),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    if (!prefixText.isNullOrEmpty()) {
                        Text(prefixText, color = SecondaryText, fontSize = 13.sp,
                            fontFamily = HarmonySansSC, style = CMSNoFontPadding)
                        Spacer(Modifier.width(6.dp))
                    }
                    leadingContent?.invoke()
                    if (leadingContent != null) Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, color = TertiaryText, fontSize = size.fontSize,
                                fontFamily = HarmonySansSC, style = CMSNoFontPadding)
                        }
                        inner()
                    }
                    if (trailingContent != null) {
                        Spacer(Modifier.width(8.dp))
                        trailingContent()
                    }
                    if (!suffixText.isNullOrEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text(suffixText, color = SecondaryText, fontSize = 13.sp,
                            fontFamily = HarmonySansSC, style = CMSNoFontPadding)
                    }
                    if (showClearButton && value.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text("×", color = SecondaryText, fontSize = 18.sp,
                            modifier = Modifier.clickable { onValueChange("") })
                    }
                    if (variant == CMSTextFieldVariant.Stepper) {
                        Spacer(Modifier.width(6.dp))
                        Text("−", color = SecondaryText, fontSize = 18.sp,
                            modifier = Modifier.clickable {
                                onValueChange(stepTextValue(value, -1))
                            })
                        Spacer(Modifier.width(6.dp))
                        Text("+", color = SecondaryText, fontSize = 18.sp,
                            modifier = Modifier.clickable {
                                onValueChange(stepTextValue(value, 1))
                            })
                    }
                }
            }
        )
        supportingText?.let {
            Text(it, color = when {
                isError -> Color(0xFFFF5B68)
                isSuccess -> Color(0xFF50C878)
                else -> SecondaryText
            }, fontSize = 12.sp, fontFamily = HarmonySansSC, style = CMSNoFontPadding)
        }
    }
}

private fun stepTextValue(value: String, delta: Int): String {
    val number = value.toDoubleOrNull() ?: 0.0
    val next = number + delta
    return if (next % 1.0 == 0.0) next.toLong().toString() else next.toString()
}
