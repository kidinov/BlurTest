package com.blur.andrey.blurtest

import android.graphics.SurfaceTexture
import android.renderscript.*
import android.view.Surface
import android.view.TextureView
import timber.log.Timber


class BlurFilter(private val rs: RenderScript) : TextureView.SurfaceTextureListener {
    var width: Int = 0
        private set
    var height: Int = 0
        private set
    private var aConvIn: Allocation? = null
    private var aConvOut: Allocation? = null
    private var aBlurOut: Allocation? = null
    private val blurRc: ScriptIntrinsicBlur = ScriptIntrinsicBlur.create(rs, Element.RGBA_8888(rs)).apply {
        setRadius(BLUR_RADIUS)
    }
    private val yuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.RGBA_8888(rs))
    private var surface: SurfaceTexture? = null

    private fun setupSurface() {
        if (surface != null) {
            aBlurOut?.surface = Surface(surface)
        }
    }

    fun reset(width: Int, height: Int) {
        Timber.d("reset $width $height")
        aBlurOut?.destroy()

        this.width = width
        this.height = height

        val tbConvIn = Type.Builder(rs, Element.U8(rs))
                .setX(width)
                .setY(height)
                .setYuvFormat(android.graphics.ImageFormat.NV21)
        aConvIn = Allocation.createTyped(rs, tbConvIn.create(), Allocation.USAGE_SCRIPT)

        val tbConvOut = Type.Builder(rs, Element.RGBA_8888(rs))
                .setX(width)
                .setY(height)
        aConvOut = Allocation.createTyped(rs, tbConvOut.create(), Allocation.USAGE_SCRIPT)

        val tbBlurOut = Type.Builder(rs, Element.RGBA_8888(rs))
                .setX(width)
                .setY(height)
        aBlurOut = Allocation.createTyped(rs, tbBlurOut.create(),
                Allocation.USAGE_SCRIPT or Allocation.USAGE_IO_OUTPUT)

        setupSurface()
    }

    fun execute(yuv: ByteArray) {
        if (surface != null) {
            Timber.d("execute start")
            //YUV -> RGB
            aConvIn!!.copyFrom(yuv)
            yuvToRgb.setInput(aConvIn)
            yuvToRgb.forEach(aConvOut)

            Timber.d("before blur")
            //RGB -> BLURED RGB
            blurRc.setInput(aConvOut)
            blurRc.forEach(aBlurOut)
            Timber.d("before ioSend")
            aBlurOut!!.ioSend()
            Timber.d("execute end")
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        this.surface = surface
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        this.surface = surface
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        this.surface = null
        setupSurface()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    companion object {
        private val BLUR_RADIUS = 25f
    }
}
