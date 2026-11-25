package tech.nouverse.nouride.android.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * A page's content kept to a readable width on a wide screen, centred (`spec/layout.md`): its one child is measured at
 * most [maxPx] wide, and at the full width on anything narrower. [centred] also centres it from top to bottom, for the
 * first screens that stand alone in the middle (inside a ScrollView that fills its viewport).
 */
internal class Readable(context: Context, maxPx: Int, child: View) : FrameLayout(context) {
  var maxPx: Int = maxPx
    set(value) {
      if (field == value) return
      field = value
      requestLayout()
    }

  var centred: Boolean = false
    set(value) {
      if (field == value) return
      field = value
      (getChildAt(0).layoutParams as LayoutParams).gravity = gravity()
      requestLayout()
    }

  init {
    addView(child, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, gravity()))
  }

  private fun gravity() = if (centred) Gravity.CENTER else Gravity.CENTER_HORIZONTAL

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val width = MeasureSpec.getSize(widthMeasureSpec)
    val capped = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) maxPx else Columns.readable(width, maxPx)
    // The child is measured at the capped width, then this frame takes the whole width to centre it in.
    super.onMeasure(MeasureSpec.makeMeasureSpec(capped, MeasureSpec.EXACTLY), heightMeasureSpec)
    setMeasuredDimension(maxOf(width, capped), measuredHeight)
  }
}

/**
 * Two columns side by side, as the desktop's grid draws them: [first] takes [firstWeight] of the width and [second] the
 * rest, [gapPx] apart, each as tall as its own content. When nothing in [second] is shown (no update waits, Nouride is
 * not installed yet), [first] stands alone, centred at most [aloneMaxPx] wide, rather than beside an empty column.
 */
internal class Columns(
  context: Context,
  private val first: View,
  private val second: ViewGroup,
  private val firstWeight: Float,
  private val gapPx: Int,
  private val aloneMaxPx: Int,
) : ViewGroup(context) {
  init {
    addView(first, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    addView(second, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
  }

  private fun secondShown(): Boolean = (0 until second.childCount).any { second.getChildAt(it).visibility != View.GONE }

  private var split = Split(0, 0, 0, 0)

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val width = MeasureSpec.getSize(widthMeasureSpec)
    val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    split = split(width, gapPx, firstWeight, aloneMaxPx, secondShown())
    first.measure(MeasureSpec.makeMeasureSpec(split.firstWidth, MeasureSpec.EXACTLY), free)
    second.measure(MeasureSpec.makeMeasureSpec(split.secondWidth, MeasureSpec.EXACTLY), free)
    setMeasuredDimension(width, if (split.secondWidth > 0) maxOf(first.measuredHeight, second.measuredHeight) else first.measuredHeight)
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    first.layout(split.firstLeft, 0, split.firstLeft + first.measuredWidth, first.measuredHeight)
    second.layout(split.secondLeft, 0, split.secondLeft + second.measuredWidth, second.measuredHeight)
  }

  /** Where each column starts and how wide it is, in px; a column not shown is 0 wide. */
  data class Split(val firstLeft: Int, val firstWidth: Int, val secondLeft: Int, val secondWidth: Int)

  companion object {
    /** A page's width on a screen [width] wide: never wider than [maxPx], never wider than the screen. */
    fun readable(width: Int, maxPx: Int): Int = minOf(width, maxPx)

    /**
     * The two columns in [width]: side by side with [gapPx] between, [firstWeight] of the rest to the first; or, with
     * nothing to show in the second, the first alone, centred, at most [aloneMaxPx] wide. Pure, so it is tested.
     */
    fun split(width: Int, gapPx: Int, firstWeight: Float, aloneMaxPx: Int, secondShown: Boolean): Split {
      if (!secondShown) {
        val alone = minOf(width, aloneMaxPx)
        return Split((width - alone) / 2, alone, 0, 0)
      }
      val first = ((width - gapPx) * firstWeight).toInt()
      return Split(0, first, first + gapPx, width - gapPx - first)
    }

    /** A column of cards, one under another, for either side. */
    fun stack(context: Context): LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
  }
}
