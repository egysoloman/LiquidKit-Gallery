package com.kyant.foldfiles

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.kyant.backdrop.components.LiquidListStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class FilePage(val entry: Entry, val depth: Int, val rows: List<Entry> = emptyList(),
    val text: String? = null, val error: String? = null, val needsGrant: Boolean = false)
internal enum class FileLayout(val title: String) { List("列表"), Grid("网格"), Cards("卡片") }
internal enum class FileOrder(val title: String) { Name("名称"), Modified("修改时间"), Size("大小") }

internal val Pinned = Entry("fold:pins", "固定")
internal enum class FilesTab { RecentFiles, PinnedFiles, Browse }

internal class FilesModel(app: Application, private val state: SavedStateHandle) : AndroidViewModel(app) {
    val store = FileStore(app)
    private var path = state.get<ArrayList<String>>("path")?.map { Entry.decode(it) }?.takeIf { it.isNotEmpty() } ?: listOf(store.library)
    var cursor by mutableIntStateOf((state.get<Int>("cursor") ?: 0).coerceIn(path.indices))
        private set
    var tab by mutableStateOf(state.get<String>("tab")?.let(FilesTab::valueOf) ?: FilesTab.Browse)
        private set

    private val tabPaths = mutableMapOf<FilesTab, List<Entry>>()
    private val tabCursors = mutableMapOf<FilesTab, Int>()
    private val tabPages = mutableStateMapOf<FilesTab, FilePage>()
    private val tabPreviousPages = mutableStateMapOf<FilesTab, FilePage?>()

    fun pageFor(target: FilesTab): FilePage? = tabPages[target]
    fun previousPageFor(target: FilesTab): FilePage? = tabPreviousPages[target]
    fun cursorFor(target: FilesTab): Int = if (target == tab) cursor else (tabCursors[target] ?: 0)

    fun selectTab(next: FilesTab) {
        if (next == tab) return
        tabPaths[tab] = path
        tabCursors[tab] = cursor
        state["path.${tab.name}"] = ArrayList(path.map(Entry::encode))
        state["cursor.${tab.name}"] = cursor
        tab = next
        state["tab"] = next.name
        val root = when (next) { FilesTab.RecentFiles -> Recent; FilesTab.PinnedFiles -> Pinned; FilesTab.Browse -> store.library }
        path = tabPaths[next]
            ?: state.get<ArrayList<String>>("path.${next.name}")?.map(Entry::decode)?.takeIf { it.isNotEmpty() }
            ?: listOf(root)
        cursor = (tabCursors[next] ?: state.get<Int>("cursor.${next.name}") ?: 0).coerceIn(path.indices)
        tabPaths[next] = path
        tabCursors[next] = cursor
        val target = path[cursor]
        if (next == FilesTab.PinnedFiles && target == Pinned && Pinned.id !in visited) {
            visited[Pinned.id] = FilePage(Pinned, 0, pins)
        }
        val cachedPage = tabPages[next]?.takeIf { it.entry.id == target.id && it.depth == cursor }
            ?: visited[target.id]?.copy(depth = cursor)
        if (cachedPage != null) {
            tabPages[next] = cachedPage
            page = cachedPage
        }
        val parent = path.getOrNull(cursor - 1)
        val cachedPrev = parent?.let { visited[it.id]?.copy(depth = cursor - 1) }
        tabPreviousPages[next] = cachedPrev
        previousPage = cachedPrev
        changePage()
    }

    private fun updateOtherTabs(transform: (Entry) -> Entry?) {
        for (other in FilesTab.entries.filter { it != tab }) {
            val key = "path.${other.name}"
            val entries = tabPaths[other] ?: state.get<ArrayList<String>>(key)?.map(Entry::decode) ?: continue
            val index = tabCursors[other] ?: state.get<Int>("cursor.${other.name}") ?: 0
            val fallback = when (other) {
                FilesTab.RecentFiles -> Recent
                FilesTab.PinnedFiles -> Pinned
                FilesTab.Browse -> store.library
            }
            val updated = entries.mapNotNull(transform).ifEmpty { listOf(fallback) }
            val nextIndex = (entries.take(index + 1).mapNotNull(transform).size - 1).coerceIn(updated.indices)
            tabPaths[other] = updated
            tabCursors[other] = nextIndex
            state[key] = ArrayList(updated.map(Entry::encode))
            state["cursor.${other.name}"] = nextIndex
            tabPages.remove(other)
            tabPreviousPages.remove(other)
        }
        visited.clear()
    }

