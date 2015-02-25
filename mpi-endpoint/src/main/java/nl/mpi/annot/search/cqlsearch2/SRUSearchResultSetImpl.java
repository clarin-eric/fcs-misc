package nl.mpi.annot.search.cqlsearch2;

//import com.sun.xml.internal.bind.marshaller.NamespacePrefixMapper;
import de.clarin.fcs.schema.DataViewType;
import de.clarin.fcs.schema.ObjectFactory;
import de.clarin.fcs.schema.ResourceType;
import de.clarin.fcs.schema.ResourceType.ResourceFragment;
import de.clarin.fcs.schema.Result;
import eu.clarin.sru.server.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import com.google.common.collect.Collections2;

import java.util.Collection;
import javax.xml.stream.XMLStreamWriter;

import nl.mpi.annot.search.lib.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.z3950.zing.cql.CQLAndNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLOrNode;
import org.z3950.zing.cql.CQLTermNode;

/**
 * Extention of {@link SRUSearchResultSet} class from SRUServer package; it is adapted for our endpoint.
 * @author olhsha
 */
public class SRUSearchResultSetImpl extends SRUSearchResultSet {

    SRUServerConfig _srvConfig = null;
    SRURequest _request = null;
    SRUDiagnosticList _diag = null;
    private static Logger _logger = LoggerFactory.getLogger(SRUSearchResultSetImpl.class);
    /**
     * Connection to a single query for the Trova search engine
     */
    QueryServer _pQuery = null;
    /**
     * Used by nextRecord and writeRecord to access a specific hit
     */
    int _index = -1;
    private String _matchMode = Constants.REGEXP_MATCH;
    private SRUSearchEngineImpl _sruEngineImpl = null;
    private List<String> _searchableCorpora;
    private int _startRecord;
    private int _totalRecordCounter; // amount of found records
    private int _recordCount; // amount of found records within total found records, starting from startRecord till startRecord+maximumRecords
    private List<SearchHit> _foundRecords = null;
    private int _contextSize = 3;
    private final String dataviewTypeHits = "application/x-clarin-fcs-hits+xml";
    /**
     * the list of characters that cause XML-parser exception *
     **/
    private final char[] WeirdCHARACTERS = {(char) (Integer.parseInt("0013", 16))};
    private final String[] _auxTIERS = {Constants.TIER_TYPE_LABEL + "Spch", /* CGN */
        Constants.TIER_TYPE_LABEL + "text", /* ESF, IFA, childes, talkbank */
        Constants.TIER_TYPE_LABEL + "eng", /* childes, talkbank*/
        Constants.TIER_TYPE_LABEL + "exp", /* childes, talkbank*/
        Constants.TIER_TYPE_LABEL + "flo", /* childes*/
        Constants.TIER_TYPE_LABEL + "ort", /* hieroglyphs:  childes, talkbank*/
        Constants.TIER_TYPE_LABEL + "META_Comment", /* ESF, talkbank */
        Constants.TIER_TYPE_LABEL + "META_Bck", /* childes, talkbank*/
        Constants.TIER_TYPE_LABEL + "META_Situation", /* childes, talkbank*/
        Constants.TIER_TYPE_LABEL + "act", /* childes, talkbank*/
        Constants.TIER_TYPE_LABEL + "com", /* childes, talkbank*/};
    private final ArrayList<String> TIERCONSTRAINTS = new ArrayList<String>(Arrays.asList(_auxTIERS));
    private final String[] _auxALLOWED_EXTRA_REQUEST_PARAMETERS = {CQLConstants.X_FCS_CONTEXT, CQLConstants.X_FCS_DATAVIEWS};
    private final ArrayList<String> ALLOWED_EXTRA_REQUEST_PARAMETERS = new ArrayList<String>(Arrays.asList(_auxALLOWED_EXTRA_REQUEST_PARAMETERS));
    
 
    /**
     * 
     * @param sruServerConfig the {@link SRUServerConfig} passed from the aggregator.
     * @param sruRequest the object of {@link SRURequest} representing  SRU/CQL "search" request with parameters; the request is passed from the aggregator.
     * @param sruDiagnosticList the reference to the list of diagnostics passed from/to the aggregator.
     * @param sruEngineImpl the instance of {@link SRUSearchEngine} class of SRUServer package.
     * @param searchableCorpora the list of the node id-s of main ("root") searchable corpora.
     */
    protected SRUSearchResultSetImpl(SRUServerConfig sruServerConfig, SRURequest sruRequest, SRUDiagnosticList sruDiagnosticList,
            SRUSearchEngineImpl sruEngineImpl, List<String> searchableCorpora) {

        super(sruDiagnosticList);

        _srvConfig = sruServerConfig;
        _request = sruRequest;
        _diag = sruDiagnosticList;
        _sruEngineImpl = sruEngineImpl;
        _searchableCorpora = searchableCorpora;
        _startRecord = (sruRequest.getStartRecord() < 1) ? 1 : sruRequest.getStartRecord();
        _index = _startRecord - 1; // SRU-indexing, starts with 1; will be increased by one on the first iteration
        _foundRecords = null;
        _totalRecordCounter = 0;
        _recordCount = 0;
    }

