/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mpi.annot.search.cqlsearch2;

import de.clarin.fcs.schema.I18NString;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import nl.mpi.corpusstructure.CorpusStructureDBImpl;
import nl.mpi.corpusstructure.Node;
import nl.mpi.corpusstructure.UnknownNodeException;
import nl.mpi.imdi.api.IMDIDom;
import nl.mpi.imdi.api.IMDIElement;
import nl.mpi.util.OurURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 *
 * @author olhsha
 */
public class CatalogData {

    private List<I18NString> _nodeTitle = null; 
    private String _landingPageURL = null;
    private ArrayList<String> _languages = null;
    private boolean _hasSubResources = false;
    private List<I18NString> _descriptions = null;
    private Logger _logger = LoggerFactory.getLogger(CatalogData.class);
    
    /**
     * Contains not accessible via the corpus-structure API information about 
     * a corpus node with "nodeId".  The information is taken from the catalog file for this node. The extended information includes 
     * the list of node titles and descriptions in different languages, subject languages of the node. 
     * @param db corpus-structure database.
     * @param nodeId the Id of a node in the database "db".
     * @param user the logged-in corpus-structure database user. The parameter is passed from context.xml of the tomcat via the instance of  {@link SRUSearchEngineImpl} class.s
     */

    public CatalogData(CorpusStructureDBImpl db, String nodeId, String user, String handleResolverURL) {

        try {

            _landingPageURL = CQLHelpers.constructHandleView(db, nodeId, handleResolverURL);
            Node[] nodes = db.getChildrenNodes(nodeId);
            _hasSubResources = (nodes.length > 0);

            // find the catalogue 
            int catalogueIndex = catalogueIsFound(nodes);
            if (catalogueIndex > -1) {
                setCatalogData(db, nodes[catalogueIndex]);
            } else {
                setEmptyCatalogData();
            }

        } catch (UnknownNodeException e) {
            _logger.error(nodeId + " is an unknown node", e);
        }

    }

