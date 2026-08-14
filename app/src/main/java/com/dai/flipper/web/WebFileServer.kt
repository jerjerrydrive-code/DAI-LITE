package com.dai.flipper.web

import android.content.Context
import android.net.wifi.WifiManager
import com.dai.flipper.ble.FlipperFileSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*

/**
 * WebUI File Manager
 *
 * Embedded HTTP server for browser-based Flipper file management.
 * Access via http://phone-ip:8888
 *
 * Features:
 * - Browse Flipper file system
 * - Upload/download files
 * - Create/delete directories
 * - View/edit text files
 * - Mobile-responsive UI
 */
class WebFileServer(
    private val context: Context,
    private val flipperFileSystem: FlipperFileSystem,
    private val port: Int = 8888
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _connectionCount = MutableStateFlow(0)
    val connectionCount: StateFlow<Int> = _connectionCount.asStateFlow()

    fun start() {
        if (_isRunning.value) return

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                _isRunning.value = true
                _serverUrl.value = "http://${getLocalIpAddress()}:$port"

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    launch {
                        handleConnection(socket)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    e.printStackTrace()
                }
            } finally {
                _isRunning.value = false
                _serverUrl.value = null
            }
        }
    }

    fun stop() {
        serverJob?.cancel()
        serverSocket?.close()
        serverSocket = null
        _isRunning.value = false
        _serverUrl.value = null
    }

    private fun getLocalIpAddress(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wifiManager.connectionInfo.ipAddress
        return String.format(
            java.util.Locale.US,
            "%d.%d.%d.%d",
            ipInt and 0xff,
            (ipInt shr 8) and 0xff,
            (ipInt shr 16) and 0xff,
            (ipInt shr 24) and 0xff
        )
    }

    private suspend fun handleConnection(socket: Socket) {
        _connectionCount.value++
        try {
            socket.use { sock ->
                val inputStream = BufferedInputStream(sock.getInputStream())
                val outputStream = sock.getOutputStream()
                val writer = PrintWriter(OutputStreamWriter(outputStream, Charsets.UTF_8), true)

                // Read request line
                val requestLine = readHttpLine(inputStream) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                val path = URLDecoder.decode(parts[1], "UTF-8")

                // Read headers
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readHttpLine(inputStream) ?: break
                    if (line.isEmpty()) break
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        headers[line.substring(0, colonIndex).trim()] =
                            line.substring(colonIndex + 1).trim()
                    }
                }

                // Route request
                when {
                    path == "/" || path == "/index.html" -> serveIndexPage(writer)
                    path.startsWith("/api/list") -> handleListApi(path, writer)
                    path.startsWith("/api/read") -> handleReadApi(path, writer)
                    path.startsWith("/api/download") -> handleDownloadApi(path, outputStream)
                    path.startsWith("/api/delete") && method == "POST" -> handleDeleteApi(path, writer)
                    path.startsWith("/api/mkdir") && method == "POST" -> handleMkdirApi(path, writer)
                    path.startsWith("/api/upload") && method == "POST" ->
                        handleUploadApi(path, headers, inputStream, writer)
                    path == "/style.css" -> serveCss(writer)
                    else -> serve404(writer)
                }
            }
        } finally {
            _connectionCount.value--
        }
    }

    private fun serveIndexPage(writer: PrintWriter) {
        val html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Dai - Flipper File Manager</title>
    <link rel="stylesheet" href="/style.css">
</head>
<body>
    <header>
        <h1>🐬 Dai File Manager</h1>
        <div id="status">Connected</div>
    </header>

    <nav id="breadcrumb">
        <a href="#" onclick="navigate('/ext')">/ext</a>
    </nav>

    <main>
        <div id="toolbar">
            <button onclick="goUp()">⬆️ Up</button>
            <button onclick="refresh()">🔄 Refresh</button>
            <button onclick="showNewFolder()">📁 New Folder</button>
            <label class="upload-btn">
                📤 Upload
                <input type="file" id="uploadInput" onchange="uploadFile()" hidden>
            </label>
        </div>

        <div id="file-list"></div>
    </main>

    <div id="modal" class="modal hidden">
        <div class="modal-content">
            <span class="close" onclick="closeModal()">&times;</span>
            <div id="modal-body"></div>
        </div>
    </div>

    <script>
        let currentPath = '/ext';

        async function navigate(path) {
            currentPath = path;
            updateBreadcrumb();
            await loadDirectory(path);
        }

        function updateBreadcrumb() {
            const parts = currentPath.split('/').filter(p => p);
            let breadcrumb = '';
            let path = '';
            parts.forEach((part, i) => {
                path += '/' + part;
                const p = path;
                breadcrumb += '<a href="#" onclick="navigate(\'' + p + '\')">' + part + '</a>';
                if (i < parts.length - 1) breadcrumb += ' / ';
            });
            document.getElementById('breadcrumb').innerHTML = breadcrumb || '/';
        }

        async function loadDirectory(path) {
            try {
                const res = await fetch('/api/list?path=' + encodeURIComponent(path));
                const data = await res.json();

                if (data.error) {
                    alert(data.error);
                    return;
                }

                const list = document.getElementById('file-list');
                list.innerHTML = '';

                data.files.forEach(file => {
                    const item = document.createElement('div');
                    item.className = 'file-item' + (file.isDir ? ' dir' : '');

                    const icon = file.isDir ? '📁' : getFileIcon(file.name);
                    const size = file.isDir ? '' : formatSize(file.size);

                    item.innerHTML =
                        '<span class="icon">' + icon + '</span>' +
                        '<span class="name">' + file.name + '</span>' +
                        '<span class="size">' + size + '</span>' +
                        '<span class="actions">' +
                            (file.isDir ? '' : '<button onclick="downloadFile(\'' + path + '/' + file.name + '\')">⬇️</button>') +
                            '<button onclick="deleteItem(\'' + path + '/' + file.name + '\', ' + file.isDir + ')">🗑️</button>' +
                        '</span>';

                    if (file.isDir) {
                        item.querySelector('.name').onclick = () => navigate(path + '/' + file.name);
                    } else {
                        item.querySelector('.name').onclick = () => viewFile(path + '/' + file.name);
                    }

                    list.appendChild(item);
                });

                if (data.files.length === 0) {
                    list.innerHTML = '<div class="empty">Empty directory</div>';
                }
            } catch (e) {
                alert('Error loading directory: ' + e.message);
            }
        }

        function getFileIcon(name) {
            const ext = name.split('.').pop().toLowerCase();
            const icons = {
                'sub': '📡', 'ir': '🔴', 'nfc': '💳', 'rfid': '🏷️',
                'txt': '📝', 'log': '📋', 'fap': '🎮', 'png': '🖼️',
                'jpg': '🖼️', 'ibtn': '🔘', 'key': '🔑'
            };
            return icons[ext] || '📄';
        }

        function formatSize(bytes) {
            if (bytes < 1024) return bytes + ' B';
            if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
            return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
        }

        function goUp() {
            const parts = currentPath.split('/').filter(p => p);
            if (parts.length > 1) {
                parts.pop();
                navigate('/' + parts.join('/'));
            }
        }

        function refresh() {
            loadDirectory(currentPath);
        }

        async function viewFile(path) {
            try {
                const res = await fetch('/api/read?path=' + encodeURIComponent(path));
                const data = await res.json();

                if (data.error) {
                    alert(data.error);
                    return;
                }

                document.getElementById('modal-body').innerHTML =
                    '<h3>' + path.split('/').pop() + '</h3>' +
                    '<pre>' + escapeHtml(data.content) + '</pre>' +
                    '<button onclick="downloadFile(\'' + path + '\')">Download</button>';
                document.getElementById('modal').classList.remove('hidden');
            } catch (e) {
                alert('Error reading file: ' + e.message);
            }
        }

        function escapeHtml(str) {
            return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        }

        function downloadFile(path) {
            window.location.href = '/api/download?path=' + encodeURIComponent(path);
        }

        async function deleteItem(path, isDir) {
            if (!confirm('Delete ' + path.split('/').pop() + '?')) return;

            try {
                const res = await fetch('/api/delete?path=' + encodeURIComponent(path), { method: 'POST' });
                const data = await res.json();

                if (data.error) {
                    alert(data.error);
                } else {
                    refresh();
                }
            } catch (e) {
                alert('Error: ' + e.message);
            }
        }

        function showNewFolder() {
            const name = prompt('Folder name:');
            if (name) createFolder(name);
        }

        async function createFolder(name) {
            try {
                const res = await fetch('/api/mkdir?path=' + encodeURIComponent(currentPath + '/' + name), { method: 'POST' });
                const data = await res.json();

                if (data.error) {
                    alert(data.error);
                } else {
                    refresh();
                }
            } catch (e) {
                alert('Error: ' + e.message);
            }
        }

        async function uploadFile() {
            const input = document.getElementById('uploadInput');
            const file = input.files[0];
            if (!file) return;

            const formData = new FormData();
            formData.append('file', file);

            try {
                const res = await fetch('/api/upload?path=' + encodeURIComponent(currentPath + '/' + file.name), {
                    method: 'POST',
                    body: formData
                });
                const data = await res.json();

                if (data.error) {
                    alert(data.error);
                } else {
                    refresh();
                }
            } catch (e) {
                alert('Error: ' + e.message);
            }

            input.value = '';
        }

        function closeModal() {
            document.getElementById('modal').classList.add('hidden');
        }

        // Initial load
        navigate('/ext');
    </script>
</body>
</html>
        """.trimIndent()

        sendHtmlResponse(writer, html)
    }

    private fun serveCss(writer: PrintWriter) {
        val css = """
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0d0d0d; color: #fff; }
header { background: linear-gradient(135deg, #1a1a2e, #16213e); padding: 16px; display: flex; justify-content: space-between; align-items: center; }
header h1 { font-size: 1.5rem; }
#status { color: #4caf50; font-size: 0.9rem; }
nav { background: #1e1e2e; padding: 12px 16px; font-size: 0.9rem; }
nav a { color: #ff6b00; text-decoration: none; }
nav a:hover { text-decoration: underline; }
main { padding: 16px; }
#toolbar { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
#toolbar button, .upload-btn { background: #2a2a3e; border: 1px solid #444; color: #fff; padding: 8px 16px; border-radius: 8px; cursor: pointer; font-size: 0.9rem; }
#toolbar button:hover, .upload-btn:hover { background: #3a3a4e; }
.upload-btn { display: inline-block; }
.file-item { display: flex; align-items: center; padding: 12px; background: #1e1e2e; border-radius: 8px; margin-bottom: 8px; gap: 12px; }
.file-item:hover { background: #2a2a3e; }
.file-item.dir .name { color: #ff6b00; cursor: pointer; }
.file-item .icon { font-size: 1.5rem; }
.file-item .name { flex: 1; cursor: pointer; }
.file-item .size { color: #888; font-size: 0.85rem; min-width: 80px; text-align: right; }
.file-item .actions { display: flex; gap: 4px; }
.file-item .actions button { background: transparent; border: none; cursor: pointer; font-size: 1rem; padding: 4px 8px; }
.empty { text-align: center; color: #666; padding: 40px; }
.modal { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.8); display: flex; align-items: center; justify-content: center; }
.modal.hidden { display: none; }
.modal-content { background: #1e1e2e; padding: 24px; border-radius: 16px; max-width: 90%; max-height: 80vh; overflow: auto; position: relative; }
.modal-content h3 { margin-bottom: 16px; }
.modal-content pre { background: #0d0d0d; padding: 16px; border-radius: 8px; overflow: auto; max-height: 50vh; font-size: 0.85rem; }
.modal-content button { margin-top: 16px; background: #ff6b00; border: none; color: #fff; padding: 10px 20px; border-radius: 8px; cursor: pointer; }
.close { position: absolute; top: 16px; right: 16px; font-size: 1.5rem; cursor: pointer; color: #888; }
        """.trimIndent()

        writer.println("HTTP/1.1 200 OK")
        writer.println("Content-Type: text/css")
        writer.println("Content-Length: ${css.length}")
        writer.println()
        writer.print(css)
        writer.flush()
    }

    private suspend fun handleListApi(path: String, writer: PrintWriter) {
        val flipperPath = extractQueryParam(path, "path") ?: "/ext"

        try {
            val result = flipperFileSystem.listDirectory(flipperPath)
            if (result.isSuccess) {
                val files = result.getOrNull() ?: emptyList()
                val json = buildString {
                    append("{\"files\":[")
                    files.forEachIndexed { index, entry ->
                        if (index > 0) append(",")
                        val safeName = jsonEscape(entry.name)
                        append("{\"name\":\"$safeName\",\"isDir\":${entry.isDirectory},\"size\":${entry.size}}")
                    }
                    append("]}")
                }
                sendJsonResponse(writer, json)
            } else {
                sendJsonResponse(writer, "{\"error\":\"Failed to list directory\"}")
            }
        } catch (e: Exception) {
            sendJsonResponse(writer, "{\"error\":\"${jsonEscape(e.message ?: "Unknown error")}\"}")
        }
    }

    private suspend fun handleReadApi(path: String, writer: PrintWriter) {
        val flipperPath = extractQueryParam(path, "path") ?: return

        try {
            val result = flipperFileSystem.readFile(flipperPath)
            if (result.isSuccess) {
                val content = result.getOrNull() ?: ""
                val escapedContent = content
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                sendJsonResponse(writer, "{\"content\":\"$escapedContent\"}")
            } else {
                sendJsonResponse(writer, "{\"error\":\"Failed to read file\"}")
            }
        } catch (e: Exception) {
            sendJsonResponse(writer, "{\"error\":\"${jsonEscape(e.message ?: "Unknown error")}\"}")
        }
    }

    private suspend fun handleDownloadApi(path: String, outputStream: OutputStream) {
        val flipperPath = extractQueryParam(path, "path") ?: return
        val filename = flipperPath.substringAfterLast("/")

        try {
            val result = flipperFileSystem.readFile(flipperPath)
            if (result.isSuccess) {
                val content = result.getOrNull() ?: ""
                val bytes = content.toByteArray()

                val response = """
                    HTTP/1.1 200 OK
                    Content-Type: application/octet-stream
                    Content-Disposition: attachment; filename="$filename"
                    Content-Length: ${bytes.size}

                """.trimIndent() + "\r\n\r\n"

                outputStream.write(response.toByteArray())
                outputStream.write(bytes)
                outputStream.flush()
            }
        } catch (e: Exception) {
            // Error handling
        }
    }

    private suspend fun handleDeleteApi(path: String, writer: PrintWriter) {
        val flipperPath = extractQueryParam(path, "path") ?: return

        try {
            val result = flipperFileSystem.deleteFile(flipperPath)
            if (result.isSuccess) {
                sendJsonResponse(writer, "{\"success\":true}")
            } else {
                sendJsonResponse(writer, "{\"error\":\"Failed to delete\"}")
            }
        } catch (e: Exception) {
            sendJsonResponse(writer, "{\"error\":\"${jsonEscape(e.message ?: "Unknown error")}\"}")
        }
    }

    private suspend fun handleMkdirApi(path: String, writer: PrintWriter) {
        val flipperPath = extractQueryParam(path, "path") ?: return

        try {
            val result = flipperFileSystem.createDirectory(flipperPath)
            if (result.isSuccess) {
                sendJsonResponse(writer, "{\"success\":true}")
            } else {
                sendJsonResponse(writer, "{\"error\":\"Failed to create directory\"}")
            }
        } catch (e: Exception) {
            sendJsonResponse(writer, "{\"error\":\"${jsonEscape(e.message ?: "Unknown error")}\"}")
        }
    }

    private suspend fun handleUploadApi(
        path: String,
        headers: Map<String, String>,
        inputStream: InputStream,
        writer: PrintWriter
    ) {
        val flipperPath = extractQueryParam(path, "path") ?: return

        try {
            val contentType = headers["Content-Type"] ?: ""
            if (!contentType.startsWith("multipart/form-data")) {
                sendJsonResponse(writer, "{\"error\":\"Unsupported content type\"}")
                return
            }

            val boundary = contentType.substringAfter("boundary=", "")
            if (boundary.isBlank()) {
                sendJsonResponse(writer, "{\"error\":\"Missing multipart boundary\"}")
                return
            }

            val contentLength = headers["Content-Length"]?.toIntOrNull() ?: 0
            if (contentLength <= 0) {
                sendJsonResponse(writer, "{\"error\":\"Missing content length\"}")
                return
            }

            val body = readFully(inputStream, contentLength)
            val boundaryBytes = "--$boundary".toByteArray(Charsets.UTF_8)
            val parts = splitByteArray(body, boundaryBytes)

            var savedCount = 0
            var totalBytes = 0L

            for (part in parts) {
                val trimmed = trimLeadingCrlf(part)
                if (trimmed.isEmpty()) continue
                if (trimmed.contentEquals("--".toByteArray(Charsets.UTF_8))) continue

                val headerEnd = indexOfBytes(trimmed, "\r\n\r\n".toByteArray(Charsets.UTF_8), 0)
                if (headerEnd <= 0) continue

                val headerText = trimmed.copyOfRange(0, headerEnd).toString(Charsets.UTF_8)
                val bodyBytes = trimmed.copyOfRange(headerEnd + 4, trimmed.size)
                val fileBytes = trimTrailingCrlf(bodyBytes)

                val disposition = headerText.lines().firstOrNull {
                    it.startsWith("Content-Disposition", ignoreCase = true)
                } ?: continue

                val filename = Regex("filename=\"([^\"]*)\"").find(disposition)?.groupValues?.get(1)
                if (filename.isNullOrBlank()) continue

                val safeName = sanitizeFilename(filename)
                val targetPath = "${flipperPath.trimEnd('/')}/$safeName"
                val writeResult = flipperFileSystem.writeFileBytes(targetPath, fileBytes)
                if (writeResult.isFailure) {
                    sendJsonResponse(writer, "{\"error\":\"Failed to write ${jsonEscape(safeName)}\"}")
                    return
                }

                savedCount += 1
                totalBytes += fileBytes.size
            }

            if (savedCount > 0) {
                sendJsonResponse(
                    writer,
                    "{\"success\":true,\"files\":$savedCount,\"bytes\":$totalBytes}"
                )
            } else {
                sendJsonResponse(writer, "{\"error\":\"No files found in upload\"}")
            }
        } catch (e: Exception) {
            sendJsonResponse(writer, "{\"error\":\"${jsonEscape(e.message ?: "Unknown error")}\"}")
        }
    }

    private fun readHttpLine(inputStream: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val byte = inputStream.read()
            if (byte == -1) {
                return if (buffer.size() == 0) null else buffer.toString(Charsets.UTF_8.name())
            }
            if (byte == '\n'.code) {
                return buffer.toString(Charsets.UTF_8.name())
            }
            if (byte != '\r'.code) {
                buffer.write(byte)
            }
        }
    }

    private fun readFully(inputStream: InputStream, length: Int): ByteArray {
        var remaining = length
        val output = ByteArrayOutputStream(length)
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val read = inputStream.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        return output.toByteArray()
    }

    private fun splitByteArray(data: ByteArray, delimiter: ByteArray): List<ByteArray> {
        if (delimiter.isEmpty()) return listOf(data)
        val parts = mutableListOf<ByteArray>()
        var start = 0
        var index = indexOfBytes(data, delimiter, start)
        while (index >= 0) {
            parts.add(data.copyOfRange(start, index))
            start = index + delimiter.size
            index = indexOfBytes(data, delimiter, start)
        }
        if (start <= data.size) {
            parts.add(data.copyOfRange(start, data.size))
        }
        return parts
    }

    private fun indexOfBytes(data: ByteArray, pattern: ByteArray, start: Int): Int {
        if (pattern.isEmpty()) return start
        if (data.size < pattern.size) return -1
        var i = start
        while (i <= data.size - pattern.size) {
            var matched = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return i
            i++
        }
        return -1
    }

    private fun trimLeadingCrlf(data: ByteArray): ByteArray {
        var start = 0
        if (data.size >= 2 && data[0] == '\r'.code.toByte() && data[1] == '\n'.code.toByte()) {
            start = 2
        }
        return if (start == 0) data else data.copyOfRange(start, data.size)
    }

    private fun trimTrailingCrlf(data: ByteArray): ByteArray {
        if (data.size >= 2 &&
            data[data.size - 2] == '\r'.code.toByte() &&
            data[data.size - 1] == '\n'.code.toByte()
        ) {
            return data.copyOfRange(0, data.size - 2)
        }
        return data
    }

    private fun sanitizeFilename(name: String): String {
        val base = name.substringAfterLast("/").substringAfterLast("\\")
        return base.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(120)
    }

    private fun jsonEscape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun serve404(writer: PrintWriter) {
        val html = "<html><body><h1>404 Not Found</h1></body></html>"
        writer.println("HTTP/1.1 404 Not Found")
        writer.println("Content-Type: text/html")
        writer.println("Content-Length: ${html.length}")
        writer.println()
        writer.print(html)
        writer.flush()
    }

    private fun sendHtmlResponse(writer: PrintWriter, html: String) {
        writer.println("HTTP/1.1 200 OK")
        writer.println("Content-Type: text/html; charset=utf-8")
        writer.println("Content-Length: ${html.toByteArray().size}")
        writer.println()
        writer.print(html)
        writer.flush()
    }

    private fun sendJsonResponse(writer: PrintWriter, json: String) {
        writer.println("HTTP/1.1 200 OK")
        writer.println("Content-Type: application/json")
        writer.println("Content-Length: ${json.length}")
        writer.println()
        writer.print(json)
        writer.flush()
    }

    private fun extractQueryParam(path: String, param: String): String? {
        val queryStart = path.indexOf('?')
        if (queryStart < 0) return null

        val query = path.substring(queryStart + 1)
        query.split('&').forEach { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size == 2 && parts[0] == param) {
                return URLDecoder.decode(parts[1], "UTF-8")
            }
        }
        return null
    }
}
