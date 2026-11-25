package com.kyant.backdrop.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.backdrop.RuntimeShader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** A capsule with a smooth change in curvature from each straight edge into its cap. */
val LiquidCapsule: Shape = GlassCapsuleShape()

// Integrate a smoothstep across the join. Value, slope and curvature match the two
// continuations at both ends. A square uses the exact circle (zero transition length).
private fun capAxis(x: Float, ease: Float): Float {
    if (x <= -ease) return 0f
    if (x >= ease) return x
    val u = (x + ease) / (2f * ease)
    return ease * u * u * u * (2f - u)
}

internal fun capsuleDistance(p: Offset, rect: Rect): Float {
    val horizontal = rect.width >= rect.height
    val r = rect.minDimension / 2f
    val straight = rect.maxDimension / 2f - r
    val axial = if (horizontal) abs(p.x - rect.center.x) else abs(p.y - rect.center.y)
    val side = if (horizontal) p.y - rect.center.y else p.x - rect.center.x
    return hypot(capAxis(axial - straight, minOf(.75f * r, straight)), side) - r
}

internal fun glassUnion(a: Float, b: Float, spacing: Float): Float {
    if (spacing <= 0f) return minOf(a, b)
    val k = 3f * spacing
    val h = (1f - abs(a - b) / k).coerceAtLeast(0f)
    return minOf(a, b) - .5f * spacing * h * h * h
}

internal fun facingAnchor(r: Rect, other: Rect): Offset {
    val extent = (r.maxDimension - r.minDimension) / 2f
    val d = other.center - r.center
    return r.center + if (r.width >= r.height) Offset(d.x.coerceIn(-extent, extent), 0f)
    else Offset(0f, d.y.coerceIn(-extent, extent))
}

internal fun capsuleBridgeDistance(p: Offset, a: Rect, b: Rect): Float {
    val pa = facingAnchor(a, b)
    val pb = facingAnchor(b, a)
    val ab = pb - pa
    val lenSq = ab.x * ab.x + ab.y * ab.y
    val t = if (lenSq > .001f) (((p.x - pa.x) * ab.x + (p.y - pa.y) * ab.y) / lenSq).coerceIn(0f, 1f) else 0f
    val proj = Offset(pa.x + ab.x * t, pa.y + ab.y * t)
    val ra = a.minDimension / 2f
    val rb = b.minDimension / 2f
    return hypot(p.x - proj.x, p.y - proj.y) - (ra + (rb - ra) * t)
}

internal fun fusedPairDistance(p: Offset, a: Rect, b: Rect, spacing: Float): Float {
    val dA = capsuleDistance(p, a)
    val dB = capsuleDistance(p, b)
    val rawMin = minOf(dA, dB)
    if (spacing <= 0f) return rawMin
    val smin = glassUnion(dA, dB, spacing)
    val envelope = minOf(rawMin, capsuleBridgeDistance(p, a, b))
    return maxOf(smin, envelope)
}

internal fun glassUnionAll(p: Offset, rects: List<Rect>, spacing: Float): Float {
    if (rects.isEmpty()) return Float.POSITIVE_INFINITY
    var acc = capsuleDistance(p, rects[0])
    for (i in 1 until rects.size) {
        acc = minOf(acc, capsuleDistance(p, rects[i]))
    }
    if (spacing <= 0f || rects.size < 2) return acc
    val reach = spacing * 3f
    for (i in rects.indices) {
        val ri = rects[i]
        for (j in i + 1 until rects.size) {
            val rj = rects[j]
            if (ri.inflate(reach).overlaps(rj)) {
                acc = minOf(acc, fusedPairDistance(p, ri, rj, spacing))
            }
        }
    }
    return acc
}

