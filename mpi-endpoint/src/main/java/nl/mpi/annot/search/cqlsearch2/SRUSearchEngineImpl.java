package nl.mpi.annot.search.cqlsearch2;

import com.google.common.cache.LoadingCache;
import de.clarin.fcs.schema.EndpointDescriptionResource.Languages;
import de.clarin.fcs.schema.EndpointDescriptionType;
import de.clarin.fcs.schema.ResourceType;
import eu.clarin.sru.server.*;
import eu.clarin.sru.server.utils.SRUSearchEngineBase;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import nl.mpi.annot.search.lib.SearchClient;
import nl.mpi.corpusstructure.CorpusStructureDBImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

 /**
     * Implementation of SRUSearchEngine class of SRUServer package. It initiates the endpoint by filling in parameters and triggering connection to the database. 
     * Moreover, it dispatches SRU-CQL requests from the aggregator.
     **/

public class SRUSearchEngineImpl extends SRUSearchEngineBase {

    private SearchClient _searchClient = null;
    private CorpusStructureDBImpl _db;
    private String _searchDbUrl = null;
    private String _userId = null;
    private List<String> _searchableCorpora;
    private int _tierCollectionThreads = 3;
    private int _queryThreads = 3;
    private Logger _logger = LoggerFactory.getLogger(SRUSearchEngineImpl.class);
    private Marshaller explainMarshaller;
    private Marshaller searchRetrieveMarshaller;        
    private ArrayList<LoadingCache<String, Languages>> _cache = new ArrayList<LoadingCache<String, Languages>>();
    private LanguageHarvestingThread _languageThread;
    private String _imdiLanguageMapURL;
    private String _imdiNamespaceURL;
    private String _handleResolverURL;
    private String _recordSchemaIdentifier;
    private String _schemaLocationExplain;
    private String _schemaLocationSearchRetrieve;
    /**
     *  Performs SRU/CQL explain request; the results will be passed serialised to the aggregator via 
     * "writer" parameter near the exit point of "handleRequest" method.
     * 
     * @param sruServerConfig an instance of {@link SRUServerConfig} class (SRUServer package), passed from/ the aggregator.
     * @param sruRequest an instance of {@link SRURequest} class (SRUServer package), passed from the aggregator.
     * @param sruDiagnosticList an instance of {@link SRUDiagnosticList} class (SRUServer package), passed from/to the aggregator.
     * @return instance of {@link SRUExplainResult} class (SRUServer package) which is created and used within this method.
     */
    @Override
    public SRUExplainResult explain(SRUServerConfig sruServerConfig, SRURequest sruRequest, SRUDiagnosticList sruDiagnosticList){
        SRUExplainResultImpl result = new SRUExplainResultImpl(this, _searchableCorpora, sruRequest, sruDiagnosticList, _cache);
        result.handleRequest();
        return result;
    }
    
    /**
     * Performs SRU/CQL search request; the results will be passed serialised to the aggregator via 
     * "writer" parameter near the exit point of "handleRequest" method.
     * 
    * @param sruServerConfig an instance of {@link SRUServerConfig} class (SRUServer package), passed from the aggregator.
     * @param sruRequest an instance of {@link SRURequest"} class (SRUServer package), passed from the aggregator.
     * @param sruDiagnosticList an instance of {@link SRUDiagnosticList} class (SRUServer package), passed from/to the aggregator.
     * @return instance of {@link SRUSearchResultSet} class (SRUServer package) which is created and used within this method.
     
     * @throws SRUException if recordSchemaIdentifier is null or not equal to CLARIN_FCS_RECORD_SCHEMA, 
     * or the exception is passed from "handleRequest"  method.
     */
    @Override
    public SRUSearchResultSet search(SRUServerConfig sruServerConfig, SRURequest sruRequest, SRUDiagnosticList sruDiagnosticList) throws SRUException {
       
        final String recordSchemaIdentifier =
                sruRequest.getRecordSchemaIdentifier();
        if ((recordSchemaIdentifier != null)
                && !recordSchemaIdentifier.equals(sruServerConfig.getSchemaInfo().get(0).getIdentifier())) {
            throw new SRUException(
                    SRUConstants.SRU_UNKNOWN_SCHEMA_FOR_RETRIEVAL,
                    recordSchemaIdentifier, "Record schema \""
                    + recordSchemaIdentifier
                    + "\" is not supported.");
        }

        SRUSearchResultSetImpl ret = new SRUSearchResultSetImpl(sruServerConfig, sruRequest, sruDiagnosticList, this, _searchableCorpora);
        ret.handleRequest();
        return ret;
    }

   /** Performs SRU/CQL scan request, which is not specified yet.
     * @param sruServerConfig an instance of {@link SRUServerConfig} class (SRUServer package), passed from the aggregator.
     * @param sruRequest an instance of {@link SRURequest} class (SRUServer package), passed from the aggregator.
     * @param sruDiagnosticList an instance of {@link SRUDiagnosticList} class (SRUServer package), passed from/to the aggregator.
     * @return instance of {@link SRUScanResult} class (SRUServer package).
    * 
    */
    @Override
    public SRUScanResultSet scan(SRUServerConfig sruServerConfig, SRURequest sruRequest, SRUDiagnosticList sruDiagnosticList) {
         return null;
    }

