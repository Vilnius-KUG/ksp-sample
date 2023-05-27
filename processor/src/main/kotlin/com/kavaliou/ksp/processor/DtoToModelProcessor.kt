package com.kavaliou.ksp.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.*
import com.kavaliou.ksp.annotation.DtoToModel
import com.kavaliou.ksp.processor.modelgen.generateModelClasses

/** Processes [DtoToModel] annotations in order to create 'model' representations of annotated classes. */
class DtoToModelProcessor(
    private val logger: KSPLogger,
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotationName: String = DtoToModel::class.qualifiedName
            ?: throw IllegalStateException("Annotation name could not be retrieved.")

        val fileToClassMap = mutableMapOf<KSFile, MutableSet<KSClassDeclaration>>()
        resolver.getSymbolsWithAnnotation(annotationName)
            .filterIsInstance(KSClassDeclaration::class.java)
            .forEach { annotated ->
                val file = annotated.findClass()
                if (fileToClassMap.containsKey(file)) {
                    fileToClassMap[file]?.add(annotated)
                } else {
                    fileToClassMap[file] = mutableSetOf(annotated)
                }
            }

        generateModelClasses(codeGenerator, logger, fileToClassMap)

        return emptyList()
    }

    /** Returns a [KSFile] for the node. */
    private fun KSNode.findClass(): KSFile {
        var parent = this.parent
        while (parent != null) {
            if (parent is KSFile) {
                return parent
            } else {
                parent = parent.parent
            }
        }
        logger.error("Can't find enclosing file!", this)
        throw IllegalStateException("Can't find enclosing file!")
    }
}