internal fun appendFusionNeckPath(
    target: Path,
    a: Rect,
    b: Rect,
    spacing: Float,
    densityPx: Float,
) {
    val reach = spacing * 3f
    if (reach <= 0f || !a.inflate(reach).overlaps(b)) return
    val from = facingAnchor(a, b)
    val to = facingAnchor(b, a)
    val length = (to - from).getDistance()
    if (length < .001f) return
    val along = (to - from) / length
    val normal = Offset(-along.y, along.x)
    val limit = maxOf(a.maxDimension, b.maxDimension) + reach
    fun border(p: Offset, sign: Float): Offset {
        var lo = 0f
        var hi = limit
        repeat(5) {
            val m = (lo + hi) * 0.5f
            if (fusedPairDistance(p + normal * (sign * m), a, b, spacing) <= 0f) lo = m else hi = m
        }
        return p + normal * (sign * (lo + hi) * 0.5f)
    }
    val steps = ceil(length / (6.0f * densityPx)).toInt().coerceIn(5, 10)
    val lowerX = FloatArray(steps + 1)
    val lowerY = FloatArray(steps + 1)
    var lowerCount = 0
    fun finish() {
        if (lowerCount == 0) return
        for (i in lowerCount - 1 downTo 0) {
            target.lineTo(lowerX[i], lowerY[i])
        }
        target.close()
        lowerCount = 0
    }
    for (i in 0..steps) {
        val p = from + along * (length * i / steps)
        if (fusedPairDistance(p, a, b, spacing) > 0f) { finish(); continue }
        val upper = border(p, -1f)
        if (lowerCount == 0) target.moveTo(upper.x, upper.y) else target.lineTo(upper.x, upper.y)
        val lower = border(p, 1f)
        lowerX[lowerCount] = lower.x
        lowerY[lowerCount] = lower.y
        lowerCount++
    }
    finish()
}

internal fun buildFusionNeckPath(
    a: Rect,
    b: Rect,
    spacing: Float,
    densityPx: Float,
    distanceFn: (Offset) -> Float,
): Path {
    val reach = spacing * 3f
    if (reach <= 0f || !a.inflate(reach).overlaps(b)) return Path()
    fun facing(r: Rect, other: Rect): Offset {
        val extent = (r.maxDimension - r.minDimension) / 2f
        val d = other.center - r.center
        return r.center + if (r.width >= r.height) Offset(d.x.coerceIn(-extent, extent), 0f)
        else Offset(0f, d.y.coerceIn(-extent, extent))
    }
    val from = facing(a, b)
    val to = facing(b, a)
    val length = (to - from).getDistance()
    if (length < .001f) return Path()
    val along = (to - from) / length
    val normal = Offset(-along.y, along.x)
    val limit = maxOf(a.maxDimension, b.maxDimension) + reach
    fun border(p: Offset, sign: Float): Offset {
        var lo = 0f
        var hi = limit
        repeat(8) {
            val m = (lo + hi) / 2f
            if (distanceFn(p + normal * (sign * m)) <= 0f) lo = m else hi = m
        }
        return p + normal * (sign * (lo + hi) / 2f)
    }
    val steps = ceil(length / (3.0f * densityPx)).toInt().coerceIn(8, 22)
    val neck = Path()
    val lower = mutableListOf<Offset>()
    fun finish() {
        if (lower.isEmpty()) return
        for (i in lower.indices.reversed()) {
            val p = lower[i]
            neck.lineTo(p.x, p.y)
        }
        neck.close()
        lower.clear()
    }
    for (i in 0..steps) {
        val p = from + along * (length * i / steps)
        if (distanceFn(p) > 0f) { finish(); continue }
        val upper = border(p, -1f)
        if (lower.isEmpty()) neck.moveTo(upper.x, upper.y) else neck.lineTo(upper.x, upper.y)
        lower += border(p, 1f)
    }
    finish()
    return neck
}

internal fun Path.addLiquidCapsule(rect: Rect) {
    if (rect.isEmpty) return
    if (abs(rect.width - rect.height) <= .02f) {
        val cx = rect.center.x
        val cy = rect.center.y
        val r = minOf(rect.width, rect.height) * 0.5f
        val c = r * 0.5522848f
        // Strictly clockwise circle so NonZero unions with clockwise fusion necks never cancel interiors.
        moveTo(cx, cy - r)
        cubicTo(cx + c, cy - r, cx + r, cy - c, cx + r, cy)
        cubicTo(cx + r, cy + c, cx + c, cy + r, cx, cy + r)
        cubicTo(cx - c, cy + r, cx - r, cy + c, cx - r, cy)
        cubicTo(cx - r, cy - c, cx - c, cy - r, cx, cy - r)
        close()
        return
    }
    val horizontal = rect.width > rect.height
    val r = rect.minDimension / 2f
    val straight = (rect.maxDimension / 2f - r).coerceAtLeast(.01f)
    val ease = minOf(.75f * r, straight).coerceAtLeast(.01f)
    val start = straight - ease
    val span = r + ease
    val maxSlopeY = sqrt(r * span) * 1.5f
    fun orient(x: Float, y: Float): Offset = if (horizontal) Offset(x, y) else Offset(-y, x)
    fun point(x: Float, y: Float) = rect.center + orient(x, y)
    // Hermite segments follow the same implicit contour used by the lens and rim.
    fun quadrant(sx: Float, sy: Float, reverse: Boolean) {
        val step = (PI / 16).toFloat() * if (reverse) -1f else 1f
        var previous = Offset.Zero
        var tangent = Offset.Zero
        for (i in 0..8) {
            val t = (if (reverse) 8 - i else i) * (PI / 16).toFloat()
            val x = start + span * sin(t)
            val q = capAxis(x - straight, ease)
            val y = sqrt(((r - q) * (r + q)).coerceAtLeast(0f))
            val u = ((x - straight + ease) / (2f * ease)).coerceIn(0f, 1f)
            val slope = u * u * (3f - 2f * u)
            val dx = span * cos(t)
            val rawDy = if (y > .02f) -q * slope * dx / y else -sqrt(r * span)
            val dy = if (rawDy.isFinite()) rawDy.coerceIn(-maxSlopeY, maxSlopeY) else -sqrt(r * span)
            val here = point(sx * x, sy * y)
            val derivative = orient(sx * dx, sy * dy) * (step / 3f)
            if (i == 0) lineTo(here.x, here.y) else {
                val first = previous + tangent
                val last = here - derivative
                cubicTo(first.x, first.y, last.x, last.y, here.x, here.y)
            }
            previous = here
            tangent = derivative
        }
    }
    val top = point(start, -r)
    moveTo(top.x, top.y)
    quadrant(1f, -1f, false); quadrant(1f, 1f, true)
    quadrant(-1f, 1f, false); quadrant(-1f, -1f, true)
    close()
}

