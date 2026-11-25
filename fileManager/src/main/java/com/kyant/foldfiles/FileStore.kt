package com.kyant.foldfiles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document as Doc
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.Locale

internal data class Entry(
    val id: String, val name: String, val mime: String = Doc.MIME_TYPE_DIR, val modified: Long = 0,
    val bytes: Long? = null, val flags: Int = 0, val childCount: Int? = null,
) {
    val folder get() = mime == Doc.MIME_TYPE_DIR
    val canCreate get() = folder && flags and Doc.FLAG_DIR_SUPPORTS_CREATE != 0
    val canRename get() = flags and Doc.FLAG_SUPPORTS_RENAME != 0
    val canDelete get() = flags and Doc.FLAG_SUPPORTS_DELETE != 0
    fun encode() = JSONArray().put(id).put(name).put(mime).put(modified).put(bytes ?: -1L).put(flags).put(childCount ?: -1).toString()
    companion object {
        fun decode(value: String): Entry {
            val a = JSONArray(value)
            return Entry(a.getString(0), a.getString(1), a.getString(2), a.getLong(3), a.getLong(4).takeIf { it >= 0 },
                a.getInt(5), a.optInt(6, -1).takeIf { it >= 0 })
        }
    }
}

internal val Recent = Entry("fold:recent", "最近打开")

/** The app's library and persistently granted SAF trees use the same document operations. */
internal class FileStore(private val context: Context) {
    private val resolver = context.contentResolver
    private val root = File(context.filesDir, "library").canonicalFile
    val library = Entry(Uri.fromFile(root).toString(), "本机文库", flags = Doc.FLAG_DIR_SUPPORTS_CREATE)
    val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val projection = arrayOf(Doc.COLUMN_DOCUMENT_ID, Doc.COLUMN_DISPLAY_NAME, Doc.COLUMN_MIME_TYPE,
        Doc.COLUMN_LAST_MODIFIED, Doc.COLUMN_SIZE, Doc.COLUMN_FLAGS)

    fun ensureLibrary() {
        if (root.isDirectory) return
        val staging = File(context.filesDir, "library-initializing")
        staging.mkdirs()
        val originals = mapOf(
            "路线手记/河岸步行.md" to "# 河岸步行\n\n从旧码头向东，沿着河堤走到白桥。日落前四十分钟，水面会接到最后一束斜光。",
            "路线手记/车站路线.txt" to "中央车站 → 旧书店 → 河堤入口\n全程约 3.6 公里。",
            "印刷样张/用纸.csv" to "编号,纸张,克重\n01,暖白棉纸,180\n02,雾面再生纸,240",
            "影像目录/拍摄顺序.txt" to "01 雨后的车站\n02 清晨的堤岸\n03 市集的门牌",
            "河岸采集.md" to "# 河岸采集\n\n沿水而行的声音、字迹与色彩。\n\n01  清晨六时，记录桥下的回声。\n02  正午前，收集沿河招牌的字形。\n03  黄昏时，在西岸整理今天的笔记。",
            "取景清单.csv" to "地点,时间,镜头\n堤岸,06:20,35mm\n书店,11:00,50mm\n南站,17:10,28mm",
            "色彩索引.json" to "{\n  \"薄荷\": \"#91D7BF\",\n  \"海墨\": \"#2F5557\",\n  \"稻穗\": \"#E0BA72\"\n}",
            "展签草稿.txt" to "折页 / 城市散步\n\n一张纸，沿着三条折痕，记下一座城市。",
            "馆藏索引.tsv" to "卷号\t题名\nF01\t河岸采集\nF02\t市集手记",
        )
        for ((path, text) in originals) {
            val file = File(staging, path)
            file.parentFile?.mkdirs()
            file.writeText(text)
        }
        check(staging.renameTo(root) || root.isDirectory) { "文库初始化失败" }
    }

