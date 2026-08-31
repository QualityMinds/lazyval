package com.qualityminds.lazyval.processor.internal;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Resolves the package a generator writes to, preferring its own override over the global
 * base-package default and falling back to a supplier when neither is configured.
 *
 * @see com.qualityminds.lazyval.processor.spi.Generator.Context#generatorPackage
 */
class PackageLookup {

    record DefaultConfig(String basePackage, @Nullable String layer){
        static @Nullable DefaultConfig of(@Nullable String basePackage, @Nullable String layer){
            if(basePackage == null){
                return null;
            }
            return new DefaultConfig(basePackage, layer);
        }

        String defaultPackage() {
            if(layer != null){
                return basePackage + "." + layer;
            }
            return basePackage;
        }
    }

    static String computePackage(@Nullable DefaultConfig config, @Nullable String generatorOverridePackage, Supplier<String> fallbackSupplier){
        if(generatorOverridePackage != null){
            return generatorOverridePackage;
        }else if(config != null){
            return config.defaultPackage();
        } else {
            return fallbackSupplier.get();
        }
    }

    private PackageLookup(){}
}
