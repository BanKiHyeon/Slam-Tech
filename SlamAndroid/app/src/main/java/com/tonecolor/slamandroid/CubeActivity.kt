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
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.Entity
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
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

class CubeActivity : ComponentActivity() {
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

    private lateinit var material: Material
    private lateinit var materialInstance: MaterialInstance

    private lateinit var vertexBuffer: VertexBuffer
    private lateinit var indexBuffer: IndexBuffer

    private val animator = ValueAnimator.ofFloat(0.0f, 360.0f)

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
            .geometry(0, PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, 6 * 6)
            .material(0, materialInstance)
            .build(engine, renderable)

        scene.addEntity(renderable)

        //startAnimation()

        setContent {
            SlamAndroidTheme {
                SurfaceViewContent(Modifier.fillMaxSize(), MaterialTheme.colorScheme.background)
            }
        }
    }

    private fun setMesh() {
        val floatSize = 4
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

        val vertexCount = 6 * 4

        val tfPX = FloatArray(4)
        val tfNX = FloatArray(4)
        val tfPY = FloatArray(4)
        val tfNY = FloatArray(4)
        val tfPZ = FloatArray(4)
        val tfNZ = FloatArray(4)

        MathUtils.packTangentFrame(0.0f, 1.0f, 0.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, tfPX)
        MathUtils.packTangentFrame(0.0f, 1.0f, 0.0f, 0.0f, 0.0f, -1.0f, -1.0f, 0.0f, 0.0f, tfNX)
        MathUtils.packTangentFrame(-1.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f, tfPY)
        MathUtils.packTangentFrame(-1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, -1.0f, 0.0f, tfNY)
        MathUtils.packTangentFrame(0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, tfPZ)
        MathUtils.packTangentFrame(0.0f, -1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f, tfNZ)

        val vertexData = ByteBuffer.allocate(vertexCount * vertexSize)
            .order(ByteOrder.nativeOrder())
            // Face -Z
            .put(Vertex(-1.0f, -1.0f, -1.0f, tfNZ))
            .put(Vertex(-1.0f, 1.0f, -1.0f, tfNZ))
            .put(Vertex(1.0f, 1.0f, -1.0f, tfNZ))
            .put(Vertex(1.0f, -1.0f, -1.0f, tfNZ))
            // Face +X
            .put(Vertex(1.0f, -1.0f, -1.0f, tfPX))
            .put(Vertex(1.0f, 1.0f, -1.0f, tfPX))
            .put(Vertex(1.0f, 1.0f, 1.0f, tfPX))
            .put(Vertex(1.0f, -1.0f, 1.0f, tfPX))
            // Face +Z
            .put(Vertex(-1.0f, -1.0f, 1.0f, tfPZ))
            .put(Vertex(1.0f, -1.0f, 1.0f, tfPZ))
            .put(Vertex(1.0f, 1.0f, 1.0f, tfPZ))
            .put(Vertex(-1.0f, 1.0f, 1.0f, tfPZ))
            // Face -X
            .put(Vertex(-1.0f, -1.0f, 1.0f, tfNX))
            .put(Vertex(-1.0f, 1.0f, 1.0f, tfNX))
            .put(Vertex(-1.0f, 1.0f, -1.0f, tfNX))
            .put(Vertex(-1.0f, -1.0f, -1.0f, tfNX))
            // Face -Y
            .put(Vertex(-1.0f, -1.0f, 1.0f, tfNY))
            .put(Vertex(-1.0f, -1.0f, -1.0f, tfNY))
            .put(Vertex(1.0f, -1.0f, -1.0f, tfNY))
            .put(Vertex(1.0f, -1.0f, 1.0f, tfNY))
            // Face +Y
            .put(Vertex(-1.0f, 1.0f, -1.0f, tfPY))
            .put(Vertex(-1.0f, 1.0f, 1.0f, tfPY))
            .put(Vertex(1.0f, 1.0f, 1.0f, tfPY))
            .put(Vertex(1.0f, 1.0f, -1.0f, tfPY))
            .flip()

        vertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, vertexSize)
            .attribute(VertexAttribute.TANGENTS, 0, AttributeType.FLOAT4, 3 * floatSize, vertexSize)
            .build(engine)

        vertexBuffer.setBufferAt(engine, 0, vertexData)

        val indexData = ByteBuffer.allocate(6 * 2 * 3 * shortSize)
            .order(ByteOrder.nativeOrder())
        repeat(6) {
            val i = (it * 4).toShort()
            indexData
                .putShort(i).putShort((i + 1).toShort()).putShort((i + 2).toShort())
                .putShort(i).putShort((i + 2).toShort()).putShort((i + 3).toShort())
        }
        indexData.flip()

        indexBuffer = IndexBuffer.Builder()
            .indexCount(vertexCount * 2)
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

    private fun startAnimation() {
        animator.interpolator = LinearInterpolator()
        animator.duration = 4000
        animator.repeatMode = ValueAnimator.RESTART
        animator.repeatCount = ValueAnimator.INFINITE
        animator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            val transformMatrix = FloatArray(16)
            override fun onAnimationUpdate(a: ValueAnimator) {
                Matrix.setRotateM(transformMatrix, 0, -(a.animatedValue as Float), 0.0f, 0.0f, 1.0f)
                val tcm = engine.transformManager
                tcm.setTransform(tcm.getInstance(renderable), transformMatrix)
            }
        })
        animator.start()
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
        animator.start()
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameScheduler)
        animator.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()

        choreographer.removeFrameCallback(frameScheduler)
        animator.cancel()
        uiHelper.detach()

        engine.destroyEntity(renderable)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
        engine.destroyMaterial(material)

        val entityManager = EntityManager.get()
        entityManager.destroy(renderable)
        entityManager.destroy(camera.entity)

        engine.destroy()
    }
}