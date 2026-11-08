package com.chitfund.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

data class StatementRow(
    val month: String,
    val date: String,
    val bid: String,
    val amount: String,
    val status: String
)

/** Builds a simple A4 PDF statement and shares it (WhatsApp, email, ...). */
object PdfStatement {
    private const val W = 595
    private const val H = 842
    private const val M = 40f

    fun create(
        ctx: Context,
        fileName: String,
        heading: String,
        lines: List<String>,
        rows: List<StatementRow>,
        footer: List<String>
    ): File {
        val dir = File(ctx.cacheDir, "statements").apply { mkdirs() }
        val file = File(dir, fileName.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".pdf")

        val title = Paint().apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = Color.parseColor("#0F5132") }
        val body = Paint().apply { textSize = 12f; color = Color.BLACK }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val rule = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        val cols = floatArrayOf(40f, 95f, 190f, 290f, 400f)

        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var y = 0f

        fun newPage(first: Boolean) {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            canvas = page!!.canvas
            y = 50f
            if (first) {
                canvas!!.drawText(heading, M, y, title); y += 26f
                lines.forEach { canvas!!.drawText(it, M, y, body); y += 17f }
                y += 8f
            }
            val c = canvas!!
            val heads = listOf("Month", "Date", "Bid", "Amount", "Status")
            heads.forEachIndexed { i, t -> c.drawText(t, cols[i], y, bold) }
            y += 6f
            c.drawLine(M, y, W - M, y, rule)
            y += 16f
        }

        newPage(true)
        for (r in rows) {
            if (y > H - 80f) newPage(false)
            val c = canvas!!
            c.drawText(r.month, cols[0], y, body)
            c.drawText(r.date, cols[1], y, body)
            c.drawText(r.bid, cols[2], y, body)
            c.drawText(r.amount, cols[3], y, body)
            c.drawText(r.status, cols[4], y, body)
            y += 20f
        }
        if (y > H - 40f - footer.size * 17f) newPage(false)
        val c = canvas!!
        y += 6f
        c.drawLine(M, y, W - M, y, rule)
        y += 18f
        footer.forEach { c.drawText(it, M, y, bold); y += 17f }
        page?.let { doc.finishPage(it) }

        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun share(ctx: Context, file: File, subject: String) {
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "Share statement").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not share the statement", Toast.LENGTH_SHORT).show()
        }
    }
}
