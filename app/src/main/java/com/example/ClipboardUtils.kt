package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

object ClipboardUtils {

    fun sanitizeLatex(raw: String): String {
        var text = raw.trim()
        // Strip common block or inline LaTeX delimiters
        if (text.startsWith("$$") && text.endsWith("$$") && text.length >= 4) {
            text = text.substring(2, text.length - 2).trim()
        } else if (text.startsWith("$") && text.endsWith("$") && text.length >= 2) {
            text = text.substring(1, text.length - 1).trim()
        } else if (text.startsWith("\\[") && text.endsWith("\\]") && text.length >= 4) {
            text = text.substring(2, text.length - 2).trim()
        } else if (text.startsWith("\\(") && text.endsWith("\\)") && text.length >= 4) {
            text = text.substring(2, text.length - 2).trim()
        }
        // Replace carriage returns and newlines with space
        text = text.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
        return text
    }

    fun showCopySelector(context: Context, textToCopy: String, label: String = "LaTeX"): Boolean {
        if (textToCopy.isBlank()) {
            Toast.makeText(context, "没有可复制的内容", Toast.LENGTH_SHORT).show()
            return false
        }
        val activity = context as? androidx.fragment.app.FragmentActivity
        if (activity != null) {
            CopySelectionBottomSheetDialogFragment.newInstance(textToCopy, label)
                .show(activity.supportFragmentManager, CopySelectionBottomSheetDialogFragment.TAG)
            return true
        } else {
            copyToClipboard(context, textToCopy, label)
            return true
        }
    }

    fun copyToClipboard(context: Context, latex: String, label: String = "LaTeX") {
        if (latex.isEmpty()) {
            Toast.makeText(context, "没有可复制的内容", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, latex)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "已复制 LaTeX 算式: $latex", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getClipboardText(context: Context): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val primaryClip = clipboard.primaryClip
            if (primaryClip != null && primaryClip.itemCount > 0) {
                val raw = primaryClip.getItemAt(0).text?.toString()
                if (!raw.isNullOrBlank()) {
                    sanitizeLatex(raw)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun showPasteSelector(context: Context): Boolean {
        val text = getClipboardText(context)
        if (text.isNullOrBlank()) {
            Toast.makeText(context, "剪贴板中没有可粘贴的内容", Toast.LENGTH_SHORT).show()
            return false
        }
        val activity = context as? androidx.fragment.app.FragmentActivity
        if (activity != null) {
            PasteSelectionBottomSheetDialogFragment.newInstance(text)
                .show(activity.supportFragmentManager, PasteSelectionBottomSheetDialogFragment.TAG)
            return true
        }
        return false
    }

    fun pasteFromClipboard(context: Context, currentFieldValue: TextFieldValue): TextFieldValue? {
        val pastedText = getClipboardText(context)
        return if (!pastedText.isNullOrEmpty()) {
            val updated = insertAtCursor(currentFieldValue, pastedText)
            Toast.makeText(context, "已从剪贴板粘贴 LaTeX 算式", Toast.LENGTH_SHORT).show()
            updated
        } else {
            Toast.makeText(context, "剪贴板中没有可粘贴的算式", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun insertAtCursor(fieldValue: TextFieldValue, textToInsert: String): TextFieldValue {
        val text = fieldValue.text
        val selection = fieldValue.selection
        val selStart = minOf(selection.start, selection.end).coerceIn(0, text.length)
        val selEnd = maxOf(selection.start, selection.end).coerceIn(0, text.length)

        val before = text.substring(0, selStart)
        val after = text.substring(selEnd, text.length)
        val newText = before + textToInsert + after
        val newCursorPos = (selStart + textToInsert.length).coerceIn(0, newText.length)

        return TextFieldValue(text = newText, selection = TextRange(newCursorPos))
    }
}