    /**
     * Initializes the search engine.
     *
     * @param config (not used at the moment) the {@link SRUServerConfig} object for this search engine.
     * @param params additional parameters gathered from the Servlet configuration and Servlet context.
     */
    @Override
    public void init(ServletContext context, SRUServerConfig config, Map<String, String> params) throws SRUConfigException {
        super.init(context, config, params);
        _logger.info("INIT");
        _userId = params.get("nl.mpi.cqlsearch2.userId");
        _searchableCorpora = Collections.unmodifiableList(Arrays.asList((params.get("nl.mpi.cqlsearch2.searchableCorpora")).split(",")));
        _tierCollectionThreads = Integer.parseInt(params.get("nl.mpi.cqlsearch2.tierCollectionThreads"));
        _queryThreads = Integer.parseInt(params.get("nl.mpi.cqlsearch2.queryThreads"));
        _searchDbUrl = params.get("nl.mpi.annexUrl");
        _imdiLanguageMapURL = params.get("nl.mpi.cqlsearch2.imdiLanguageMapURL");
        _imdiNamespaceURL =params.get("nl.mpi.cqlsearch2.imdiNamespaceURL");
        _handleResolverURL = params.get("nl.mpi.cqlsearch2.handleResolverURL");
        _recordSchemaIdentifier = params.get("eu.clarin.sru.server.recordScemaIdentifier");
        _schemaLocationExplain=params.get("nl.mpi.cqlsearch2.schemaLocationEndpointDescription");
        _schemaLocationSearchRetrieve = params.get("nl.mpi.cqlsearch2.schemaLocationResource");
        
        this.initDbConnection(params.get("nl.mpi.annot.search.searchDbURL"),
                params.get("nl.mpi.annot.search.searchDbUser"),
                params.get("nl.mpi.annot.search.searchDbPassword"),
                params.get("nl.mpi.annot.search.luceneDirectory"),
                params.get("nl.mpi.db.corpusstructure"));
       
       JaxbMarshallerFactory jaxbMarshallerFactoryExplain =  new JaxbMarshallerFactory();
       JaxbMarshallerFactory jaxbMarshallerFactorySearchRetrieve =  new JaxbMarshallerFactory();
       try {
         jaxbMarshallerFactoryExplain.setSchemaLocation(_schemaLocationExplain);
         explainMarshaller = jaxbMarshallerFactoryExplain.createMarshaller(EndpointDescriptionType.class);
         jaxbMarshallerFactorySearchRetrieve.setSchemaLocation(_schemaLocationSearchRetrieve);
         searchRetrieveMarshaller = jaxbMarshallerFactorySearchRetrieve.createMarshaller(ResourceType.class);
       
       } catch (PropertyException e) {
            throw new SRUConfigException("JaxbMarshallerFactory cannot set schema: "+e.getMessage());   
        } catch (JAXBException e2) {
            throw new SRUConfigException("JaxbMarshallerFactory cannot create marshaller: "+e2.getMessage());
            
        }
       
       
       // Side effect on cache: there it is filled in
       _languageThread = new LanguageHarvestingThread(_db, _searchableCorpora, _imdiLanguageMapURL, _imdiNamespaceURL, _cache);
       int currentPriority = _languageThread.getPriority();
       //_languageThread.setPriority(currentPriority-1);
       _languageThread.setName("Language Thread");
       _languageThread.start();
    }
    
    public String getHandleResolverURL(){
        return _handleResolverURL;
    }
    
    public String getImdiNamespaceURL(){
        return _imdiNamespaceURL;
    }
    
    public String getImdiLanguageMapURL(){
        return _imdiLanguageMapURL;
    }
    
  
    /**
     * 
     * @return {@link Marshaller} object for serializing extra data on "explain" request.
     */
    public Marshaller getExplainMarshaller(){
        return explainMarshaller;
    }
    
    /**
     * 
     * @return {@link Marshaller} object for serializing response on "search" request.
     */
    public Marshaller getSearchRetrieveMarshaller(){
        return searchRetrieveMarshaller;
    }

    /**
     * 
     *
     * @param url Trova JDBC url.
     * @param user SQL user for url.
     * @param pass SQL user password.
     * @param luceneDir directory with Lucene index for Trova, or null (name must indicate N-gram N, e.g. /data/lucene-indexes/trova.5)
     * @param corpusStructureDBName 
     * @throws IllegalStateException if the connection to the database fails (creation of the search client throws an SQLException).
     */
    private void initDbConnection(String url, String user, String pass, String luceneDir, String corpusStructureDBName) throws IllegalStateException {

        try {
            _searchClient = new SearchClient(url, user, pass, ((luceneDir == null || luceneDir.trim().length() == 0) ? null : new File(luceneDir))); // for searchRetrieve
        } catch (SQLException sqle) {
            throw new IllegalStateException("Cannot connect to Annex/Trova search DB at " + url + " as user " + user, sqle);
        }
        _db = new CorpusStructureDBImpl(corpusStructureDBName);
    }

    /**
     * 
     * @return the database-user id.
     */
    public String getUserId() {
        return _userId;
    }

    /**
     * 
     * @return the {@link CorpusStructureDBImpl} object set for *this*.
     */
    public CorpusStructureDBImpl getCorpusStructureDB() {
        return _db;
    }

     /**
     * 
     * @return the {@link SearchClient} object set for *this*.
     */
    public SearchClient getSearchClient() {
        return _searchClient;
    }

     /**
     * 
     * @return the URL of the search database set for *this*.
     */
    public String getSearchDBUrl() {
        return _searchDbUrl;
    }

    /**
     * 
     * @return the number of threads for making tier collection.
     */
    public int getTierCollectionThreads() {
        return _tierCollectionThreads;
    }

    /**
     * 
     * @return the number of threads for performing Trova query.
     */
    public int getQueryThreads() {
        return _queryThreads;
    }
   
    
}
