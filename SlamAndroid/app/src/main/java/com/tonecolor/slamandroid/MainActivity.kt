package com.tonecolor.slamandroid

import android.os.Bundle
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.Entity
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.SwapChainFlags
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.FilamentHelper
import com.google.android.filament.android.UiHelper
import com.tonecolor.slamandroid.ui.theme.SlamAndroidTheme

class MainActivity : ComponentActivity() {

    companion object {
        init {
            Filament.init()
        }
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var choreographer: Choreographer
    private lateinit var displayHelper: DisplayHelper
    private lateinit var uiHelper: UiHelper

    private var swapChain: SwapChain? = null

    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: View
    private lateinit var camera: Camera

    private var frameScheduler = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            if (uiHelper.isReadyToRender) {
                if (swapChain != null && renderer.beginFrame(swapChain!!, frameTimeNanos)) {
                    renderer.render(view)
                    renderer.endFrame()
                }
            }
        }
    }

    @Entity
    private var renderable = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        surfaceView = SurfaceView(this)
        choreographer = Choreographer.getInstance()
        displayHelper = DisplayHelper(this)

        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                val flags = if (SwapChain.isSRGBSwapChainSupported(engine)) {
                    uiHelper.swapChainFlags or SwapChainFlags.CONFIG_SRGB_COLORSPACE
                } else {
                    uiHelper.swapChainFlags
                }
                swapChain = engine.createSwapChain(surface, flags)
                displayHelper.attach(renderer, surfaceView.display)
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                    swapChain = null
                }
            }

            override fun onResized(width: Int, height: Int) {
                val zoom = 1.5
                val aspect = width.toDouble() / height.toDouble()
                camera.setProjection(
                    Camera.Projection.ORTHO,
                    -aspect * zoom, aspect * zoom, -zoom, zoom, 0.0, 10.0
                )
                view.viewport = Viewport(0, 0, width, height)
                FilamentHelper.synchronizePendingFrames(engine)
            }
        }
        uiHelper.attachTo(surfaceView)

        engine = Engine.Builder().featureLevel(Engine.FeatureLevel.FEATURE_LEVEL_0).build()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera(engine.entityManager.create())

        scene.skybox = Skybox.Builder().color(0.5294f, 0.8078f, 0.9804f, 1.0f).build(engine)
        view.isPostProcessingEnabled = false
        view.camera = camera
        view.scene = scene

        renderable = EntityManager.get().create()

        RenderableManager.Builder(1)
            .boundingBox(Box(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.01f))
            .build(engine, renderable)

        scene.addEntity(renderable)

        setContent {
            SlamAndroidTheme {
                SurfaceViewContent(Modifier.fillMaxSize(), MaterialTheme.colorScheme.background)
            }
        }
    }

    @Composable
    fun SurfaceViewContent(modifier: Modifier, color: Color) {
        Surface(
            modifier = modifier,
            color = color
        ) {
            AndroidView(factory = { context ->
                surfaceView
            }, modifier = Modifier.fillMaxSize())
        }
    }

    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(frameScheduler)
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameScheduler)
    }

    override fun onDestroy() {
        super.onDestroy()

        choreographer.removeFrameCallback(frameScheduler)
        uiHelper.detach()

        engine.destroyEntity(renderable)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)

        val entityManager = EntityManager.get()
        entityManager.destroy(renderable)
        entityManager.destroy(camera.entity)

        engine.destroy()
    }
}


