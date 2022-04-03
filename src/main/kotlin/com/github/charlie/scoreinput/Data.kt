package com.github.charlie.scoreinput

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import javax.swing.JFileChooser
import javax.swing.filechooser.FileFilter

val currentData = LinkedList<Pair<String, Float>>()
private val dataFileChooser = JFileChooser(preference.get("dataPath", File("").absolutePath)).apply {
    fileFilter = Data.DataFilter()
    fileSelectionMode = JFileChooser.FILES_ONLY
    isMultiSelectionEnabled = false
}
object Data {
    fun open() {
        if (dataFileChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
        tryCatch("读取失败！") {
            val entries = dataFileChooser.selectedFile.readLines()
                    .map { it.split(" ", limit = 2) }
                    .filter { it.size == 2 }
                    .map { it[0] to it[1].toFloat() }.toList()
            preference.put("dataPath", dataFileChooser.currentDirectory.absolutePath)
            preference.sync()
            if (entries.none()) showErrorDialog("文件没有有效内容！")
            else {
                currentData.clear()
                currentData += entries
                tableModel.fireTableRowsInserted(0, currentData.size - 1)
                updateStatus("成功读入文件：${dataFileChooser.selectedFile}，读入了${entries.size}个条目")
            }
        }
    }

    fun save() {
        if (dataFileChooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
        val str = currentData.joinToString("\r\n") { it.first + " " + it.second }
        tryCatch("写入失败！") {
            var filename = dataFileChooser.selectedFile.toString()
            if (!filename.endsWith(".txt")) filename += ".txt"
            Files.write(Paths.get(filename), str.toByteArray(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
            updateStatus("成功写入文件：$filename，写入了${currentData.size}个条目")
            preference.put("dataPath", dataFileChooser.currentDirectory.absolutePath)
            preference.sync()
        }
    }

    class DataFilter: FileFilter() {
        override fun getDescription(): String = "成绩数据文件 (.txt)"
        override fun accept(f: File?): Boolean = f?.isDirectory ?: false || f?.extension == "txt"
    }
}