    fun saved(key: String): List<Entry> {
        val a = JSONArray(prefs.getString(key, "[]"))
        return (0 until a.length()).mapNotNull { runCatching { Entry.decode(a.getString(it)) }.getOrNull() }
    }
    fun save(key: String, values: List<Entry>) {
        prefs.edit().putString(key, JSONArray(values.map { it.encode() }).toString()).apply()
    }
    private fun local(uri: Uri): File {
        val f = File(requireNotNull(uri.path)).canonicalFile
        require(f == root || f.path.startsWith(root.path + File.separator)) { "无效的文库路径" }
        return f
    }
    private fun localEntry(f: File): Entry {
        require(f.exists()) { "文件已移走" }
        val flags = (if (f != root) Doc.FLAG_SUPPORTS_RENAME or Doc.FLAG_SUPPORTS_DELETE else 0) or
            (if (f.isDirectory) Doc.FLAG_DIR_SUPPORTS_CREATE else 0)
        val ext = f.extension.lowercase(Locale.ROOT)
        val mime = if (f.isDirectory) Doc.MIME_TYPE_DIR else when (ext) {
            "md", "txt", "tsv" -> "text/plain"
            "csv" -> "text/csv"
            "json" -> "application/json"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
        return Entry(Uri.fromFile(f).toString(), if (f == root) library.name else f.name, mime,
            f.lastModified(), if (f.isDirectory) null else f.length(), flags, if (f.isDirectory) f.list()?.size else null)
    }
    private fun query(queryUri: Uri, tree: Uri): List<Entry> {
        val writable = resolver.persistedUriPermissions.any { it.isWritePermission &&
            it.uri.authority == tree.authority && DocumentsContract.getTreeDocumentId(it.uri) == DocumentsContract.getTreeDocumentId(tree) }
        return resolver.query(queryUri, projection, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)).toString()
                    add(Entry(id, c.getString(1) ?: "未命名", c.getString(2) ?: "application/octet-stream",
                        c.getLong(3), if (c.isNull(4)) null else c.getLong(4), if (writable) c.getInt(5) else 0))
                }
            }
        } ?: throw IOException("无法读取此位置")
    }
    fun info(id: String): Entry {
        val uri = Uri.parse(id)
        return if (uri.scheme == "file") localEntry(local(uri)) else query(uri, uri).firstOrNull() ?: throw IOException("文件已移走")
    }
    fun connect(tree: Uri, granted: Int): Entry {
        val flags = granted and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        resolver.takePersistableUriPermission(tree, flags)
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return info(uri.toString())
    }
    fun children(folder: Entry): List<Entry> {
        if (folder.id == Recent.id) return saved("recent").mapNotNull { runCatching { info(it.id) }.getOrNull() }
        val uri = Uri.parse(folder.id)
        if (uri.scheme == "file") return local(uri).listFiles()?.map(::localEntry) ?: throw IOException("无法读取文件夹")
        return query(DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri)), uri)
    }
    private fun checkedName(raw: String): String = raw.trim().also { name ->
        require(name.isNotEmpty() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it.code < 32 }) { "请输入有效名称" }
    }
    fun createFolder(parent: Entry, raw: String) {
        val name = checkedName(raw)
        require(parent.canCreate) { "此位置为只读" }
        require(children(parent).none { it.name == name }) { "已有同名项目" }
        val uri = Uri.parse(parent.id)
        if (uri.scheme == "file") check(File(local(uri), name).mkdir()) { "无法创建文件夹" }
        else checkNotNull(DocumentsContract.createDocument(resolver, uri, Doc.MIME_TYPE_DIR, name)) { "无法创建文件夹" }
    }
    fun rename(parent: Entry, entry: Entry, raw: String): Entry {
        val name = checkedName(raw)
        if (entry.name == name) return entry
        require(entry.canRename) { "此项目不可改名" }
        val uri = Uri.parse(entry.id)
        if (parent != Recent) require(children(parent).none { it.name == name }) { "已有同名项目" }
        if (uri.scheme == "file") {
            val old = local(uri)
            val new = File(old.parentFile, name)
            check(!new.exists() && old.renameTo(new)) { "名称已存在或无法改名" }
            return localEntry(new)
        }
        return info(checkNotNull(DocumentsContract.renameDocument(resolver, uri, name)) { "无法改名" }.toString())
    }
    fun delete(entry: Entry) {
        require(entry.canDelete) { "此项目不可删除" }
        val uri = Uri.parse(entry.id)
        val done = if (uri.scheme == "file") local(uri).let { it != root && it.deleteRecursively() }
            else DocumentsContract.deleteDocument(resolver, uri)
        check(done) { "删除失败" }
    }
    fun shareUri(entry: Entry): Uri {
        val uri = Uri.parse(entry.id)
        return if (uri.scheme == "file") FileProvider.getUriForFile(context, context.packageName + ".documents", local(uri)) else uri
    }
    fun preview(entry: Entry): String? {
        if (!(entry.mime.startsWith("text/") || entry.mime == "application/json")) return null
        val limit = 64 * 1024
        val data = ByteArray(limit + 1)
        val count = resolver.openInputStream(shareUri(entry))?.use { input ->
            var done = 0
            while (done < data.size) {
                val read = input.read(data, done, data.size - done)
                if (read <= 0) break
                done += read
            }
            done
        } ?: throw IOException("无法读取文件")
        return data.decodeToString(endIndex = minOf(limit, count)) + if (count > limit) "\n\n… 预览到此为止" else ""
    }
}
