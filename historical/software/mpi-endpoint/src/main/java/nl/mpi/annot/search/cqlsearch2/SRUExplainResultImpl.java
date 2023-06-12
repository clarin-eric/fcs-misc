/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mpi.annot.search.cqlsearch2;

import de.clarin.fcs.schema.EndpointDescriptionType.SupportedDataViews;
import de.clarin.fcs.schema.EndpointDescriptionResource;
import de.clarin.fcs.schema.EndpointDescriptionResource.AvailableDataViews;
import de.clarin.fcs.schema.EndpointDescriptionResource.Languages;
import de.clarin.fcs.schema.EndpointDescriptionType;
import de.clarin.fcs.schema.EndpointDescriptionType.Capabilities;
import de.clarin.fcs.schema.I18NString;
import de.clarin.fcs.schema.ObjectFactory;
import de.clarin.fcs.schema.Resources;
import eu.clarin.sru.server.SRUConstants;
import eu.clarin.sru.server.SRUDiagnosticList;
import eu.clarin.sru.server.SRUExplainResult;
import eu.clarin.sru.server.SRURequest;
import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import nl.mpi.corpusstructure.CorpusStructureDBImpl;
import nl.mpi.corpusstructure.Node;
import nl.mpi.corpusstructure.NodeIdUtils;
import nl.mpi.corpusstructure.UnknownNodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.cache.LoadingCache;
import eu.clarin.sru.server.SRUServer;
import java.util.concurrent.ExecutionException;

/**
 * Extention of SRUExplainResult class from {@link SRUServer package}. It is adapted for our endpoint.
 * @author olhsha
 */
public class SRUExplainResultImpl extends SRUExplainResult {

    private EndpointDescriptionType _endpointDesc = new EndpointDescriptionType();
    private final BigInteger _version = BigInteger.ONE;
    private final String[] _capabilities = {"http://clarin.eu/fcs/capability/basic-search"};
    private List<String> _searchableCopropa;
    private SRUSearchEngineImpl _sruEngineImpl = null;
    private static final String[] ALL_FORMATS = {"*"};
    private SRUDiagnosticList _diag;
    private SRURequest _request;
    private final String[] _auxALLOWED_EXTRA_REQUEST_PARAMETERS = {CQLConstants.X_FCS_ENDPOINT_DESCRIPTION};
    private final ArrayList<String> ALLOWED_EXTRA_REQUEST_PARAMETERS = new ArrayList<String>(Arrays.asList(_auxALLOWED_EXTRA_REQUEST_PARAMETERS));
    private boolean _needExtraEndpointDescription;
    private Logger _logger = LoggerFactory.getLogger(SRUExplainResultImpl.class);
    private ArrayList<LoadingCache<String, Languages>> _cache;
    /**
     * 
     * @param sruEngineImpl implementation of {@link SRUSearchEngine} class of SRUServer package. 
     * It initiates the endpoint by filling in parameters and triggering connection to the database. Also, it dispatches SRU-CQL requests from the aggregator;
     * @param searchableCopropa list of the node identifiers of main ("root") searchable corpora.
     * @param sruRequest SRU/CQL "explain" request passed from the aggregator.
     * @param sruDiagnosticList the reference to the list of diagnostics passed from/to the aggregator. 
     * to be filled in during computations when something goes wrong.
     * @param cache the reference to an array (which should contain only 1 element) of cached mappings from node identifiers
     * to the corresponding {@link Languages}; the cache is to be filled in when connection between the aggregator and 
     * the endpoint is initialized via the {@link SRUSearchEngineImpl} instance.
     */
    public SRUExplainResultImpl(SRUSearchEngineImpl sruEngineImpl, List<String> searchableCopropa, SRURequest sruRequest, SRUDiagnosticList sruDiagnosticList, ArrayList<LoadingCache<String, Languages>> cache) {
        super(sruDiagnosticList);
        _diag = sruDiagnosticList;
        _request = sruRequest;
        _searchableCopropa = searchableCopropa;
        _sruEngineImpl = sruEngineImpl;
        _cache = cache;
    }
    
    

    
    /**
     * 
     * @return "true" if the current sru explain request has "x-fcs-endpoint-description=true".
     */
    @Override
    public boolean hasExtraResponseData() {
        return _needExtraEndpointDescription;
    }

