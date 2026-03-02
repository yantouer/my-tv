package com.lizongying.mytv

import android.util.Log
import com.lizongying.mytv.models.ProgramType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object LiveSourceManager {
    private const val TAG = "LiveSourceManager"
    
    /**
     * 从网络地址加载直播源
     * @param url 直播源地址
     * @return 解析后的频道列表，按分类分组
     */
    suspend fun loadLiveSources(url: String): Map<String, List<TV>> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val lines = mutableListOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                lines.add(line!!)
            }
            reader.close()
            connection.disconnect()
            
            parseLiveSources(lines)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load live sources: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * 解析直播源
     * @param lines 直播源文件的所有行
     * @return 解析后的频道列表，按分类分组
     */
    private fun parseLiveSources(lines: List<String>): Map<String, List<TV>> {
        val result = mutableMapOf<String, MutableList<TV>>()
        var currentCategory = "默认"
        var id = 0
        var currentTitle = ""
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            // 处理m3u8格式
            if (trimmedLine.startsWith("#EXTM3U")) {
                // m3u8文件头，跳过
                continue
            } else if (trimmedLine.startsWith("#EXTINF:")) {
                // 提取频道名称
                val titleStart = trimmedLine.indexOf(",") + 1
                if (titleStart > 0 && titleStart < trimmedLine.length) {
                    currentTitle = trimmedLine.substring(titleStart).trim()
                }
                continue
            } else if (trimmedLine.startsWith("#genre#")) {
                // 分类行
                currentCategory = trimmedLine.substring(7).trim()
                if (currentCategory.isNotEmpty() && !result.containsKey(currentCategory)) {
                    result[currentCategory] = mutableListOf()
                }
                continue
            } else if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                // 跳过空行和其他注释
                continue
            }
            
            val videoUrl = trimmedLine
            val name = if (currentTitle.isNotEmpty()) currentTitle else "未知频道"
            
            // 创建TV对象
            val tv = TV(
                id = id,
                title = name,
                alias = name,
                videoUrl = listOf(videoUrl),
                channel = currentCategory,
                logo = "",
                pid = "",
                sid = "",
                programType = ProgramType.Y_PROTO, // 使用默认的节目类型
                needToken = false,
                mustToken = false
            )
            
            // 添加到对应分类
            if (!result.containsKey(currentCategory)) {
                result[currentCategory] = mutableListOf()
            }
            result[currentCategory]?.add(tv)
            id++
            
            // 重置当前标题
            currentTitle = ""
        }
        
        return result
    }
}