    val canForward get() = cursor < path.lastIndex
    val ancestors get() = path.take(cursor).withIndex().toList().asReversed()
    var page by mutableStateOf<FilePage?>(null)
        private set
    var previousPage by mutableStateOf<FilePage?>(null)
        private set
    private val visited = object : LinkedHashMap<String, FilePage>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FilePage>?): Boolean = size > 32
    }
    var roots by mutableStateOf(store.saved("roots"))
        private set
    var pins by mutableStateOf(store.saved("pins"))
        private set
    private val queries = mutableStateMapOf<String, String>()
    fun queryFor(id: String): String = queries[id].orEmpty()
    fun setQuery(id: String, value: String) { if (value.isEmpty()) queries.remove(id) else queries[id] = value }
    var query: String
        get() = queryFor(path[cursor].id)
        set(value) = setQuery(path[cursor].id, value)
    var selecting by mutableStateOf(false)
        private set
    private val selection = mutableStateMapOf<String, Entry>()
    val selected: Set<String> get() = selection.keys
    val selectedFiles: List<Entry> get() = selection.values.toList()
    var revision by mutableIntStateOf(0)
        private set
    private val outlines = mutableStateMapOf<String, List<String>>().apply {
        state.get<Bundle>("outlines")?.let { stored ->
            for (key in stored.keySet()) put(key, stored.getStringArrayList(key).orEmpty())
        }
    }
    fun expandedFolders(page: String): List<String> = outlines[page].orEmpty()
    fun toggleFolder(page: String, folder: String) {
        val previous = expandedFolders(page)
        val next = if (folder in previous) previous - folder else previous + folder
        if (next.isEmpty()) outlines.remove(page) else outlines[page] = next
        state["outlines"] = Bundle().also { bundle -> outlines.forEach { (key, ids) -> bundle.putStringArrayList(key, ArrayList(ids)) } }
    }
    var layout by mutableStateOf(FileLayout.valueOf(store.prefs.getString("layout", "List")!!))
        private set
    var order by mutableStateOf(FileOrder.valueOf(store.prefs.getString("order", "Name")!!))
        private set
    var opacity by mutableFloatStateOf(store.prefs.getFloat("opacity", .5f).coerceIn(0f, 1f))
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    private var load: Job? = null

    init {
        tabPaths[tab] = path
        tabCursors[tab] = cursor
        refresh()
    }

    fun refresh() {
        load?.cancel()
        val activeTab = tab
        val wanted = path[cursor]
        val index = cursor
        val parent = path.getOrNull(index - 1)
        val cachedPrev = parent?.let { visited[it.id]?.copy(depth = index - 1) }
        previousPage = cachedPrev
        tabPreviousPages[activeTab] = cachedPrev
        val cached = visited[wanted.id]?.copy(depth = index)
        if (cached != null) {
            page = cached
            tabPages[activeTab] = cached
        }
        load = viewModelScope.launch {
            val result = readPage(wanted, index)
            if (result.error == null && !store.prefs.contains("pins") && wanted.id == store.library.id) {
                pins = result.rows.filter { it.name in setOf("路线手记", "印刷样张") }
                store.save("pins", pins)
            }
            if (tab == activeTab && page != result) {
                page = result
                revision++
            }
            if (tabPages[activeTab] != result) {
                tabPages[activeTab] = result
            }
            visited[wanted.id] = result
            // Restored histories can have a parent that has never been drawn in this process.
            if (parent != null && tabPreviousPages[activeTab] == null) {
                val preview = readPage(parent, index - 1)
                visited[parent.id] = preview
                if (tab == activeTab) previousPage = preview
                tabPreviousPages[activeTab] = preview
            }
        }
    }
    private suspend fun readPage(wanted: Entry, depth: Int): FilePage = withContext(Dispatchers.IO) {
        try {
            store.ensureLibrary()
            val actual = if (wanted == Recent || wanted == Pinned) wanted else store.info(wanted.id)
            if (actual.folder) FilePage(actual, depth, if (actual == Pinned) store.saved("pins") else store.children(actual))
            else FilePage(actual, depth, text = store.preview(actual))
        } catch (cancel: CancellationException) { throw cancel }
        catch (e: Exception) {
            val denied = e is SecurityException && wanted.id.startsWith("content:")
            val detail = if (denied) "请重新选择这个文件夹，以恢复访问权限。" else "请确认文件夹仍在原来的位置，然后重试。"
            FilePage(wanted, depth, error = detail, needsGrant = denied)
        }
    }
    private fun changePage() {
        tabPaths[tab] = path
        tabCursors[tab] = cursor
        state["path"] = ArrayList(path.map(Entry::encode))
        state["cursor"] = cursor
        selectMode(false)
        refresh()
    }
    fun open(entry: Entry) {
        if (entry.id == path[cursor].id) return
        path = path.take(cursor + 1) + entry
        cursor = path.lastIndex
        if (!entry.folder) store.save("recent", (listOf(entry) + store.saved("recent")).distinctBy { it.id }.take(40))
        changePage()
    }
    fun jump(index: Int) {
        if (index in path.indices && index != cursor) { cursor = index; changePage() }
    }
    fun selectMode(active: Boolean) { selecting = active; selection.clear() }
    fun toggle(entry: Entry) { if (selection.remove(entry.id) == null) selection[entry.id] = entry }
    fun changeLayout(new: FileLayout) {
        layout = new
        store.prefs.edit().putString("layout", new.name).apply()
    }
    fun changeOrder(new: FileOrder) {
        order = new
        store.prefs.edit().putString("order", new.name).apply()
    }
    fun changeOpacity(value: Float) { opacity = value.coerceIn(0f, 1f) }
    fun saveOpacity() { store.prefs.edit().putFloat("opacity", opacity).apply() }
    fun pin(entry: Entry) {
        pins = if (pins.any { it.id == entry.id }) pins.filterNot { it.id == entry.id } else pins + entry
        store.save("pins", pins)
        val pinnedRoot = FilePage(Pinned, 0, pins)
        visited[Pinned.id] = pinnedRoot
        val pinnedCursor = tabCursors[FilesTab.PinnedFiles] ?: 0
        if (tabPaths[FilesTab.PinnedFiles]?.getOrNull(pinnedCursor) == Pinned) {
            tabPages[FilesTab.PinnedFiles] = pinnedRoot
        }
        if (page?.entry == Pinned) refresh()
    }

    private fun <T> operation(work: () -> T, then: (T) -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try { then(withContext(Dispatchers.IO) { work() }) }
            catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { message = e.localizedMessage ?: "操作未完成" }
            finally { busy = false }
        }
    }
    fun connect(uri: Uri, flags: Int) = operation({ store.connect(uri, flags) }) { root ->
        roots = (roots + root).distinctBy { it.id }
        store.save("roots", roots)
        if (path[cursor].id == root.id) refresh() else open(root)
    }
    fun newFolder(parent: Entry, name: String) = operation({ store.createFolder(parent, name) }) { refresh() }
    fun rename(parent: Entry, old: Entry, name: String) = operation({ store.rename(parent, old, name) }) { next ->
        fun remap(e: Entry): Entry = when {
            e.id == old.id -> next
            old.folder && old.id.startsWith("file:") && e.id.startsWith(old.id + "/") ->
                e.copy(id = next.id + e.id.removePrefix(old.id))
            else -> e
        }
        pins = pins.map(::remap)
        store.save("pins", pins)
        store.save("recent", store.saved("recent").map(::remap))
        updateOtherTabs(::remap)
        path = path.map(::remap)
        changePage()
    }
    fun delete(entries: List<Entry>) = operation({
        entries.map { e -> e to runCatching { store.delete(e) }.exceptionOrNull() }
    }) { results ->
        val removed = results.filter { it.second == null }.map { it.first }
        fun keep(e: Entry) = removed.none { r -> e.id == r.id || (r.folder && r.id.startsWith("file:") && e.id.startsWith(r.id + "/")) }
        updateOtherTabs { it.takeIf(::keep) }
        pins = pins.filter(::keep)
        store.save("pins", pins)
        store.save("recent", store.saved("recent").filter(::keep))
        val kept = path.take(cursor + 1).count(::keep)
        path = path.filter(::keep).ifEmpty { listOf(store.library) }
        cursor = (kept - 1).coerceIn(path.indices)
        changePage()
        val failed = results.filter { it.second != null }
        if (failed.isNotEmpty()) message = "有 " + failed.size + " 项未删除：" + failed.first().first.name
    }
}
