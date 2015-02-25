/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mpi.annot.search.cqlsearch2;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import nl.mpi.corpusstructure.CorpusStructureDBImpl;
import nl.mpi.corpusstructure.Node;
import nl.mpi.corpusstructure.UnknownNodeException;
import nl.mpi.imdi.api.IMDIDom;
import nl.mpi.util.OurURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author olhsha
 */
public class ExtraTermData {

    private HashMap<String, String> _nodeTitle=null; // to do; make it a hash-map: language->title
    private String _landingPageURL = null;
    private ArrayList<String> _languages = null;
    private boolean _hasSubResources = false;
    private HashMap<String, String> _descriptions = null;
    private Logger _logger = LoggerFactory.getLogger(ExtraTermData.class);

    public ExtraTermData(CorpusStructureDBImpl db, String nodeId, String user) {

        try {
            _landingPageURL = db.getObjectURI(nodeId).toString();
            Node[] nodes = db.getChildrenNodes(nodeId);
            _hasSubResources = (nodes.length > 0);

            // find the catalogue 
            int catalogueIndex = catalogueIsFound(nodes);
            if (catalogueIndex > -1) {
                setExtraTermDataFromCatalogue(db, nodes[catalogueIndex]);
            }            
            else {
                setEmptyExtraTermData();
            }

        } catch (UnknownNodeException e) {
            _logger.error(nodeId + " is an unknown node",  e);
        }

    }