   /**
    * 
    * @param nodes an array of nodes.
    * @return the index of the first catalog found in the array "nodes", or -1 is such a catalog is not found.
    */
    private int catalogueIsFound(Node[] nodes) {
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].getNodeType() == Node.CATALOGUE) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Fills-in the fields of *this* instance of {@link CatalogData} by the data given in  "catalogue".
     * @param db a corpus-structure database.
     * @param catalogue a catalog node in this database.
     */
    private void setCatalogData(CorpusStructureDBImpl db, Node catalogue) {
        OurURL catalogueURL = null;
        try {
            URL catalogueURLtmp = db.getObjectURI(catalogue.getNodeId()).toURL();
            catalogueURL = new OurURL(catalogueURLtmp);
        } catch (MalformedURLException catalogueURIException) {
            _logger.error("Cannot translate catalogue's URI " + db.getObjectURI(catalogue.getNodeId()) + " into an URL", catalogueURIException);
            _logger.error("Extra term data stay at their default empty setting");
            return;
        }

        IMDIDom dom = new IMDIDom();
        Document doc = dom.loadIMDIDocument(catalogueURL, false);
        _nodeTitle = computeTitles(doc, dom);
        _languages = computeLanguages(doc, dom);
        _descriptions = computeDescriptions(doc, dom);
    }

    /**
     * 
     * @param doc an xml document.
     * @param dom the document's dom.
     * @param prefixPath the prefix of "XPath".
     * @param suffixPath the suffix of "XPath".
     * @param attributeName
     * @param attributeValue
     * @return the list of (string) values of the xml document "doc" corresponding to the XPath-like
     * prefixPath+(nummer)+suffixPath+(nummer); if the attribute name and value are given, then
     * they are added to this XPpath-like presentation as well.
     * 
     */
    private ArrayList<String[]> getElementValues(Document doc, IMDIDom dom,
            String prefixPath, String suffixPath, String attributeName, String attributeValue) {
        ArrayList<String[]> result = new ArrayList<String[]>();
        IMDIElement imdiElementRunner;
        IMDIElement imdiAttributeRunner;
        String whereElement;

        String dotSuffixPath = (suffixPath == null) ? null : ("." + suffixPath);
        String dotAttributeName = (attributeName == null) ? null : ("." + attributeName);

        int elementNumber = 1;
        do {
            whereElement = (suffixPath == null) ? prefixPath + "(" + elementNumber + ")"
                    : prefixPath + "(" + elementNumber + ")" + dotSuffixPath;
            imdiElementRunner = dom.getIMDIElement(doc, whereElement);
            if (imdiElementRunner != null) {
                if (dotAttributeName == null) {
                    // the value of the attribue is not important to know, and no filtering on the attribute
                    addElementAndAttribute(result, imdiElementRunner.getValue(), null);
                } else {
                    imdiAttributeRunner = dom.getIMDIElement(doc, whereElement + dotAttributeName);
                    if (imdiAttributeRunner != null) {
                        if (imdiAttributeRunner.isAttribute()) {
                            if (attributeValue == null) {
                                // the value of the attribute is important to know, but no filtering
                                addElementAndAttribute(result, imdiElementRunner.getValue(), imdiAttributeRunner.getValue());
                            } else if ((imdiAttributeRunner.getValue()).equals(attributeValue)) {
                                // filtering on the value of the attribute
                                addElementAndAttribute(result, imdiElementRunner.getValue(), attributeValue);
                            }
                        }
                    }
                }
            }
            elementNumber++;
        } while (imdiElementRunner != null);
        return result;

    }

   /**
    * 
    * @param l a list of string arrays of length 2, representing a pair "element value" -- "attribute value".
    * @param elementVal  the "zero" element of the array to be added to the list
    * @param attributeVal the "first" element of the array to be added to th list
    */
    private void addElementAndAttribute(ArrayList<String[]> l, String elementVal, String attributeVal) {
        String[] tmp = new String[2];
        tmp[0] = elementVal;
        tmp[1] = attributeVal;
        l.add(tmp);
    }

    /**
     * 
     * @param doc catalog document.
     * @param dom the document's dom.
     * @return the list of titles listed in the catalog, also as "keys". A title consist of the title itself and its language.
     */
    private List<I18NString> computeTitles(Document doc, IMDIDom dom) {
        List<I18NString> result = new ArrayList<I18NString>();

        // setting up English Titles    
        ArrayList<String[]> englishTitles = getElementValues(doc, dom, "Catalogue.Key", null, "Name", "EnglishTitle");
        for (int i = 0; i < englishTitles.size(); i++) {
            I18NString i18nstring = new I18NString();
            i18nstring.setLang("eng");
            i18nstring.setValue(englishTitles.get(i)[0]);
            result.add(i18nstring);
        }

        // searching for the other-languages titles        
        IMDIElement imdiElementTitle = dom.getIMDIElement(doc, "Catalogue.Title");
        ArrayList<String[]> otherTitles = new ArrayList<String[]>();
        addElementAndAttribute(otherTitles, imdiElementTitle.getValue(), null);
        ArrayList<String[]> otherTitlesInKeys = getElementValues(doc, dom, "Catalogue.Key", null, "Name", "ExtraTitle");
        otherTitles.addAll(otherTitlesInKeys);
        int amountOfOtherTitles = otherTitles.size();

        // searching for languages the for language titles
        // the first language is the language of the catalogue title, the rest are the languages of extra titles
        ArrayList<String[]> otherLanguages = getElementValues(doc, dom, "Catalogue.Key", null, "Name", "TitleLanguage");
        int amountOfOtherLanguages = otherLanguages.size();

        // setting up other-language titles
        if (amountOfOtherTitles != amountOfOtherLanguages) {
            _logger.info("The amount of titles does not match the amount of title languages in the catalogue, truncated to the minimum");
            if (amountOfOtherTitles > amountOfOtherLanguages) {
                amountOfOtherTitles = amountOfOtherLanguages;
            }
        }
        for (int i = 0; i < amountOfOtherTitles; i++) {
            I18NString i18nstring = new I18NString();
            i18nstring.setLang(otherLanguages.get(i)[0]);
            i18nstring.setValue(otherTitles.get(i)[0]);
            result.add(i18nstring);
        }
        this.removeDuplicatedElements(result);
        return result;
    }
    
    /**
     * 
     * @param l a list of I18NString-s, where  an instance of {@link I18NString} represents a pair "language-text".
     * @param begin the first index.
     * @param end the last index.
     * @param i18nstring a pair "language-text".
     * @return the index of "i18nstring" in the list "l" if found, and -1 otherwise.
     */
    private int getIndex(List<I18NString> l, int begin, int end, I18NString i18nstring){
        for (int i=begin; i<=end; i++) {
           if(l.get(i).getLang().equals(i18nstring.getLang()) && l.get(i).getValue().equals(i18nstring.getValue())) {
               return i;
           }
        }
        return -1;
    }
    
    /**
     * Removes the duplicated elements from the list "l"
     * @param l a list of I18NString-s, where  an instance of {@link I18NString} represents a pair "language-text".
     */
    private void removeDuplicatedElements(List<I18NString> l){
        if (l==null) {
            return;
        }
        if (l.isEmpty() || l.size() == 1) {
          return;  
        }
        int i=1; 
        while(i<l.size()) {
           if (this.getIndex(l, 0, i-1, l.get(i)) >-1) {
               l.remove(i);
           } else {
               i++;
           }
        }
    }

    /**
     * 
     * @param doc a catalog document.
     * @param dom the document's dom.
     * @return the list of subject languages listed in the catalog.
     */
    private ArrayList<String> computeLanguages(Document doc, IMDIDom dom) {
        ArrayList<String> result = new ArrayList<String>();
        ArrayList<String[]> languages = getElementValues(doc, dom, "Catalogue.SubjectLanguage", "Id", null, null);

        int lengthLanguages = languages.size();
        for (int i = 0; i < lengthLanguages; i++) {
            result.add(CQLHelpers.removeISO369_3_prefix(languages.get(i)[0]));
        }
        return result;
    }

    /**
     * 
     * @param doc a catalog document.
     * @param dom the document's dom.
     * @return a list of descriptions listed in the catalog as pairs "language-text", represented via the type {@link I18NString}.
     */
    private List<I18NString> computeDescriptions(Document doc, IMDIDom dom) {
        List<I18NString> result = new ArrayList<I18NString>();
        ArrayList<String[]> descriptions = getElementValues(doc, dom, "Catalogue.Description", null, "LanguageId", null);
        int descriptionsLength = descriptions.size();
        for (int i = 0; i < descriptionsLength; i++) {
            I18NString i18nstring = new I18NString();
            i18nstring.setLang(CQLHelpers.removeISO369_3_prefix(descriptions.get(i)[1]));
            i18nstring.setValue(descriptions.get(i)[0]);
            result.add(i18nstring);
        }

        return result;
    }

    /**
     * Sets empty fields for *this* {@link CatalogData} instance. Used for corpora for which no catalog is given.
     */
    private void setEmptyCatalogData() {
        _logger.info("No extra-term data are provided for non-root searchable corpora");
        _nodeTitle = new ArrayList<I18NString>();
        //      _landingPageURL = " ";
        _languages = new ArrayList<String>();
        _languages.add(" ");
        _hasSubResources = false;
        _descriptions = new ArrayList<I18NString>();
    }

    /**
     * 
     * @return the list of node titles set in *this* {@link CatalogData} instance. A node title is of type {@link I18NString}, which represents a pair "language-text".
     */
    public List<I18NString> getNodeTitle() {
        return _nodeTitle;
    }

    /**
     * 
     * @return the landing-page URI set in *this* {@link CatalogData} instance.
     */
    public String getLandingPageURL() {
        return _landingPageURL;
    }
    
    /**
     * 
     * @return the list of languages set in *this* {@link CatalogData} instance. 
     */
    public ArrayList<String> getLanguages() {
        return _languages;
    }

    /**
     * 
     * @return "the value of *this* _hasSubResources. 
     */
    public boolean getHasSubResources() {
        return _hasSubResources;
    }

    /**
     * 
     * @return the list of languages set in *this* {@link CatalogData} instance. A description is of type {@link I18NString}, which represents a pair "language-text".
     */
    public List<I18NString> getDescriptions() {
        return _descriptions;
    }

   
}


