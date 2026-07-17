package com.qualityminds.lazyval.ksp.internal

import java.util.function.Supplier

/**
 * @see com.qualityminds.lazyval.ksp.spi.Generator.Context.generatorPackage
 */
object PackageLookup {

    @JvmRecord
    data class DefaultConfig(val basePackage: String, val layer: String?) {
        fun defaultPackage(): String {
            if (layer != null) {
                return "$basePackage.$layer"
            }
            return basePackage
        }

        companion object {
            @JvmStatic
            fun of(basePackage: String?, layer: String?): DefaultConfig? {
                if (basePackage == null) {
                    return null
                }
                return DefaultConfig(basePackage, layer)
            }
        }
    }

    @JvmStatic
    fun computePackage(
        config: DefaultConfig?,
        generatorOverridePackage: String?,
        fallbackSupplier: Supplier<String>
    ): String {
        return generatorOverridePackage
            ?: (config?.defaultPackage() ?: fallbackSupplier.get())
    }

}