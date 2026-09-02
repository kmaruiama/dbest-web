package sources.xml;

/**
 * Schema metadata inferred from an XML document.
 */
public record XmlColumn(String name, XmlColumnType type) {
}
