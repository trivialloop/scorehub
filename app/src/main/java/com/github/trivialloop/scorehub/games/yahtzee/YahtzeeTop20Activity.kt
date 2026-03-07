package com.github.trivialloop.scorehub.games.yahtzee

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.data.Player
import com.github.trivialloop.scorehub.databinding.ActivityYahtzeeTop20Binding
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class YahtzeeTop20Activity : AppCompatActivity() {
    private lateinit var binding: ActivityYahtzeeTop20Binding
    private lateinit var database: AppDatabase

    companion object {
        const val GAME_TYPE = "yahtzee"
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYahtzeeTop20Binding.inflate(layoutInflater)
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
            val top20Results = database.gameResultDao().getTop20ByGameType(GAME_TYPE)
            val top20WithColors = mutableListOf<Top20Entry>()

            for (result in top20Results) {
                val player = database.playerDao().getPlayerById(result.playerId)
                top20WithColors.add(
                    Top20Entry(
                        result = result,
                        color = player?.color ?: android.graphics.Color.GRAY
                    )
                )
            }

            binding.recyclerView.adapter = Top20Adapter(top20WithColors)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

data class Top20Entry(
    val result: GameResult,
    val color: Int
)

class Top20Adapter(private val entries: List<Top20Entry>) :
    RecyclerView.Adapter<Top20Adapter.ViewHolder>() {

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
        val result = entry.result

        // Trophy for the 3 firsts players
        holder.textPosition.text = when (position) {
            0 -> "🥇"
            1 -> "🥈"
            2 -> "🥉"
            else -> "${position + 1}"
        }

        // Player color
        val drawable = holder.colorIndicator.background as? GradientDrawable
        drawable?.setColor(entry.color)

        // Player name
        holder.textPlayerName.text = result.playerName

        // Date
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        holder.textDate.text = dateFormat.format(Date(result.playedAt))

        // Score
        holder.textScore.text = result.score.toString()
    }

    override fun getItemCount() = entries.size
}
