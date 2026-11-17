package com.zehn.ai
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator

class Orb(c: Context) : View(c) {
  var bmp: Bitmap? = null
  var speaking = false
  private var t = 0f
  private val p = Paint(Paint.ANTI_ALIAS_FLAG)
  private val an = ValueAnimator.ofFloat(0f, 360f).apply {
    duration = 5000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
    addUpdateListener { t = it.animatedValue as Float; invalidate() }
  }
  override fun onAttachedToWindow() { super.onAttachedToWindow(); an.start() }
  override fun onDetachedFromWindow() { an.cancel(); super.onDetachedFromWindow() }

  override fun onDraw(cv: Canvas) {
    val cx = width / 2f; val cy = height / 2f; val r = minOf(width, height) / 2f - 4f
    val amp = if (speaking) (Math.sin(Math.toRadians((t * 8f).toDouble())).toFloat() * 5f) else 0f
    p.style = Paint.Style.FILL
    p.shader = RadialGradient(cx, cy, r, intArrayOf(0x66FF4FA8, 0x00FF4FA8), null, Shader.TileMode.CLAMP)
    cv.drawCircle(cx, cy, r, p); p.shader = null
    p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 3f
    for (i in 0..30) {
      val a = Math.toRadians(135.0 + i * 9.0)
      val lit = i <= 18 + (if (speaking) (amp + 5f).toInt() else 0)
      p.color = if (lit) 0xFF00E5FF.toInt() else 0x33FFFFFF
      val r1 = r - 2f; val r2 = r - 12f
      cv.drawLine(cx + (r1 * Math.cos(a)).toFloat(), cy + (r1 * Math.sin(a)).toFloat(),
        cx + (r2 * Math.cos(a)).toFloat(), cy + (r2 * Math.sin(a)).toFloat(), p)
    }
    p.strokeWidth = 5f
    val rr = r - 22f
    p.color = 0xFFFF4FA8.toInt(); cv.drawArc(cx - rr, cy - rr, cx + rr, cy + rr, t, 110f, false, p)
    p.color = 0xFF00E5FF.toInt(); cv.drawArc(cx - rr, cy - rr, cx + rr, cy + rr, t + 180f, 110f, false, p)
    p.strokeWidth = 2f; p.color = 0x55FFFFFF
    val r3 = rr - 8f
    cv.drawArc(cx - r3, cy - r3, cx + r3, cy + r3, -t * 1.6f, 240f, false, p)
    val fr = r3 - 8f + amp / 2f
    p.style = Paint.Style.FILL
    p.color = 0xFF1C1040.toInt(); cv.drawCircle(cx, cy, fr, p)
    cv.save()
    val path = Path(); path.addCircle(cx, cy, fr, Path.Direction.CW); cv.clipPath(path)
    val b = bmp
    if (b != null) cv.drawBitmap(b, null, RectF(cx - fr, cy - fr, cx + fr, cy + fr), null) else girl(cv, cx, cy, fr)
    cv.restore()
  }

  private fun girl(cv: Canvas, cx: Float, cy: Float, s: Float) {
    p.style = Paint.Style.FILL; p.shader = null
    p.color = 0xFF2B1B4D.toInt(); cv.drawOval(RectF(cx - s * 0.95f, cy - s * 0.95f, cx + s * 0.95f, cy + s * 1.15f), p)
    p.color = 0xFFFFD9C7.toInt(); cv.drawOval(RectF(cx - s * 0.52f, cy - s * 0.68f, cx + s * 0.52f, cy + s * 0.55f), p)
    p.color = 0xFF2B1B4D.toInt(); cv.drawArc(RectF(cx - s * 0.6f, cy - s * 0.85f, cx + s * 0.6f, cy + s * 0.05f), 180f, 180f, true, p)
    p.color = 0xFF1A1033.toInt()
    cv.drawOval(RectF(cx - s * 0.32f, cy - s * 0.02f, cx - s * 0.12f, cy + s * 0.16f), p)
    cv.drawOval(RectF(cx + s * 0.12f, cy - s * 0.02f, cx + s * 0.32f, cy + s * 0.16f), p)
    p.color = 0xFFFFFFFF.toInt()
    cv.drawCircle(cx - s * 0.2f, cy + s * 0.03f, s * 0.04f, p); cv.drawCircle(cx + s * 0.24f, cy + s * 0.03f, s * 0.04f, p)
    p.color = 0x55FF4FA8; cv.drawCircle(cx - s * 0.34f, cy + s * 0.27f, s * 0.1f, p); cv.drawCircle(cx + s * 0.34f, cy + s * 0.27f, s * 0.1f, p)
    p.color = 0xFFE0457B.toInt(); cv.drawArc(RectF(cx - s * 0.16f, cy + s * 0.2f, cx + s * 0.16f, cy + s * 0.42f), 10f, 160f, true, p)
  }
}
