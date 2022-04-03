@file:JvmName("MainFrameInit")
package com.github.charlie.scoreinput

import java.awt.Component
import java.awt.event.*
import java.util.*
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

lateinit var jFrame: JFrame
lateinit var mainFrame: MainFrame
lateinit var tableModel: AbstractTableModel
val mnuDelete = JPopupMenu()
val mnuWindowMenu = JMenuBar()

lateinit var spaceFreeItem: JCheckBoxMenuItem
lateinit var pointFreeItem: JCheckBoxMenuItem

fun prepareMainFrame() {
    spaceFreeItem = JCheckBoxMenuItem("免空格录入").apply { state = true }
    pointFreeItem = JCheckBoxMenuItem("免小数点录入").apply { state = true }
    mainFrame = MainFrame()
    initMenu()
    initTable()
    mainFrame.txtInput.addActionListener(::acceptInput)
    initShortcuts()
    NameDictionary.tryLoadLastDictionary()
}

private fun initShortcuts() {
    mainFrame.contentPane.registerKeyboardAction({ Data.open()}, KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    mainFrame.contentPane.registerKeyboardAction({ Data.save()}, KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
}

private fun initMenu() {
    mnuWindowMenu.apply {
        add(JMenu("文件...").apply {
            add(JMenuItem("打开...").apply { addActionListener { Data.open() } })
            add(JMenuItem("保存...").apply { addActionListener { Data.save() } })
            add(JMenuItem("写入Excel文档...").apply { addActionListener { writeToExcel() } })
            addSeparator()
            add(JMenuItem("打开字典...").apply { addActionListener { NameDictionary.askUserToLoadDictionary(jFrame) } })
        })
        add(JMenu("快速录入选项...").apply {
            add(pointFreeItem)
            add(spaceFreeItem)
        })
    }
    mnuDelete.apply {
        add(JMenuItem("删除...").apply {
            addActionListener {
                mainFrame.tblInputedScores.apply {
                    val size = selectedRowCount
                    var x = ""
                    while (selectedRows.isNotEmpty()) {
                        val n = selectedRows[0]
                        x = currentData[n].first
                        currentData.removeAt(n)
                        tableModel.fireTableRowsDeleted(n, n)
                    }
                    if (size == 1)
                        updateStatus("删除【$x】成功")
                    else if (size > 1)
                        updateStatus("删除了${size}个条目")
                }
            }
        })
    }
}

fun initTable() {
    mainFrame.tblInputedScores.apply table@{
        model = ScoreTableModel().apply { tableModel = this }
        selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        columnModel.getColumn(1).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
                horizontalAlignment = SwingConstants.LEFT
                return super.getTableCellRendererComponent(table, value,isSelected, hasFocus, row, column)
                        .apply { horizontalAlignment = SwingConstants.LEFT }
            }
        }
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button != MouseEvent.BUTTON3) return
                if (e.clickCount > 1) return
                val index = rowAtPoint(e.point)
                if (index == -1) return
                if (!selectedRows.contains(index))
                    setRowSelectionInterval(index, index)
                mnuDelete.show(this@table, e.x, e.y)
            }
        })
    }
}

val spacefreeInputRegex = Regex("([a-zA-Z]+)([0-9.]+)")
private fun acceptInput(e: EventObject) {
    val field = e.source as JTextField
    var strs = field.text.trim().split(" ")
    if (strs.size == 1 && spaceFreeItem.state) {
        strs = (spacefreeInputRegex.matchEntire(field.text.trim()) ?: return showErrorDialog("错误输入！格式：拼音 分数"))
                .groupValues.drop(1).toList()
    } else if (strs.size != 2)
        return showErrorDialog("错误输入！格式：拼音 分数")
    val name = NameDictionary.lookup(strs[0])
    val score: Float
    try {
        score = parseScore(strs[1])
    } catch (e: Exception) {
        showErrorDialog("分数格式错误！")
        return
    }
    if (name == null) return showErrorDialog("字典中未找到拼音【${strs[0]}】")
    val existedItem = currentData.find { it.first == name }
    if (existedItem != null && existedItem.second != score) {
        when (JOptionPane.showConfirmDialog(jFrame,
                "【$name】已有成绩！\n原值：${existedItem.second}\n新值：$score\n是否替换为新值？",
                "错误",
                JOptionPane.YES_NO_CANCEL_OPTION)) {
            JOptionPane.YES_OPTION -> {
                val index = currentData.indexOf(existedItem)
                currentData[index] = name to score
                tableModel.fireTableRowsUpdated(index, index)
                updateStatus("成功将【$name】的成绩修改为 $score")
            }
            JOptionPane.CANCEL_OPTION -> return
        }
    } else if (existedItem == null) {
        currentData += name to score
        val pos = currentData.size - 1

        tableModel.fireTableRowsInserted(pos, pos)
        mainFrame.tblInputedScores.apply {
            setRowSelectionInterval(pos, pos)
            scrollRectToVisible(getCellRect(pos, 0, true))
        }
        updateStatus("成功录入【$name】的成绩： $score")
    }
    field.text = null
}

