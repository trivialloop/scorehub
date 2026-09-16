package com.github.trivialloop.scorehub.games.belote

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.databinding.ActivityBeloteTeamAssignmentBinding
import com.github.trivialloop.scorehub.utils.LocaleHelper

class BeloteTeamAssignmentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBeloteTeamAssignmentBinding

    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    /** team[i] = 0 or 1, parallel to playerIds/playerNames/playerColors. */
    private lateinit var team: IntArray

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBeloteTeamAssignmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            binding.root.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        playerIds    = intent.getLongArrayExtra("PLAYER_IDS")     ?: longArrayOf()
        playerNames  = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS")   ?: intArrayOf()
        // Default split: first two players = Team 1, last two = Team 2 (already valid 2v2).
        team = IntArray(playerIds.size) { if (it < 2) 0 else 1 }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.belote_team_mode_title)

        binding.textDragHint.text = getString(R.string.belote_drag_hint)
        binding.textTeam1Title.text = getString(R.string.belote_team_1)
        binding.textTeam2Title.text = getString(R.string.belote_team_2)
        binding.btnRandomTeams.text = getString(R.string.belote_team_mode_random)
        binding.btnConfirmTeams.text = getString(R.string.belote_confirm_teams)

        setupDropTarget(binding.columnTeam1, teamIndex = 0)
        setupDropTarget(binding.columnTeam2, teamIndex = 1)

        binding.btnRandomTeams.setOnClickListener { randomizeTeams() }
        binding.btnConfirmTeams.setOnClickListener { confirmTeams() }

        rebuildColumns()
    }

    // ─── Random shuffle ─────────────────────────────────────────────────────────

    private fun randomizeTeams() {
        val assignment = mutableListOf(0, 0, 1, 1)
        assignment.shuffle()
        for (i in team.indices) team[i] = assignment.getOrElse(i) { 0 }
        rebuildColumns()
    }

    // ─── Chips ────────────────────────────────────────────────────────────────

    private fun rebuildColumns() {
        binding.columnTeam1.removeAllViews()
        binding.columnTeam2.removeAllViews()
        for (i in playerIds.indices) {
            val chip = makePlayerChip(i)
            if (team[i] == 0) binding.columnTeam1.addView(chip) else binding.columnTeam2.addView(chip)
        }
        updateConfirmButtonState()
    }

    @Suppress("ClickableViewAccessibility")
    private fun makePlayerChip(playerIndex: Int): TextView {
        return TextView(this).apply {
            text = playerNames[playerIndex]
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpToPx(8) }
            background = GradientDrawable().apply {
                setColor(playerColors[playerIndex])
                cornerRadius = dpToPx(8).toFloat()
            }
            tag = playerIndex

            // Immediate drag on touch down — no long-press required.
            setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val clipData = ClipData.newPlainText("playerIndex", playerIndex.toString())
                    val shadow = View.DragShadowBuilder(view)
                    view.startDragAndDrop(clipData, shadow, view, 0)
                    view.alpha = 0.3f
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun setupDropTarget(container: LinearLayout, teamIndex: Int) {
        container.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.background = GradientDrawable().apply {
                        setColor(ContextCompat.getColor(this@BeloteTeamAssignmentActivity, R.color.cell_editable_bg))
                    }
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    view.setBackgroundColor(ContextCompat.getColor(this@BeloteTeamAssignmentActivity, R.color.cell_locked_bg))
                    true
                }
                DragEvent.ACTION_DROP -> {
                    view.setBackgroundColor(ContextCompat.getColor(this@BeloteTeamAssignmentActivity, R.color.cell_locked_bg))
                    val draggedView = event.localState as? View
                    draggedView?.alpha = 1f
                    val playerIndex = (draggedView?.tag as? Int) ?: return@setOnDragListener false
                    team[playerIndex] = teamIndex
                    rebuildColumns()
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    view.setBackgroundColor(ContextCompat.getColor(this@BeloteTeamAssignmentActivity, R.color.cell_locked_bg))
                    (event.localState as? View)?.alpha = 1f
                    true
                }
                else -> true
            }
        }
    }

    private fun updateConfirmButtonState() {
        val team1Count = team.count { it == 0 }
        val team2Count = team.count { it == 1 }
        val valid = team1Count == 2 && team2Count == 2
        binding.btnConfirmTeams.isEnabled = valid
        binding.btnConfirmTeams.alpha = if (valid) 1f else 0.5f
    }

    private fun confirmTeams() {
        if (team.count { it == 0 } != 2) {
            Toast.makeText(this, getString(R.string.belote_teams_error), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, BeloteGameActivity::class.java).apply {
            putExtra("PLAYER_IDS", playerIds)
            putExtra("PLAYER_NAMES", playerNames)
            putExtra("PLAYER_COLORS", playerColors)
            putExtra("PLAYER_TEAMS", team)
        }
        startActivity(intent)
        finish()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
