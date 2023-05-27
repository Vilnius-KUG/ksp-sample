package com.kavaliou.ksp.processor.modelgen

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*
import com.kavaliou.ksp.annotation.IgnoreInModel
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toKModifier
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

/** Generates model classes. */
internal fun generateModelClasses(
    codeGenerator: CodeGenerator,
    logger: KSPLogger,
    fileToClassMap: MutableMap<KSFile, MutableSet<KSClassDeclaration>>
) {
    fileToClassMap.forEach { (ksFile, classDeclarationSet) ->
        logger.logging("[DtoToModel] converting classes in file", ksFile)
        classDeclarationSet
            .map { classDeclaration ->
                logger.logging("[DtoToModel] converting class", classDeclaration)
                val name = createName(classDeclaration)
                val visitor = PropertyCollectorVisitor()
                classDeclaration.getAllProperties().forEach {
                    it.accept(visitor, Unit)
                }
                Triple(ksFile, classDeclaration, ModelClassData(name, visitor.propertiesSet))
            }.forEach { (file, dtoClassDeclaration, data) ->
                logger.logging("[DtoToModel] creating model class with name = ${data.name}")
                // Build class and primary constructor.
                val modelClassTypeSpec = TypeSpec.classBuilder(data.name)
                    .buildPrimaryConstructor(data.properties)
                    .build()
                // Build file.
                FileSpec.builder(
                    packageName = file.packageName.asString(),
                    fileName = data.name
                ).addType(modelClassTypeSpec)
                    // Build top level converter function.
                    .buildConverter(dtoClassDeclaration, modelClassTypeSpec)
                    .build()
                    // Generate code.
                    .writeTo(
                        codeGenerator = codeGenerator,
                        // Dependencies: if something changes in `file` then output file needs to be changed too.
                        dependencies = Dependencies(true, file),
                    )
            }
    }
}

private fun createName(classDeclaration: KSClassDeclaration): String {
    // Retrieving name argument from the annotation.
    val annotationNameArg = classDeclaration.annotations.first().arguments[0].value
    // Retrieving the annotated class name.
    val dtoClassName = classDeclaration.simpleName.getShortName()

    return if (annotationNameArg is String && annotationNameArg.isNotEmpty()) {
        annotationNameArg
    } else {
        if (dtoClassName.contains("Dto")) {
            dtoClassName.replace("Dto", "Model")
        } else {
            "${dtoClassName}Model"
        }
    }
}

private data class ModelClassData(
    val name: String,
    val properties: MutableSet<Pair<String, KSTypeReference>> = mutableSetOf(),
)

private class PropertyCollectorVisitor : KSVisitorVoid() {
    val propertiesSet = mutableSetOf<Pair<String, KSTypeReference>>()

    override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
        if (property.annotations.none { it.shortName.getShortName() == IgnoreInModel::class.simpleName }) {
            propertiesSet.add(property.simpleName.getShortName() to property.type)
        }
    }
}

private fun TypeSpec.Builder.buildPrimaryConstructor(
    properties: MutableSet<Pair<String, KSTypeReference>>
): TypeSpec.Builder {
    this.primaryConstructor(
        FunSpec.constructorBuilder()
            .addParameters(
                properties.map { (name, typeReference) ->
                    ParameterSpec.builder(
                        name = name,
                        type = typeReference.toTypeName(),
                    ).build()
                }
            )
            .build()
    ).addProperties(
        properties.map { (name, typeReference) ->
            PropertySpec.builder(name = name, type = typeReference.toTypeName())
                .initializer(name)
                .addModifiers(typeReference.modifiers.mapNotNull { it.toKModifier() })
                .build()
        }
    ).build()

    return this
}

private fun FileSpec.Builder.buildConverter(
    dtoClassDeclaration: KSClassDeclaration,
    modelClassTypeSpec: TypeSpec
): FileSpec.Builder {
    val packageString = dtoClassDeclaration.packageName.asString()
    val constructorParamsString = modelClassTypeSpec.primaryConstructor
        ?.parameters
        ?.fold(StringBuilder()) { sb, spec ->
            sb.append("${spec.name} = ${spec.name}, ")
        }?.toString() ?: ""
    check(constructorParamsString.isNotEmpty())

    this.addFunction(
        FunSpec.builder(name = "to${modelClassTypeSpec.name}")
            .receiver(ClassName.bestGuess("$packageString.${dtoClassDeclaration.simpleName.getShortName()}"))
            .returns(ClassName.bestGuess("$packageString.${modelClassTypeSpec.name}"))
            .addStatement("return ${modelClassTypeSpec.name}($constructorParamsString)")
            .build()
    )

    return this
}
