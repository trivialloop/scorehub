package com.github.trivialloop.scorehub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.github.trivialloop.scorehub.databinding.ActivitySettingsBinding
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.SupportedLanguages
import com.github.trivialloop.scorehub.utils.ThemeHelper
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.backup.BackupFile
import com.github.trivialloop.scorehub.data.backup.BackupFiles
import com.github.trivialloop.scorehub.data.backup.BackupPrefs
import com.github.trivialloop.scorehub.data.backup.BackupRepository
import com.github.trivialloop.scorehub.ui.BackupMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var backupRepo: BackupRepository
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { exportTo(it) }
        }
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importFrom(it) }
        }
    
    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->

            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.appBarLayout.setPadding(
                0,
                systemBars.top,
                0,
                0
            )

            binding.root.setPadding(
                systemBars.left,
                0,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)
        
        setupLanguageSpinner()
        setupThemeSpinner()

        backupRepo = BackupRepository(AppDatabase.getDatabase(this))
        binding.btnExportBackup.setOnClickListener {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            exportLauncher.launch("scorehub-backup-$date.json")
        }
        binding.btnImportBackup.setOnClickListener { importLauncher.launch(arrayOf("*/*")) }
    }
    
    private fun setupLanguageSpinner() {
        val languages = SupportedLanguages.ALL.map { getString(it.displayNameResId) }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter

        val currentLanguage = LocaleHelper.getPersistedLocale(this)
        binding.spinnerLanguage.setSelection(SupportedLanguages.indexOf(currentLanguage))

        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newLanguage = SupportedLanguages.ALL[position].code
                if (newLanguage != currentLanguage) {
                    LocaleHelper.setLocale(this@SettingsActivity, newLanguage)
                    restartApp()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupThemeSpinner() {
        val themes = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTheme.adapter = adapter
        
        val currentTheme = ThemeHelper.getPersistedTheme(this)
        val position = when (currentTheme) {
            ThemeHelper.THEME_LIGHT -> 1
            ThemeHelper.THEME_DARK -> 2
            else -> 0
        }
        binding.spinnerTheme.setSelection(position)
        
        binding.spinnerTheme.onItemSelectedListener = 
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    val newTheme = when (position) {
                        1 -> ThemeHelper.THEME_LIGHT
                        2 -> ThemeHelper.THEME_DARK
                        else -> ThemeHelper.THEME_SYSTEM
                    }
                    if (newTheme != currentTheme) {
                        ThemeHelper.setTheme(this@SettingsActivity, newTheme)
                    }
                }
                
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
    }

        // ─── Backup ───────────────────────────────────────────────────────────────

    private fun exportTo(uri: Uri) {
        lifecycleScope.launch {
            try {
                val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
                BackupFiles.write(contentResolver, uri, backupRepo.exportToJson(version))
                Toast.makeText(this@SettingsActivity, R.string.backup_export_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.backup_export_error, e.message ?: e.javaClass.simpleName),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun importFrom(uri: Uri) {
        lifecycleScope.launch {
            try {
                val text = BackupFiles.read(contentResolver, uri)
                val backup = withContext(Dispatchers.Default) { backupRepo.parseAndCheck(text) }
                if (backupRepo.isDatabaseEmpty()) {
                    // Nothing to lose: import directly, uuids are kept
                    overwriteWith(backup)
                } else {
                    showImportChoice(uri, backup)
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, BackupMessages.of(this@SettingsActivity, e), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showImportChoice(uri: Uri, backup: BackupFile) {
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_import_choice_title)
            .setMessage(R.string.backup_import_choice_message)
            .setPositiveButton(R.string.backup_merge) { _, _ ->
                startActivity(Intent(this, BackupImportActivity::class.java).apply {
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }
            .setNegativeButton(R.string.backup_overwrite) { _, _ -> confirmOverwrite(backup) }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun confirmOverwrite(backup: BackupFile) {
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_overwrite_confirm_title)
            .setMessage(R.string.backup_overwrite_confirm_message)
            .setPositiveButton(R.string.backup_overwrite) { _, _ -> overwriteWith(backup) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun overwriteWith(backup: BackupFile) {
        lifecycleScope.launch {
            try {
                backupRepo.overwrite(backup)
                BackupPrefs.resetPlayerOrders(this@SettingsActivity)
                val games = backup.games.distinctBy { it.gameType to it.playedAt }.size
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.backup_import_done, backup.players.size, games),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, BackupMessages.of(this@SettingsActivity, e), Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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
