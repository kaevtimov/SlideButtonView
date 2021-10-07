package com.example.slidebtnlib.slidebuttonview

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.transition.Scene
import android.transition.Transition
import android.transition.TransitionInflater
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.*
import com.example.slidebtnlib.R


class SlideButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    companion object {
        //Ratio, how far the button has to be dragged, to send execution
        private const val ACCEPTANCE_RATIO = 0.85

        //Ratio of the width, how far the button should slide to tease its dragging possibility
        private const val TEASE_WIDTH_RATIO: Float = 0.15f
    }

    private val centerTextView get() = findViewById<TextView>(R.id.centerTextView)
    private val slidingButton get() = findViewById<ImageView>(R.id.slidingButton)
    private val backgroundPanel get() = findViewById<View>(R.id.backgroundPanel)

    private var buttonDrawable: Drawable? = null
    private var buttonText: String? = null
    private var buttonTextColor: Int? = null

    private var slidingBackgroundStartingWidth: Int = 0
    private var initialX: Float = 0.toFloat()
    private var endX: Float = 0.toFloat()
    private var slidingBackgroundEnd = 0.toFloat()
    private var active: Boolean = false
    private val slideListeners = ArrayList<OnSlideListener>()
    private var slideStarted = false
    private var isButtonEnabled = true

    private val loadingScene =
        Scene.getSceneForLayout(this, R.layout.slide_button_loading_state_layout, context)
    private val startScene =
        Scene.getSceneForLayout(this, R.layout.slide_button_default_state_layout, context)

    private val transitionToLoading =
        TransitionInflater.from(context).inflateTransition(R.transition.slide_button_toloading)
    private val transitionReset: Transition =
        TransitionInflater.from(context).inflateTransition(R.transition.slide_button_reset)

    init {
        prepareButtonFromAttrs(attrs)
    }

    /** Sets the text in the middle of the button */
    fun setText(text: String) {
        buttonText = text
        centerTextView.text = text
    }

    /**
     * Shows the loading state of the button. Only use this, if not triggered by itself.
     * Like if there is another e.g. retry you can use this function.
     * The loading indication will be shown automatically by dragging button far enough
     */
    fun showLoadingButton() {
        isActivated = true
        active = true
        loadingScene()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || !isButtonEnabled)
            return super.onTouchEvent(event)
        if (initialX == 0f) {
            //Check where button is at the beginning, to have left border
            initialX = slidingButton.x
        }
        if (endX == 0f) {
            //calculate the most right position, the button can stay
            endX =
                (backgroundPanel.width - (slidingButton.width + slidingButton.marginEnd) + backgroundPanel.x)
            slidingBackgroundEnd = backgroundPanel.x + backgroundPanel.width
            slidingBackgroundStartingWidth = backgroundPanel.width
        }

        if (isEnabled && !active) {
            //Touch x is the difference, of the exact tap to the x Coordinate of the button. To easy up calculation
            val touchX = event.x - (slidingButton.width / 2)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    //Check first tap was on the Button or not. If not we are not starting dragging
                    when (event.x) {
                        in initialX..(initialX + slidingButton.width) -> slideStarted = true
                    }
                    requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    //Move finger, if slideStarted = true, we can move the button
                    if (slideStarted) {
                        when (touchX) {
                            in -Float.MAX_VALUE..initialX -> {
                                setNewWidthOfBackground(slidingBackgroundStartingWidth)
                            }
                            in initialX..endX -> {
                                val newWidth =
                                    slidingBackgroundEnd - (touchX - slidingButton.marginStart)
                                setNewWidthOfBackground(newWidth.toInt())
                                centerTextView.alpha = (0.8f * (newWidth) / width)

                            }
                            else -> {
                                //Max limit, so you cannot drag button out of view
                                val newWidth =
                                    slidingBackgroundEnd - (endX - slidingButton.marginStart)
                                setNewWidthOfBackground(newWidth.toInt())
                            }

                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (slideStarted) {
                        slideStarted = false
                        if (checkIfExecutionConfirmed(touchX)) {
                            executeButton()
                            performClick()
                        } else {
                            moveButtonBack()
                        }
                    } else {
                        teaseButtonAnimation()
                    }
                    requestDisallowInterceptTouchEvent(false)
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    slideStarted = false
                    requestDisallowInterceptTouchEvent(false)
                }
            }
        }
        return true
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        applyEnabledState(enabled)
    }

    /** Resets the button, so you can drag it again, if button is enabled */
    fun reset() {
        /* We delay the reset a slight bit, otherwise we can have the issue,
           that this function gets called before transition to loading is loaded. */
        Handler(Looper.getMainLooper()).postDelayed({
            TransitionManager.endTransitions(this)
            resetBlock()
        }, 200)
    }

    /** Add Listener for get notified when execution is desired */
    fun addOnSlideListener(vararg listeners: OnSlideListener) {
        slideListeners.addAll(listeners)
    }

    /** Remove Listener for get notified when execution is desired from user */
    fun removeOnSlideListener(listener: OnSlideListener) {
        slideListeners.remove(listener)
    }

    /** Clears all listeners */
    fun clearOnSlideListeners() {
        slideListeners.clear()
    }

    private fun resetBlock() {
        active = false
        startCondition()
        applyEnabledState(isEnabled)
    }

    /** Method to complete the execution of the button */
    private fun executeButton() {
        showLoadingButton()
        slideListeners.onEach { it.onSlide(this) }
    }

    private fun startScene() {
        TransitionManager.go(
            startScene, transitionReset
        )
        setupTextView()
        active = false
        applyEnabledState(isEnabled)
    }

    private fun startCondition() {
        isActivated = false
        startScene()
    }

    private fun loadingScene() {
        TransitionManager.go(
            loadingScene,
            transitionToLoading
        )
        setupTextView()
    }

    private fun setupTextView() {
        slidingButton.setImageDrawable(buttonDrawable)
        centerTextView.text = buttonText
        buttonTextColor?.let { centerTextView.setTextColor(it) }
        centerTextView.clipToOutline = true
    }

    private fun teaseButtonAnimation() {
        val animateEnd = width * TEASE_WIDTH_RATIO
        val buttonTeaseAnimation =
            ObjectAnimator.ofFloat(slidingButton, "translationX", 0f, animateEnd, 0f)
        buttonTeaseAnimation.duration = 200
        buttonTeaseAnimation.interpolator = AccelerateDecelerateInterpolator()
        buttonTeaseAnimation.start()
    }

    private fun applyEnabledState(enabled: Boolean) {
        isButtonEnabled = enabled
        if (!active) {
            backgroundPanel.isEnabled = enabled
            slidingButton.alpha = if (enabled) 1f else 0.5f
            centerTextView.alpha = if (enabled) 1f else 0.5f
        }
    }

    private fun setNewWidthOfBackground(width: Int) {
        val params = backgroundPanel.layoutParams
        params.width = width
        backgroundPanel.layoutParams = params
        centerTextView.clipBounds = textClipRect()
    }

    private fun textClipRect(): Rect {
        val leftOutline =
            centerTextView.left * -1 + backgroundPanel.left + slidingButton.width + slidingButton.marginStart
        return Rect(leftOutline, 0, backgroundPanel.right, backgroundPanel.bottom)
    }

    /**
     * Checks if the button is dragged far enough.
     *
     * @param touchX is the left end of the button, where we would set the x of the button
     */
    private fun checkIfExecutionConfirmed(touchX: Float): Boolean {
        return (touchX - initialX) > (width - initialX - slidingButton.width - slidingButton.marginLeft - slidingButton.marginRight) * ACCEPTANCE_RATIO
    }

    /**
     * Animation to move the draggable button back
     */
    private fun moveButtonBack() {
        TransitionManager.go(
            startScene,
            transitionReset
        )
        setupTextView()
    }

    private fun prepareButtonFromAttrs(attrs: AttributeSet?) {
        val a = context.obtainStyledAttributes(
            attrs,
            R.styleable.SlideButtonView
        )

        buttonDrawable = a.getDrawable(R.styleable.SlideButtonView_buttonIcon)
        buttonText = a.getString(R.styleable.SlideButtonView_buttonText)
        buttonTextColor = a.getInt(R.styleable.SlideButtonView_textColor, context.getColor(R.color.black))

        a.recycle()

        startCondition()
    }

    interface OnSlideListener {
        fun onSlide(view: View)
    }
}
