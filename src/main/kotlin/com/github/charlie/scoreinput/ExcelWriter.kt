package com.github.charlie.scoreinput

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import javax.swing.*

class ExcelContext(private val file: File) {
    private val path = file.toPath()
    private val workbook = XSSFWorkbook(Files.newInputStream(path))
    val sheets by lazy { workbook.toList() }
    var currentSheet: Sheet? = null
    private var dialog: ExcelWritingDialog? = null
    private lateinit var nameModel: ColumnModel
    private lateinit var scoreModel: ColumnModel

    private lateinit var sheetModel: SheetModel

    fun showDialog(parent: JFrame) {
        ExcelWritingDialog(parent).apply {
            dialog = this
            prepareExcelWritingDialog()

            pack()
            setLocationRelativeTo(null)
            isVisible = true
        }
    }

    init {
        if (sheets.isEmpty()) throw IllegalArgumentException("未找到工作表！")
        selectSheet(sheets.first())
    }

    private fun ExcelWritingDialog.prepareExcelWritingDialog() {
        lblStatus.text = "已打开工作表 $file"
        cbxSheet.model = SheetModel().also { sheetModel = it }
        selectSheet(sheets.first())

        btnOK.addActionListener {
            dispose()
            if (!cbxNameColumn.isEnabled ||
                    nameModel.selectedColumn == null || scoreModel.selectedColumn == null) return@addActionListener showErrorDialog("未选择工作表！")

            tryCatch("导出失败！") {
                val result = export(nameModel.selectedColumn!!, scoreModel.selectedColumn!!)
                if (errors > 0) InputIssuesDialog(result).apply {
                    pack()
                    setSize(500, 400)
                    setLocationRelativeTo(null)
                    isVisible = true
                } else
                    JOptionPane.showMessageDialog(parent, "导出全部完成！共导出${successes}个条目！")
            }
            workbook.close()
        }
    }

    private fun selectSheet(sheet: Sheet) {
        currentSheet = sheet
        if (sheet.lastRowNum < 1) return disableAll()
        val currentHeaderRow = sheet.first()
        if (currentHeaderRow.lastCellNum < 1)
            return disableAll()

        enableAll()
        dialog?.cbxNameColumn?.model = ColumnModel(currentHeaderRow).also { nameModel = it }
        dialog?.cbxScoreColumn?.model = ColumnModel(currentHeaderRow).also { scoreModel = it }
    }

    private fun enableAll() {
        dialog?.cbxNameColumn?.isEnabled = true
        dialog?.cbxScoreColumn?.isEnabled = true
    }

    private fun disableAll() {
        dialog?.cbxNameColumn?.isEnabled = false
        dialog?.cbxScoreColumn?.isEnabled = false
    }

    private inner class SheetModel: AbstractListModel<String>(), ComboBoxModel<String> {
        override fun setSelectedItem(anItem: Any?) {
            anItem as String
            selectSheet(sheets.find { it.sheetName == anItem }!!)
        }

        override fun getElementAt(index: Int): String = sheets[index].sheetName
        override fun getSelectedItem(): Any = currentSheet?.sheetName ?: ""
        override fun getSize(): Int = sheets.size
    }

    private inner class ColumnModel(val currentHeaderRow: Row): AbstractListModel<String>(), ComboBoxModel<String> {
        var selectedColumn: Int? = 0

        override fun setSelectedItem(anItem: Any?) {
            if (anItem == null) selectedColumn = null
            selectedColumn = CellAddress((anItem as String).split(":")[0]).column
        }

        override fun getElementAt(index: Int): String =  currentHeaderRow.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)?.run { "${address.formatAsString()}: $stringCellValue" } ?: ""
        override fun getSelectedItem(): Any = currentHeaderRow.getCell(selectedColumn ?: 0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)?.run { "${address.formatAsString()}: $stringCellValue" } ?: ""
        override fun getSize(): Int = currentHeaderRow.lastCellNum.toInt()
    }

    private var errors = 0
    private var successes = 0
    private fun export(nameColumn: Int, scoreColumn: Int): String {
        val builder = StringBuilder()
        if (currentSheet == null) {
            showErrorDialog("未打开工作表！")
            return ""
        }

        val sheet = currentSheet!!
        val rowsInExcel = sheet.map { it.getCell(nameColumn) to (it.getCell(scoreColumn) ?: it.createCell(scoreColumn)) }
                .filter { it.first != null && it.first.cellType == CellType.STRING }
                .map { it.first.stringCellValue.trim() to it.second }
        currentData.forEach { entry ->
            val r = rowsInExcel.find { it.first == entry.first }
            if (r == null) {
                errors++
                builder.append("【${entry.first}】未在Excel工作表中找到\n")
            } else {
                val cell = r.second
                cell.setCellValue(entry.second.toDouble())
                successes++
            }
        }

        Files.copy(path, path.parent.resolve(path.last().toString() + ".bak"), StandardCopyOption.REPLACE_EXISTING)
        workbook.write(Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))

        if (builder.isNotEmpty()) builder.insert(0, "导出完毕，但部分条目导出失败。\n成功:${successes}，失败${errors}，共${successes + errors}\n\n")
        return builder.toString()
    }
}

fun main() {
    JFrame().apply {
        setBounds(10,10,500,500)
        isVisible = true
        File("D:\\DiskCSymlinks\\Tencent Files\\964047583\\FileRecv\\姓名.xlsx").let(::ExcelContext).showDialog(this)
    }

}

val excelChooser = JFileChooser(preference.get("excelPath", File("").absolutePath)).apply {
    fileFilter = ExcelFilter()
    fileSelectionMode = JFileChooser.FILES_ONLY
    isMultiSelectionEnabled = false
}
fun writeToExcel() {
    if (excelChooser.showOpenDialog(jFrame) == JFileChooser.APPROVE_OPTION) {
        excelChooser.selectedFile?.tryCatch("打开Excel文件失败！") {
            ExcelContext(this).showDialog(jFrame)
            preference.put("excelPath", this.absolutePath)
        }
    }
}

class ExcelFilter: javax.swing.filechooser.FileFilter() {
    override fun getDescription(): String = "Excel 文件(.xlsx)"
    override fun accept(pathname: File): Boolean = pathname.isDirectory || pathname.extension == "xlsx"
}
