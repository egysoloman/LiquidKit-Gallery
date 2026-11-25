package com.kyant.backdrop.components

/**
 * Asynchronously warms core Liquid Glass GPU shader pipelines off the main UI thread so
 * the first touch/drag or sheet presentation does not hit a synchronous RenderThread stall.
 */
expect fun warmUpLiquidGlassPipelines()
