package com.breakingfree.app.todo.domain

object Tags {
    private val separators = Regex("[\\s\\-_]+")

    /**
     * Canonical form of a tag: lower case, no leading '#', spaces and hyphens turned into
     * underscores (runs collapse to one, none at the ends). "Home - Chores" → "home_chores".
     */
    fun normalize(raw: String): String =
        raw.trim().trimStart('#').lowercase().replace(separators, "_").trim('_')

    /** Live form while typing: same substitution, but keeps a trailing '_' so the user can keep typing. */
    fun typing(raw: String): String = raw.lowercase().replace(' ', '_').replace('-', '_')
}
