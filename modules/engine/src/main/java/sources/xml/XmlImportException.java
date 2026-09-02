package sources.xml;

/**
 * Raised when an XML document cannot be safely read or interpreted as tabular data.
 */
public class XmlImportException extends Exception {
    public XmlImportException(String message) {
        super(message);
    }

    public XmlImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
