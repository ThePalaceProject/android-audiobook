package org.librarysimplified.audiobook.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import android.widget.TextView

/**
 * A single-line text view that scrolls its text horizontally when the text is wider than the
 * view, and stays still when it fits.
 *
 * The text enters from the right edge, scrolls left at a fixed slow speed, and exits past the
 * left edge before repeating. The loop restarts only while the text is fully outside the view's
 * bounds, so the motion is continuous and the text never visibly jumps.
 *
 * The text is laid out at its full width and clipped by the view's own bounds; a canvas
 * translation in [onDraw] slides the visible portion, so the view does not need to be wider than
 * its parent. The host calls [startScrolling] and [stopScrolling] to begin and end the scroll,
 * typically in response to the view becoming visible or hidden.
 */

class MarqueeTextView(
  context: Context,
  attrs: AttributeSet?
) : TextView(context, attrs, android.R.attr.textViewStyle) {
  private var animator: ValueAnimator? = null
  private var marqueeActive = false
  private var scrollOffset = 0f
  private var scrollStart = 0f
  private var scrollEnd = 0f
  private var scrollingEnabled = false

  init {
    this.maxLines = 1
  }

  /**
   * Begin scrolling the text if it is wider than the view. This is safe to call before the view
   * has been laid out; the scroll begins once the view learns its width.
   */
  fun startScrolling() {
    this.scrollingEnabled = true
    this.updateScroll()
  }

  /**
   * Stop scrolling and reset the text to its starting position.
   */
  fun stopScrolling() {
    this.scrollingEnabled = false
    this.marqueeActive = false
    this.stopAnimator()
    this.scrollOffset = 0f
    this.invalidate()
  }

  override fun onLayout(
    changed: Boolean,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
  ) {
    super.onLayout(changed, left, top, right, bottom)
    if (this.scrollingEnabled) {
      this.updateScroll()
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    this.stopAnimator()
  }

  override fun onDraw(canvas: Canvas) {
    if (this.marqueeActive) {
      canvas.translate(this.scrollOffset, 0f)
    }
    super.onDraw(canvas)
  }

  private fun updateScroll() {
    if (!this.scrollingEnabled) {
      return
    }

    if (this.width == 0) {
      return
    }

    val textWidth = this.paint.measureText(this.text.toString())
    val contentWidth = this.width - this.paddingLeft - this.paddingRight

    if (textWidth <= contentWidth) {
      this.marqueeActive = false
      this.stopAnimator()
      this.scrollOffset = 0f
      this.invalidate()
      return
    }

    // The text starts fully outside the right edge and ends fully outside the left edge. The
    // animator wraps back to the right-edge start, which only happens while the text is invisible,
    // so the loop is seamless.
    this.scrollStart = (this.width - this.paddingLeft).toFloat()
    this.scrollEnd = -(textWidth + this.paddingLeft).toFloat()
    this.scrollOffset = this.scrollStart
    this.marqueeActive = true
    this.startAnimator()
  }

  private fun startAnimator() {
    this.stopAnimator()

    val density = this.resources.displayMetrics.density
    val distance = this.scrollStart - this.scrollEnd
    val speedPxPerSecond = MARQUEE_SPEED_DP_PER_SECOND * density
    val durationMs = (distance / speedPxPerSecond * 1000f).toLong()

    val animator = ValueAnimator.ofFloat(this.scrollStart, this.scrollEnd)
    animator.duration = durationMs
    animator.interpolator = LinearInterpolator()
    animator.repeatCount = ValueAnimator.INFINITE
    animator.addUpdateListener {
      val valueNow = animator.animatedValue as Float
      this.scrollOffset = valueNow
      this.invalidate()
    }
    animator.start()
    this.animator = animator
  }

  private fun stopAnimator() {
    val animator = this.animator
    if (animator != null) {
      animator.cancel()
      this.animator = null
    }
  }

  private companion object {
    private const val MARQUEE_SPEED_DP_PER_SECOND = 30f
  }
}
