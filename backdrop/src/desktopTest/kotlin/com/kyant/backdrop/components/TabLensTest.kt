package com.kyant.backdrop.components

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabLensTest {
    @Test fun leadingEdgeTurnsBackWhileTrailingEdgeIsStillMovingTowardsTheDestination() {
        val tokens = GlassTabTokens()
        for (tracking in listOf(false, true)) for (destination in listOf(2f, -2f)) {
            fun edge(leading: Boolean) = TargetBasedAnimation(
                tokens.edgeSpring(leading, tracking).spec, Float.VectorConverter, 0f, destination)
            val start = edge(leading = destination < 0)
            val end = edge(leading = destination > 0)
            val strain = (0..600 step 2).map { ms ->
                val ns = ms * 1_000_000L
                tabEdgeStrain(start.getValueFromNanos(ns), end.getValueFromNanos(ns))
            }
            val lead = if (destination > 0) end else start
            val tail = if (destination > 0) start else end
            val reflecting = (2..600 step 2).any { ms ->
                val t = ms * 1_000_000L
                val previous = t - 2_000_000L
                (lead.getValueFromNanos(t) - lead.getValueFromNanos(previous)) * destination < 0 &&
                    (tail.getValueFromNanos(t) - tail.getValueFromNanos(previous)) * destination > 0
            }
            assertTrue("No opposing edge velocities at $destination, tracking=$tracking", reflecting)
            assertTrue("No stretch and compression at $destination", strain.max() > .04f && strain.min() < -.008f)
        }
    }

    @Test fun aStraightPlateRimRemainsLevelInsideBothFacesOfTheLens() {
        val source = Bitmap().apply { allocN32Pixels(480, 360); erase(0xff101010.toInt()) }
        val output = Bitmap().apply { allocN32Pixels(480, 360) }
        try {
            Canvas(source).use { canvas -> Paint().use { paint ->
                paint.color = -1
                canvas.drawRect(Rect.makeLTRB(0f, 84f, 480f, 276f), paint)
            } }
            RuntimeEffect.makeForShader(TabPlateShader).use { effect ->
                RuntimeShaderBuilder(effect).use { lens -> source.makeShader().use { child ->
                    lens.child("content", child)
                    lens.uniform("rect", 240f, 180f, 165f, 117f)
                    lens.uniform("plate", 240f, 180f, 220f, 96f)
                    lens.uniform("strength", 1f)
                    lens.uniform("density", 3f)
                    lens.uniform("bevelWidth", 8f * 3f)
                    lens.uniform("joinWidth", 3f * GlassTabTokens().rimJoinDepth.value)
                    lens.uniform("reduction", .135f)
                    for (dark in listOf(0f, 1f)) {
                        lens.uniform("dark", dark)
                        lens.makeShader().use { shader -> Canvas(output).use { canvas ->
                            Paint().use { paint -> paint.shader = shader; canvas.drawPaint(paint) }
                        } }
                        fun isPlate(x: Int, y: Int) = (output.getColor(x, y) and 255) >= 128
                        fun top(x: Int) = (78..107).first { isPlate(x, it) }
                        val xs = 153..327
                        val upper = xs.map(::top)
                        val lower = xs.map { x -> (253..284).last { isPlate(x, it) } }
                        assertTrue("Upper rim bowed: ${upper.min()}..${upper.max()}", upper.max() - upper.min() <= 1)
                        assertTrue("Lower rim bowed: ${lower.min()}..${lower.max()}", lower.max() - lower.min() <= 1)
                        assertTrue(upper.min() > 86 && lower.max() < 273)
                        for (direction in listOf(-1, 1)) {
                            val turn = (95..160).map { top(240 + direction * (240 - it)) }
                            val start = turn.indexOfFirst { it >= 86 }
                            val end = turn.indexOfFirst { it >= upper.min() - 1 }
                            assertTrue("Abrupt junction: $turn", start >= 0 && end - start >= 7)
                            assertTrue("Reversed shoulder: $turn", turn.zipWithNext().all { (a, b) -> b >= a - 1 })
                        }
                    }
                } }
            }
        } finally { source.close(); output.close() }
    }

    @Test fun theLensKeepsAUniformBackingNeutralAtEveryEdge() {
        val source = Bitmap().apply { allocN32Pixels(240, 140); erase(-1) }
        val output = Bitmap().apply { allocN32Pixels(240, 140); erase(0) }
        try {
            RuntimeEffect.makeForShader(TabMaterialShader).close()
            RuntimeEffect.makeForShader(TabPlateShader).use { effect ->
                RuntimeShaderBuilder(effect).use { lens ->
                    source.makeShader().use { child ->
                        lens.child("content", child)
                        lens.uniform("rect", 116f, 70f, 62f, 39f)
                        lens.uniform("strength", 1f)
                        lens.uniform("density", 1f)
                        lens.uniform("bevelWidth", 8f * 1f)
                        lens.uniform("reduction", .16f)
                        lens.uniform("plate", 120f, 70f, 102f, 32f)
                        lens.uniform("dispersion", 1.8f)
                        lens.uniform("diffusion", 2.3f)
                        lens.uniform("dark", 0f)
                        lens.makeShader().use { shader ->
                            Canvas(output).use { canvas ->
                                Paint().use { paint ->
                                    paint.shader = shader
                                    canvas.drawRect(Rect.makeWH(240f, 140f), paint)
                                }
                            }
                        }
                    }
                }
            }
            for (y in 31..109) for (x in 54..178) {
                assertEquals(-1, output.getColor(x, y))
            }
        } finally { source.close(); output.close() }
    }

    @Test fun liftingTheLensPreservesTheOrderOfScanlinesAcrossThePlateRim() {
        val source = Bitmap().apply { allocN32Pixels(160, 120) }
        val output = Bitmap().apply { allocN32Pixels(160, 120) }
        try {
            Canvas(source).use { canvas ->
                Paint().use { paint ->
                    for (y in 0 until source.height) {
                        val gray = 2 * y
                        paint.color = 0xff000000.toInt() or (gray * 0x010101)
                        canvas.drawRect(Rect.makeXYWH(0f, y.toFloat(), 160f, 1f), paint)
                    }
                }
            }
            RuntimeEffect.makeForShader(TabPlateShader).use { effect ->
                RuntimeShaderBuilder(effect).use { lens ->
                    source.makeShader().use { lens.child("content", it) }
                    lens.uniform("rect", 80f, 60f, 55f, 39f)
                    lens.uniform("plate", 80f, 60f, 73f, 32f)
                    lens.uniform("strength", 1f)
                    lens.uniform("density", 1f)
                    lens.uniform("bevelWidth", 8f * 1f)
                    lens.uniform("reduction", .135f)
                    lens.makeShader().use { shader ->
                        Canvas(output).use { canvas ->
                            Paint().use { paint -> paint.shader = shader; canvas.drawPaint(paint) }
                        }
                    }
                }
            }
            for (x in 36..80) {
                for (y in 17..39) {
                    val previous = output.getColor(x, y) and 255
                    val next = output.getColor(x, y + 1) and 255
                    assertTrue("Folded sample at ($x, $y)", next + 1 >= previous)
                }
            }
        } finally {
            source.close()
            output.close()
        }
    }

    @Test fun whiteBackingShowsANeutralGradientBetweenTheRaisedLensAndPlate() {
        val tokens = GlassTabTokens()
        Bitmap().use { source -> Bitmap().use { output ->
            source.allocN32Pixels(240, 140)
            output.allocN32Pixels(240, 140)
            source.erase(-1)
            RuntimeEffect.makeForShader(TabPlateShader).use { effect -> RuntimeShaderBuilder(effect).use { lens ->
                source.makeShader().use { lens.child("content", it) }
                lens.uniform("rect", 116f, 70f, 62f, 39f)
                lens.uniform("plate", 120f, 70f, 102f, 32f)
                lens.uniform("strength", 1f)
                lens.uniform("density", 1f)
                lens.uniform("bevelWidth", tokens.hoverBevelWidth.value)
                lens.uniform("joinWidth", tokens.rimJoinDepth.value)
                lens.uniform("reduction", .16f)
                lens.uniform("interfaceShade", tokens.lightInterfaceShade)
                lens.makeShader().use { shader -> Canvas(output).use { canvas -> Paint().use { paint ->
                    paint.shader = shader
                    canvas.drawPaint(paint)
                } } }
            } }
            for (side in listOf(-1, 1)) {
                val grays = (28..40).map { distance ->
                    val color = output.getColor(116, 70 + side * distance)
                    val gray = color and 255
                    assertEquals(0xff000000.toInt() or (gray * 0x010101), color)
                    gray
                }
                assertTrue("Missing separation: $grays", grays.min() in 237..249 && grays.distinct().size >= 4)
            }
            assertEquals(-1, output.getColor(116, 70))
            assertEquals(-1, output.getColor(116, 25))
        } }
    }

    @Test fun pressedLensKeepsInteriorInkInRegisterAndRefractsTheShoulder() {
        Bitmap().use { source -> Bitmap().use { output ->
            source.allocN32Pixels(160, 100)
            output.allocN32Pixels(160, 100)
            source.erase(0)
            Canvas(source).use { canvas -> Paint().use { paint ->
                paint.color = -1
                for (x in 0 until 160 step 5) canvas.drawRect(Rect.makeXYWH(x.toFloat(), 0f, 2f, 100f), paint)
            } }
            RuntimeEffect.makeForShader(TabInkShader).use { effect -> RuntimeShaderBuilder(effect).use { lens ->
                source.makeShader().use { child -> lens.child("content", child) }
                lens.uniform("rect", 80f, 50f, 56f, 34f)
                lens.uniform("density", 1f)
                lens.uniform("bevelWidth", GlassTabTokens().inkRefractionWidth.value)
                lens.uniform("refractionPx", GlassTabTokens().inkRefractionAmount.value)
                lens.uniform("strength", 1f)
                lens.uniform("ink", 1f, 1f, 1f, 1f)
                lens.makeShader().use { shader -> Canvas(output).use { canvas -> Paint().use { paint ->
                    paint.shader = shader
                    canvas.drawPaint(paint)
                } } }
            } }
            for (y in 35..65) for (x in 42..118) assertEquals(source.getColor(x,y), output.getColor(x,y))
            assertTrue((24..31).any { x -> source.getColor(x,50) != output.getColor(x,50) })
        } }
    }

    @Test fun selectedInkRespondsLocallyToTheSceneAndLeavesEmptyPixelsTransparent() {
        val source = Bitmap()
        val result = Bitmap()
        try {
            source.allocN32Pixels(128, 160)
            source.erase(0)
            result.allocN32Pixels(128, 160)
            Canvas(source).use { canvas ->
                Paint().use { paint ->
                    paint.color = -1
                    canvas.drawRect(Rect.makeLTRB(26f, 28f, 102f, 50f), paint)
                    paint.color = 0xff233756.toInt()
                    canvas.drawRect(Rect.makeLTRB(0f, 80f, 64f, 160f), paint)
                    paint.color = 0xff63c5b7.toInt()
                    canvas.drawRect(Rect.makeLTRB(64f, 80f, 128f, 160f), paint)
                }
            }
            RuntimeEffect.makeForShader(TabInkShader).use { effect ->
                RuntimeShaderBuilder(effect).use { lens ->
                    source.makeShader().use { child -> lens.child("content", child) }
                    lens.uniform("rect", 64f, 40f, 59f, 35f)
                    lens.uniform("strength", 1f)
                    lens.uniform("density", 1f)
                    lens.uniform("bevelWidth", 8f * 1f)
                    lens.uniform("darkSurface", 1f)
                    lens.uniform("sceneOffset", 80f)
                    lens.uniform("ink", 0f, .57f, 1f, 1f)
                    fun draw(vibrancy: Float) {
                        lens.uniform("vibrancy", vibrancy)
                        result.erase(0)
                        lens.makeShader().use { shader ->
                            Canvas(result).use { canvas ->
                                Paint().use { paint ->
                                    paint.shader = shader
                                    canvas.drawPaint(paint)
                                }
                            }
                        }
                    }
                    draw(0f)
                    assertEquals(result.getColor(46, 40), result.getColor(82, 40))
                    draw(1f)
                    val indigoRed = result.getColor(46, 40) ushr 16 and 255
                    val mintRed = result.getColor(82, 40) ushr 16 and 255
                    assertTrue(mintRed > indigoRed + 20)
                    assertEquals(0, result.getColor(64, 14))
                    assertEquals(0, result.getColor(64, 120))
                }
            }
        } finally {
            source.close()
            result.close()
        }
    }

    @Test fun aLiftedLensDrawsInteriorInkOutToBothCaps() {
        val tokens = GlassTabTokens()
        Bitmap().use { source -> Bitmap().use { output ->
            source.allocN32Pixels(160, 100)
            output.allocN32Pixels(160, 100)
            source.erase(0)
            Canvas(source).use { canvas -> Paint().use { paint ->
                paint.color = -1
                canvas.drawRect(Rect.makeLTRB(29f, 30f, 43f, 70f), paint)
                canvas.drawRect(Rect.makeLTRB(117f, 30f, 131f, 70f), paint)
            } }
            RuntimeEffect.makeForShader(TabInkShader).use { effect -> RuntimeShaderBuilder(effect).use { lens ->
                source.makeShader().use { lens.child("content", it) }
                lens.uniform("rect", 80f, 50f, 56f, 34f)
                lens.uniform("density", 1f)
                lens.uniform("strength", 1f)
                lens.uniform("bevelWidth", tokens.inkRefractionWidth.value)
                lens.uniform("refractionPx", tokens.inkRefractionAmount.value)
                lens.uniform("ink", 1f, 1f, 1f, 1f)
                lens.makeShader().use { shader -> Canvas(output).use { canvas -> Paint().use { paint ->
                    paint.shader = shader
                    canvas.drawPaint(paint)
                } } }
            } }
            for (x in listOf(25, 134)) {
                assertEquals(0, source.getColor(x, 49))
                assertTrue("The rim did not pick up the interior stripe at x=$x", output.getColor(x, 49) ushr 24 > 195)
            }
            for (x in 43..116) assertEquals(source.getColor(x, 49), output.getColor(x, 49))
        } }
    }

    @Test fun heldLensShowsOneCopyOfTheLabelAtItsFlatEdge() {
        val source = Bitmap().apply { allocN32Pixels(128, 80); erase(0) }
        val output = Bitmap().apply { allocN32Pixels(128, 80); erase(0) }
        Canvas(source).use { canvas ->
            Paint().use { paint -> canvas.drawRect(Rect.makeLTRB(45f, 56f, 84f, 59f), paint) }
        }
        RuntimeEffect.makeForShader(TabInkShader).use { effect ->
            RuntimeShaderBuilder(effect).use { lens ->
                source.makeShader().use { child ->
                    lens.child("content", child)
                    lens.uniform("rect", 64f, 40f, 56f, 34f)
                    lens.uniform("density", 1f)
                    lens.uniform("bevelWidth", 8f * 1f)
                    lens.uniform("strength", 1f)
                    lens.uniform("ink", 0.2f, 0.6f, 0.6f, 1f)
                    lens.makeShader().use { shader ->
                        Canvas(output).use { canvas ->
                            Paint().use { paint ->
                                paint.shader = shader
                                canvas.drawRect(Rect.makeWH(128f, 80f), paint)
                            }
                        }
                    }
                }
            }
        }
        val ink = (40..73).map { y -> (output.getColor(64, y) ushr 24) > 80 }
        assertEquals(1, ink.zipWithNext().count { (previous, next) -> !previous && next })
        source.close()
        output.close()
    }

    @Test fun liftedLensProducesVividChromaticDispersionAcrossTopAndBottomBevels() {
        val tokens = GlassTabTokens()
        Bitmap().use { source ->
            Bitmap().use { output ->
                source.allocN32Pixels(240, 140)
                output.allocN32Pixels(240, 140)
                source.erase(0xff000000.toInt())
                Canvas(source).use { canvas ->
                    Paint().use { paint ->
                        paint.color = -1
                        canvas.drawRect(Rect.makeLTRB(0f, 34f, 240f, 38f), paint)
                    }
                }
                RuntimeEffect.makeForShader(TabPlateShader).use { effect ->
                    RuntimeShaderBuilder(effect).use { lens ->
                        source.makeShader().use { child ->
                            lens.child("content", child)
                            lens.uniform("rect", 120f, 70f, 64f, 40f)
                            lens.uniform("plate", 120f, 70f, 102f, 32f)
                            lens.uniform("strength", 1f)
                            lens.uniform("density", 1f)
                            lens.uniform("bevelWidth", tokens.hoverBevelWidth.value)
                            lens.uniform("joinWidth", tokens.rimJoinDepth.value)
                            lens.uniform("reduction", tokens.plateMinification)
                            lens.uniform("dispersion", tokens.dispersion.value)
                            lens.uniform("diffusion", tokens.lensDiffusion.value * tokens.darkLensDiffusionScale)
                            lens.uniform("dark", 1f)
                            lens.makeShader().use { shader ->
                                Canvas(output).use { canvas ->
                                    Paint().use { paint ->
                                        paint.shader = shader
                                        canvas.drawPaint(paint)
                                    }
                                }
                            }
                        }
                    }
                }
                val maxChroma = (28..42).maxOf { y ->
                    val c = output.getColor(120, y)
                    val r = (c ushr 16) and 255
                    val g = (c ushr 8) and 255
                    val b = c and 255
                    maxOf(r, g, b) - minOf(r, g, b)
                }
                assertTrue("Expected visible chromatic dispersion at top bevel (q.x=0), got maxChroma=$maxChroma", maxChroma >= 45)
            }
        }
    }
}
