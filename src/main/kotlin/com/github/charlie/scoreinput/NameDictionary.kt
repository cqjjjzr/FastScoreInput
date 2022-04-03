package com.github.charlie.scoreinput

import java.awt.Component
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileFilter

object NameDictionary {
    private val nameDictionary = LinkedHashMap<String, String>()
    private val dictionaryLoadFileChooser = JFileChooser(preference.get("dictPath", File("").absolutePath)).apply {
        fileFilter = DictionaryFilter()
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = false
    }
    fun lookup(pinyin: String): String? {
        val results = nameDictionary.filterValues { it == pinyin.trim() }.keys.toList()
        return when (results.size) {
            0 -> null
            1 -> results.single()
            else -> disambiguate(pinyin, results)
        }
    }

    private fun disambiguate(pinyin: String, results: List<String>): String? {
        DisambiguationDialog(null, pinyin, results).apply {
            pack()
            setLocationRelativeTo(null)
            isVisible = true
            return result
        }
    }

    fun askUserToLoadDictionary(parent: Component) {
        if (dictionaryLoadFileChooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
            dictionaryLoadFileChooser.selectedFile?.tryCatch("加载字典失败！") {
                loadDictionary(readText(Charsets.UTF_8))
                updateStatus("加载字典 ${dictionaryLoadFileChooser.selectedFile} 成功！共${nameDictionary.size}词条")
                preference.apply {
                    put("dict", dictionaryLoadFileChooser.selectedFile.absolutePath)
                    put("dictPath", dictionaryLoadFileChooser.currentDirectory.absolutePath)
                    sync()
                }
            }
    }

    private fun loadDictionary(text: String) {
        nameDictionary.clear()
        text
                .lineSequence()
                .map { it.split(" ", limit = 2) }
                .filter { it.size > 1 }
                .also { if (it.none()) throw IllegalArgumentException("文件格式错误！没有找到任何词条！") }
                .forEach { (name, pinyin) ->
                    if (nameDictionary.containsKey(name) && nameDictionary[name] != pinyin)
                        throw IllegalArgumentException("冲突的字典词条！\n姓名：$name\n第一个值：${nameDictionary[name]}\n第二个值：$pinyin")
                    nameDictionary[name] = pinyin.trim()
                }
    }

    fun tryLoadLastDictionary() {
        val s = preference.get("dict", null) ?: return
        runIgnoreException {
            loadDictionary(File(s).readText(Charsets.UTF_8))
            updateStatus("加载字典 $s 成功！共${nameDictionary.size}词条")
        }
    }
    class DictionaryFilter: FileFilter() {
        override fun getDescription(): String = "拼音字典文件 (.txt)"
        override fun accept(f: File?): Boolean = f?.isDirectory ?: false || f?.extension == "txt"
    }
}