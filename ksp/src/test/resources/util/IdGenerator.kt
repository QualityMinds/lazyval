package util

import java.util.UUID

class IdGenerator() {

    fun generateId(): String = UUID.randomUUID().toString()

}