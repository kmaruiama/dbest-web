package sources.xml;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class XMLRecognizer {

    private final File xmlFile;
    private final String rootElement;
    private final String recordElement;
    private final FlatteningStrategy strategy;

    public enum FlatteningStrategy {
        NESTED_COLUMNS,
        SEPARATE_TABLES,
        JSON_COLUMNS
    }

    public XMLRecognizer(String filePath, String rootElement, String recordElement, FlatteningStrategy strategy) throws XmlImportException {
        this.xmlFile = new File(filePath);
        this.rootElement = rootElement;
        this.recordElement = recordElement;
        this.strategy = strategy != null ? strategy : FlatteningStrategy.NESTED_COLUMNS;

        if (!xmlFile.exists()) {
            throw new XmlImportException("XML file not found: " + filePath);
        }
    }

    public XMLRecognizer(String filePath) throws XmlImportException {
        this(filePath, null, null, FlatteningStrategy.NESTED_COLUMNS);
    }

    public XMLAnalysisResult analyzeStructure() throws XmlImportException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xmlFile);
            document.getDocumentElement().normalize();

            Element root = document.getDocumentElement();

            String detectedRoot = rootElement != null ? rootElement : root.getNodeName();
            String detectedRecord = recordElement;

            if (detectedRecord == null) {
                detectedRecord = detectRecordElement(root);
            }

            List<XmlColumn> columns = analyzeSchema(document, detectedRecord);
            List<Map<String, String>> sampleData = extractSampleData(document, detectedRecord, 10);

            return new XMLAnalysisResult(
                detectedRoot,
                detectedRecord,
                columns,
                sampleData,
                strategy,
                countRecords(document, detectedRecord)
            );

        } catch (Exception e) {
            throw new XmlImportException("Error analyzing XML structure: " + e.getMessage(), e);
        }
    }

    public XMLData extractData() throws XmlImportException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xmlFile);
            document.getDocumentElement().normalize();

            XMLAnalysisResult analysis = analyzeStructure();

            List<Map<String, String>> allData = extractAllData(document, analysis.getRecordElement());

            return new XMLData(
                analysis.getColumns(),
                allData,
                analysis.getRootElement(),
                analysis.getRecordElement()
            );

        } catch (Exception e) {
            throw new XmlImportException("Error extracting XML data: " + e.getMessage(), e);
        }
    }

    public XMLData extractSeparateTableData(String parentRecordElement, String childElementName) throws XmlImportException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xmlFile);
            document.getDocumentElement().normalize();

            List<Map<String, String>> allData = extractSeparateTableAllData(document, parentRecordElement, childElementName);

            List<XmlColumn> columns = new ArrayList<>();
            columns.add(new XmlColumn("parent_id", XmlColumnType.STRING));

            if (!allData.isEmpty()) {
                Map<String, String> firstRow = allData.get(0);
                for (String columnName : firstRow.keySet()) {
                    if (!columnName.equals("parent_id")) {
                        columns.add(new XmlColumn(columnName, XmlColumnType.STRING));
                    }
                }
            }

            return new XMLData(
                columns,
                allData,
                rootElement,
                childElementName
            );

        } catch (Exception e) {
            throw new XmlImportException("Error extracting separate table data: " + e.getMessage(), e);
        }
    }

    private String detectRecordElement(Element root) {
        Map<String, Integer> childCounts = new HashMap<>();
        NodeList children = root.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String nodeName = child.getNodeName();
                childCounts.put(nodeName, childCounts.getOrDefault(nodeName, 0) + 1);
            }
        }

        return childCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(root.getFirstChild() != null ? root.getFirstChild().getNodeName() : "record");
    }

    private List<XmlColumn> analyzeSchema(Document document, String recordElement) {
        Set<String> allColumns = new LinkedHashSet<>();
        Map<String, XmlColumnType> columnTypes = new HashMap<>();

        NodeList records = document.getElementsByTagName(recordElement);

        int maxRecordsToAnalyze = Math.min(100, records.getLength());

        for (int i = 0; i < maxRecordsToAnalyze; i++) {
            Element record = (Element) records.item(i);
            Map<String, String> flatRecord = flattenElement(record, "");

            for (Map.Entry<String, String> entry : flatRecord.entrySet()) {
                String columnName = entry.getKey();
                String value = entry.getValue();

                allColumns.add(columnName);

                XmlColumnType inferredType = inferDataType(value);
                XmlColumnType existingType = columnTypes.get(columnName);

                if (existingType == null) {
                    columnTypes.put(columnName, inferredType);
                } else if (existingType != inferredType && existingType != XmlColumnType.STRING) {
                    columnTypes.put(columnName, XmlColumnType.STRING);
                }
            }
        }

        List<XmlColumn> columns = new ArrayList<>();
        for (String columnName : allColumns) {
            XmlColumnType type = columnTypes.getOrDefault(columnName, XmlColumnType.STRING);
            XmlColumn column = new XmlColumn(columnName, type);
            columns.add(column);
        }

        return columns;
    }

    private XmlColumnType inferDataType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return XmlColumnType.STRING;
        }

        try {
            Integer.parseInt(value);
            return XmlColumnType.INTEGER;
        } catch (NumberFormatException ignored) {}

        try {
            Double.parseDouble(value);
            return XmlColumnType.DOUBLE;
        } catch (NumberFormatException ignored) {}

        return XmlColumnType.STRING;
    }

    private Map<String, String> flattenElement(Element element, String prefix) {
        Map<String, String> result = new HashMap<>();

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String attrName;
            if (strategy == FlatteningStrategy.SEPARATE_TABLES && prefix.isEmpty()) {
                attrName = attr.getNodeName();
            } else {
                attrName = prefix.isEmpty() ? "@" + attr.getNodeName() : prefix + ".@" + attr.getNodeName();
            }
            result.put(attrName, attr.getNodeValue());
        }

        NodeList children = element.getChildNodes();
        boolean hasElementChildren = false;
        StringBuilder textContent = new StringBuilder();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                hasElementChildren = true;
                Element childElement = (Element) child;
                String childPrefix = prefix.isEmpty() ? child.getNodeName() : prefix + "." + child.getNodeName();

                if (strategy == FlatteningStrategy.NESTED_COLUMNS) {
                    result.putAll(flattenElement(childElement, childPrefix));
                } else if (strategy == FlatteningStrategy.SEPARATE_TABLES) {
                    if (willBecomeSeperateTable(element, child.getNodeName())) {
                        String referenceId = generateReferenceId(element, childElement);
                        result.put(child.getNodeName() + "_id", referenceId);
                    } else {
                        if (hasOnlyTextContent(childElement)) {
                            result.put(child.getNodeName(), childElement.getTextContent());
                        } else {
                            result.putAll(flattenElement(childElement, childPrefix));
                        }
                    }
                } else if (strategy == FlatteningStrategy.JSON_COLUMNS) {
                    result.put(child.getNodeName(), elementToJson(childElement));
                } else {
                    result.put(childPrefix, childElement.getTextContent());
                }
            } else if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getNodeValue().trim();
                if (!text.isEmpty()) {
                    textContent.append(text);
                }
            }
        }

        if (!hasElementChildren && textContent.length() > 0) {
            String columnName = prefix.isEmpty() ? "value" : prefix;
            result.put(columnName, textContent.toString());
        }

        return result;
    }

    private List<Map<String, String>> extractSampleData(Document document, String recordElement, int maxSamples) {
        List<Map<String, String>> sampleData = new ArrayList<>();
        NodeList records = document.getElementsByTagName(recordElement);

        int samplesToExtract = Math.min(maxSamples, records.getLength());

        for (int i = 0; i < samplesToExtract; i++) {
            Element record = (Element) records.item(i);
            Map<String, String> flatRecord = flattenElement(record, "");
            sampleData.add(flatRecord);
        }

        return sampleData;
    }

    private List<Map<String, String>> extractAllData(Document document, String recordElement) {
        List<Map<String, String>> allData = new ArrayList<>();
        NodeList records = document.getElementsByTagName(recordElement);

        for (int i = 0; i < records.getLength(); i++) {
            Element record = (Element) records.item(i);
            Map<String, String> flatRecord = flattenElement(record, "");
            allData.add(flatRecord);
        }

        return allData;
    }

    private List<Map<String, String>> extractSeparateTableAllData(Document document, String parentRecordElement, String childElementName) {
        List<Map<String, String>> allData = new ArrayList<>();
        NodeList parentRecords = document.getElementsByTagName(parentRecordElement);

        for (int i = 0; i < parentRecords.getLength(); i++) {
            Element parentRecord = (Element) parentRecords.item(i);

            String parentId = findElementId(parentRecord);
            if (parentId == null) {
                parentId = String.valueOf(Math.abs(parentRecord.toString().hashCode()));
            }

            NodeList childElements = getDirectChildElements(parentRecord, childElementName);

            for (int j = 0; j < childElements.getLength(); j++) {
                Element childElement = (Element) childElements.item(j);

                Map<String, String> childData = flattenElement(childElement, "");

                childData.put("parent_id", parentId);

                allData.add(childData);
            }
        }

        return allData;
    }

    private int countRecords(Document document, String recordElement) {
        NodeList records = document.getElementsByTagName(recordElement);
        return records.getLength();
    }

    public List<String> getAllElementNames() throws XmlImportException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xmlFile);
            document.getDocumentElement().normalize();

            Set<String> elementNames = new LinkedHashSet<>();
            collectElementNames(document.getDocumentElement(), elementNames);

            return new ArrayList<>(elementNames);
        } catch (Exception e) {
            throw new XmlImportException("Error reading XML structure: " + e.getMessage(), e);
        }
    }

    private void collectElementNames(Element element, Set<String> elementNames) {
        elementNames.add(element.getNodeName());

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectElementNames((Element) child, elementNames);
            }
        }
    }

    public List<String> getPotentialRecordElements() throws XmlImportException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xmlFile);
            document.getDocumentElement().normalize();

            Map<String, Integer> elementCounts = new HashMap<>();
            countElements(document.getDocumentElement(), elementCounts);

            return elementCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new XmlImportException("Error analyzing XML structure: " + e.getMessage(), e);
        }
    }

    private void countElements(Element element, Map<String, Integer> elementCounts) {
        String elementName = element.getNodeName();
        elementCounts.put(elementName, elementCounts.getOrDefault(elementName, 0) + 1);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                countElements((Element) child, elementCounts);
            }
        }
    }

    private boolean hasOnlyTextContent(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return false;
            }
        }
        return true;
    }

    private String elementToJson(Element element) {
        StringBuilder json = new StringBuilder();
        json.append("{");

        NamedNodeMap attributes = element.getAttributes();
        boolean hasAttributes = attributes.getLength() > 0;

        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            if (i > 0) json.append(",");
            json.append("\"@").append(attr.getNodeName()).append("\":\"")
                .append(escapeJsonString(attr.getNodeValue())).append("\"");
        }

        NodeList children = element.getChildNodes();
        Map<String, Integer> childCounts = new HashMap<>();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String name = child.getNodeName();
                childCounts.put(name, childCounts.getOrDefault(name, 0) + 1);
            }
        }

        Map<String, List<String>> childValues = new HashMap<>();
        StringBuilder textContent = new StringBuilder();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String name = child.getNodeName();
                String value = elementToJson((Element) child);
                childValues.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            } else if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getNodeValue().trim();
                if (!text.isEmpty()) {
                    textContent.append(text);
                }
            }
        }

        boolean needsComma = hasAttributes;
        for (Map.Entry<String, List<String>> entry : childValues.entrySet()) {
            if (needsComma) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":");

            List<String> values = entry.getValue();
            if (values.size() == 1) {
                json.append(values.get(0));
            } else {
                json.append("[").append(String.join(",", values)).append("]");
            }
            needsComma = true;
        }

        if (textContent.length() > 0 && childValues.isEmpty()) {
            if (needsComma) json.append(",");
            json.append("\"#text\":\"").append(escapeJsonString(textContent.toString())).append("\"");
        }

        json.append("}");
        return json.toString();
    }

    private String escapeJsonString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private String generateReferenceId(Element parentElement, Element childElement) {
        String parentId = findElementId(parentElement);
        if (parentId == null) {
            parentId = String.valueOf(Math.abs(parentElement.toString().hashCode()));
        }

        return parentId;
    }

    private String findElementId(Element element) {
        String[] idAttributes = {"id", "ID", "Id", "key", "KEY", "Key"};
        for (String attr : idAttributes) {
            if (element.hasAttribute(attr)) {
                return element.getAttribute(attr);
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String nodeName = child.getNodeName().toLowerCase();
                if (nodeName.contains("id") || nodeName.contains("key")) {
                    return child.getTextContent().trim();
                }
            }
        }

        return null;
    }

    private boolean willBecomeSeperateTable(Element parentElement, String childElementName) {
        int count = 0;
        NodeList children = parentElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(childElementName)) {
                count++;
            }
        }

        return count > 1;
    }

    private NodeList getDirectChildElements(Element parent, String elementName) {
        List<Element> directChildren = new ArrayList<>();
        NodeList children = parent.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                child.getNodeName().equals(elementName)) {
                directChildren.add((Element) child);
            }
        }

        return new NodeList() {
            @Override
            public Node item(int index) {
                return index < directChildren.size() ? directChildren.get(index) : null;
            }

            @Override
            public int getLength() {
                return directChildren.size();
            }
        };
    }



    public List<SeparateTableInfo> getPotentialSeparateTables() throws XmlImportException {
        if (strategy != FlatteningStrategy.SEPARATE_TABLES) {
            return new ArrayList<>();
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xmlFile);
            document.getDocumentElement().normalize();

            Element root = document.getDocumentElement();
            String detectedRecord = recordElement != null ? recordElement : detectRecordElement(root);

            List<SeparateTableInfo> separateTables = new ArrayList<>();

            NodeList records = document.getElementsByTagName(detectedRecord);
            if (records.getLength() > 0) {
                Element firstRecord = (Element) records.item(0);
                findSeparateTableCandidates(firstRecord, "", separateTables);
            }

            return separateTables;
        } catch (Exception e) {
            throw new XmlImportException("Error analyzing separate tables: " + e.getMessage(), e);
        }
    }

    private void findSeparateTableCandidates(Element element, String parentPath, List<SeparateTableInfo> separateTables) {
        Map<String, Integer> childElementCounts = new HashMap<>();
        Map<String, Element> childElementSamples = new HashMap<>();

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String elementName = child.getNodeName();
                childElementCounts.put(elementName, childElementCounts.getOrDefault(elementName, 0) + 1);
                if (!childElementSamples.containsKey(elementName)) {
                    childElementSamples.put(elementName, (Element) child);
                }
            }
        }

        for (Map.Entry<String, Integer> entry : childElementCounts.entrySet()) {
            String elementName = entry.getKey();
            int countInThisParent = entry.getValue();
            Element sampleElement = childElementSamples.get(elementName);

            if (countInThisParent > 1) {
                String fullPath = parentPath.isEmpty() ? elementName : parentPath + "." + elementName;

                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document document = builder.parse(xmlFile);

                    NodeList allOccurrences = document.getElementsByTagName(elementName);

                    separateTables.add(new SeparateTableInfo(
                        elementName,
                        fullPath,
                        allOccurrences.getLength(),
                        analyzeElementStructure(sampleElement)
                    ));
                } catch (Exception e) {
                }
            }
        }

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String elementName = child.getNodeName();
                String fullPath = parentPath.isEmpty() ? elementName : parentPath + "." + elementName;

                if (!hasOnlyTextContent(childElement) && childElementCounts.get(elementName) == 1) {
                    findSeparateTableCandidates(childElement, fullPath, separateTables);
                }
            }
        }
    }

    private List<String> analyzeElementStructure(Element element) {
        List<String> columns = new ArrayList<>();

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            columns.add(attr.getNodeName());
        }

        if (hasOnlyTextContent(element)) {
            columns.add("value");
        } else {
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    columns.add(child.getNodeName());
                }
            }
        }

        return columns;
    }

    public static class SeparateTableInfo {
        private final String elementName;
        private final String fullPath;
        private final int occurrenceCount;
        private final List<String> columns;

        public SeparateTableInfo(String elementName, String fullPath, int occurrenceCount, List<String> columns) {
            this.elementName = elementName;
            this.fullPath = fullPath;
            this.occurrenceCount = occurrenceCount;
            this.columns = columns;
        }

        public String getElementName() { return elementName; }
        public String getFullPath() { return fullPath; }
        public int getOccurrenceCount() { return occurrenceCount; }
        public List<String> getColumns() { return columns; }

        @Override
        public String toString() {
            return String.format("%s (%d occurrences, %d columns)", elementName, occurrenceCount, columns.size());
        }
    }
}
