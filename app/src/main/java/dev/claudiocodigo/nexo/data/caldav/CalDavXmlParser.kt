package dev.claudiocodigo.nexo.data.caldav

import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo
import java.io.ByteArrayInputStream
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException

/**
 * Namespace-aware, regex-free reader for CalDAV/WebDAV XML responses
 * (`DAV:` and CalDAV `urn:ietf:params:xml:ns:caldav`, plus the Apple color
 * extension used by Nextcloud). Properties and privileges are read by
 * (namespaceURI, localName) pair — never by textual prefix.
 */
object CalDavXmlParser {

    private const val NS_D = "DAV:"
    private const val NS_CAL = "urn:ietf:params:xml:ns:caldav"
    private const val NS_APPLE = "http://apple.com/ns/ical/"

    /** Builds a normalized property key: `namespaceWithoutTrailingColon:localName`. */
    private fun q(ns: String?, name: String): String = "${(ns ?: "").trimEnd(':')}:$name"

    /** One `<response>` entry in a multistatus. */
    data class Entry(
        val href: String,
        val properties: Map<String, List<String>>,
        val displayName: String?,
        val description: String?,
        val color: String?,
        val isCalendar: Boolean,
        val calendarComponents: Set<String>,
        val privileges: Set<String>,
        val syncToken: String?,
        /** Direct response status used by RFC 6578 (200 changed, 404 removed). */
        val statusCode: Int? = null
    )

    /** Parses a `<multistatus>` into its entries. */
    fun parseMultistatus(xml: String): List<Entry> {
        return parseMultistatusResult(xml).entries
    }

    /** Parsing outcome used by callers that must distinguish an empty
     * collection from an empty/malformed response. */
    data class MultistatusParseResult(
        val entries: List<Entry>,
        val wellFormedMultistatus: Boolean,
        val syncToken: String? = null
    )

    fun parseMultistatusResult(xml: String): MultistatusParseResult {
        val doc = newDocument(xml)
        val root = doc.documentElement
        if (root == null || root.localName != "multistatus") {
            return MultistatusParseResult(emptyList(), false)
        }
        val responses = root.getElementsByTagNameNS("*", "response")
        val entries = buildList {
            for (i in 0 until responses.length) {
                val response = responses.item(i) as? Element ?: continue
                parseResponse(response)?.let(::add)
            }
        }
        val syncToken = directChild(root, NS_D, "sync-token")?.textContent?.trim()?.takeIf { it.isNotEmpty() }
        return MultistatusParseResult(entries, entries.size == responses.length, syncToken)
    }

    fun syncTokenKey(): String = q(NS_D, "sync-token")
    fun etagKey(): String = q(NS_D, "getetag")
    fun calendarDataKey(): String = q(NS_CAL, "calendar-data")

    /** Parses the discovery bundle: principal and calendar-home-set. */
    fun parseDiscovery(xml: String): DiscoveryBundle {
        val entries = parseMultistatus(xml)
        val first = entries.firstOrNull() ?: return DiscoveryBundle(null, null)
        return DiscoveryBundle(
            principal = first.properties[q(NS_D, "current-user-principal")]?.firstOrNull(),
            calendarHomeSet = first.properties[q(NS_CAL, "calendar-home-set")]?.firstOrNull()
        )
    }

    /** Builds a [CalendarInfo] from an [Entry], keeping only VEVENT calendars. */
    fun toCalendarInfo(entry: Entry, baseHref: String): CalendarInfo? {
        if (!entry.isCalendar) return null
        val components = entry.calendarComponents
        if (components.isNotEmpty() && "VEVENT" !in components) return null
        return CalendarInfo(
            href = resolveHref(baseHref, entry.href),
            displayName = entry.displayName,
            description = entry.description,
            color = entry.color,
            supportsVeEvent = true,
            hasWritePrivilege = entry.privileges.any { it.contains("write") },
            syncToken = entry.syncToken
        )
    }

