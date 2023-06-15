package com.kavaliou.ksp.processor

import com.tschuchort.compiletesting.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals

class DtoToModelProcessorTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun annotatedDtoClass_generatesExpectedModelClass() {
        val kspCompileResult = compile(
            SourceFile.kotlin(
                name = "Dto.kt",
                contents = """
               package test

               import com.kavaliou.ksp.annotation.DtoToModel

               @DtoToModel
               data class TestDto(
                   val id: String,
                   val name: String,
                   val surname: String,
               )
            """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, kspCompileResult.result.exitCode)
        assertEquals(1, kspCompileResult.generatedFiles.size)
        val file = kspCompileResult.generatedFiles.first()
        file.inputStream().use {
            val contents = String(it.readBytes()).trimIndent()
            assertEquals(
                """
                package test
    
                import kotlin.String
    
                public class TestModel(
                  public val id: String,
                  public val name: String,
                  public val surname: String,
                )
    
                public fun TestDto.toTestModel(): TestModel = TestModel(id = id, name = name, surname = surname, )
            """.trimIndent(), contents
            )
        }
    }

    @Test
    fun annotatedDtoClass_customModelName_generatesModelClassWithCustomName() {
        val className = "MyModel"
        val kspCompileResult = compile(
            SourceFile.kotlin(
                name = "Dto.kt",
                contents = """
               package test

               import com.kavaliou.ksp.annotation.DtoToModel

               @DtoToModel(name = "$className")
               data class TestDto(
                   val id: String,
                   val name: String,
                   val surname: String,
               )
            """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, kspCompileResult.result.exitCode)
        assertEquals(1, kspCompileResult.generatedFiles.size)
        val file = kspCompileResult.generatedFiles.first()
        file.inputStream().use {
            val contents = String(it.readBytes()).trimIndent()
            assertEquals(
                """
                package test
    
                import kotlin.String
    
                public class $className(
                  public val id: String,
                  public val name: String,
                  public val surname: String,
                )
    
                public fun TestDto.to$className(): $className = $className(id = id, name = name, surname = surname, )
            """.trimIndent(), contents
            )
        }
    }

    @Test
    fun annotatedDtoClass_dtoClassHasNoDtoSuffix_generatesModelClassWithExpectedName() {
        val dtoClassName = "TestData"
        val kspCompileResult = compile(
            SourceFile.kotlin(
                name = "Dto.kt",
                contents = """
               package test

               import com.kavaliou.ksp.annotation.DtoToModel

               @DtoToModel
               data class $dtoClassName(
                   val id: String,
                   val name: String,
                   val surname: String,
               )
            """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, kspCompileResult.result.exitCode)
        assertEquals(1, kspCompileResult.generatedFiles.size)
        val file = kspCompileResult.generatedFiles.first()
        file.inputStream().use {
            val contents = String(it.readBytes()).trimIndent()
            assertEquals(
                """
                package test
    
                import kotlin.String
    
                public class ${dtoClassName}Model(
                  public val id: String,
                  public val name: String,
                  public val surname: String,
                )
    
                public fun $dtoClassName.to${dtoClassName}Model(): ${dtoClassName}Model = ${dtoClassName}Model(id = id, name = name, surname =
                    surname, )
            """.trimIndent(), contents
            )
        }
    }

    @Test
    fun annotatedDtoClass_hasIgnoredFields_generatesModelClassWithoutIgnoredFields() {
        val kspCompileResult = compile(
            SourceFile.kotlin(
                name = "Dto.kt",
                contents = """
               package test

               import com.kavaliou.ksp.annotation.DtoToModel
               import com.kavaliou.ksp.annotation.IgnoreInModel

               @DtoToModel
               data class TestDto(
                   @IgnoreInModel val id: String,
                   val name: String,
                   val surname: String,
               )
            """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, kspCompileResult.result.exitCode)
        assertEquals(1, kspCompileResult.generatedFiles.size)
        val file = kspCompileResult.generatedFiles.first()
        file.inputStream().use {
            val contents = String(it.readBytes()).trimIndent()
            assertEquals(
                """
                package test
    
                import kotlin.String
    
                public class TestModel(
                  public val name: String,
                  public val surname: String,
                )
    
                public fun TestDto.toTestModel(): TestModel = TestModel(name = name, surname = surname, )
            """.trimIndent(), contents
            )
        }
    }

    private fun compile(vararg sourceFiles: SourceFile): KspCompileResult {
        val compilation = prepareCompilation(*sourceFiles)
        val result = compilation.compile()
        return KspCompileResult(
            result,
            findGeneratedFiles(compilation)
        )
    }

    private fun prepareCompilation(vararg sourceFiles: SourceFile): KotlinCompilation =
        KotlinCompilation()
            .apply {
                workingDir = temporaryFolder.root
                inheritClassPath = true
                symbolProcessorProviders = listOf(DtoToModelProcessorProvider())
                sources = sourceFiles.asList()
                verbose = false
                kspIncremental = true
            }

    private fun findGeneratedFiles(compilation: KotlinCompilation): List<File> {
        return compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.isFile }
            .toList()
    }

    private data class KspCompileResult(
        val result: KotlinCompilation.Result,
        val generatedFiles: List<File>
    )
}