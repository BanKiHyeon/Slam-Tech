package com.tonecolor.slamandroid

import android.animation.ValueAnimator
import android.opengl.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import android.view.animation.LinearInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.Entity
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.MathUtils
import com.google.android.filament.RenderableManager
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.SwapChainFlags
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.FilamentHelper
import com.google.android.filament.android.UiHelper
import com.tonecolor.slamandroid.ui.theme.SlamAndroidTheme
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels

class CameraActivity : ComponentActivity(), ActivityCompat.OnRequestPermissionsResultCallback  {
    init {
        Filament.init()
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

    private lateinit var material: Material
    private lateinit var materialInstance: MaterialInstance

    private lateinit var vertexBuffer: VertexBuffer
    private lateinit var indexBuffer: IndexBuffer

    private lateinit var cameraHelper: CameraHelper

    private var frameScheduler = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            if (uiHelper.isReadyToRender) {

                cameraHelper.pushExternalImageToFilament()

                if (swapChain != null && renderer.beginFrame(swapChain!!, frameTimeNanos)) {
                    renderer.render(view)
                    renderer.endFrame()
                }
            }
        }
    }

    @Entity
    private var renderable = 0

    @Entity
    private var light = 0

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

        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera(engine.entityManager.create())

        scene.skybox = Skybox.Builder().color(0.5294f, 0.8078f, 0.9804f, 1.0f).build(engine)
        //view.isPostProcessingEnabled = false
        view.camera = camera
        view.scene = scene

        setMaterial()
        setMesh()

        renderable = EntityManager.get().create()

        RenderableManager.Builder(1)
            .boundingBox(Box(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.01f))
            //.boundingBox(Box(-0.5f, -0.5f, 0.0f, 0.5f, 0.5f, 0.01f))
            .geometry(0, PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, 6)
            .material(0, materialInstance)
            .build(engine, renderable)

        scene.addEntity(renderable)

        light = EntityManager.get().create()

        val (r, g, b) = Colors.cct(5_500.0f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(r, g, b)
            .intensity(110_000.0f)
            .direction(0.0f, -0.5f, -1.0f)
            .castShadows(true)
            .build(engine, light)

        scene.addEntity(light)

        camera.setExposure(16.0f, 1.0f / 125.0f, 100.0f)
        camera.lookAt(0.0, 0.0, 6.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)

        cameraHelper = CameraHelper(this, engine, materialInstance)
        cameraHelper.openCamera()

        setContent {
            SlamAndroidTheme {
                SurfaceViewContent(Modifier.fillMaxSize(), MaterialTheme.colorScheme.background)
            }
        }
    }

    private fun setMesh() {
        val floatSize = 4
        val intSize = 4
        val shortSize = 2
        val vertexSize = 3 * floatSize + 4 * floatSize

        @Suppress("ArrayInDataClass")
        data class Vertex(val x: Float, val y: Float, val z: Float, val tangents: FloatArray)

        fun ByteBuffer.put(v: Vertex): ByteBuffer {
            putFloat(v.x)
            putFloat(v.y)
            putFloat(v.z)
            v.tangents.forEach { putFloat(it) }
            return this
        }

        val vertexCount = 4
        val tf = FloatArray(4)

//        MathUtils.packTangentFrame(0.0f, 1.0f, 0.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, tf)
//        MathUtils.packTangentFrame(0.0f, 1.0f, 0.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f, 0.0f, tf)
//        MathUtils.packTangentFrame(-1.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f, tf)
//        MathUtils.packTangentFrame(-1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, -1.0f, 0.0f, tf)
//        MathUtils.packTangentFrame(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, tf)
//        MathUtils.packTangentFrame(0.0f, -1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f, tf)

        val vertexData = ByteBuffer.allocate(vertexCount * vertexSize)
            .order(ByteOrder.nativeOrder())
            .put(Vertex(0.5f, -0.5f, 0f, tf))
            .put(Vertex(0.5f, 0.5f, 0f, tf))
            .put(Vertex(-0.5f, -0.5f, 0f, tf))
            .put(Vertex(-0.5f, 0.5f, 0f, tf))
            .flip()

        vertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, vertexSize)
            .attribute(VertexAttribute.TANGENTS, 0, AttributeType.FLOAT4, 3 * floatSize, vertexSize)
            .build(engine)

        vertexBuffer.setBufferAt(engine, 0, vertexData)

        val indexData = ByteBuffer.allocate(2 * 3 * shortSize)
            .order(ByteOrder.nativeOrder())
            .putShort(0)
            .putShort(1)
            .putShort(2)
            .putShort(1)
            .putShort(3)
            .putShort(2)
            .flip()

        indexBuffer = IndexBuffer.Builder()
            .indexCount(6)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        indexBuffer.setBuffer(engine, indexData)
    }

    private fun setMaterial() {
        assets.openFd("materials/lit.filamat").use { fd ->
            val input = fd.createInputStream()
            val dst = ByteBuffer.allocate(fd.length.toInt())

            val src = Channels.newChannel(input)
            src.read(dst)
            src.close()

            val mat = dst.apply { rewind() }

            material = Material.Builder().payload(mat, mat.remaining()).build(engine)
            material.compile(
                Material.CompilerPriorityQueue.HIGH,
                Material.UserVariantFilterBit.ALL,
                Handler(Looper.getMainLooper())
            ) {
                android.util.Log.i("Material", "Material " + material.name + " compiled.")
            }
            engine.flush()
        }

        materialInstance = material.createInstance()
        materialInstance.setParameter("baseColor", Colors.RgbType.SRGB, 1.0f, 0.85f, 0.57f)
        materialInstance.setParameter("metallic", 0.0f)
        materialInstance.setParameter("roughness", 0.3f)
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
        cameraHelper.onResume()
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameScheduler)
        cameraHelper.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()

        choreographer.removeFrameCallback(frameScheduler)
        uiHelper.detach()

        engine.destroyEntity(light)
        engine.destroyEntity(renderable)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
        engine.destroyMaterialInstance(materialInstance)
        engine.destroyMaterial(material)

        val entityManager = EntityManager.get()
        entityManager.destroy(light)
        entityManager.destroy(renderable)
        entityManager.destroy(camera.entity)

        engine.destroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!cameraHelper.onRequestPermissionsResult(requestCode, grantResults)) {
            this.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}