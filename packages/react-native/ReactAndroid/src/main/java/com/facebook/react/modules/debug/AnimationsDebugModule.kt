/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.modules.debug

import android.widget.Toast
import com.facebook.common.logging.FLog
import com.facebook.fbreact.specs.NativeAnimationsDebugModuleSpec
import com.facebook.react.bridge.JSApplicationCausedNativeException
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.common.ReactConstants
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.debug.interfaces.DeveloperSettings
import java.util.Locale

/**
 * Module that records debug information during transitions (animated navigation events such as
 * going from one screen to another).
 */
@ReactModule(name = NativeAnimationsDebugModuleSpec.NAME)
internal class AnimationsDebugModule(
    reactContext: ReactApplicationContext?,
    private val catalystSettings: DeveloperSettings?
) : NativeAnimationsDebugModuleSpec(reactContext) {
  private var frameCallback: FpsDebugFrameCallback? = null

  override fun startRecordingFps() {
    if (catalystSettings == null || !catalystSettings.isAnimationFpsDebugEnabled()) {
      return
    }
    if (frameCallback != null) {
      throw JSApplicationCausedNativeException("Already recording FPS!")
    }
    frameCallback = FpsDebugFrameCallback(getReactApplicationContext())
    frameCallback?.startAndRecordFpsAtEachFrame()
  }

  /**
   * Called when an animation finishes. The caller should include the animation stop time in ms
   * (unix time) so that we know when the animation stopped from the JS perspective and we don't
   * count time after as being part of the animation.
   */
  override fun stopRecordingFps(animationStopTimeMs: Double) {
    if (frameCallback == null) {
      return
    }
    frameCallback!!.stop()

    // Casting to long is safe here since animationStopTimeMs is unix time and thus relatively small
    val fpsInfo = frameCallback!!.getFpsInfo(animationStopTimeMs.toLong())
    if (fpsInfo == null) {
      Toast.makeText(getReactApplicationContext(), "Unable to get FPS info", Toast.LENGTH_LONG)
          .show()
    } else {
      val fpsString =
          String.format(
              Locale.US,
              "FPS: %.2f, %d frames (%d expected)",
              fpsInfo.fps,
              fpsInfo.totalFrames,
              fpsInfo.totalExpectedFrames)
      val jsFpsString =
          String.format(
              Locale.US,
              "JS FPS: %.2f, %d frames (%d expected)",
              fpsInfo.jsFps,
              fpsInfo.totalJsFrames,
              fpsInfo.totalExpectedFrames)
      val debugString =
          """
            $fpsString
            $jsFpsString
            Total Time MS: ${String.format(Locale.US, "%d", fpsInfo.totalTimeMs)}
            """
              .trimIndent()
      FLog.d(ReactConstants.TAG, debugString)
      Toast.makeText(getReactApplicationContext(), debugString, Toast.LENGTH_LONG).show()
    }
    frameCallback = null
  }

  override fun invalidate() {
    frameCallback?.stop()
    frameCallback = null
  }
}
