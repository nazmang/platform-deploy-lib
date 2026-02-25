package com.nazmang.platform

/**
 * Minimal YAML parser for deploy.yaml-style content.
 * Handles maps, lists, string and number values. No anchors/aliases or multi-line blocks.
 * Used when Pipeline Utility Steps (readYaml) is not available.
 */
class YamlParser {

    static Object parse(String yamlText) {
        if (!yamlText?.trim()) return [:]
        def lines = yamlText.readLines()
        def idx = [0]
        return parseBlock(lines, idx, 0)
    }

    private static Object parseBlock(List lines, def idx, int baseIndent) {
        def result = null
        def currentList = null
        def currentMap = null
        def inList = false

        while (idx[0] < lines.size()) {
            def line = lines[idx[0]]
            def stripped = line.trim()
            if (stripped == '' || stripped.startsWith('#')) {
                idx[0]++
                continue
            }

            def indent = line.length() - line.replaceAll('^\\s*', '').length()
            if (baseIndent > 0 && indent < baseIndent) break
            if (indent > baseIndent && result == null) {
                idx[0]++
                continue
            }

            if (stripped.startsWith('- ')) {
                def listItem = stripped.substring(2).trim()
                if (currentList == null) {
                    currentList = []
                    if (currentMap != null) throw new IllegalStateException("Mixed map and list")
                    result = currentList
                }
                inList = true
                if (listItem.contains(':')) {
                    def firstColon = listItem.indexOf(':')
                    def key = listItem.substring(0, firstColon).trim()
                    def valPart = listItem.substring(firstColon + 1).trim()
                    def itemMap = [:]
                    itemMap[key] = (valPart == '' || valPart == '|' || valPart == '>') ? null : parseValue(valPart)
                    idx[0]++
                    def nested = parseBlock(lines, idx, indent + 2)
                    if (nested instanceof Map && !nested.isEmpty()) itemMap.putAll(nested)
                    currentList.add(itemMap)
                } else if (listItem == '' || listItem.endsWith(':')) {
                    idx[0]++
                    def nested = parseBlock(lines, idx, indent + 2)
                    currentList.add(nested ?: [:])
                } else {
                    currentList.add(parseValue(listItem))
                    idx[0]++
                }
                continue
            }

            def colonPos = stripped.indexOf(':')
            if (colonPos > 0) {
                def key = stripped.substring(0, colonPos).trim()
                def valuePart = stripped.substring(colonPos + 1).trim()
                if (currentMap == null) {
                    currentMap = [:]
                    if (currentList == null) result = currentMap
                }
                if (valuePart == '' || valuePart == '|' || valuePart == '>') {
                    idx[0]++
                    def nested = parseBlock(lines, idx, indent + 2)
                    currentMap[key] = nested != null ? nested : [:]
                } else {
                    currentMap[key] = parseValue(valuePart)
                    idx[0]++
                }
                continue
            }

            idx[0]++
        }

        return result ?: [:]
    }

    private static Object parseValue(String s) {
        if (s == 'null' || s == '~') return null
        if (s == 'true') return true
        if (s == 'false') return false
        if (s.startsWith('"') && s.endsWith('"')) return s.substring(1, s.length() - 1).replace('\\"', '"')
        if (s.startsWith("'") && s.endsWith("'")) return s.substring(1, s.length() - 1).replace("\\'", "'")
        if (s.isNumber()) return s.toLong()
        // Try double only for single-dot decimals; version strings like 0.14.8 must stay as string
        if (s.contains('.') && s.replace('.', '').isNumber()) {
            try {
                return s.toDouble()
            } catch (NumberFormatException ignored) {
                // e.g. "0.14.8" has multiple points
            }
        }
        return s
    }
}
