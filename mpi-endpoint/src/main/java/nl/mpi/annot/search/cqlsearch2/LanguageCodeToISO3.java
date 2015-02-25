/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mpi.annot.search.cqlsearch2;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * This class uses an xml file which map obsolete language codes to ISO3 code. 
 * The location of the file must be set to "_imdiLanguageMapURL".
 * @author olhsha
 */
public class LanguageCodeToISO3 {

    private Logger _logger = LoggerFactory.getLogger(CatalogData.class);
    private Map<String, String> _codeMap = new HashMap<String, String>();
    private String _imdiLanguageMapURL;
    private String _imdiNamespaceURL;

    public LanguageCodeToISO3(String imdiLanguageMapURL, String imdpiNamespaceURL) {
        _imdiLanguageMapURL = imdiLanguageMapURL;
        _imdiNamespaceURL = imdpiNamespaceURL;
        _codeMap = this.retrieveCodeMap();
    }

    public Map<String, String> getCodeMap() {
        return _codeMap;
    }

    private Map<String, String> retrieveCodeMap() {
        Map<String, String> result = new HashMap<String, String>();
        try {
            URL fileURL = new URL(_imdiLanguageMapURL);
            InputStream mapStream = fileURL.openStream();
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(mapStream);
            doc.getDocumentElement().normalize();
            NodeList nList = doc.getElementsByTagNameNS(_imdiNamespaceURL, "Entry");
            int length = nList.getLength();
            for (int i = 0; i < length; i++) {
                Node nNode = nList.item(i);
                Element eElement = (Element) nNode;
                String isoCode = eElement.getTextContent();
                String originalCode = eElement.getAttribute("Value");
                result.put(originalCode, isoCode);
            }

        } catch (ParserConfigurationException e1) {
            _logger.error("Will return empty code map because: " + e1.getMessage());
        } catch (SAXException e2) {
            _logger.error("Will return empty code map because: " + e2.getMessage());
        } catch (IOException e3) {
            _logger.error("Will return empty code map because: " + e3.getMessage());
        }
        return result;
    }
}
