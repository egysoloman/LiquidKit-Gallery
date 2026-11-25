package com.kyant.backdrop

import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return runtimeShaders.getOrPut(key) { RuntimeShader(string) }
    }

    fun clear() {
        runtimeShaders.clear()
    }
}

/**
 * Shared AGSL compiler cache across BackdropEffectScope instances. RenderEffect.createRuntimeShaderEffect
 * snapshots uniforms at creation time, so reusing compiled RuntimeShader templates per shader source
 * avoids redundant AGSL compilation across cards, pills, and page transitions.
 */
internal object SharedRenderEffectShaderCache : RuntimeShaderCache {

    private const val MaxEntries = 64
    private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        runtimeShaders[string]?.let { return it }
        if (runtimeShaders.size >= MaxEntries) runtimeShaders.clear()
        return RuntimeShader(string).also { runtimeShaders[string] = it }
    }
}