/* Catalogue specs
 * 
Catalogue.Access.Availability
Catalogue.Access.Contact.Address
Catalogue.Access.Contact.Email
Catalogue.Access.Contact.Name
Catalogue.Access.Contact.Organisation
Catalogue.Access.Date
Catalogue.Access.Description(X)
Catalogue.Access.Owner
Catalogue.Access.Publisher

Catalogue.Applications
Catalogue.Author(X)
Catalogue.ContactPerson
Catalogue.ContentType(X)
Catalogue.Date
Catalogue.Description(X)
Catalogue.DistributionForm

Catalogue.DocumentLanguage.Description(X)

Catalogue.DocumentLanguage(X)
Catalogue.DocumentLanguage(X).Id
Catalogue.DocumentLanguage(X).Name

Catalogue.Format.Audio
Catalogue.Format.Image
Catalogue.Format.Text
Catalogue.Format.Video

Catalogue.Id
Catalogue.Key(X)

Catalogue.LocationList(X).Address
Catalogue.LocationList(X).Continent
Catalogue.LocationList(X).Country
Catalogue.LocationList(X).Region
Catalogue.LocationList(X).Region(X)

Catalogue.MetadataLink
Catalogue.Name
Catalogue.Pricing

Catalogue.Project(X)
Catalogue.Project(X).Contact.Address
Catalogue.Project(X).Contact.Email
Catalogue.Project(X).Contact.Name
Catalogue.Project(X).Contact.Organisation
Catalogue.Project(X).Description
Catalogue.Project(X).Description(X)
Catalogue.Project(X).Id
Catalogue.Project(X).Name
Catalogue.Project(X).Title

Catalogue.Publications
Catalogue.Publisher(X)

Catalogue.Quality.Audio
Catalogue.Quality.Image
Catalogue.Quality.Text
Catalogue.Quality.Video

Catalogue.ReferenceLink
Catalogue.Size
Catalogue.SmallestAnnotationUnit

Catalogue.SubjectLanguage.Description(X)

Catalogue.SubjectLanguage(X)
Catalogue.SubjectLanguage(X).Id
Catalogue.SubjectLanguage(X).Name

Catalogue.Title

Note: CORPUS files link to CATALOGUE using:

Corpus.Catalogue.CatalogueHandle
Corpus.Catalogue.CatalogueLink

 * 
 * 
 */