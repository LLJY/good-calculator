package edu.singaporetech.inf2007quiz01.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import edu.singaporetech.inf2007quiz01.FortranBridge
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Pure OpenGL ES 2.0 renderer that displays the Fortran raytraced sphere.
 *
 * This is a hand-written GLSL shader pipeline that:
 * 1. Creates a fullscreen quad (2 triangles, 6 vertices)
 * 2. Uploads the 64x64 Fortran framebuffer as a GL_TEXTURE_2D
 * 3. Renders the textured quad with GL_NEAREST filtering
 *    (because pixel art aesthetics match the vaporwave theme)
 *
 * The Fortran raytracer computes Lambertian diffuse shading on a
 * unit sphere. This OpenGL renderer then displays that result using
 * a full GPU pipeline — vertex shader, rasterizer, fragment shader,
 * texture sampler — to show a 64x64 image that could have been
 * displayed as a Bitmap in an ImageView.
 *
 * Language count contribution: GLSL (vertex + fragment shaders)
 */
class SphereRenderer : GLSurfaceView.Renderer {

    // --- Shader sources (GLSL ES 100) ---

    private val vertexShaderSource = """
        // Vertex shader for the Fortran sphere quad.
        // A full GPU vertex pipeline to position 4 corners of a rectangle.
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    private val fragmentShaderSource = """
        // Fragment shader for the Fortran sphere quad.
        // Samples a 64x64 texture that was raytraced in Fortran,
        // passed through JNI, uploaded via glTexImage2D, and is
        // now being rendered by the GPU. For a calculator.
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform float uTime;
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            // Subtle CRT scanline effect for extra vaporwave
            float scanline = 0.95 + 0.05 * sin(vTexCoord.y * 64.0 * 3.14159);
            // Chromatic aberration because why not
            float shift = sin(uTime * 2.0) * 0.005;
            float r = texture2D(uTexture, vTexCoord + vec2(shift, 0.0)).r;
            float g = color.g;
            float b = texture2D(uTexture, vTexCoord - vec2(shift, 0.0)).b;
            gl_FragColor = vec4(r, g, b, color.a) * scanline;
        }
    """.trimIndent()

    // --- Geometry: fullscreen quad ---

    private val quadVertices = floatArrayOf(
        // positions     // texcoords
        -1f, -1f, 0f,   0f, 1f,
         1f, -1f, 0f,   1f, 1f,
        -1f,  1f, 0f,   0f, 0f,
         1f,  1f, 0f,   1f, 0f,
    )

    private val quadIndices = shortArrayOf(0, 1, 2, 2, 1, 3)

    // --- GL handles ---

    private var program = 0
    private var textureId = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var textureUniform = 0
    private var timeUniform = 0
    private var vertexBuffer: FloatBuffer? = null
    private var indexBuffer: java.nio.ShortBuffer? = null
    private var startTime = System.nanoTime()

    // --- Pixel data from Fortran ---

    @Volatile
    private var pendingPixels: ByteArray? = null

    /**
     * Queue new pixel data from the Fortran raytracer.
     * Called from the UI/consensus thread; consumed on the GL thread.
     */
    fun updatePixels(rgba: ByteArray?) {
        pendingPixels = rgba
    }

    // --- GLSurfaceView.Renderer implementation ---

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.05f, 0.01f, 0.13f, 1f)  // deep purple

        // Compile shaders
        val vertShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        val fragShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertShader)
        GLES20.glAttachShader(program, fragShader)
        GLES20.glLinkProgram(program)

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureUniform = GLES20.glGetUniformLocation(program, "uTexture")
        timeUniform = GLES20.glGetUniformLocation(program, "uTime")

        // Create vertex buffer
        vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadVertices)
            .also { it.position(0) }

        indexBuffer = ByteBuffer.allocateDirect(quadIndices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(quadIndices)
            .also { it.position(0) }

        // Create texture
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        textureId = texIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // Upload initial black texture
        val black = ByteBuffer.allocateDirect(64 * 64 * 4).also {
            for (i in 0 until 64 * 64) {
                it.put(13); it.put(2); it.put(33); it.put(-1)  // vaporwave purple
            }
            it.position(0)
        }
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            64, 64, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, black)

        startTime = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Upload pending pixels from Fortran if available
        pendingPixels?.let { pixels ->
            pendingPixels = null
            val buf = ByteBuffer.allocateDirect(pixels.size)
                .order(ByteOrder.nativeOrder())
                .put(pixels)
                .also { it.position(0) }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0,
                64, 64, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        // Time uniform for CRT effect animation
        val elapsed = (System.nanoTime() - startTime) / 1_000_000_000f
        GLES20.glUniform1f(timeUniform, elapsed)

        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureUniform, 0)

        // Position attribute (3 floats, stride 5 floats)
        vertexBuffer?.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT,
            false, 5 * 4, vertexBuffer)

        // TexCoord attribute (2 floats, offset 3)
        vertexBuffer?.position(3)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT,
            false, 5 * 4, vertexBuffer)

        // Draw
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6,
            GLES20.GL_UNSIGNED_SHORT, indexBuffer)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    // --- Shader compilation ---

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }
}