    /**
     * Performs search request; if the query is not supported adds the corresponding diagnostics and returns.
     * @throws SRUException if the method "makePQuery" or "runSearch" throws exceptions.
     */
    public void handleRequest() throws SRUException {
      
        List<String> searchExpressions ;
        try {
           searchExpressions =  this.cqlToTrovaRequests(_request.getQuery());
        } catch (NonSupportedQueryException e){
            _diag.addDiagnostic(SRUConstants.SRU_QUERY_FEATURE_UNSUPPORTED, "query", "Query "+_request.getQuery()+" is not supported.");
            return;
        }


        if (CQLHelpers.isInvalidElementsInTheList(_request.getExtraRequestDataNames(), ALLOWED_EXTRA_REQUEST_PARAMETERS)) {
            _foundRecords = null;
            _recordCount = 0;
            _diag.addDiagnostic(SRUConstants.SRU_UNSUPPORTED_PARAMETER, "SRU unsupported parameter", "There is an unsupported extra request parameter");
            return;
        }
        
        // checkDataviews return true iff the list of dataviews contains at least one supported view (hits in our case)
        if (!this.checkDataviews(_request)){
            return;
        }
        
        
        TierCollection domain = makeDomain(_request, _sruEngineImpl.getUserId(), _sruEngineImpl.getTierCollectionThreads(),
                _searchableCorpora, _sruEngineImpl.getSearchClient());
        _pQuery = makePQuery(_sruEngineImpl, _logger);

        
        int requestedMaximumRecords = (_request.getMaximumRecords() > _srvConfig.getMaximumRecords())
                ? _srvConfig.getMaximumRecords() : _request.getMaximumRecords();
        
        _foundRecords = this.runSearch(_pQuery, domain, searchExpressions, _matchMode, TIERCONSTRAINTS, _startRecord, requestedMaximumRecords);

    }
    
   

    /**
     * 
     * @return the amount of found suitable records containing the search terms.
     */
    @Override
    public int getTotalRecordCount() {
        return _totalRecordCounter;
    }

    /**
     * 
     * @return the amount of records to be return to the aggregator.
     */
    @Override
    public int getRecordCount() {
        return _recordCount;
    }

    /**
     * 
     * @return the record schema identifier.
     */
    @Override
    public String getRecordSchemaIdentifier() {
        return _request.getRecordSchemaIdentifier();
    }

    /**
     * 
     * @return true if the next record is expected by the aggregator.
     */
    @Override
    public boolean nextRecord() {
        _index++;
        return (_index < _startRecord + _recordCount);
    }

    /**
     * 
     * @return null. Not implemented, not clear what must be there: check SRUServer package/interface.
     */
    @Override
    public String getRecordIdentifier() {
        return null;
    }