    /**
     * 
     * @param writer passed from/to aggregator and filled in by the current method.
     */
    @Override
    public void writeExtraResponseData(XMLStreamWriter writer)
            throws XMLStreamException {
        JAXBElement<EndpointDescriptionType> endpointDescriptionElement = (new ObjectFactory()).createEndpointDescription(_endpointDesc);
        try {
            _sruEngineImpl.getExplainMarshaller().marshal(endpointDescriptionElement, writer);
        } catch (JAXBException e1) {
            _diag.addDiagnostic(SRUConstants.SRU_GENERAL_SYSTEM_ERROR, "Marshalling Exception", e1.getMessage());
        }

    }

    /**
     * Handles the explain request. 
     * It adds the corresponding diagnostics if the request contains an unsupported parameter or a cached map for corpora languages is not computed yet.
     * It calls help methods if extra description data have been requested.
     */
    public void handleRequest() {
        _logger.debug("I have started to handle the request");
        if (CQLHelpers.isInvalidElementsInTheList(_request.getExtraRequestDataNames(), ALLOWED_EXTRA_REQUEST_PARAMETERS)) {
            _diag.addDiagnostic(SRUConstants.SRU_UNSUPPORTED_PARAMETER, "SRU unsupported parameter", "There is an unsupported extra request parameter");
        } else {
            _needExtraEndpointDescription = parseBoolean(_request.getExtraRequestData(CQLConstants.X_FCS_ENDPOINT_DESCRIPTION));
            if (_needExtraEndpointDescription) {
                if (_cache.isEmpty()) {
                _diag.addDiagnostic(SRUConstants.SRU_RECORD_TEMPORARILY_UNAVAILABLE, "The extra data are not ready yet", "The information about corpora languages is being retrieved.");
                return;
                }
                this.setDescription();
            }
        };
    }

    /**
     * Calls help methods that fill in the endpoint-description fields: version, capabilities, supported data views, sub-resources.
     */
    private void setDescription() {
        _endpointDesc.setVersion(_version);
        _endpointDesc.setCapabilities(this.setCapabilities(_capabilities));
        _endpointDesc.setSupportedDataViews(this.SetSuportedDataViews());
        _endpointDesc.setResources((new Resources()));
        _endpointDesc.getResources().getResource().addAll(this.makeResources(this.makeListTopNodes(_searchableCopropa)));

    }

    /**
     * 
     * @return supported dataviews which is by now contain only a default data view "hits".
     */
    private SupportedDataViews SetSuportedDataViews() {
        SupportedDataViews result = new SupportedDataViews();
        result.getSupportedDataView().add(new DefaultDataView());
        return result;
    }

    /**
     * 
     * @param capabilitiesListed the list of used capabilities. By now consists only of "http://clarin.eu/fcs/capability/basic-search".
     * @return the instance of the class {@link Capabilities} filled in by the elements of the input list. 
     */
    private Capabilities setCapabilities(String[] capabilitiesListed) {
        Capabilities capabilly = new Capabilities();
        List<String> capList = capabilly.getCapability();
        for (String runner : capabilitiesListed) {
            capList.add(runner);
        }
        return capabilly;
    }

