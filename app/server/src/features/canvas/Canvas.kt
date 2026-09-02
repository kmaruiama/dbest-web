package dbest.features.canvas

import dbest.features.canvas.history.Command
import dbest.features.canvas.history.History
import dbest.features.canvas.history.edit
import dbest.features.canvas.history.redo
import dbest.features.canvas.history.undo
import dbest.kernel.util.isEmpty

data class Ack(
    val revision: Int,
    val depth: Int,
    val canUndo: Boolean,
    val canRedo: Boolean,
)

data class CanvasState(val history: History, val revision: Int)

fun editCanvas(state: CanvasState, command: Command): CanvasState =
    CanvasState(edit(state.history, command), state.revision + 1)

fun undoCanvas(state: CanvasState): CanvasState =
    if (isEmpty(state.history.undoStack)) state
    else CanvasState(undo(state.history), state.revision + 1)

fun redoCanvas(state: CanvasState): CanvasState =
    if (isEmpty(state.history.redoStack)) state
    else CanvasState(redo(state.history), state.revision + 1)

fun ackFor(state: CanvasState): Ack =
    Ack(state.revision, state.history.undoStack.size, !isEmpty(state.history.undoStack), !isEmpty(state.history.redoStack))
