package com.salatlock.app

import android.content.Context
import android.content.SharedPreferences

/** Stockage en mémoire, utilisé seulement quand le système n'en fournit pas (aperçus du design). */
class MemPrefs : SharedPreferences {
    private val m = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = HashMap(m)
    override fun getString(k: String?, d: String?) = m[k] as String? ?: d
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(k: String?, d: MutableSet<String>?) = (m[k] as Set<String>?)?.toMutableSet() ?: d
    override fun getInt(k: String?, d: Int) = m[k] as Int? ?: d
    override fun getLong(k: String?, d: Long) = m[k] as Long? ?: d
    override fun getFloat(k: String?, d: Float) = m[k] as Float? ?: d
    override fun getBoolean(k: String?, d: Boolean) = m[k] as Boolean? ?: d
    override fun contains(k: String?) = m.containsKey(k)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val p = HashMap<String, Any?>(); private val rm = HashSet<String>(); private var clear = false
        override fun putString(k: String?, v: String?) = apply { p[k!!] = v }
        override fun putStringSet(k: String?, v: MutableSet<String>?) = apply { p[k!!] = v?.toSet() }
        override fun putInt(k: String?, v: Int) = apply { p[k!!] = v }
        override fun putLong(k: String?, v: Long) = apply { p[k!!] = v }
        override fun putFloat(k: String?, v: Float) = apply { p[k!!] = v }
        override fun putBoolean(k: String?, v: Boolean) = apply { p[k!!] = v }
        override fun remove(k: String?) = apply { rm += k!! }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() { if (clear) m.clear(); rm.forEach { m.remove(it) }; m.putAll(p) }
    }

    companion object {
        private val stores = HashMap<String, MemPrefs>()
        fun of(ctx: Context, name: String): SharedPreferences {
            val real = try { (ctx.applicationContext ?: ctx).getSharedPreferences(name, Context.MODE_PRIVATE) } catch (_: Exception) { null }
            // Les aperçus du design fournissent un faux stockage qui ne sait pas écrire : on le remplace.
            val works = try { real?.edit()?.putBoolean("_probe", true) != null } catch (_: Exception) { false }
            return if (real != null && works) real else stores.getOrPut(name) { MemPrefs() }
        }
    }
}