    /**
     * 
     * @param searchableCorpora the list of node identifiers of main ("root") searchable corpora.
     * @return the list of nodes (instances of class {@link Node} of corpus-structure API) corresponding to node id-s from the input list.
     */
    private List<Node> makeListTopNodes(List<String> searchableCorpora) {
        List<Node> nodes = new ArrayList<Node>();
        for (String corpusId : searchableCorpora) {
            if (NodeIdUtils.isNodeId(corpusId)) {
                try {
                    nodes.add(_sruEngineImpl.getCorpusStructureDB().getNode(corpusId));
                } catch (UnknownNodeException e) {
                    _logger.error(corpusId + " is an unknown node", e);
                }
            } else {
                _logger.error("There is a wrong nodeId " + corpusId + " in the root list of searchable corpora.");
                return null;
            }
        }
        return nodes;
    }

    /**
     * 
     * @param nodes the list of instances of {@link Node} class of corpus-structure API.
     * @return the list of instances of {@link EndpointDescriptionResource} class whose fields are filled in by calling the help method on each element of the input list.
     */
    private List<EndpointDescriptionResource> makeResources(List<Node> nodes) {
        ArrayList<EndpointDescriptionResource> results = new ArrayList<EndpointDescriptionResource>();
        for (Node node : nodes) {
            if (node.getNodeType() == Node.CORPUS || node.getNodeType() == Node.SESSION) {
                EndpointDescriptionResource resource = this.makeEndpointDescriptionResource(_sruEngineImpl.getCorpusStructureDB(), node, _sruEngineImpl.getUserId());
                results.add(resource);
            }

        }
        return results;
    }

    /**
     * 
     * @param db the corpus-structure database.
     * @param node an instance of the class {@link Node} from corpus-structure API.
     * @param user the id of the user who accesses the database,passed from context.xml file of tomcat.
     * @return an instance of the {@link EndpoinDescriptionResource} class whose fields are set using the information from "node", the catalog data and cached mapping "nodeId --> Languages".
     */
    private EndpointDescriptionResource makeEndpointDescriptionResource(CorpusStructureDBImpl db, Node node, String user) {

        EndpointDescriptionResource resource = new EndpointDescriptionResource();
        String nodeId = node.getNodeId();
        String nodeHandle;
        try {
            String nodePID = db.getObjectPID(nodeId);
            if (nodePID != null) {
                // before VLO was connected to Aggregator we did not need suffix "@format=cmdi"
                nodeHandle = nodePID.endsWith(CQLHelpers.formatcmdi) ? nodePID : nodePID.concat(CQLHelpers.formatcmdi);
            } else {
                URI nodeURI = db.getObjectURI(nodeId);
                if (nodeURI != null) {
                    nodeHandle = nodeURI.toString();
                    _logger.debug("For node " + nodeId + " we take not its handle (since it is absent), but its URI " + nodeHandle);
                } else {
                    nodeHandle = null;
                    _logger.error("Node " + nodeId + " does not have either a handle or URI");
                }
            }

            resource.setPid(nodeHandle);
            resource.setAvailableDataViews(this.makeAvailableDataViews());
            //not used
//            String nodeName = (stringSanityCheck(node.getName()) ? node.getName() : (stringSanityCheck(node.getTitle()) ? node.getTitle() : "No valid name or title provided for this node "));

//            _logger.debug("Node " + nodeId + ": db.countDescendants is called");
//            int _numberOfRecords = db.countDescendants(nodeId, user, -1, true, ALL_FORMATS, true);
//            _logger.debug("Node " + nodeId + ": db.countDescendants returns " + _numberOfRecords);

            CatalogData cd = new CatalogData(db, nodeId, user, _sruEngineImpl.getHandleResolverURL());
            resource.getDescription().addAll(cd.getDescriptions());
            resource.setLandingPageURI(cd.getLandingPageURL());

            Resources subresources = new Resources();
            subresources.getResource().addAll(this.makeSubresourcesForNode(db, node));
            resource.setResources(subresources);

            // get languages as a union of languages of the subresources after the subresources are constructed
            resource.setLanguages(this.makeLanguages(subresources));
            resource.getTitle().addAll(cd.getNodeTitle());

        } catch (UnknownNodeException e) {
            _logger.error(nodeId + " is an invalide nodeID", e.getMessage());
        }
        return resource;
    }

