package edu.oxy.util.xml

import java.io.Reader
import java.util.*
import javax.xml.namespace.QName
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.events.*

sealed interface Node
data class Element(
    val name: String,
    val attributes: Map<String, List<String>>,
    val children: List<Node>
) : Node

data class Text(val data: String) : Node

private sealed interface State
private object Ready : State
private data class Open(val stuff: String) : State
private data class Closed(val element: Element) : State

class ParseError(message: String) : RuntimeException(message)

// Nested `this`.
private fun QName.qname(): String = buildString {
    append(prefix)
    append(":")
    append(localPart)
}

/**
 * A very simple XML parser that does two things: compacts QNames into one simple name and ignores whitespace.
 * The purpose for this is to deal with simple XML from APIs as simply as possible.
 */
fun slax(reader: Reader): Element {
    val xmlInputFactory = XMLInputFactory.newInstance()
    val xmlEventReader = xmlInputFactory.createXMLEventReader(reader)
    var state: State = Ready
    val stack = Stack<Element>()

    fun assertOpen() = when (state) {
        is Open -> Unit
        else -> throw ParseError("State not open: $state")
    }

    fun checkStack() {
        check(stack.isNotEmpty()) { "Stack empty: ${stack.size}" }
    }

    while (xmlEventReader.hasNext()) {
        when (val event = xmlEventReader.nextEvent()) {
            is StartDocument -> when (state) {
                Ready -> state = Open(event.toString())
                else -> throw ParseError("Document already started: $state")
            }
            is StartElement -> {
                assertOpen()
                // We need to do this conversion because Kotlin has no way to work with iterators as collections.
                val attrsList: List<Attribute> = event.attributes.let { attrs ->
                    buildList {
                        while (attrs.hasNext()) {
                            // For some reason, we need to cast this even though it looks like we shouldn't.
                            val attribute: Attribute = attrs.next() as Attribute
                            add(attribute)
                        }
                    }
                }
                val attributes = attrsList.fold(mapOf<String, List<String>>()) { map, attr ->
                    val key: String = attr.name.qname()
                    map + (key to when (val value = map[key]) {
                        null -> listOf(attr.value)
                        else -> value + attr.value
                    })
                }
                val element = Element(
                    event.name.qname(),
                    attributes,
                    listOf()
                )
                stack.push(element)
            }
            is EndElement -> {
                assertOpen()
                checkStack()
                val head = stack.pop()
                if (stack.isEmpty())
                    state = Closed(head)
                else {
                    val parent = stack.pop()
                    stack.push(parent.copy(children = parent.children + head))
                }
            }
            is Characters -> {
                assertOpen()
                checkStack()
                // Discard all whitespace that is not adjacent to text.
                if (!event.isWhiteSpace) {
                    val head = stack.pop()
                    stack.push(head.copy(children = head.children + Text(event.data.trim())))
                }
            }
            is EndDocument -> {
                when (state) {
                    is Open -> throw ParseError("State should be closed.")
                    is Closed -> {
                        check(stack.isEmpty()) { "Stack not empty: ${stack.size}" }
                    }
                    else -> throw IllegalStateException("Bad things: $state")
                }
            }
            else -> TODO("Gotta catch 'em all!")
        }
    }
    return when (state) {
        is Closed -> state.element
        else -> throw IllegalStateException("Bad things: $state")
    }
}

/**
 * Calls the action on this node and all children.
 */
fun Node.visit(action: (Node) -> Unit) {
    action(this)
    when (this) {
        is Element -> children.forEach { it.visit(action) }
        else -> Unit
    }
}

/**
 * Performs recursive descent from the node, gathering nodes that satisfy the predicate.
 */
fun Node.filterNodes(predicate: (Node) -> Boolean): List<Node> = this.let { root ->
    buildList {
        root.visit { node ->
            if (predicate(node))
                add(node)
        }
    }
}

/**
 * Returns list of elements, including the root, that match the predicate.
 */
fun Node.filterElements(predicate: (Element) -> Boolean): List<Element> = this.let { root ->
    buildList {
        root.visit { node ->
            when (node) {
                is Element -> {
                    if (predicate(node))
                        add(node)
                }
                else -> Unit
            }
        }
    }
}

/**
 * Returns list of elements whose name matches the string.
 */
fun Node.filterByNameExact(name: String): List<Element> = filterElements { e -> name == e.name }

/**
 * Returns list of elements whose name contains the string.
 */
fun Node.filterByNameContains(name: String): List<Element> = filterElements { e -> e.name.contains(name) }

/**
 * Returns list of elements whose name matches the regex.
 */
fun Node.filterByNameRegex(regex: Regex): List<Element> = filterElements { e -> regex.matches(e.name) }

/**
 * Get all the text children of a node (direct and indirect children).
 */
fun Node.getText(): List<String> = this.let { root ->
    buildList {
        root.visit { node ->
            when (node) {
                is Text -> add(node.data)
                else -> Unit
            }
        }
    }
}