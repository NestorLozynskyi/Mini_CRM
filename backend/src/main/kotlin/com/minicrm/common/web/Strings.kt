package com.minicrm.common.web

/** Порожній/пробільний рядок → null; інакше обрізаний. */
fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
