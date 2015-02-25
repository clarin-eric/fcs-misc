/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mpi.annot.search.cqlsearch2;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import de.clarin.fcs.schema.EndpointDescriptionResource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import nl.mpi.corpusstructure.CorpusStructureDBImpl;
import nl.mpi.corpusstructure.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The tread is run separately from the main thread when the application is initiated (see "init" method of {@link SRUSearchEngineImpl}). 
 * In this thread the languages of searchable ("root") corpora and their direct subcorpora are retrieved from the corresponding sessions and saved in a cache.
 * 
 * @author olhsha
 */
public class LanguageHarvestingThread extends Thread{
    
   private CorpusStructureDBImpl _db;
   private List<String> _searchableCorpora;
   private ArrayList<LoadingCache<String, EndpointDescriptionResource.Languages>> _cache;
   private Logger _logger = LoggerFactory.getLogger(LanguageHarvestingThread.class);
   private LanguageHarvester _languageHarvester;
   private String _imdiLanguageMapURL;
   private String _imdiNamespaceURL;
   
   /**
    * 
    * @param db corpus-structure database.
    * @param searchableCorpora the list of main searchable-node identifiers.
    * @param cache the reference to list of language caches; the reference is used to implement the side effect of creating a language-storing cache while thread is running; only cache[0] will be used later.
    */
   public LanguageHarvestingThread(CorpusStructureDBImpl db, List<String> searchableCorpora, String imdiLanguageMapURL, String imdiNamespaceURL, ArrayList<LoadingCache<String, EndpointDescriptionResource.Languages>> cache){
       _imdiLanguageMapURL = imdiLanguageMapURL;
       _imdiNamespaceURL = imdiNamespaceURL;
       _db =db;
       _searchableCorpora = searchableCorpora;
       _cache = cache;
   }
   /**
    * creates a single-element of the array "cache".
    * While creating, the instance of {@link LanguageHarvester} is made and used to retrieve languages of the given node (via its nodeId). Trade-off space-time is in the favor of time.
    * Also, the method calls the method "initLanguageCache" which triggers loading cached lists of languages when the application is started on the server.
    */
   @Override
   public void run(){
      _languageHarvester = new LanguageHarvester(_db, _searchableCorpora, _imdiLanguageMapURL, _imdiNamespaceURL);
      LoadingCache<String, EndpointDescriptionResource.Languages> cache = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(365, TimeUnit.DAYS)
                .build(
                new CacheLoader<String, EndpointDescriptionResource.Languages>() {
                    @Override
                    public EndpointDescriptionResource.Languages load(String nodeId) {
                        EndpointDescriptionResource.Languages result = _languageHarvester.getLanguages(nodeId);
                        return result;
                    }
                }); 
      _cache.add(cache);
     try {
           this.initLanguageCache(_cache.get(0));
           //this.stop();
        } catch (ExecutionException e) {
            _logger.error("Cannot initialise cached list of languages: " + e.getMessage());
        } 
   }
   
   /**
    * Calls get method of LoadingCache that initiates loading cache if it is not loaded yet.
    * @param cache a cached mapping from corpus-node identifiers to the corresponding instances of the {@link Languages} class containing a list of ISO-3 codes of languages for a given node id.
    * @throws ExecutionException if cache.get(-) throws it.
    * 
    */
   private void initLanguageCache(LoadingCache<String, EndpointDescriptionResource.Languages> cache) throws ExecutionException{
        for (String nodeId : _searchableCorpora) {
            cache.get(nodeId);
            Node[] kids = _db.getChildrenNodes(nodeId);
            for (Node kid : kids) {
                if (kid.getNodeType() == Node.CORPUS || kid.getNodeType() == Node.SESSION) {
                    cache.get(kid.getNodeId());
                }
            }
        }
    } 
   
}