    /**
     * 
     * @param writer serialized presentation of the response passed from and then back to the aggregator.
     */
    @Override
    public void writeRecord(XMLStreamWriter writer) {
        
        SearchHit hit = _foundRecords.get(_index - _startRecord);
        ResourceType fcsResource = this.getResource(hit, hit.getFirstField(), hit.layerTiers.get(0));
        JAXBElement<ResourceType> resourceTypeElem = (new ObjectFactory()).createResource(fcsResource);
        
        try {
            _sruEngineImpl.getSearchRetrieveMarshaller().marshal(resourceTypeElem, writer);
        } catch (JAXBException e1) {
            _diag.addDiagnostic(SRUConstants.SRU_GENERAL_SYSTEM_ERROR, "Serialization error", e1.getMessage());
        } 


    }

    /**
     * 
     * @param source a string.
     * @return overworked output string with escape symbols (causing xml-parser exception) replaced with lozenge.
     */ 
    private String replaceStrangeSymbolsWithLozenge(String source) {

        if (source == null) {
            return source;
        }

        String cleanedSource = source;
        String tmp;
        for (char c : WeirdCHARACTERS) {
            tmp = cleanedSource.replace(c, CQLConstants.LOZENGE);
            cleanedSource = tmp;
        }
        return cleanedSource;
    }

    /**
     * 
     * @param request an {@link SRURequest} object.
     * @return true if the request does not contain extra-parameter "x-fcs-dataviews", or if it contains "hits" since  for our endpoint "hits" is the only implemented dataview. Must be re-implemented once another possible data-view is
     * implemented for the end-point.
     * Also checks, if a dataview is in the list of acceptable FCS dataviews, and if not, adds the corresponding FCS diagnostics. 
     */
    private boolean checkDataviews(SRURequest request){
       if (!(request.getExtraRequestDataNames()).contains(CQLConstants.X_FCS_DATAVIEWS)) {
           return true;
       }
       String dataviewsstring  = request.getExtraRequestData(CQLConstants.X_FCS_DATAVIEWS);
       List<String> dataviews = Arrays.asList(dataviewsstring.split(","));
       for (String dataview: dataviews) {
           if (!dataview.equals(CQLConstants.hits)) {
              _diag.addDiagnostic(CQLConstants.FCS_DIAGNOSTIC_REQUESTED_DATA_VIEW_INVALID, dataview, "Requested Data View is not valid for this resource");
           }
       }
       return dataviews.contains(CQLConstants.hits);
    }
  
    /**
     * 
     * @param request an {@link SRURequest} object.
     * @param searchableCorpora the list of node id-s of searchable corpora.
     * @return the list of node identifiers of top search nodes. If the request contains 
     * "x-fcs-context" parameter then the tope node identifiers are generated from the value
     * of this parameter which is a coma-separated list of handles of node-id-s. Otherwise
     * the searchable corpora form the list of the top-nodes.
     */
    private ArrayList<String> makeTopNodesList(SRURequest request, List<String> searchableCorpora) {

        ArrayList<String> topNodes = new ArrayList<String>();
        String tosearch = request.getExtraRequestData(CQLConstants.X_FCS_CONTEXT);
        if (tosearch != null) {
            topNodes = CQLHelpers.inListHandleOrURItoNodeId(_sruEngineImpl.getCorpusStructureDB(), Arrays.asList(tosearch.split(",")));
        } else {
            for (String c : searchableCorpora) {
                topNodes.add(c);
            }
        }
        return topNodes;
    }
    
    /**
     * 
     * @param request an {@link SRURequest} object.
     * @param userId the database-user id.
     * @param tierCollectionThreads the number of threads for creating tier collection.
     * @param searchableCorpora the list of node identifiers of searchable ("root") corpora.
     * @param searchClient a {@link SearchClient} object.
     * @return a {@link TierCollection} object constructed for the input parameters.
     */
    private TierCollection makeDomain(SRURequest request, String userId, int tierCollectionThreads, List<String> searchableCorpora, SearchClient searchClient) {
        ArrayList<String> topNodes = makeTopNodesList(request, searchableCorpora);
        TierCollection domain = new TierCollection(tierCollectionThreads, searchClient, userId, topNodes);
        return domain;
    }