    private int catalogueIsFound(Node[] nodes) {
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].getNodeType() == Node.CATALOGUE) {
                return i;
            }
        }
        return -1;
    }

    private void setExtraTermDataFromCatalogue(CorpusStructureDBImpl db, Node catalogue) {
        URL catalogueURL = null;
        // InputStream catalogueStream = null;
        // DocumentBuilder docBuilder = null;
        // URLConnection con = null;        
         
        try {
            catalogueURL = db.getObjectURI(catalogue.getNodeId()).toURL();
        } catch (MalformedURLException catalogueURIException) {
            _logger.error("Cannot translate catalogue's URI " + db.getObjectURI(catalogue.getNodeId()) + " into an URL",  catalogueURIException);
            _logger.error("Extra term data stay at their default empty setting");
            return;
        }
        
        IMDIDom dom = new IMDIDom();
        Hashtable hasTable = null;        
        Document doc = null;
        
        try {
        doc = dom.loadIMDIDocument(new OurURL(catalogueURL), false);
        hasTable=(dom.getKeyValuePairs(doc,  "Catalogue"));
        } catch (MalformedURLException e){
            _logger.error("Cannot turn " + catalogueURL+" into OurURL", e);
            _logger.error("Extra term data stay at their default empty setting");
            return;
        }
        
        /*
        try {
            con = catalogueURL.openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            catalogueStream = con.getInputStream();
        } catch (IOException openCatalogueException) {
            _logger.error("Cannot get strean form the catalogue's URL " + catalogueURL, openCatalogueException);
            _logger.error("Extra term data stay at their default empty setting");
            return;
        }

        try {
            DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
            docBuilder = fac.newDocumentBuilder();
        } catch (ParserConfigurationException parserE) {
            _logger.error("Cannot create parser for the catalogue " + catalogueURL, parserE);
            _logger.error("Extra term data stay at their default empty setting");
            return;
        }

        try {
            doc = docBuilder.parse(catalogueStream);
            catalogueStream.close();
        } catch (SAXException saxE) {
            _logger.error("Cannot parse the catalogues stream obtained from " + catalogueURL, saxE);
            _logger.error("Extra term data stay at their default empty setting");
            return;
        } catch (IOException ioE) {
            _logger.error("Cannot parse the catalogue's stream obtained from " + catalogueURL, ioE);
            _logger.error("Extra term data stay at their default empty setting");
            return;
        } */


        //XPathFactory factory = XPathFactory.newInstance();
        _nodeTitle = getTitles(hasTable, doc, dom);
        _languages = null;
        _descriptions = null; 
        
        //_languages = getLanguages(factory, doc, catalogueURL);
        //_descriptions = getDescriptions(factory, doc, catalogueURL);
    }

    //private HashMap<String, String> getTitles(XPathFactory factory, Document doc, URL catalogueURL) {
      
        private HashMap<String, String> getTitles(Hashtable hashTable, Document doc, IMDIDom dom) {
    
        HashMap<String, String> result = new HashMap<String, String>();

        String englishTitle = hashTable.get("EnglishTitle").toString();
        result.put(englishTitle, "eng");

        String otherTitles = (dom.getIMDIElement(doc, "Catalogue.Title")).getValue();
        //int amountOfOtherTitles = (otherTitles == null) ? 0 : otherTitles.getLength();

        String otherLanguages = hashTable.get("TitleLanguage").toString();
        //int amountOfOtherLanguages = (otherLanguages == null) ? 0 : otherLanguages.getLength();

        /*if (amountOfOtherTitles != amountOfOtherLanguages) {
            _logger.error("The amount of titles does not match the amount of title languages in the catalogue");
        }
        for (int i = 0; i < amountOfOtherTitles; i++) {
            result.put(otherTitles.item(i).getTextContent(), otherLanguages.item(i).getTextContent());
        }*/
        result.put(otherTitles, otherLanguages);
        return result;
    }

    ///// 
    // private String getEnglishTitle(XPathFactory factory, Document doc, URL catalogueURL) {
        /*
        NodeList xmlNodes = null;
        XPath xpath = factory.newXPath();
        String xpathEnglishTitle = "/METATRANSCRIPT/Catalogue/Keys/Key[@Name=\"EnglishTitle\"]";
        try {
            xmlNodes = (NodeList) xpath.evaluate(xpathEnglishTitle, doc, XPathConstants.NODESET);
        } catch (XPathExpressionException xpathE) {
            _logger.error("Cannot parse the English Title obtained from the catalogue " + catalogueURL, xpathE);
            _logger.error("At least one title must be given according to FCS specification");
        }

        String result = ((xmlNodes != null) ? (xmlNodes.getLength() > 0 ? xmlNodes.item(0).getTextContent() : "No English title is given") : "No English title is given");
        
        */
      /*  IMDIDom dom = new IMDIDom();
        try {
        Document docNew = dom.loadIMDIDocument(new OurURL(catalogueURL), false);
        Hashtable tmpTable=(dom.getKeyValuePairs(docNew,  "Catalogue"));
        String result = tmpTable.get("EnglishTitle").toString();
        return result;
        } catch (MalformedURLException e){
            _logger.error("Cannot parse the English Title obtained from the catalogue " + catalogueURL, e);
        }
        
        return " ";
    }*/

    /////
    /*private NodeList getOtherTitles(Hashtable hashtable) {
        NodeList result = null;
        XPath xpathOthers = factory.newXPath();
        String xpathTitle = "/METATRANSCRIPT/Catalogue/Title";
        try {
            result = (NodeList) xpathOthers.evaluate(xpathTitle, doc, XPathConstants.NODESET);
        } catch (XPathExpressionException xpathE) {
            _logger.error("Cannot parse the Title obtained from the catalogue " + catalogueURL, xpathE);
        }
        
        
        return result;
    }*/

    ////
    /*private NodeList getLanguagesOfOtherTitles(Hashtable hashTable) {
        NodeList result = null;
        XPath xpathOthersLanguages = factory.newXPath();
        String xpathTitleLanguages = "/METATRANSCRIPT/Catalogue/Keys/Key[@Name=\"TitleLanguage\"]";
        try {
            result = (NodeList) xpathOthersLanguages.evaluate(xpathTitleLanguages, doc, XPathConstants.NODESET);
        } catch (XPathExpressionException xpathE) {
            _logger.error("Cannot parse the title language obtained from the catalogue " + catalogueURL, xpathE);
        }

        return result;
    }*/

    /////////////
    private ArrayList<String> getLanguages(XPathFactory factory, Document doc, URL catalogueURL) {
        XPath xpath = factory.newXPath();
        String xpathLanguages = "/METATRANSCRIPT/Catalogue/SubjectLanguages/Language/Id";
        try {
            NodeList xmlNodes = (NodeList) xpath.evaluate(xpathLanguages, doc, XPathConstants.NODESET);
            int lengthLanguages = (xmlNodes == null) ? 0 : xmlNodes.getLength();
            if (lengthLanguages == 0) {
                _logger.error("At least one language must be given according to FCs specification");
                return null;
            }
            ArrayList<String> result = new ArrayList<String>();
            for (int i = 0; i < lengthLanguages; i++) {
                result.add(CQLHelpers.removeISO369prefix(xmlNodes.item(i).getTextContent()));
            }
            return result;
        } catch (XPathExpressionException xpathE) {
            _logger.error("Cannot parse the languages in the catalogue " + catalogueURL, xpathE);
            _logger.error("At least one language must be given according to FCs specification");
        }
        return null;
    }

    private HashMap<String, String> getDescriptions(XPathFactory factory, Document doc, URL catalogueURL) {
        XPath xpath = factory.newXPath();
        String xpathDescriptions = "/METATRANSCRIPT/Catalogue/Description";
        try {
            NodeList xmlNodes = (NodeList) xpath.evaluate(xpathDescriptions, doc, XPathConstants.NODESET);
            int lengthDescriptions = (xmlNodes == null) ? 0 : xmlNodes.getLength();
            if (lengthDescriptions == 0) {
                _logger.info("There are no descriptions for this corpus");
                return null;
            }
            HashMap<String, String> result = new HashMap<String, String>();
            String currentLanguage = null;
            String currentDescriptionText = null;
            for (int i = 0; i < lengthDescriptions; i++) {
                currentLanguage = CQLHelpers.removeISO369prefix(xmlNodes.item(i).getAttributes().getNamedItem("LanguageId").getTextContent());
                currentDescriptionText = xmlNodes.item(i).getTextContent();
                result.put(currentLanguage, currentDescriptionText);
            }
            return result;
        } catch (XPathExpressionException xpathE) {
            _logger.info("Cannot parse the descriptions obtained from the catalogue " + catalogueURL, xpathE);
            _logger.info("There will be no descriptions for this corpus");
        }
        return null;
    }
    
    private void setEmptyExtraTermData(){
        _nodeTitle = new HashMap<String, String>();
        _nodeTitle.put("eng", "No extra-term data are provided for non-root searchabale corpora");
        _landingPageURL = "??";
        _languages = new ArrayList<String>();
        _languages.add("??");
        _hasSubResources=false;
        _descriptions = new HashMap<String, String>();
        _descriptions.put("??", "??");
    }
    
    public HashMap<String, String> getNodeTitle(){
        return _nodeTitle;
    }
    
    public String getLandingPageURL(){
        return _landingPageURL;
    }
    
    public ArrayList<String> getLanguages(){
        return _languages;
    }
    
    public boolean getHasSubResources(){
        return _hasSubResources;
    }
    
    
    public HashMap<String, String> getDescriptions(){
        return _descriptions;
    }
}