fun parseScore(s: String): Float {
    if (s.contains('.') || !spaceFreeItem.state) return s.toFloat()
    if (s.startsWith("1") && s.length == 4) return (s.substring(0, 3) + "." + s.substring(3)).toFloat()
    if (s.length == 3 && !s.startsWith("1")) return (s.substring(0, 2) + "." + s.substring(2)).toFloat()
    return s.toFloat()
}

class ScoreTableModel: AbstractTableModel() {
    // COLUMN 0 姓名
    // COLUMN 1 分数
    override fun getRowCount(): Int = currentData.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = when (columnIndex){
        0 -> currentData[rowIndex].first
        1 -> currentData[rowIndex].second
        else -> throw IllegalArgumentException()
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        require(columnIndex == 1 && aValue is Float && rowIndex < currentData.size)
        if (currentData[rowIndex].second == aValue) return
        currentData[rowIndex] = currentData[rowIndex].first to aValue
        updateStatus("成功将【${currentData[rowIndex].first}】的成绩修改为 $aValue")
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == 1
    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex){
        0 -> String::class.java
        1 -> java.lang.Float::class.java
        else -> throw IllegalArgumentException()
    }
    override fun getColumnCount(): Int = 2
    override fun getColumnName(column: Int): String = when (column){
        0 -> "姓名"
        1 -> "分数"
        else -> throw IllegalArgumentException()
    }
}

object Dummy
fun main() {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

    val version = Properties().run {
        try {
            load(Dummy::class.java.getResourceAsStream("/version.properties"))
            return@run getProperty("version", "unknown version")
        } catch (ex: Exception) {
            return@run "unknown version"
        }
    }
    JFrame("成绩快速录入 by CharlieJiang $version").apply {
        jFrame = this
        prepareMainFrame()
        contentPane = mainFrame.contentPane
        jMenuBar = mnuWindowMenu
        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        prepareWindowPreference()
        isVisible = true
        mainFrame.txtInput.requestFocus()
    }
}

private fun JFrame.prepareWindowPreference() {
    preference.apply {
        setBounds(
                getInt("x", 50),
                getInt("y", 50),
                getInt("w", 750),
                getInt("h", 500)
        )
        if (getBoolean("max", false)) extendedState = extendedState or JFrame.MAXIMIZED_BOTH

        addComponentListener(object : ComponentListener {
            override fun componentMoved(e: ComponentEvent?) {
                if (extendedState and JFrame.MAXIMIZED_BOTH != 0) return
                putInt("x", x)
                putInt("y", y)
                sync()
            }

            override fun componentResized(e: ComponentEvent?) {
                if (extendedState and JFrame.MAXIMIZED_BOTH != 0) return
                putInt("w", width)
                putInt("h", height)
                sync()
            }

            override fun componentHidden(e: ComponentEvent?) = Unit
            override fun componentShown(e: ComponentEvent?) = Unit
        })
        addWindowStateListener {
            if (it.newState and JFrame.MAXIMIZED_BOTH != 0) putBoolean("max", true)
            else putBoolean("max", false)
            sync()
        }
    }
}

fun updateStatus(text: String) {
    mainFrame.lblStatus.text = text
}
