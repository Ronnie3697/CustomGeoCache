package com.customgeocache.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/**
 * Programmaticky generované ikony kešek (kruh s písmenem) — barvy odpovídají
 * typu v geocaching.com mapě.
 *
 * Použití: zaregistruj všechny ikony do stylu přes `Style.addImage(id, bitmap)`,
 * pak na SymbolOptions volej `.withIconImage(id)`.
 */
object MapMarkerIcons {

    /** ID ikon zaregistrovaných ve Style. Musí odpovídat typu cache name v DB. */
    object Id {
        const val TRADITIONAL = "icon-traditional"
        const val MULTI = "icon-multi"
        const val MYSTERY = "icon-mystery"
        const val EARTH = "icon-earth"
        const val EVENT = "icon-event"
        const val VIRTUAL = "icon-virtual"
        const val WHERIGO = "icon-wherigo"
        const val LETTERBOX = "icon-letterbox"
        const val DEFAULT = "icon-default"

        // Decoration overlays (smajlík = found, disketka = offline)
        const val DECOR_FOUND = "decor-found"
        const val DECOR_OFFLINE = "decor-offline"

        fun forCacheType(type: String): String = when {
            type.startsWith("Trad") -> TRADITIONAL
            type.startsWith("Multi") -> MULTI
            type.startsWith("Mystery") -> MYSTERY
            type.startsWith("Earth") -> EARTH
            type.contains("Event") -> EVENT
            type.startsWith("Virtual") -> VIRTUAL
            type.startsWith("Wherigo") -> WHERIGO
            type.startsWith("Letterbox") -> LETTERBOX
            else -> DEFAULT
        }
    }

    /** Vrátí mapu (id -> bitmap) všech ikon. Volat jednou při startu / po style reload. */
    fun buildAll(): Map<String, Bitmap> = mapOf(
        Id.TRADITIONAL to createPinIcon("T", 0xFF2E7D32.toInt()),     // zelená
        Id.MULTI       to createPinIcon("M", 0xFFFF8F00.toInt()),     // oranžová
        Id.MYSTERY     to createPinIcon("?", 0xFF1976D2.toInt()),     // modrá
        Id.EARTH       to createPinIcon("E", 0xFF5D4037.toInt()),     // hnědá
        Id.EVENT       to createPinIcon("Ev", 0xFFD32F2F.toInt()),    // červená
        Id.VIRTUAL     to createPinIcon("V", 0xFF6A1B9A.toInt()),     // fialová
        Id.WHERIGO     to createPinIcon("W", 0xFFF9A825.toInt()),     // žlutá
        Id.LETTERBOX   to createPinIcon("L", 0xFF455A64.toInt()),     // šedomodrá
        Id.DEFAULT     to createPinIcon("•", 0xFF616161.toInt()),     // šedá
        Id.DECOR_FOUND   to createSmileyIcon(),
        Id.DECOR_OFFLINE to createFloppyIcon()
    )

    /**
     * Vyrobí pin-shape ikonu — kruh nahoře, špička dolů. Ankor (referenční bod
     * v SymbolManager) je střed-dole, takže špička přesně sedí na souřadnici.
     */
    private fun createPinIcon(letter: String, fillColor: Int): Bitmap {
        val size = 88                    // px
        val pinHeight = 80               // celá výška pinu
        val circleRadius = size / 2f - 4

        val bm = Bitmap.createBitmap(size, pinHeight, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)

        val centerX = size / 2f
        val centerY = circleRadius + 4

        // Pin tail (shadow + tip)
        val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }
        val tipPath = Path().apply {
            moveTo(centerX - circleRadius * 0.55f, centerY + circleRadius * 0.5f)
            lineTo(centerX, pinHeight.toFloat() - 4f)
            lineTo(centerX + circleRadius * 0.55f, centerY + circleRadius * 0.5f)
            close()
        }
        // White outline (under tip)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        c.drawPath(tipPath, outline)
        c.drawPath(tipPath, tipPaint)

        // White circle (outer)
        val whiteCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        c.drawCircle(centerX, centerY, circleRadius, whiteCircle)

        // Colored fill circle (inner)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }
        c.drawCircle(centerX, centerY, circleRadius - 4, fill)

        // Letter
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = if (letter.length > 1) 30f else 40f
        }
        val textY = centerY - (text.descent() + text.ascent()) / 2
        c.drawText(letter, centerX, textY, text)

        return bm
    }

    /** Zelený kruh s usmívajícím se obličejem — značí, že keš už byla zalogována. */
    private fun createSmileyIcon(): Bitmap {
        val size = 56
        val bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        val cx = size / 2f
        val cy = size / 2f
        val outerR = size / 2f - 2

        // Bílý okraj
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawCircle(cx, cy, outerR, white)

        // Žlutá výplň (klasický smajlík)
        val yellow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC107.toInt() }
        c.drawCircle(cx, cy, outerR - 4, yellow)

        // Oči
        val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        c.drawCircle(cx - 7, cy - 4, 3.5f, black)
        c.drawCircle(cx + 7, cy - 4, 3.5f, black)

        // Úsměv
        val smile = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            strokeCap = Paint.Cap.ROUND
        }
        val rect = android.graphics.RectF(cx - 9f, cy - 4f, cx + 9f, cy + 11f)
        c.drawArc(rect, 20f, 140f, false, smile)

        return bm
    }

    /** Šedá disketka — značí, že keš je stažená lokálně (s detailem, popisem, hintem). */
    private fun createFloppyIcon(): Bitmap {
        val size = 56
        val bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)

        val pad = 4f
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 8f, 8f, white)

        // Tělo disketky
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF455A64.toInt() }
        c.drawRoundRect(pad, pad, size - pad, size - pad, 4f, 4f, body)

        // Horní kovová slot (uřezaný roh)
        val slot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF263238.toInt() }
        val slotW = size * 0.55f
        val slotH = size * 0.32f
        c.drawRect(
            (size - slotW) / 2f, pad + 1f,
            (size + slotW) / 2f, pad + slotH,
            slot
        )
        // bílý čtverec uvnitř slotu (mock label)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawRect(
            (size - slotW * 0.55f) / 2f, pad + 4f,
            (size + slotW * 0.55f) / 2f, pad + slotH - 3f,
            label
        )

        // Štítek dole
        val sticker = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFECEFF1.toInt() }
        c.drawRect(
            pad + 4f, size * 0.55f,
            size - pad - 4f, size - pad - 4f,
            sticker
        )

        return bm
    }
}
