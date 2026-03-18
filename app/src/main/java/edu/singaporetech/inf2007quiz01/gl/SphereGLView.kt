package edu.singaporetech.inf2007quiz01.gl

import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Compose wrapper for the OpenGL sphere renderer.
 *
 * Embeds a GLSurfaceView that displays the Fortran-raytraced sphere
 * using hand-written GLSL shaders with CRT scanline and chromatic
 * aberration effects.
 *
 * The rendering pipeline for a 64x64 image:
 *   Fortran (CPU raytrace) -> JNI -> ByteArray -> glTexSubImage2D -> GPU
 *
 * This is the most GPU-accelerated way to display a 4KB image.
 */
@Composable
fun SphereGLView(
    renderer: SphereRenderer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val glSurfaceView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            glSurfaceView.onPause()
        }
    }

    AndroidView(
        factory = { glSurfaceView },
        modifier = modifier
    )
}