    /**
     * 
     * @param subresources an instance of {@link Resources} class that contains the list of instances of {@link EndpointDescriptionResource} class.
     * @return the instance of {@link Language}class that contains the list of all languages spoken or referred to in "subresources".
     */
    private Languages makeLanguages(Resources subresources) {
        List<EndpointDescriptionResource> subresourceList = subresources.getResource();
        Languages langs = new Languages();
        for (EndpointDescriptionResource subresource : subresourceList) {
            CQLHelpers.merge(langs.getLanguage(), subresource.getLanguages().getLanguage());
        }
        return langs;
    }

    /**
     * 
     * @return  the instance of {@link AvailableDataviews}  based on the single supported dataview "hits". 
     */
    private AvailableDataViews makeAvailableDataViews() {
        AvailableDataViews result = new AvailableDataViews();
        result.getRef().add(_endpointDesc.getSupportedDataViews().getSupportedDataView().get(0));
        return result;
    }

    /**
     * 
     * @param value a string.
     * @return Boolean.parseBoolean(value) if value not null, and "false" otherwise.
     */
    private boolean parseBoolean(String value) {
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return false;
    }

    /**
     * 
     * @param db the corpus-structure database.
     * @param node an instance of the class {@link Node} from corpus-structure API.
     * @return the list of instances of {@link EndpointDescriptionResource} corresponding to the direct children nodes (corpora or sessions) of "node".
     */
    private List<EndpointDescriptionResource> makeSubresourcesForNode(CorpusStructureDBImpl db, Node node) {
        List<EndpointDescriptionResource> result = new ArrayList<EndpointDescriptionResource>();
        Node[] kids = db.getChildrenNodes(node.getNodeId());
        for (Node kid : kids) {
            if (kid.getNodeType() == Node.CORPUS || kid.getNodeType() == Node.SESSION) {
                result.add(this.makeSubresource(db, kid.getNodeId()));
            }
        }
        return result;
    }

    /**
     * 
     * @param db the corpus-structure database.
     * @param nodeId the identifier of a corpus node.
     * @return the instance of the {@link EndpointDescriptionResource} class whose fields are filled 
     * based on corpus-structure information for the "nodeId" and the cached mapping "nodeId --> Languages"
     */
    private EndpointDescriptionResource makeSubresource(CorpusStructureDBImpl db, String nodeId) {
        EndpointDescriptionResource result = new EndpointDescriptionResource();
        result.setAvailableDataViews(this.makeAvailableDataViews());
        result.setLandingPageURI(CQLHelpers.constructHandleView(db, nodeId, _sruEngineImpl.getHandleResolverURL()));
        try {
            result.setLanguages(_cache.get(0).get(nodeId));
        } catch (ExecutionException e) {
            _diag.addDiagnostic(SRUConstants.SRU_GENERAL_SYSTEM_ERROR, nodeId, e.getMessage());
             result.setLanguages((new LanguageHarvester(db, _searchableCopropa, _sruEngineImpl.getImdiLanguageMapURL(), _sruEngineImpl.getImdiNamespaceURL())).getLanguages(nodeId));
        }
        result.setPid(db.getObjectPID(nodeId));

        //resource.setResources(new Resources()); implement subresources for the subcorpora of the first level later, via xml-cash
        //result.getDescription().add("??");


        I18NString i18nstring = new I18NString();
        i18nstring.setLang("eng");
        Node node = db.getNode(nodeId);
        String nodeTitle = node.getTitle();
        String title = (nodeTitle == null || nodeTitle.trim().isEmpty()) ? node.getName() : nodeTitle;
        i18nstring.setValue(title);
        result.getTitle().add(i18nstring);

        return result;
    }

   
   
   
}
