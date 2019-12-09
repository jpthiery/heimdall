package com.github.jpthiery.heimdall.domain

import java.security.MessageDigest
import java.util.*

fun createProjectIdFromName(name: String): ProjectId {
    val idASByte = MessageDigest.getInstance("SHA-256").digest(name.toByteArray())
    val id = UUID.nameUUIDFromBytes(idASByte)
    return ProjectId(id.toString())
}