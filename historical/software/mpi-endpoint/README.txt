CQL Search

The CQL-search servlet is a part of the CLARIN Federated Search Project. 
The information about the project can be found at 
http://www.clarin.eu/fcs .

The servelt provides  a (simple) SRU/CQL-based interface to the outside world 
which can be used  to search through the Trova Database.

=== SRU/CQL Requests ===

The servlet is implemented to perform SRU/CQL requests according to the sandart
that can be found at http://www.loc.gov/standards/sru/specs/search-retrieve.html.
Typical search request look as follows.

For corpus1:

cqlservlet.mpi.nl/?operation=searchRetrieve&query=kind&version=1.1
cqlservlet.mpi.nl/?operation=searchRetrieve&query=kind&version=1.1&maximumRecords=19
cqlservlet.mpi.nl/?operation=scan&scanClause=fcs.resource=root&version=1.1
cqlservlet.mpi.nl/?operation=scan&scanClause=fcs.resource=root&version=1.1&x-clarin-resource-info=true

For lux16, lux17, replace "cqlservlet.mpi.nl" with "lux16.mpi.nl:8080/ds/cqlsearch2" 
and "lux17.mpi.nl:8080/ds/cqlsearch" respectively

The servlet is still under development: for instance, complex logical CRU requests 
built up using AND and OR still have to be implemented.

=== Logging in ===

So far no login procedure is assumed. An external user gets standard default name 
that allows to search for terms in the corpora (within Trova Database) that MPI 
keeps open for Federated Search. 

=== License ===

GNU GPL