    private fun parseResponse(response: Element): Entry? {
        val href = response.getElementsByTagNameNS(NS_D, "href").item(0)?.textContent ?: return null
        val properties = linkedMapOf<String, MutableList<String>>()
        var isCalendar = false
        val componentNames = linkedSetOf<String>()
        val privileges = linkedSetOf<String>()
        val directStatus = directChild(response, NS_D, "status")?.textContent?.let(::statusCode)

        // Only properties from an HTTP 200 propstat are usable. A 404
        // propstat is expected for optional WebDAV properties and must not
        // overwrite a valid value (or manufacture a calendar).
        val propstats = response.getElementsByTagNameNS(NS_D, "propstat")
        for (i in 0 until propstats.length) {
            val propstat = propstats.item(i) as? Element ?: continue
            val status = directChild(propstat, NS_D, "status")?.textContent.orEmpty()
            if (!status.matches(Regex("HTTP/\\d(?:\\.\\d)?\\s+200(?:\\s|$).*", RegexOption.IGNORE_CASE))) continue
            val prop = directChild(propstat, NS_D, "prop") ?: continue
            for (j in 0 until prop.childNodes.length) {
                val child = prop.childNodes.item(j) as? Element ?: continue
                val ns = child.namespaceURI
                val name = child.localName
                when {
                    ns == NS_D && name == "resourcetype" ->
                        isCalendar = isCalendar ||
                            child.getElementsByTagNameNS(NS_CAL, "calendar").length > 0
                    ns == NS_CAL && name == "supported-calendar-component-set" -> {
                        val comps = child.getElementsByTagNameNS(NS_CAL, "comp")
                        for (c in 0 until comps.length) {
                            (comps.item(c) as? Element)?.getAttribute("name")
                                ?.takeIf { it.isNotBlank() }?.let { componentNames.add(it) }
                        }
                    }
                    ns == NS_D && name == "current-user-privilege-set" -> {
                        val privs = child.getElementsByTagNameNS(NS_D, "privilege")
                        for (p in 0 until privs.length) {
                            val privEl = privs.item(p) as? Element ?: continue
                            val inner = privEl.firstElementChildNode() ?: continue
                            privileges.add(inner.localName ?: inner.nodeName)
                        }
                    }
                    else -> properties.getOrPut(q(ns, name)) { mutableListOf() }.add(child.textContent.orEmpty())
                }
            }
        }

        fun textFor(ns: String, name: String): String? =
            properties[q(ns, name)]?.firstOrNull()?.takeIf { it.isNotBlank() }

        return Entry(
            href = href,
            properties = properties,
            displayName = textFor(NS_D, "displayname"),
            description = textFor(NS_CAL, "calendar-description"),
            color = textFor(NS_APPLE, "color"),
            isCalendar = isCalendar,
            calendarComponents = componentNames,
            privileges = privileges,
            syncToken = textFor(NS_D, "sync-token"),
            statusCode = directStatus
        )
    }

    private fun statusCode(status: String): Int? =
        status.trim().split(Regex("\\s+")).getOrNull(1)?.toIntOrNull()

    private fun newDocument(xml: String): org.w3c.dom.Document {
        // Android's XML provider does not implement every JAXP feature exposed
        // by DocumentBuilderFactory. Reject declarations that enable external
        // entities before invoking the provider, so security does not depend on
        // an optional Xerces-only flag.
        if (xml.contains("<!DOCTYPE", ignoreCase = true)) {
            throw SAXException("Resposta XML insegura: declarações DOCTYPE não são permitidas")
        }

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true

        // These are provider-specific defense-in-depth options. Android's
        // platform DocumentBuilderFactory intentionally does not implement
        // every JAXP API (notably setXIncludeAware/isXIncludeAware), so do not
        // invoke those APIs at all: their UnsupportedOperationException is
        // the source of "Unknown version 0.0" on Android. The mandatory
        // provider-independent defenses above still reject every DOCTYPE and
        // block entity resolution even when these optional options are absent.
        setFeatureBestEffort(factory, "http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeatureBestEffort(factory, "http://xml.org/sax/features/external-general-entities", false)
        setFeatureBestEffort(factory, "http://xml.org/sax/features/external-parameter-entities", false)
        setFeatureBestEffort(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        setAttributeBestEffort(factory, "http://javax.xml.XMLConstants/property/accessExternalDTD", "")
        setAttributeBestEffort(factory, "http://javax.xml.XMLConstants/property/accessExternalSchema", "")

        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
        return builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    private fun setFeatureBestEffort(factory: DocumentBuilderFactory, name: String, value: Boolean) {
        try {
            factory.setFeature(name, value)
        } catch (_: Exception) {
            // Some Android releases do not expose Xerces feature URIs.
        }
    }

    private fun setAttributeBestEffort(factory: DocumentBuilderFactory, name: String, value: String) {
        try {
            factory.setAttribute(name, value)
        } catch (_: Exception) {
            // accessExternal* is optional on Android's platform provider.
        }
    }

    private fun directChild(parent: Element, namespace: String, localName: String): Element? {
        for (i in 0 until parent.childNodes.length) {
            val child = parent.childNodes.item(i) as? Element ?: continue
            if (child.namespaceURI == namespace && child.localName == localName) return child
        }
        return null
    }

    /** Returns the first child element of [parent], skipping text/comment nodes. */
    private fun Element.firstElementChildNode(): Element? {
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) return node
        }
        return null
    }

    /** Resolves an href against a base URL, keeping it to the same origin. */
    fun resolveHref(base: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        return if (href.startsWith("/")) originOf(base) + href else base.trimEnd('/') + "/" + href
    }

    private fun originOf(base: String): String {
        val schemeEnd = base.indexOf("://")
        if (schemeEnd < 0) return base.trimEnd('/')
        val afterScheme = base.substring(schemeEnd + 3)
        val pathStart = afterScheme.indexOf('/')
        val authority = if (pathStart < 0) afterScheme else afterScheme.substring(0, pathStart)
        return base.substring(0, schemeEnd + 3) + authority
    }

    data class DiscoveryBundle(
        val principal: String?,
        val calendarHomeSet: String?
    )
}