private const val SingleCapsuleOutlineCacheCap = 32
private val singleCapsuleOutlineCache = mutableMapOf<Long, Outline>()

internal data class GlassCapsuleShape(
    val first: Rect? = null, val second: Rect? = null, val spacing: Float = 0f,
) : Shape {
    fun firstRect(size: Size) = first ?: Rect(Offset.Zero, size)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (first == null && second == null) {
            val key = (size.width.toRawBits().toLong() shl 32) or (size.height.toRawBits().toLong() and 0xffffffffL)
            singleCapsuleOutlineCache[key]?.let { return it }
            val built = Outline.Generic(Path().apply { addLiquidCapsule(Rect(Offset.Zero, size)) })
            if (singleCapsuleOutlineCache.size >= SingleCapsuleOutlineCacheCap) {
                singleCapsuleOutlineCache.clear()
            }
            singleCapsuleOutlineCache[key] = built
            return built
        }
        val a = firstRect(size)
        val lobes = Path().apply {
            fillType = PathFillType.NonZero
            addLiquidCapsule(a)
        }
        val b = second ?: return Outline.Generic(lobes)
        lobes.addLiquidCapsule(b)
        val reach = spacing * 3f
        if (reach > 0f && a.inflate(reach).overlaps(b)) {
            appendFusionNeckPath(lobes, a, b, spacing, density.density)
        }
        return Outline.Generic(lobes)
    }
}

/** Smooth-union SDF shape across N arbitrary capsule lobes using fast non-boolean fusion bridges. */
internal data class GlassCapsulesShape(
    val rects: List<Rect>,
    val spacing: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val valid = rects.filter { it.width >= 2f && it.height >= 2f }
        if (valid.isEmpty()) return Outline.Generic(Path().apply { addLiquidCapsule(Rect(Offset.Zero, size)) })
        if (valid.size == 1) return Outline.Generic(Path().apply { addLiquidCapsule(valid[0]) })
        if (valid.size == 2) return GlassCapsuleShape(valid[0], valid[1], spacing).createOutline(size, layoutDirection, density)

        val combined = Path().apply { fillType = PathFillType.NonZero }
        for (i in valid.indices) {
            combined.addLiquidCapsule(valid[i])
        }
        val reach = spacing * 3f
        if (reach > 0f) {
            val densityPx = density.density
            for (i in valid.indices) {
                val ri = valid[i]
                for (j in i + 1 until valid.size) {
                    val rj = valid[j]
                    if (ri.inflate(reach).overlaps(rj)) {
                        appendFusionNeckPath(combined, ri, rj, spacing, densityPx)
                    }
                }
            }
        }
        return Outline.Generic(combined)
    }
}

