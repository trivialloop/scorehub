package com.github.trivialloop.scorehub.games.cactus

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.trivialloop.scorehub.BaseActivity
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.databinding.ActivityCactusTop20Binding
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CactusTop20Activity : BaseActivity() {

    private lateinit var binding: ActivityCactusTop20Binding
    private lateinit var database: AppDatabase

    companion object {
        const val GAME_TYPE = "cactus"
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCactusTop20Binding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.top_20)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        loadTop20()
    }

    private fun loadTop20() {
        lifecycleScope.launch {
            // In Cactus, highest score = best — getTop20ByGameType already orders DESC
            val entries = database.gameResultDao().getTop20ByGameType(GAME_TYPE).map { result ->
                val player = database.playerDao().getPlayerById(result.playerId)
                CactusTop20Entry(result, player?.color ?: android.graphics.Color.GRAY)
            }
            binding.recyclerView.adapter = CactusTop20Adapter(entries)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

data class CactusTop20Entry(val result: GameResult, val color: Int)

class CactusTop20Adapter(private val entries: List<CactusTop20Entry>) :
    RecyclerView.Adapter<CactusTop20Adapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textPosition: android.widget.TextView = view.findViewById(R.id.textPosition)
        val colorIndicator: View = view.findViewById(R.id.colorIndicator)
        val textPlayerName: android.widget.TextView = view.findViewById(R.id.textPlayerName)
        val textDate: android.widget.TextView = view.findViewById(R.id.textDate)
        val textScore: android.widget.TextView = view.findViewById(R.id.textScore)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top20, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.textPosition.text = when (position) {
            0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${position + 1}"
        }
        (holder.colorIndicator.background as? GradientDrawable)?.setColor(entry.color)
        holder.textPlayerName.text = entry.result.playerName
        holder.textDate.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            .format(Date(entry.result.playedAt))
        holder.textScore.text = entry.result.score.toString()
    }

    override fun getItemCount() = entries.size
}