    /**
     * 
     * @param engineImpl an {@link SRUSearchEngineImpl} object.
     * @param logger this logger used to log an exception if thrown while creating a query.
     * @return a {@link QueryServer} object, a pQuery. 
     * @throws SRUException if an exception is thrown while creating a query for the "engineImpl".
     */
    private QueryServer makePQuery(SRUSearchEngineImpl engineImpl, Logger logger) throws SRUException {
        QueryServer pQuery = null;
        int queryThreads = engineImpl.getQueryThreads();
        try {
            pQuery = engineImpl.getSearchClient().createQuery(queryThreads);
        } catch (Exception e) {
            logger.info(e.toString());
            throw new SRUException(SRUConstants.SRU_GENERAL_SYSTEM_ERROR, e.toString());
        }

        return pQuery;
    }

    /**
     * 
     * @param pQuery a {@link QueryServer} object.
     * @param domain a {@link TierCollection} object.
     * @param trovaQueries a list of string Trova queries, which is translation of CQL query to a collection of conjunctions;
     * for instance, a query (a AND b) OR (c AND d) is translated to [a.*c, c.*a, a.*d, d.*a, b.*c, c.*b, b.*d,d.*b].
     * @param matchMode Trova Match mode.
     * @param tierConstraints a list of Trova tiers within which the search must be run. 
     * @param startRecord the first record to be passed to the aggregator.
     * @param requestedMaximumRecords the upper bound on the amount of requested records.
     * @return the list of Trova search hits, each of which contains the left and the right contexts and the term/regular expression hit itself.
     * @throws SRUException if the amount of found hits is less than the requested index of the first record to display.
     */
    private List<SearchHit> runSearch(QueryServer pQuery, TierCollection domain, List<String> trovaQueries, String matchMode,
            ArrayList<String> tierConstraints, int startRecord, int requestedMaximumRecords) throws SRUException {
        List<SearchHit> result = new ArrayList<SearchHit>();
        _totalRecordCounter = 0;
        long expectRecords = (long) startRecord + (long) requestedMaximumRecords - 1;
        for (String trovaQuery : trovaQueries) {
            pQuery.setSimpleQuery(trovaQuery, Constants.ANNOTATIONS, false, matchMode, tierConstraints);

            // first null: no filetype constraints; second null: query as set by setSimpleQuery
            pQuery.doQuery(domain, null, null);
            SearchStatistics stats = pQuery.getSearchStatistics();
            while (_totalRecordCounter + stats.nHits < (expectRecords) && pQuery.isRunning()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    // Do Nothing, being interrupted is not bad.
                }
                stats = pQuery.getSearchStatistics();
            }
            _totalRecordCounter = _totalRecordCounter + stats.nHits;
            result.addAll(pQuery.getHits(0, stats.nHits, _contextSize));
        }
        if (startRecord > _totalRecordCounter) {
            _recordCount = 0;
            if (_totalRecordCounter > 0) {
                throw new SRUException(SRUConstants.SRU_FIRST_RECORD_POSITION_OUT_OF_RANGE, "startRecord", "Value of startRecord is higher than the number of hits of the query");
            } else {
                return null;
            }
        } else {
            int usefulRecordCount = (_totalRecordCounter < expectRecords) ? _totalRecordCounter : (int) expectRecords;
            _recordCount = usefulRecordCount - (startRecord - 1);
            return result.subList(startRecord - 1, _recordCount);
        }
    }

    /**
     * 
     * @param hit a {@link SearchHit} object.
     * @param atomicHit a {@link HitField} object.
     * @param layer a {@link HitLayer} object.
     * @return the {@link resourceType} object whose fields are filled based on input parameters.
     */
    private ResourceType getResource(SearchHit hit, HitField atomicHit, HitLayer layer) {

        ResourceType resultingFcsResource = new ResourceType();
        
        //setting up ref
        
        String landingPage = this.getLandingPageOfHit(hit, String.valueOf(hit.beginTime),
                String.valueOf(hit.endTime - atomicHit.beginTime), layer.tierName);
        resultingFcsResource.setRef(landingPage);
        
        // setting up PID
        String PID = CQLHelpers.nodeIdToPID(_sruEngineImpl.getCorpusStructureDB(), hit.transcriptionNodeId, _sruEngineImpl.getHandleResolverURL());
        resultingFcsResource.setPid(PID);
        
        // setting resource fragment (from top to bottom in the xml structure)
        List<ResourceFragment> resourceFragments = resultingFcsResource.getResourceFragment();
        ResourceFragment resourceFragment = new ResourceFragment();
        resourceFragments.add(resourceFragment);

        // setting data-view in resource fragment
        List<DataViewType> fragmentDataViewTypes = resourceFragment.getDataView();
        DataViewType fragmentDataView = new DataViewType();
        fragmentDataViewTypes.add(fragmentDataView);
        fragmentDataView.setType(dataviewTypeHits);
        
        // setting "result"  in data-view
        Result searchResult = new Result();

        StringBuilder sb = new StringBuilder();
        // Start building the string representing the KWIC Hit

        Collections.reverse(hit.leftContext); // // next-to-hit was first, leftmost was last! 
        for (String s : hit.leftContext) {
            sb.append(s);
            sb.append(" ");
        }
        String leftcontext = sb.toString();

        sb = new StringBuilder();
        for (String s : hit.rightContext) {
            sb.append(s);
            sb.append(" ");
        }
        String rightcontext = sb.toString();

        int length = atomicHit.hitLength;
        int index = atomicHit.hitPositionInAnnotation;
        List<Serializable> content = searchResult.getContent();
        content.add(replaceStrangeSymbolsWithLozenge(leftcontext + atomicHit.annotation.substring(0, index)).trim());
        String hitStr = replaceStrangeSymbolsWithLozenge(atomicHit.annotation.substring(index, index + length).trim());
        JAXBElement<String> hitJaxbElement = (new ObjectFactory()).createResultHit(hitStr);
        content.add(hitJaxbElement);
        content.add(replaceStrangeSymbolsWithLozenge(atomicHit.annotation.substring(index + length) + " " + rightcontext).trim());
       
        fragmentDataView.setAny(searchResult);
        
        return resultingFcsResource;
    }

    /**
     * 
     * @param hit a {@link SearchHit} object.
     * @param beginTime a string representing the start time of the video under the hit.
     * @param duration the duration of the video fragment corresponding to the hit.
     * @param tierName the name of the annotation tier where the hit takes place.
     * @return the URI of the landing page based on the input parameters.
     */
    private String getLandingPageOfHit(SearchHit hit, String beginTime, String duration, String tierName) {
        return _sruEngineImpl.getSearchDBUrl() + "?nodeid=" + hit.transcriptionNodeId.replaceAll("#", "%23")
                + "&time=" + beginTime + "&duration=" + duration + "&tiername=" + tierName;
    }
    
    /**
     * Constructs a presentation of the DNF of the request, so that e.g on (A OR B) AND (C OR D) it outputs [[A,C], [A, D], [B,C], [B, D]].
     * @param request a {@link CQLNode} object representing CQL search request.
     * @return a list of lists of terms, so that the outer list means the disjunction of the the inner lists, where an inner list contains the operands of a conjunction.
     * @throws NonSupportedQueryException if the CQL input query is not supported.
     */
    private List<List<String>> listConjunctions(CQLNode request) throws NonSupportedQueryException{
        
        if (request instanceof CQLTermNode) {
            List<List<String>> result = new ArrayList<List<String>>();
            ArrayList<String> resultElement = new ArrayList<String>();
            String searchTerm = ((CQLTermNode) request).getTerm();
            resultElement.add(searchTerm);
            result.add(resultElement);
            return result;
        }
        
        if (request instanceof CQLAndNode) {
            CQLAndNode requestDowncasted = (CQLAndNode) request;
            CQLNode leftOperand = requestDowncasted.getLeftOperand();
            CQLNode rightOperand = requestDowncasted.getRightOperand();
            List<List<String>> leftRegExps = this.listConjunctions(leftOperand);
            List<List<String>> rightRegExps = this.listConjunctions(rightOperand);
            List<List<String>> result = new ArrayList<List<String>>();
            for (List<String> left : leftRegExps) {
                for (List<String> right : rightRegExps) {
                    ArrayList<String> l = new ArrayList<String>(left);
                    ArrayList<String> r = new ArrayList<String>(right);
                    //regExpA.append("(").append(left).append(") .* (").append(right).append(")");
                    l.addAll(r);
                    result.add(l);
                }
            }
            return result;
        }
        
         if (request instanceof CQLOrNode) {
            CQLOrNode requestDowncasted = (CQLOrNode) request;
            CQLNode leftOperand = requestDowncasted.getLeftOperand();
            CQLNode rightOperand = requestDowncasted.getRightOperand();
            List<List<String>> result = this.listConjunctions(leftOperand);
            List<List<String>> rightRegExp = this.listConjunctions(rightOperand);
            result.addAll(rightRegExp);
            return result;
        }
        
        throw  (new NonSupportedQueryException());  
    }
    
   
    /**
     * 
     * @param term an atomic search term, a word; e.g. "kind".
     * @return maps an atomic search term into a Trova reg.exp.
     */
    private String termToRegexpr(String term) {
        StringBuilder regExp = new StringBuilder();
        regExp.append('\\').append('b').append(term).append('\\').append('b');
       return regExp.toString();
    }
    
    /**
     * 
     * @param listOfConjunctions list of lists of strings, where each inner list represents a set of conjuncts.
     * @return list of all possible permutations of all the elements of the input; e.g. on [[a,b], [x,y]] it outputs [[a, b], [b,a], [x, y], [y, x]].
     */
    private List<List<String>> permuteConjunctions(List<List<String>> listOfConjunctions) {
       
       //now we need to permute each conjunction
        List<List<String>> result = new ArrayList<List<String>>();
        for (List<String> conjunction : listOfConjunctions) {
            Collection<List<String>> listOfPermutedConjunctions = Collections2.permutations(conjunction);
            for (List<String> permutedConjunction: listOfPermutedConjunctions){
                result.add(permutedConjunction);
            }
        }
        return result;
    }
    
    /**
     * 
     * @param terms list of strings, each of them represents an atomic search term.
     * @return a regular expression representing ordered occurrences of all the terms from the input; e.g. on [x, y, z] it outputs (x).*((y).*(z)) .
     */
    private String listToRegExp(List<String> terms){
       if (terms == null){
           return null;
       }
       if (terms.isEmpty()) {
           return "";
       }
       int length = terms.size();
       if (length == 1) {
           return this.termToRegexpr(terms.get(0));
       }
       StringBuilder result = new StringBuilder();
       String firstTerm = this.termToRegexpr(terms.get(0));
       List<String> tail = terms.subList(1, length);
       String tailRegExp = this.listToRegExp(tail);
       result.append("(").append(firstTerm).append(").*(").append(tailRegExp).append(")");
       return result.toString();
    }
    
    /**
     * 
     * @param request a CQL request term.
     * @return a collection of trova regular expressions equivalent to the input CQL search term,
     * @throws NonSupportedQueryException 
     */
    public List<String> cqlToTrovaRequests(CQLNode request) throws NonSupportedQueryException{
       List<List<String>> listOfConjucntions = this.listConjunctions(request);
       List<List<String>> listOfPermutedConjunctions  = this.permuteConjunctions(listOfConjucntions);
       List<String> result = new ArrayList<String>();
       for (List<String> permutedConjunction:listOfPermutedConjunctions){
           result.add(this.listToRegExp(permutedConjunction));
       }
       return result;
    }
    
}
