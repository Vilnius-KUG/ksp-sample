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

/** Generates model class. */
internal fun generateModelClass(
    dtoKsClassDeclaration: KSClassDeclaration,
    codeGenerator: CodeGenerator,
    logger: KSPLogger,
) {
    val dtoPackage = dtoKsClassDeclaration.packageName.asString()
    val dtoClassName = dtoKsClassDeclaration.simpleName.getShortName()
    val className = createName(dtoKsClassDeclaration)

    logger.info("Generating $className class from $dtoClassName.", dtoKsClassDeclaration)
    val modelClass = TypeSpec.classBuilder(className)
        .buildPrimaryConstructor(collectProperties(dtoKsClassDeclaration, logger))
        .build()

    logger.info("Generating a converter function.")
    val converterFunction = buildConverterFunction(
        dtoClassPackage = dtoPackage,
        dtoClassName = dtoClassName,
        modelClassTypeSpec = modelClass
    )

    logger.info("Writing generated code to a file.")
    FileSpec.builder(dtoPackage, className)
        .addType(modelClass)
        .addFunction(converterFunction)
        .build()
        .writeTo(
            codeGenerator = codeGenerator,
            // Dependencies: if something changes in the original `file` then output re-generates.
            dependencies = Dependencies(aggregating = true, sources = arrayOf(dtoKsClassDeclaration.findFile())),
        )
}

/** Returns a [KSFile] where the node was defined. */
private fun KSNode.findFile(): KSFile {
    var parent = this.parent
    while (parent != null) {
        if (parent is KSFile) {
            return parent
        } else {
            parent = parent.parent
        }
    }
    throw IllegalStateException("Can't find enclosing file!")
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

private fun collectProperties(
    classDeclaration: KSClassDeclaration,
    logger: KSPLogger,
): Set<Pair<String, KSTypeReference>> {
    val visitor = object : KSVisitorVoid() {
        val propertiesSet = mutableSetOf<Pair<String, KSTypeReference>>()

        override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
            if (property.annotations.none { it.shortName.getShortName() == IgnoreInModel::class.simpleName }) {
                propertiesSet.add(property.simpleName.getShortName() to property.type)
            } else {
                logger.info("Property has been ignored.", property)
            }
        }
    }
    classDeclaration.getAllProperties().forEach {
        it.accept(visitor, Unit)
    }

    return visitor.propertiesSet
}

private fun TypeSpec.Builder.buildPrimaryConstructor(
    properties: Set<Pair<String, KSTypeReference>>
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

private fun buildConverterFunction(
    dtoClassPackage: String,
    dtoClassName: String,
    modelClassTypeSpec: TypeSpec
): FunSpec {
    val constructorParamsString = modelClassTypeSpec.primaryConstructor
        ?.parameters
        ?.fold(StringBuilder()) { sb, spec ->
            sb.append("${spec.name} = ${spec.name}, ")
        }?.toString() ?: ""
    check(constructorParamsString.isNotEmpty())

    return FunSpec.builder(name = "to${modelClassTypeSpec.name}")
        .receiver(ClassName.bestGuess("$dtoClassPackage.$dtoClassName"))
        .returns(ClassName.bestGuess("$dtoClassPackage.${modelClassTypeSpec.name}"))
        .addStatement("return ${modelClassTypeSpec.name}($constructorParamsString)")
        .build()
}
