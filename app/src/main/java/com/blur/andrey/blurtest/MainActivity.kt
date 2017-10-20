package com.blur.andrey.blurtest

import android.os.Bundle
import android.renderscript.RenderScript
import android.support.v7.app.AppCompatActivity
import io.fotoapparat.Fotoapparat
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private lateinit var fotoapparat: Fotoapparat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initQrScanner()
    }

    override fun onStart() {
        super.onStart()
        fotoapparat.start()
    }

    override fun onStop() {
        fotoapparat.stop()
        super.onStop()
    }

    private fun initQrScanner() {
        val filter = BlurFilter(RenderScript.create(this))
        tvWholeOverlay.surfaceTextureListener = filter

        fotoapparat = Fotoapparat
                .with(this)
                .into(cvQrScanner)
                .frameProcessor({
                    if (it.size.width != filter.width || it.size.height != filter.height) {
                        filter.reset(it.size.width, it.size.height)
                    }
                    filter.execute(it.image)
                })
                .build()
    }
}
