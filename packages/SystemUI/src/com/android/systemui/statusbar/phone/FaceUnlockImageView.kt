/*
 * Copyright (C) 2023 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.statusbar.phone

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.TimeInterpolator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator

import androidx.core.animation.doOnStart
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnCancel

import com.android.systemui.R
import com.android.systemui.statusbar.policy.ConfigurationController

class FaceUnlockImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {

    enum class State { SCANNING, NOT_VERIFIED, SUCCESS, HIDDEN }

    private enum class AnimationState { NONE, SCANNING, DISMISS, SUCCESS, FAILED }

    private val startAnimation = createScaleAnimation(true)
    private val dismissAnimation = createScaleAnimation(start = false)
    private val scanningAnimation = createScanningAnimation()
    private val successAnimation = createSuccessRotationAnimation()
    private val failureShakeAnimation = createShakeAnimation(10f)

    private var currentAnimationState = AnimationState.NONE
    
    private var colorState: ColorStateList = ColorStateList.valueOf(Color.WHITE)
    private var iconStateResId: Int = 0

    private val vibrator: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    companion object {
        private const val BIOMETRICS_SCANNING_TIMEOUT: Long = 5000
        private const val SUCCESS_ANIMATION_DURATION: Long = 800
        private const val FAILED_ANIMATION_DURATION: Long = 500

        private lateinit var configurationController: ConfigurationController
        private var instance: FaceUnlockImageView? = null

        @JvmStatic
        fun setConfigurationController(cfgController: ConfigurationController) {
            configurationController = cfgController
        }

        @JvmStatic
        fun getConfigurationController(): ConfigurationController {
            return configurationController
        }

        @JvmStatic
        fun setBouncerState(state: State) {
            instance?.postDelayed({
                instance?.setState(state)
            }, 100)
        }

        @JvmStatic
        fun setInstance(instance: FaceUnlockImageView) {
            this.instance = instance
        }
    }

    init {
        visibility = View.GONE
        setImageResource(0)
    }

    public override fun onAttachedToWindow() {
        setInstance(this)
        getConfigurationController().addCallback(configurationChangedListener)
    }

    public override fun onDetachedFromWindow() {
        getConfigurationController().removeCallback(configurationChangedListener)
    }

    private val configurationChangedListener = object : ConfigurationController.ConfigurationListener {
        override fun onUiModeChanged() = updateColor()
        override fun onThemeChanged() = updateColor()
    }

    fun setKeyguardColorState(color: ColorStateList) {
        colorState = color
        updateColor()
    }

    fun updateColor() {
        val isDark = (context.resources.configuration.uiMode
                and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        imageTintList = if (this.id == R.id.bouncer_face_unlock_icon) {
            if (isDark) ColorStateList.valueOf(Color.WHITE) else ColorStateList.valueOf(Color.BLACK)
        } else {
            colorState
        }
    }

    fun setOnDozingChanged(dozing: Boolean) {
        if (dozing) {
            setState(State.HIDDEN)
        }
    }

    fun setBiometricMessage(msg: CharSequence) {
        setState(
            when (msg) {
                context.getString(R.string.keyguard_face_successful_unlock) -> State.SUCCESS
                context.getString(R.string.keyguard_face_failed) -> State.NOT_VERIFIED
                context.getString(R.string.face_unlock_recognizing) -> State.SCANNING
                else -> State.HIDDEN
            }
        )
    }

    fun setState(state: State) {
        updateFaceIconState(state)
        handleAnimationForState(state)
    }

    private fun updateFaceIconState(state: State) {
        iconStateResId = when (state) {
            State.SCANNING -> R.drawable.face_scanning
            State.NOT_VERIFIED -> R.drawable.face_not_verified
            State.SUCCESS -> R.drawable.face_success
            State.HIDDEN -> iconStateResId
        }
        setImageResource(iconStateResId)
    }

    private fun createScanningAnimation(): ObjectAnimator {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.2f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.2f, 1f)
        return ObjectAnimator.ofPropertyValuesHolder(this, scaleX, scaleY).apply {
            duration = 5000
            interpolator = LinearInterpolator()
        }
    }

    private fun createSuccessRotationAnimation(): ObjectAnimator {
        return ObjectAnimator.ofFloat(this, View.ROTATION_Y, 0f, 360f).apply {
            duration = SUCCESS_ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    private fun createShakeAnimation(amplitude: Float): ObjectAnimator {
        return ObjectAnimator.ofFloat(this, View.TRANSLATION_X, 0f, amplitude, -amplitude, amplitude, -amplitude, 0f).apply {
            duration = FAILED_ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    private fun createScaleAnimation(start: Boolean): ObjectAnimator {
        val startScale = if (start) 0f else 1f
        val endScale = if (start) 1f else 0f
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, startScale, endScale)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, startScale, endScale)
        return ObjectAnimator.ofPropertyValuesHolder(this, scaleX, scaleY).apply {
            duration = 500
            interpolator = AccelerateDecelerateInterpolator()
            if (!start) {
                doOnEnd {
                    startDismissAnimation()
                }
            }
        }
    }

    private fun vibrate(effect: Int) {
        post {
            val vibrationEffect = VibrationEffect.createPredefined(effect)
            vibrator.vibrate(vibrationEffect)
        }
    }

    private fun startDismissAnimation() {
        if (currentAnimationState != AnimationState.DISMISS) {
            val delay = when (currentAnimationState) {
                AnimationState.FAILED -> FAILED_ANIMATION_DURATION + 150L
                AnimationState.SUCCESS -> SUCCESS_ANIMATION_DURATION + 150L
                else -> 150L
            }
            postOnAnimationDelayed({
                if (currentAnimationState != AnimationState.DISMISS) {
                    dismissAnimation.start()
                    currentAnimationState = AnimationState.DISMISS
                    clearResources()
                }
            }, delay)
        }
    }

    private fun handleAnimationForState(state: State) {
        scanningAnimation.cancel()
        if (state != State.HIDDEN) cancelAnimations()
        visibility = View.VISIBLE
        when (state) {
            State.SCANNING -> {
                postOnAnimation {
                    startAnimation.start()
                    startAnimation.doOnEnd {
                        postOnAnimation {
                            scanningAnimation.start()
                            currentAnimationState = AnimationState.SCANNING
                        }
                    }
                }
            }
            State.SUCCESS -> {
                successAnimation.start()
                currentAnimationState = AnimationState.SUCCESS
            }
            State.NOT_VERIFIED -> {
                failureShakeAnimation.start()
                currentAnimationState = AnimationState.FAILED
            }
            State.HIDDEN -> {
                scanningAnimation.doOnCancel {
                    postOnAnimation {
                        startDismissAnimation()
                    }
                }
                clearResources()
            }
        }
        listOf(scanningAnimation, successAnimation, failureShakeAnimation).forEach {
            it.doOnEnd {
                postOnAnimation {
                    startDismissAnimation()
                }
            }
        }
        vibrate(if (state == State.NOT_VERIFIED) VibrationEffect.EFFECT_DOUBLE_CLICK else VibrationEffect.EFFECT_CLICK)
    }

    private fun cancelAnimations() {
        dismissAnimation.cancel()
        failureShakeAnimation.cancel()
        successAnimation.cancel()
    }
    
    private fun clearResources() {
        if (currentAnimationState == AnimationState.DISMISS) {
            dismissAnimation.doOnEnd {
                visibility = View.GONE
                setImageResource(0)
                currentAnimationState = AnimationState.NONE
            }
        }
    }
}
