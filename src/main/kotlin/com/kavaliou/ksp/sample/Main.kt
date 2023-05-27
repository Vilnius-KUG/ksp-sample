package com.kavaliou.ksp.sample

import com.kavaliou.ksp.annotation.DtoToModel
import com.kavaliou.ksp.annotation.IgnoreInModel
import com.kavaliou.ksp.annotation.VilniusKugPrinter

fun main(args: Array<String>) {}

@DtoToModel
data class BillingDataDto(
    val id: String,
    @IgnoreInModel val accountName: String,
    val isDueDateExpired: Boolean,
    @IgnoreInModel val amount: Double,
    val formattedAmount: String,
)

fun doNothing(
    @VilniusKugPrinter h: Int,
    @VilniusKugPrinter e: Int,
    @VilniusKugPrinter l: Int,
    @VilniusKugPrinter lo: Int,
    @VilniusKugPrinter world: Int,
) {}
