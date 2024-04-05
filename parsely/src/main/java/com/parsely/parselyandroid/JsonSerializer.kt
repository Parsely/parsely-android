package com.parsely.parselyandroid

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.io.StringWriter

internal object JsonSerializer {

    fun toParselyEventsPayload(eventsToSend: List<Map<String, Any?>?>): String {
        val batchMap: MutableMap<String, Any> = HashMap()
        batchMap["events"] = eventsToSend
        return toJson(batchMap).orEmpty()
    }
    /**
     * Encode an event Map as JSON.
     *
     * @param map The Map object to encode as JSON.
     * @return The JSON-encoded value of `map`.
     */
    private fun toJson(map: Map<String, Any>): String? {
        val mapper = ObjectMapper()
        var ret: String? = null
        try {
            val strWriter = StringWriter()
            mapper.writeValue(strWriter, map)
            ret = strWriter.toString()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return ret
    }
}
