package com.kavaliou.ksp.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.auto.service.AutoService

@AutoService(SymbolProcessorProvider::class)
class VilniusKugPrinterProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return VilniusKugPrinterProcessor(
            logger = environment.logger,
        )
    }
}
