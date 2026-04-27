package com.github.trivialloop.scorehub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.github.trivialloop.scorehub.databinding.ActivitySettingsBinding
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ThemeHelper

class SettingsActivity : BaseActivity() {
    private lateinit var binding: ActivitySettingsBinding
    
    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)
        
        setupLanguageSpinner()
        setupThemeSpinner()
    }
    
    private fun setupLanguageSpinner() {
        val languages = arrayOf(
            getString(R.string.english),
            getString(R.string.french)
        )
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter
        
        val currentLanguage = LocaleHelper.getPersistedLocale(this)
        binding.spinnerLanguage.setSelection(if (currentLanguage == "fr") 1 else 0)
        
        binding.spinnerLanguage.onItemSelectedListener = 
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    val newLanguage = if (position == 1) "fr" else "en"
                    if (newLanguage != currentLanguage) {
                        LocaleHelper.setLocale(this@SettingsActivity, newLanguage)
                        restartApp()
                    }
                }
                
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
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
