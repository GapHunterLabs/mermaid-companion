package dev.gaphunter.mermaidcompanion.keywords

/**
 * Words worth their own color, per diagram type. Mermaid keywords are case-sensitive. A word is a
 * keyword only in the diagram types that use it, so a node called `title` in a flowchart stays a
 * node; words with a hyphen (`cherry-pick`, `x-axis`) are left out because the lexer splits them.
 */
object MermaidKeywords {

    /** The words that open a diagram, mapped to the type they declare. */
    val DIAGRAM_TYPES: Map<String, String> = mapOf(
        "flowchart" to "flowchart", "graph" to "flowchart",
        "sequenceDiagram" to "sequence", "classDiagram" to "class", "classDiagram-v2" to "class",
        "stateDiagram" to "state", "stateDiagram-v2" to "state", "erDiagram" to "er",
        "journey" to "journey", "gantt" to "gantt", "pie" to "pie", "quadrantChart" to "quadrant",
        "requirementDiagram" to "requirement", "gitGraph" to "git", "mindmap" to "mindmap",
        "timeline" to "timeline", "zenuml" to "zenuml", "sankey" to "sankey", "sankey-beta" to "sankey",
        "xychart" to "xychart", "xychart-beta" to "xychart", "block" to "block", "block-beta" to "block",
        "packet" to "packet", "packet-beta" to "packet", "kanban" to "kanban", "architecture" to "architecture",
        "architecture-beta" to "architecture", "radar" to "radar", "radar-beta" to "radar",
        "treemap" to "treemap", "treemap-beta" to "treemap",
        "C4Context" to "c4", "C4Container" to "c4", "C4Component" to "c4", "C4Dynamic" to "c4", "C4Deployment" to "c4",
    )

    private val COMMON = setOf("accTitle", "accDescr", "title", "direction")

    private val BY_TYPE: Map<String, Set<String>> = mapOf(
        "flowchart" to setOf("subgraph", "end", "click", "classDef", "class", "style", "linkStyle", "call", "href", "callback"),
        "sequence" to setOf(
            "participant", "actor", "as", "activate", "deactivate", "loop", "alt", "else", "opt", "par", "and", "critical",
            "break", "rect", "end", "note", "Note", "over", "left", "right", "of", "autonumber", "box", "create", "destroy",
            "link", "links", "properties", "details",
        ),
        "class" to setOf("class", "interface", "enum", "namespace", "note", "for", "classDef", "style", "cssClass", "callback", "link", "click"),
        "state" to setOf("state", "note", "end", "as", "left", "right", "of", "classDef", "class", "style"),
        "journey" to setOf("section"),
        "gantt" to setOf(
            "dateFormat", "axisFormat", "tickInterval", "excludes", "includes", "todayMarker", "section", "weekday",
            "inclusiveEndDates", "topAxis", "displayMode", "done", "active", "crit", "milestone", "after", "until",
        ),
        "pie" to setOf("showData"),
        "requirement" to setOf(
            "requirement", "functionalRequirement", "interfaceRequirement", "performanceRequirement", "physicalRequirement",
            "designConstraint", "element", "id", "text", "risk", "verifymethod", "type", "docRef",
            "satisfies", "traces", "contains", "derives", "refines", "verifies", "copies",
        ),
        "git" to setOf("commit", "branch", "checkout", "switch", "merge", "id", "tag", "type", "order", "NORMAL", "REVERSE", "HIGHLIGHT"),
        "timeline" to setOf("section"),
        "xychart" to setOf("line", "bar", "horizontal"),
        "block" to setOf("columns", "space", "end", "block"),
        "architecture" to setOf("group", "service", "junction", "in"),
        "radar" to setOf("axis", "curve", "max", "min", "ticks", "showLegend", "graticule"),
        "c4" to setOf(
            "Person", "Person_Ext", "System", "System_Ext", "SystemDb", "SystemDb_Ext", "SystemQueue", "Container",
            "Container_Ext", "ContainerDb", "ContainerQueue", "Component", "Component_Ext", "ComponentDb", "Boundary",
            "Enterprise_Boundary", "System_Boundary", "Container_Boundary", "Node", "Deployment_Node", "Rel", "BiRel",
            "Rel_U", "Rel_D", "Rel_L", "Rel_R", "Rel_Back", "UpdateElementStyle", "UpdateRelStyle", "UpdateLayoutConfig",
        ),
    )

    fun keywordsFor(type: String?): Set<String> = COMMON + (BY_TYPE[type] ?: emptySet())

    fun isKeyword(word: String, type: String?): Boolean = word in COMMON || BY_TYPE[type]?.contains(word) == true
}
