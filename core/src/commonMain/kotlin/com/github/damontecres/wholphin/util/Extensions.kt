package com.github.damontecres.wholphin.util

import java.util.UUID

/**
 * String without dashes; the inverse of [org.jellyfin.sdk.model.serializer.toUUID]
 */
fun UUID.toServerString(): String = this.toString().replace("-", "")

/**
 * String is not null, not blank
 */
fun String?.isNotNullOrBlank(): Boolean = !this.isNullOrBlank()
