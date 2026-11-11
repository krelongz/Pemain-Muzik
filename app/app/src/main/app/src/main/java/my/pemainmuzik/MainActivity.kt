package my.pemainmuzik

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.OpenableColumns
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

class MainActivity : Activity() {

    private class Track(var title: String, var artist: String, var uri: String?)

    private val redirect = "pemainmuzik://callback"
    private val tracks = mutableListOf<Track>()
    private var idx = -1
    private var shuffle = false
    private var player: MediaPlayer? = null
    private var spotifyToken: String? = null
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var prefs: SharedPreferences
    private lateinit var nowTitle: TextView
    private lateinit var nowArtist: TextView
    private lateinit var seek: SeekBar
    private lateinit var playBtn: Button
    private lateinit var shufBtn: Button
    private lateinit var status: TextView
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>

    private val ticker = object : Runnable {
        override fun run() {
            try {
                val p = player
                if (p != null && p.isPlaying && p.duration > 0) {
                    seek.progress = (p.currentPosition * 1000L / p.duration).toInt()
                }
            } catch (e: Exception) {
            }
            ui.postDelayed(this, 500)
        }
    }

    // ---------- kitaran hidup ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("pm", Context.MODE_PRIVATE)
        loadTracks()
        buildUi()
        refresh()
        ui.post(ticker)
        handleRedirect(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRedirect(intent)
    }

    override fun onDestroy() {
        ui.removeCallbacks(ticker)
        player?.release()
        player = null
        super.onDestroy()
    }

    // ---------- antara muka ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun btn(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }

    private fun row(vararg views: View): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        for (v in views) {
            r.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        return r
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(24), dp(16), dp(16))
        root.setBackgroundColor(Color.parseColor("#EEF1F5"))