internal fun RuntimeShader.setCapsules(shape: Shape, size: Size) {
    fun setRect(name: String, rect: Rect?) {
        if (rect == null) setFloatUniform(name, 0f, 0f, 0f, 0f)
        else setFloatUniform(name, rect.center.x, rect.center.y, rect.width / 2f, rect.height / 2f)
    }
    when (shape) {
        is GlassCapsuleShape -> {
            setRect("capA", shape.firstRect(size))
            setRect("capB", shape.second)
            setRect("capC", null)
            setRect("capD", null)
            setFloatUniform("capSpacing", shape.spacing)
        }
        is GlassCapsulesShape -> {
            val r = shape.rects
            setRect("capA", r.getOrNull(0))
            setRect("capB", r.getOrNull(1))
            setRect("capC", r.getOrNull(2))
            setRect("capD", r.getOrNull(3))
            setFloatUniform("capSpacing", shape.spacing)
        }
        else -> {
            setRect("capA", null)
            setRect("capB", null)
            setRect("capC", null)
            setRect("capD", null)
            setFloatUniform("capSpacing", 0f)
        }
    }
}

internal const val GlassCapsuleSDF = """
float sdGlassCap(float2 p, float2 b) {
    float2 v = b.x >= b.y ? abs(p) : abs(p.yx);
    float r = min(b.x, b.y), straight = max(b.x, b.y) - r;
    float e = min(0.75 * r, straight), x = v.x - straight;
    float u = clamp((x + e) / max(2.0 * e, 0.0001), 0.0, 1.0);
    float q = x >= e ? x : e * u * u * u * (2.0 - u);
    return length(float2(q, v.y)) - r;
}
"""
internal const val GlassCapsuleField = """
uniform float4 capA;
uniform float4 capB;
uniform float4 capC;
uniform float4 capD;
uniform float capSpacing;
$GlassCapsuleSDF
float sminCap(float a, float b, float s) {
    if (s <= 0.0) return min(a, b);
    float h = max(0.0, 1.0 - abs(a - b) / max(3.0 * s, 0.0001));
    return min(a, b) - 0.5 * s * h * h * h;
}
float2 capFacing(float4 c, float4 other) {
    float2 b = c.zw;
    float r = min(b.x, b.y);
    float extent = max(max(b.x, b.y) - r, 0.0);
    float2 d = other.xy - c.xy;
    return c.xy + (b.x >= b.y ? float2(clamp(d.x, -extent, extent), 0.0) : float2(0.0, clamp(d.y, -extent, extent)));
}
float sdBridge(float2 p, float4 ca, float4 cb) {
    float2 pa = capFacing(ca, cb);
    float2 pb = capFacing(cb, ca);
    float2 ab = pb - pa;
    float lenSq = dot(ab, ab);
    float t = lenSq > 0.001 ? clamp(dot(p - pa, ab) / lenSq, 0.0, 1.0) : 0.0;
    float ra = min(ca.z, ca.w);
    float rb = min(cb.z, cb.w);
    return length(p - (pa + ab * t)) - mix(ra, rb, t);
}
float pairCapDist(float2 p, float4 ca, float4 cb, float s) {
    float dA = sdGlassCap(p - ca.xy, ca.zw);
    float dB = sdGlassCap(p - cb.xy, cb.zw);
    float rawMin = min(dA, dB);
    if (s <= 0.0) return rawMin;
    return max(sminCap(dA, dB, s), min(rawMin, sdBridge(p, ca, cb)));
}
float capDistance(float2 p) {
    float d = sdGlassCap(p - capA.xy, capA.zw);
    if (capB.z > 0.0) d = min(d, sdGlassCap(p - capB.xy, capB.zw));
    if (capC.z > 0.0) d = min(d, sdGlassCap(p - capC.xy, capC.zw));
    if (capD.z > 0.0) d = min(d, sdGlassCap(p - capD.xy, capD.zw));
    if (capSpacing > 0.0) {
        if (capB.z > 0.0) d = min(d, pairCapDist(p, capA, capB, capSpacing));
        if (capC.z > 0.0) d = min(d, pairCapDist(p, capA, capC, capSpacing));
        if (capD.z > 0.0) d = min(d, pairCapDist(p, capA, capD, capSpacing));
        if (capB.z > 0.0 && capC.z > 0.0) d = min(d, pairCapDist(p, capB, capC, capSpacing));
        if (capB.z > 0.0 && capD.z > 0.0) d = min(d, pairCapDist(p, capB, capD, capSpacing));
        if (capC.z > 0.0 && capD.z > 0.0) d = min(d, pairCapDist(p, capC, capD, capSpacing));
    }
    return d;
}
float2 capNormal(float2 p) {
    float2 e = float2(0.55, 0);
    float2 g = float2(capDistance(p+e)-capDistance(p-e), capDistance(p+e.yx)-capDistance(p-e.yx));
    return g / max(length(g), 0.0001);
}
"""
