/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mpi.annot.search.cqlsearch2;

import de.clarin.fcs.schema.EndpointDescriptionType.SupportedDataViews.SupportedDataView;

/**
 * Representing a data view that corresponds to a data view "hits".
 * @author olhsha
 */
public class DefaultDataView extends SupportedDataView{
    
    private final String _defaultDataViewId = CQLConstants.hits;
    private final String _defaultDeliveryPolicy = CQLConstants.send_by_default;
    private final String _defaultDataView = CQLConstants.hits_type;
    
    /**
     * Creates a DefaultDataView instance and setss up the fields
     * "_defaultDataViewId", "_defaultDeliveryPolicy" and "_defaultDataView" to
     * "hits", "send-by-default", and "application/x-clarin-fcs-hits+xml" respectively.
     */
    public DefaultDataView(){
        this.id = _defaultDataViewId;
        this.deliveryPolicy = _defaultDeliveryPolicy;
        this.value = _defaultDataView;
    }
}
