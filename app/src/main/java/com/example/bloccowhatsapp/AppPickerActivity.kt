package com.example.bloccowhatsapp

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

/**
 * Schermata per selezionare le app da bloccare tra quelle
 * realmente installate sul telefono.
 */
class AppPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SELECTED_APPS = "selected_apps"
        const val RESULT_APPS = "result_apps"
    }

    private lateinit var adapter: AppListAdapter
    private val allApps = mutableListOf<InstalledApp>()
    private val filteredApps = mutableListOf<InstalledApp>()
    private val selectedApps = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        // App già selezionate in precedenza
        val preSelected = intent.getStringArrayListExtra(EXTRA_SELECTED_APPS) ?: arrayListOf()
        selectedApps.addAll(preSelected)

        val rvApps = findViewById<RecyclerView>(R.id.rvApps)
        val etSearch = findViewById<EditText>(R.id.etSearch)
        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirmApps)

        // Carica app installate in background
        loadInstalledApps()

        adapter = AppListAdapter(filteredApps, selectedApps)
        rvApps.layoutManager = LinearLayoutManager(this)
        rvApps.adapter = adapter

        // Ricerca
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Conferma selezione
        btnConfirm.setOnClickListener {
            val result = Intent().apply {
                putStringArrayListExtra(RESULT_APPS, ArrayList(selectedApps))
            }
            setResult(RESULT_OK, result)
            finish()
        }

        // Pulsante indietro nella toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Scegli le app da bloccare"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val installedPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        allApps.clear()
        for (appInfo in installedPackages) {
            // Escludi app di sistema e la nostra stessa app
            if (appInfo.packageName == packageName) continue
            if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
                // Includi solo app di sistema con launcher (es. alcune app preinstallate utili)
                val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent == null) continue
            }

            val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName) ?: continue
            val appName = pm.getApplicationLabel(appInfo).toString()
            val icon = try { pm.getApplicationIcon(appInfo.packageName) } catch (e: Exception) { null }

            allApps.add(InstalledApp(appInfo.packageName, appName, icon))
        }

        // Ordine alfabetico
        allApps.sortBy { it.name.lowercase() }
        filteredApps.clear()
        filteredApps.addAll(allApps)

        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun filterApps(query: String) {
        filteredApps.clear()
        if (query.isEmpty()) {
            filteredApps.addAll(allApps)
        } else {
            filteredApps.addAll(
                allApps.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
                }
            )
        }
        adapter.notifyDataSetChanged()
    }

    data class InstalledApp(
        val packageName: String,
        val name: String,
        val icon: Drawable?
    )

    class AppListAdapter(
        private val apps: List<InstalledApp>,
        private val selected: MutableSet<String>
    ) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.appIcon)
            val name: TextView = view.findViewById(R.id.appName)
            val pkg: TextView = view.findViewById(R.id.appPackage)
            val checkbox: CheckBox = view.findViewById(R.id.appCheckbox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.name.text = app.name
            holder.pkg.text = app.packageName
            if (app.icon != null) holder.icon.setImageDrawable(app.icon)
            else holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
            holder.checkbox.isChecked = selected.contains(app.packageName)

            val toggle = {
                if (selected.contains(app.packageName)) {
                    selected.remove(app.packageName)
                    holder.checkbox.isChecked = false
                } else {
                    selected.add(app.packageName)
                    holder.checkbox.isChecked = true
                }
            }
            holder.itemView.setOnClickListener { toggle() }
            holder.checkbox.setOnClickListener { toggle() }
        }

        override fun getItemCount() = apps.size
    }
}
