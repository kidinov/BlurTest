package com.blur.andrey.blurtest

import android.graphics.SurfaceTexture
import android.renderscript.*
import android.view.Surface
import android.view.TextureView
import com.payconiq.customers.ScriptC_rotator
import timber.log.Timber

const val BLUR_RADIUS = 12f

class BlurFilter(private val rs: RenderScript) : TextureView.SurfaceTextureListener {
    private var finalWidth: Int = 0

    private var alocIn: Allocation? = null
    private var alocConv: Allocation? = null
    private var alocResize: Allocation? = null
    private var alocRotation: Allocation? = null
    private var alocOut: Allocation? = null

    private val blur = ScriptIntrinsicBlur.create(rs, Element.RGBA_8888(rs)).apply {
        setRadius(BLUR_RADIUS)
    }
    private val resize = ScriptIntrinsicResize.create(rs)
    private val rotate = ScriptC_rotator(rs)
    private val yuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.RGBA_8888(rs))

    private var surface: SurfaceTexture? = null

    private fun prepare(w: Int, h: Int) {
        Timber.d("prepare $w $h")

        val tbConvIn = Type.Builder(rs, Element.U8(rs))
                .setX(w)
                .setY(h)
                .setYuvFormat(android.graphics.ImageFormat.NV21)
                .create()
        alocIn = Allocation.createTyped(rs, tbConvIn)

        val tbConvOut = Type.Builder(rs, Element.RGBA_8888(rs))
                .setX(w)
                .setY(h)
                .create()
        alocConv = Allocation.createTyped(rs, tbConvOut)

        val scaledW = finalWidth
        val scaledH = finalWidth
        Timber.d("scaledW - $scaledW, scaledH - $scaledH")
        val tbResizeOut = Type.createXY(rs, alocConv!!.element, scaledW, scaledH)
        alocResize = Allocation.createTyped(rs, tbResizeOut)

        val tbRotationOut = Type.createXY(rs, alocConv!!.element, scaledW, scaledH)
        alocRotation = Allocation.createTyped(rs, tbRotationOut)
        rotate._inWidth = scaledW
        rotate._inHeight = scaledH

        val tbBlurOut = Type.Builder(rs, Element.RGBA_8888(rs))
                .setX(scaledW)
                .setY(scaledH)
                .create()
        alocOut = Allocation.createTyped(rs, tbBlurOut, Allocation.USAGE_SCRIPT or Allocation.USAGE_IO_OUTPUT)

        alocOut!!.surface = Surface(surface)
    }

    fun execute(yuv: ByteArray, w: Int, h: Int, angle: Int) {
        if (surface == null) {
            return
        }

        if (!isPrepared()) {
            prepare(w, h)
        }

        Timber.d("execute start angle - $angle")
        //YUV -> RGB
        alocIn!!.copyFrom(yuv)
        yuvToRgb.setInput(alocIn)
        yuvToRgb.forEach(alocConv)

        Timber.d("before resize")

        //RESIZE
        resize.setInput(alocConv)
        resize.forEach_bicubic(alocResize)

        Timber.d("before rotation")

        //ROTATION
        when (angle) {
            270 -> {
                rotate._inImage = alocResize
                rotate.forEach_rotate_270_clockwise(alocResize, alocRotation)
                blur.setInput(alocRotation)
            }
            90 -> {
                rotate._inImage = alocResize
                rotate.forEach_rotate_90_clockwise(alocResize, alocRotation)
                blur.setInput(alocRotation)
            }
            else -> {
                blur.setInput(alocResize)
            }
        }

        Timber.d("before blur")
        //RGB -> BLURED RGB
        blur.forEach(alocOut)
        Timber.d("before ioSend")
        alocOut!!.ioSend()
        Timber.d("execute end")
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        finalWidth = width / 5
        this.surface = surface
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        finalWidth = width / 5
        this.surface = surface
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        this.surface = null
        return true
    }

    private fun isPrepared() = alocIn != null
}