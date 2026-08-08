package com.example.myapplication.infrastructure.api

import org.json.JSONObject
import java.net.URL

class RainViewerApi {
    fun fetchRainViewerUrl(): String? {
        return try {
            val responseJson = URL("https://api.rainviewer.com/public/weather-maps.json").readText()
            val jsonObject = JSONObject(responseJson)
            val host = jsonObject.getString("host")
            val pastArray = jsonObject.getJSONObject("radar").getJSONArray("past")
            val ultimulRadar = pastArray.getJSONObject(pastArray.length() - 1)
            val pathCorect = ultimulRadar.getString("path")
            "$host$pathCorect/256/{z}/{x}/{y}/2/1_1.png"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun fetchRainViewerFrames(): List<Pair<Long, String>> {
        return try {
            val responseJson = URL("https://api.rainviewer.com/public/weather-maps.json").readText()
            val jsonObject = JSONObject(responseJson)
            val host = jsonObject.getString("host")
            val pastArray = jsonObject.getJSONObject("radar").getJSONArray("past")
            
            val list = mutableListOf<Pair<Long, String>>()
            for (i in 0 until pastArray.length()) {
                val frameObj = pastArray.getJSONObject(i)
                val time = frameObj.getLong("time")
                val path = frameObj.getString("path")
                val url = "$host$path/256/{z}/{x}/{y}/2/1_1.png"
                list.add(Pair(time, url))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
