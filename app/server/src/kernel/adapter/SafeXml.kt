package dbest.kernel.adapter

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

fun requireSafeXml(path: String) {
    try {
        secureFactory().newDocumentBuilder().parse(File(path))
    } catch (error: Exception) {
        throw IllegalArgumentException("arquivo XML invalido ou inseguro", error)
    }
}

private fun secureFactory(): DocumentBuilderFactory {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    factory.isXIncludeAware = false
    factory.isExpandEntityReferences = false
    return factory
}
