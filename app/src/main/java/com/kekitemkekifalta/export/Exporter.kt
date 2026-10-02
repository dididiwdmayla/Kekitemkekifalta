package com.kekitemkekifalta.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.kekitemkekifalta.AppContainer
import com.kekitemkekifalta.core.QuantityFormat
import com.kekitemkekifalta.core.RouteGrouping
import com.kekitemkekifalta.core.Sector
import com.kekitemkekifalta.core.ShoppingListText
import com.kekitemkekifalta.data.isNeed
import com.kekitemkekifalta.data.sectorEnum
import com.kekitemkekifalta.data.toSpec
import com.kekitemkekifalta.ui.common.findActivity
import com.kekitemkekifalta.ui.common.formatDateFull
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** The kekifalta list ready to be shared, grouped by the market route or by sector. */
data class ExportList(val title: String, val subtitle: String, val sections: List<ShoppingListText.Section>) {
    val text: String get() = ShoppingListText.format(title, subtitle, sections)
    val count: Int get() = sections.sumOf { it.lines.size }
}

suspend fun AppContainer.buildExportList(marketId: String?): ExportList {
    val need = items.observeItems().first().filter { it.isNeed }
    val date = formatDateFull(System.currentTimeMillis())
    val market = marketId?.let { id -> markets.observeMarkets().first().firstOrNull { it.id == id } }
    if (market == null) {
        val sections = RouteGrouping.bySector(need, { it.sectorEnum }, { it.normalizedName }).map { (sector, list) ->
            ShoppingListText.Section("${sector.emoji} ${sector.label}", list.map { ShoppingListText.Line(it.name, QuantityFormat.format(it.quantity, it.unit), it.note) })
        }
        return ExportList("kekifalta", date, sections)
    }
    val aisles = markets.observeAisles(market.id).first()
    val notes = markets.observeShelfNotes(market.id).first().associateBy { it.itemId }
    val groups = RouteGrouping.group(
        aisles.map { it.toSpec() },
        need,
        sectorOf = { it.sectorEnum },
        overrideAisleOf = { notes[it.id]?.aisleId },
        sortKey = { it.normalizedName },
    )
    val sections = groups.filter { it.items.isNotEmpty() }.map { group ->
        val title = group.aisle?.let { aisle -> aisle.name + sectorHint(aisle.name, aisle.sectors) } ?: "Sem corredor"
        ShoppingListText.Section(
            title,
            group.items.map { item ->
                val shelf = notes[item.id]?.note?.takeIf { it.isNotBlank() }
                ShoppingListText.Line(item.name, QuantityFormat.format(item.quantity, item.unit), listOfNotNull(shelf, item.note).joinToString(" · ").ifBlank { null })
            },
        )
    }
    return ExportList("kekifalta · ${market.name}", date, sections)
}

/** "Corredor 3 (Mercearia)" unless the name already says it. */
private fun sectorHint(name: String, sectors: List<Sector>): String {
    if (sectors.isEmpty()) return ""
    if (sectors.size == 1 && sectors.first().label.equals(name, ignoreCase = true)) return ""
    return " (" + sectors.joinToString(", ") { it.label } + ")"
}

object Exporter {
    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Mandar a lista"))
    }

    fun copy(context: Context, text: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("kekifalta", text))
    }

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Mandar o PDF"))
    }

    fun printPdf(context: Context, file: File) {
        val activity = context.findActivity() ?: return
        val manager = activity.getSystemService(PrintManager::class.java) ?: return
        manager.print("kekifalta", FilePrintAdapter(file), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }

    /** A4, two columns, checkbox per item. Spills to extra pages only if the list is huge. */
    fun writePdf(context: Context, list: ExportList): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "kekifalta.pdf")
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val gutter = 24f
        val columnWidth = (pageWidth - margin * 2 - gutter) / 2
        val ink = Color.rgb(42, 30, 20)
        val muted = Color.rgb(122, 104, 87)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 22f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 11f }
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 13f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val itemPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 12f }
        val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 9.5f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC) }
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; style = Paint.Style.STROKE; strokeWidth = 1.2f }
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; strokeWidth = 1.5f }

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var top = margin
        var column = 0

        canvas.drawText(list.title, margin, top + 20f, titlePaint)
        canvas.drawText("${list.subtitle} · ${list.count} ${if (list.count == 1) "item" else "itens"}", margin, top + 38f, subPaint)
        canvas.drawLine(margin, top + 48f, pageWidth - margin, top + 48f, rulePaint)
        top += 66f
        var y = top
        val bottom = pageHeight - margin

        fun x() = margin + column * (columnWidth + gutter)
        fun ensure(space: Float) {
            if (y + space <= bottom) return
            if (column == 0) {
                column = 1
            } else {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                column = 0
                top = margin
            }
            y = top
        }

        if (list.sections.isEmpty()) {
            canvas.drawText("Nada faltando!", margin, y + 14f, itemPaint)
        }
        list.sections.forEach { section ->
            ensure(42f)
            val header = TextUtils.ellipsize(section.title, headerPaint, columnWidth, TextUtils.TruncateAt.END).toString()
            canvas.drawText(header, x(), y + 13f, headerPaint)
            y += 22f
            section.lines.forEach { line ->
                val note = line.note
                ensure(if (note != null) 30f else 19f)
                val boxTop = y + 2f
                canvas.drawRoundRect(x(), boxTop, x() + 10f, boxTop + 10f, 2f, 2f, boxPaint)
                val label = line.name + (line.quantity?.let { " ($it)" } ?: "")
                val text = TextUtils.ellipsize(label, itemPaint, columnWidth - 18f, TextUtils.TruncateAt.END).toString()
                canvas.drawText(text, x() + 17f, y + 11f, itemPaint)
                y += 17f
                if (note != null) {
                    val noteText = TextUtils.ellipsize(note, notePaint, columnWidth - 18f, TextUtils.TruncateAt.END).toString()
                    canvas.drawText(noteText, x() + 17f, y + 8f, notePaint)
                    y += 12f
                }
                y += 2f
            }
            y += 10f
        }
        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }
}

private class FilePrintAdapter(private val file: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}
