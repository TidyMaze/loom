package com.yrolland.loom

import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class OnboardingStoreTest {

    private class FakeSharedPreferences : SharedPreferences {
        private val map = ConcurrentHashMap<String, Any>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (map[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class FakeEditor(private val target: ConcurrentHashMap<String, Any>) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor { key?.let { temp[it] = value }; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { key?.let { temp[it] = values }; return this }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { key?.let { temp[it] = value }; return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { key?.let { temp[it] = value }; return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { key?.let { temp[it] = value }; return this }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { key?.let { temp[it] = value }; return this }
            override fun remove(key: String?): SharedPreferences.Editor { key?.let { temp[it] = null }; return this }
            override fun clear(): SharedPreferences.Editor { clearFlag = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearFlag) target.clear()
                temp.forEach { (k, v) -> if (v == null) target.remove(k) else target[k] = v }
            }
        }
    }

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var store: OnboardingStore

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        store = OnboardingStore(prefs)
    }

    @Test
    fun initiallyNotCompleted() {
        assertFalse(store.isOnboardingCompleted())
    }

    @Test
    fun markCompletedPersists() {
        store.setOnboardingCompleted()
        assertTrue(store.isOnboardingCompleted())

        // Same underlying prefs
        val newStore = OnboardingStore(prefs)
        assertTrue(newStore.isOnboardingCompleted())
    }
}
