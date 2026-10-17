package com.jarvis.interfaceapp.avatar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform

/** Renderer replacement boundary; normalized 240 x 320 artwork, no animation clock or storage. */
interface AvatarArtwork {
    val resourceId: String
    fun DrawScope.render(pose: AvatarPose)
    /** One artwork instance per host; release future textures/players here. */
    fun release() {}
}

/** Original procedural demonstration, not the final character or a flattened picture.
 * Cached independent shapes: rear hair, uniform, neck, face, eyes/lids, front hair.
 */
class DemoAvatarArtwork : AvatarArtwork {
    override val resourceId = "jarvis.demo.adult-vector.v1"
    private val cyan = Color(0xFF65DCE9)
    private val skin = Brush.linearGradient(listOf(Color(0xFFFFDCC6), Color(0xFFDCA68B)), Offset(80f, 80f), Offset(160f, 180f))
    private val hair = Brush.linearGradient(listOf(Color(0xFF684A40), Color(0xFF302326)), Offset(70f, 30f), Offset(165f, 220f))
    private val suit = Brush.linearGradient(listOf(Color(0xFF263B5A), Color(0xFF0A1328)), Offset(40f, 210f), Offset(190f, 320f))
    private val backHair = Path().apply {
        moveTo(64f, 99f); cubicTo(55f, 3f, 172f, -3f, 180f, 91f)
        cubicTo(188f, 145f, 169f, 171f, 193f, 231f)
        quadraticTo(120f, 264f, 47f, 231f); cubicTo(73f, 185f, 47f, 143f, 64f, 99f); close()
    }
    private val torso = Path().apply {
        moveTo(14f, 320f); lineTo(24f, 258f); quadraticTo(37f, 232f, 91f, 210f)
        lineTo(149f, 210f); quadraticTo(203f, 232f, 216f, 258f); lineTo(226f, 320f); close()
    }
    private val face = Path().apply {
        moveTo(78f, 76f); cubicTo(77f, 48f, 163f, 48f, 162f, 82f)
        lineTo(158f, 137f); quadraticTo(151f, 168f, 120f, 181f)
        quadraticTo(89f, 168f, 82f, 137f); close()
    }
    private val fringe = Path().apply {
        moveTo(64f, 98f); cubicTo(54f, 24f, 111f, 13f, 140f, 36f)
        cubicTo(167f, 30f, 181f, 61f, 171f, 114f)
        quadraticTo(158f, 83f, 151f, 62f); quadraticTo(132f, 88f, 103f, 89f)
        lineTo(125f, 62f); quadraticTo(93f, 95f, 71f, 115f); close()
    }
    private val leftHair = Path().apply {
        moveTo(73f, 80f); quadraticTo(88f, 122f, 73f, 165f)
        quadraticTo(62f, 195f, 78f, 226f); quadraticTo(39f, 208f, 58f, 146f); close()
    }
    private val rightHair = Path().apply {
        moveTo(163f, 72f); quadraticTo(178f, 124f, 164f, 172f)
        quadraticTo(159f, 199f, 180f, 230f); quadraticTo(197f, 202f, 180f, 151f); close()
    }
    private val collar = Path().apply {
        moveTo(87f, 210f); lineTo(120f, 241f); lineTo(153f, 210f)
        lineTo(164f, 222f); lineTo(120f, 260f); lineTo(76f, 222f); close()
    }
    private val mouth = Path().apply { moveTo(110f, 151f); quadraticTo(120f, 156f, 130f, 151f) }
    private val eyes = floatArrayOf(99f, 141f)
    private val lid = Path()
    override fun DrawScope.render(pose: AvatarPose) {
        drawRect(Color(0xFF101C2C), size = Size(240f, 320f))
        drawCircle(cyan.copy(alpha = 0.08f), 120f, Offset(120f, 114f))
        // Rear plane responds less than the face/front strands: depth parallax.
        withTransform({ translate(0f, -pose.breath); rotate(pose.headTilt * 0.45f, Offset(120f, 188f)) }) {
            drawPath(backHair, hair)
        }
        withTransform({
            translate(0f, -pose.breath * 2f)
            scale(1f + pose.breath * 0.012f, 1f + pose.breath * 0.008f, Offset(120f, 320f))
        }) {
            drawPath(torso, suit)
            drawOval(skin, Offset(104f, 165f), Size(32f, 69f))
            drawPath(collar, Color(0xFF0D192C))
            drawPath(collar, cyan.copy(alpha = 0.8f), style = Stroke(1.5f))
            drawLine(cyan.copy(alpha = 0.5f), Offset(120f, 260f), Offset(120f, 320f), 1.2f)
            drawRoundRect(cyan, Offset(167f, 264f), Size(18f, 3f))
        }
        withTransform({ translate(0f, -pose.breath * 1.5f); rotate(pose.headTilt, Offset(120f, 188f)) }) {
            drawOval(skin, Offset(75f, 113f), Size(13f, 25f))
            drawOval(skin, Offset(152f, 113f), Size(13f, 25f))
            drawPath(face, skin)
            drawPath(face, Color(0xFFB88072).copy(alpha = 0.4f), style = Stroke(1f))
            for (x in eyes) {
                val openness = 1f - pose.eyelid.coerceIn(0f, 1f)
                clipRect(x - 13f, 116f - 7f * openness, x + 13f, 116f + 6f * openness) {
                    drawOval(Color(0xFFFFF3E8), Offset(x - 12f, 109f), Size(24f, 13f))
                    drawCircle(Color(0xFF756956), 5.5f, Offset(x, 116f))
                    drawCircle(Color(0xFF25242B), 2.8f, Offset(x, 116f))
                    drawCircle(cyan.copy(alpha = 0.85f), 1.4f, Offset(x + 2f, 113f))
                }
                lid.reset()
                lid.apply {
                    moveTo(x - 12f, 116f); quadraticTo(x, 116f - 10f * openness, x + 12f, 116f)
                }
                drawPath(lid, Color(0xFF3E2930), style = Stroke(1.8f))
                drawLine(Color(0xFF674338), Offset(x - 10f, 101f), Offset(x + 9f, 100f), 2f)
            }
            val parallax = pose.headTilt * 0.22f
            drawLine(Color(0xFFC68D7B), Offset(120f + parallax, 124f), Offset(117f + parallax, 138f), 1.2f)
            drawOval(Color(0xFFD9928C).copy(alpha = 0.2f), Offset(87f, 133f), Size(15f, 7f))
            drawOval(Color(0xFFD9928C).copy(alpha = 0.2f), Offset(138f, 133f), Size(15f, 7f))

            drawPath(mouth, Color(0xFF9F5F64), style = Stroke(1.5f))
            withTransform({ translate(parallax, 0f) }) {
                drawPath(fringe, hair); drawPath(leftHair, hair); drawPath(rightHair, hair)
                drawLine(Color(0xFFAB8570).copy(alpha = 0.6f), Offset(75f, 123f), Offset(68f, 187f), 1.3f)
                drawLine(cyan.copy(alpha = 0.22f), Offset(178f, 146f), Offset(179f, 201f), 1f)
            }
        }
    }
}

/** Emergency drawing independent of replaceable assets; never calls a failing renderer again. */
fun DrawScope.drawStaticAvatarFallback() {
    drawRect(Color(0xFF101C2C), size = Size(240f, 320f))
    drawOval(Color(0xFF302326), Offset(65f, 24f), Size(110f, 210f))
    drawOval(Color(0xFF263B5A), Offset(30f, 210f), Size(180f, 180f))
    drawOval(Color(0xFFE5B69D), Offset(83f, 61f), Size(74f, 115f))
    drawLine(Color(0xFF302326), Offset(96f, 116f), Offset(107f, 116f), 2f)
    drawLine(Color(0xFF302326), Offset(133f, 116f), Offset(144f, 116f), 2f)
}