        val title = TextView(this)
        title.text = "Pemain Muzik"
        title.textSize = 24f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.parseColor("#14202E"))
        root.addView(title)

        nowTitle = TextView(this)
        nowTitle.text = "Belum ada lagu dipilih"
        nowTitle.textSize = 18f
        nowTitle.setTextColor(Color.parseColor("#14202E"))
        nowTitle.setPadding(0, dp(12), 0, 0)
        root.addView(nowTitle)

        nowArtist = TextView(this)
        nowArtist.text = "Import senarai, lepas itu tambah fail audio."
        nowArtist.setTextColor(Color.parseColor("#5D6B7C"))
        root.addView(nowArtist)

        seek = SeekBar(this)
        seek.max = 1000
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                try {
                    val p = player
                    if (p != null && p.duration > 0) {
                        p.seekTo((progress / 1000.0 * p.duration).toInt())
                    }
                } catch (e: Exception) {
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        root.addView(seek)

        shufBtn = btn("Rawak: mati") {
            shuffle = !shuffle
            shufBtn.text = if (shuffle) "Rawak: hidup" else "Rawak: mati"
        }
        playBtn = btn("Main") { togglePlay() }
        root.addView(
            row(
                shufBtn,
                btn("<<") { step(-1) },
                playBtn,
                btn(">>") { step(1) }
            )
        )

        listView = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ ->
            val t = tracks[pos]
            if (t.uri != null) playAt(pos) else openJamendo(t)
        }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            val t = tracks[pos]
            AlertDialog.Builder(this)
                .setTitle("Buang lagu ini?")
                .setMessage(t.title)
                .setPositiveButton("Buang") { _, _ ->
                    if (idx == pos) {
                        player?.release()
                        player = null
                        idx = -1
                        playBtn.text = "Main"
                    } else if (idx > pos) {
                        idx--
                    }
                    tracks.removeAt(pos)
                    saveTracks()
                    refresh()
                }
                .setNegativeButton("Batal", null)
                .show()
            true
        }
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        status = TextView(this)
        status.setTextColor(Color.parseColor("#5D6B7C"))
        status.setPadding(0, dp(6), 0, dp(6))
        root.addView(status)

        root.addView(
            row(
                btn("Import Spotify") { spotifyDialog() },
                btn("Tampal senarai") { pasteDialog() },
                btn("Tambah fail") { pickFiles() }
            )
        )
        setContentView(root)
    }

    private fun refresh() {
        adapter.clear()
        tracks.forEachIndexed { i, t ->
            val mark = if (i == idx) "> " else if (t.uri != null) "* " else "- "
            val sub = if (t.uri != null) {
                t.artist
            } else {
                (if (t.artist.isEmpty()) "" else t.artist + " | ") + "belum ada fail, ketik untuk cari di Jamendo"
            }
            adapter.add(mark + t.title + "\n" + sub)
        }
        adapter.notifyDataSetChanged()
        val have = tracks.count { it.uri != null }
        status.text = if (tracks.isEmpty()) {
            "Senarai kosong. Guna Import Spotify atau Tampal senarai."
        } else {
            "${tracks.size} lagu, $have ada fail. Tekan lama untuk buang."
        }
    }

    private fun toast(m: String) {
        Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    }

    // ---------- main lagu ----------

    private fun playAt(i: Int) {
        val t = tracks.getOrNull(i) ?: return
        val u = t.uri ?: return
        try {
            player?.release()
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(applicationContext, Uri.parse(u))
            mp.setOnPreparedListener {
                it.start()
                playBtn.text = "Jeda"
            }
            mp.setOnCompletionListener { step(1) }
            mp.setOnErrorListener { _, _, _ ->
                toast("Fail ini tak boleh dimainkan.")
                true
            }
            mp.prepareAsync()
            player = mp
            idx = i
            nowTitle.text = t.title
            nowArtist.text = t.artist
            seek.progress = 0
            refresh()
        } catch (e: Exception) {
            toast("Fail ini tak boleh dimainkan: " + e.message)
        }
    }

    private fun togglePlay() {
        val p = player
        if (p == null) {
            val first = tracks.indexOfFirst { it.uri != null }
            if (first >= 0) playAt(first) else toast("Tambah fail audio dulu.")
            return
        }
        try {
            if (p.isPlaying) {
                p.pause()
                playBtn.text = "Main"
            } else {
                p.start()
                playBtn.text = "Jeda"
            }
        } catch (e: Exception) {
        }
    }

    private fun step(dir: Int) {
        val ok = tracks.indices.filter { tracks[it].uri != null }
        if (ok.isEmpty()) return
        val next = if (shuffle) {
            ok.random()
        } else {
            val pos = ok.indexOf(idx)
            if (pos < 0) ok[0] else ok[(pos + dir + ok.size) % ok.size]
        }
        playAt(next)
    }

    private fun openJamendo(t: Track) {
        val q = enc((t.title + " " + t.artist).trim())
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.jamendo.com/search?q=$q")))
        } catch (e: Exception) {
            toast("Tak dapat buka pelayar.")
        }
    }

    // ---------- fail audio ----------

    private fun pickFiles() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "audio/*"
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(i, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1 || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        val cd = data.clipData
        if (cd != null) {
            for (k in 0 until cd.itemCount) uris.add(cd.getItemAt(k).uri)
        } else {
            val single = data.data
            if (single != null) uris.add(single)
        }
        var matched = 0
        var added = 0
        for (u in uris) {
            try {
                contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
            val name = displayName(u)
            val nf = norm(name)
            val m = tracks.firstOrNull {
                it.uri == null && norm(it.title).length >= 3 && nf.contains(norm(it.title))
            }
            if (m != null) {
                m.uri = u.toString()
                matched++
            } else {
                val base = name.substringBeforeLast('.')
                val parts = base.split(" - ", limit = 2)
                if (parts.size == 2) {
                    tracks.add(Track(parts[1].trim(), parts[0].trim(), u.toString()))
                } else {
                    tracks.add(Track(base.trim(), "", u.toString()))
                }
                added++
            }
        }
        saveTracks()
        refresh()
        toast("${uris.size} fail: $matched dipadankan dengan senarai, $added ditambah sebagai lagu baru.")
    }

    private fun displayName(u: Uri): String {
        var n: String? = null
        try {
            contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) n = c.getString(0)
            }
        } catch (e: Exception) {
        }
        return n ?: u.lastPathSegment ?: "lagu"
    }

    // ---------- import teks / CSV ----------

    private fun norm(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '"') {
                if (q && i + 1 < line.length && line[i + 1] == '"') {
                    cur.append('"')
                    i++
                } else {
                    q = !q
                }
            } else if (ch == ',' && !q) {
                out.add(cur.toString())
                cur.setLength(0)
            } else {
                cur.append(ch)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }

    private fun parseList(text: String): List<Pair<String, String>> {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<Pair<String, String>>()
        if (lines.isEmpty()) return out
        if (lines[0].lowercase().contains("track name")) {
            val cols = splitCsv(lines[0]).map { it.trim().lowercase() }
            val ti = cols.indexOf("track name")
            var ai = -1
            for ((k, c) in cols.withIndex()) {
                if (ai < 0 && c.startsWith("artist name")) ai = k
            }
            for (n in 1 until lines.size) {
                val f = splitCsv(lines[n])
                if (ti >= 0 && ti < f.size && f[ti].isNotBlank()) {
                    val artist = if (ai >= 0 && ai < f.size) f[ai].split(";")[0].trim() else ""
                    out.add(Pair(f[ti].trim(), artist))
                }
            }
            return out
        }
        for (l in lines) {
            val m = l.split(Regex("\\s+[-\u2013\u2014]\\s+"), limit = 2)
            if (m.size == 2) out.add(Pair(m[0].trim(), m[1].trim())) else out.add(Pair(l, ""))
        }
        return out
    }

    private fun addTracks(items: List<Pair<String, String>>): Int {
        var n = 0
        for ((title, artist) in items) {
            if (title.isBlank()) continue
            val key = norm(title)
            if (tracks.any { norm(it.title) == key }) continue
            tracks.add(Track(title, artist, null))
            n++
        }
        saveTracks()
        refresh()
        return n
    }

    private fun pasteDialog() {
        val et = EditText(this)
        et.hint = "Tajuk - Artis (satu lagu satu baris)\natau tampal CSV"
        et.minLines = 6
        et.gravity = android.view.Gravity.TOP
        AlertDialog.Builder(this)
            .setTitle("Tampal senarai lagu")
            .setView(et)
            .setPositiveButton("Import") { _, _ ->
                val items = parseList(et.text.toString())
                if (items.isEmpty()) {
                    toast("Tiada lagu dikesan.")
                } else {
                    toast("${addTracks(items)} lagu ditambah.")
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ---------- simpan senarai ----------

    private fun saveTracks() {
        val arr = JSONArray()
        for (t in tracks) {
            val o = JSONObject()
            o.put("title", t.title)
            o.put("artist", t.artist)
            if (t.uri != null) o.put("uri", t.uri)
            arr.put(o)
        }
        prefs.edit().putString("tracks", arr.toString()).apply()
    }

    private fun loadTracks() {
        try {
            val s = prefs.getString("tracks", null) ?: return
            val arr = JSONArray(s)
            for (k in 0 until arr.length()) {
                val o = arr.getJSONObject(k)
                val u = if (o.has("uri")) o.getString("uri") else null
                tracks.add(Track(o.optString("title"), o.optString("artist"), u))
            }
        } catch (e: Exception) {
        }
    }

    // ---------- Spotify (OAuth PKCE) ----------

    private fun b64(b: ByteArray): String =
        Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun spotifyDialog() {
        val et = EditText(this)
        et.hint = "Spotify Client ID"
        et.setSingleLine(true)
        et.setText(prefs.getString("cid", ""))
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val info = TextView(this)
        info.text = "1. Buka developer.spotify.com/dashboard dan cipta app.\n" +
            "2. Tambah Redirect URI: $redirect\n" +
            "3. Tampal Client ID di bawah.\n\n" +
            "Akaun pemilik app mesti Spotify Premium. Hanya playlist kau sendiri atau yang kau kolaborasi boleh dibaca."
        box.addView(info)
        box.addView(et)
        AlertDialog.Builder(this)
            .setTitle("Sambung ke Spotify")
            .setView(box)
            .setPositiveButton("Sambung") { _, _ ->
                val cid = et.text.toString().trim()
                if (cid.isEmpty()) toast("Masukkan Client ID dulu.") else startAuth(cid)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun startAuth(cid: String) {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        val verifier = b64(bytes)
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        prefs.edit().putString("cid", cid).putString("verifier", verifier).apply()
        val uri = Uri.Builder()
            .scheme("https")
            .authority("accounts.spotify.com")
            .appendPath("authorize")
            .appendQueryParameter("client_id", cid)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirect)
            .appendQueryParameter("scope", "playlist-read-private playlist-read-collaborative")
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            toast("Tak dapat buka pelayar.")
        }
    }

    private fun handleRedirect(i: Intent?) {
        val d = i?.data ?: return
        if (d.scheme != "pemainmuzik") return
        setIntent(Intent())
        val err = d.getQueryParameter("error")
        if (err != null) {
            toast("Spotify: $err")
            return
        }
        val code = d.getQueryParameter("code") ?: return
        exchange(code)
    }

    private fun worker(job: () -> Unit) {
        Thread {
            try {
                job()
            } catch (e: Exception) {
                runOnUiThread { toast("Ralat: " + e.message) }
            }
        }.start()
    }

    private fun http(method: String, url: String, bearer: String?, body: String?): Pair
