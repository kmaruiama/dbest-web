package dbest

import java.awt.EventQueue
import java.awt.Frame
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

class DialogUnavailableException(message: String, cause: Throwable) :
    RuntimeException(message, cause)

private const val UNAVAILABLE_MESSAGE = "nao foi possivel abrir a janela de selecao neste sistema"
private const val REPLACE_POLL_MS = 100L

private val admit = Semaphore(1, true)
private val onScreen = AtomicReference<JFileChooser?>(null)

fun pickDirectory(): Path? =
    choose(
        "DBest — escolher a pasta das sessoes",
        "Escolher",
        { chooser -> chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY },
    )

fun pickTableFile(filterLabel: String, extensions: List<String>): Path? =
    choose(
        "DBest — escolher tabela",
        "Abrir",
        { chooser ->
            chooser.fileFilter = FileNameExtensionFilter(filterLabel, *extensions.toTypedArray())
        },
    )

private fun choose(title: String, approveLabel: String, prepare: (JFileChooser) -> Unit): Path? {
    while (!admit.tryAcquire(REPLACE_POLL_MS, TimeUnit.MILLISECONDS)) {
        val open = onScreen.get()
        if (open != null) {
            EventQueue.invokeLater({ open.cancelSelection() })
        }
    }
    var picked: File? = null

    val chooseFileRoutine = {
        val owner = frontOwner()
        val chooser = JFileChooser()
        onScreen.set(chooser)
        try {
            chooser.dialogTitle = title
            prepare(chooser)
            if (chooser.showDialog(owner, approveLabel) == JFileChooser.APPROVE_OPTION) {
                picked = chooser.selectedFile
            }
        } finally {
            onScreen.set(null)
            owner.dispose()
        }
    }

    try {
        EventQueue.invokeAndWait(chooseFileRoutine)
    } catch (error: Exception) {
        throw DialogUnavailableException(UNAVAILABLE_MESSAGE, error)
    } finally {
        admit.release()
    }
    return picked?.toPath()
}

private fun frontOwner(): Frame {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (ignored: Exception) {}
    val owner = Frame()
    owner.isUndecorated = true
    owner.setSize(1, 1)
    owner.setLocationRelativeTo(null)
    owner.isAlwaysOnTop = true
    owner.isVisible = true
    owner.toFront()
    owner.requestFocus()
    return owner
}
