# slax

The theoretical minimum of XML.

This project implements a brutally simple, in memory representation of XML based on the StAX parser.

The only two types of nodes are Element and Text.  This corresponds to the fact that we're often 
consuming XML from an API, so we really don't care about processing instructions, comments (usually absent, anyway), and the like.
It also ignores namespaces other than to fully qualify element names.
Again, we generally don't care about namespaces at all.

* It treats elements as single QNames, e.g. `<myns:someTag/>` has the name `"myns:someTag"` as a single string.

* All text nodes containing only whitespace are automatically discarded.

* All text nodes are trimmed of whitespace.

* Provides a variety of methods for searching through nodes based on ways of matching the name of the node.