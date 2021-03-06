package gr.athenainnovation.imis.fusion.gis.core;

//import com.google.common.base.Optional;
import static com.google.common.base.Preconditions.*;
import com.google.common.collect.Maps;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;
import gr.athenainnovation.imis.fusion.gis.cli.FusionGISCLI;
//import static gr.athenainnovation.imis.fusion.gis.cli.FusionGISCLI.getFAGIState;
//import static gr.athenainnovation.imis.fusion.gis.cli.FusionGISCLI.st;
import gr.athenainnovation.imis.fusion.gis.gui.workers.DBConfig;
import gr.athenainnovation.imis.fusion.gis.gui.workers.Dataset;
import gr.athenainnovation.imis.fusion.gis.gui.workers.GraphConfig;
import gr.athenainnovation.imis.fusion.gis.gui.workers.ImporterWorker;
import gr.athenainnovation.imis.fusion.gis.postgis.PostGISImporter;
import static gr.athenainnovation.imis.fusion.gis.postgis.PostGISImporter.DATASET_A;
import static gr.athenainnovation.imis.fusion.gis.postgis.PostGISImporter.DATASET_B;
import gr.athenainnovation.imis.fusion.gis.utils.Constants;
import gr.athenainnovation.imis.fusion.gis.utils.Utilities;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import org.apache.jena.atlas.web.auth.HttpAuthenticator;
import org.apache.jena.atlas.web.auth.SimpleAuthenticator;
import org.apache.log4j.Logger;
import virtuoso.jdbc4.VirtuosoConnection;
import virtuoso.jdbc4.VirtuosoPreparedStatement;
import virtuoso.jdbc4.VirtuosoResultSet;
import virtuoso.jena.driver.VirtGraph;

/**
 * Provides the infrastructure for the export of metadata and geometric triples from a dataset with a given SPARQL endpoint and 
 * their loading into a PostGIS db using an instance of {@link PostGISImporter}.
 */

public class Importer {
    private static final Logger LOG = Logger.getLogger(Importer.class);
    
    private final ImporterWorker callback;
    private final PostGISImporter postGISImporter;
    
    private float elapsedTime = 0f;
    private int importedTripletsCount = 0;
    
    private boolean initialized = false;
    
    private GraphConfig grConf;
    
    public float getElapsedTime() {
        return elapsedTime;
    }

    public void setElapsedTime(float elapTime) {
        this.elapsedTime = elapTime;
    }

    public int getImportedTripletsCount() {
        return importedTripletsCount;
    }

    public void setImportedTripletsCount(int impTriplets) {
        this.importedTripletsCount = impTriplets;
    }
    
    public void addImportedTriplets(int addTriplets) {
        this.importedTripletsCount += addTriplets;
    }
    
    /**
     * Constructs a new instance of the importer with the given {@link PostGISImporter}.
     * @param dBConfig database configuration
     * @param callback callback
     * @param grConf graph confguration
     * @throws SQLException 
     * @throws RuntimeException in case of an unrecoverable error. The cause of the error will be encapsulated by the thrown RuntimeException
     */
    public Importer(final DBConfig dBConfig, final ImporterWorker callback, final GraphConfig grConf) {
        this.callback = callback;
        this.postGISImporter = new PostGISImporter(dBConfig);
        
        this.setInitialized(this.postGISImporter.isInitialized());
        
        this.grConf = grConf;
    }
    
    public Importer(final DBConfig dBConfig, final GraphConfig grConf) throws SQLException {
        this.callback = null;
        this.postGISImporter = new PostGISImporter(dBConfig);
        
        this.setInitialized(this.postGISImporter.isInitialized());

        this.grConf = grConf;
    }
    
    /**
     * This method fetches all triples with a subject matching parameter subjectRegex and a predicate not matching {@link Importer#HAS_GEOMETRY_REGEX}.
     * Those triples are then imported into a PostGIS database using an instance of {@link PostGISImporter}.
     * 
     * @param datasetIdent {@link PostGISImporter#DATASET_A} for dataset A or {@link PostGISImporter#DATASET_B} for dataset B
     * @param sourceDataset source dataset from which to extract triples
     * @throws SQLException 
     * @throws RuntimeException in case of an unrecoverable error. The cause of the error will be encapsulated by the thrown RuntimeException
     */
    /*
    public void importMetadata(final int datasetIdent, final Dataset sourceDataset) throws SQLException {
        checkArgument(datasetIdent == DATASET_A || datasetIdent == DATASET_B, "Illegal dataset code: " + datasetIdent);
        final String sourceEndpoint = sourceDataset.getEndpoint();
        final String sourceGraph = sourceDataset.getGraph();
        final String subjectRegex = sourceDataset.getSubjectRegex();
        checkIfValidRegexArgument(subjectRegex);
        
        final String restriction = "?s ?p ?o FILTER (regex(?s, \"" + subjectRegex + "\", \"i\")) FILTER (!regex(?p, \"" + HAS_GEOMETRY_REGEX +"\", \"i\"))"; //put wkt filter jan16
        final String queryString = "SELECT ?s ?p ?o WHERE { " + restriction + " }";
        
        final int totalCount = queryExpectedResultSetSize(sourceEndpoint, sourceGraph, restriction);
        int currentCount = 1;
        
        QueryExecution queryExecution = null;
        
        try {
            postGISImporter.loadInfo(datasetIdent, sourceEndpoint, sourceGraph);
            
            final Query query = QueryFactory.create(queryString);
            queryExecution = QueryExecutionFactory.sparqlService(sourceEndpoint, query, sourceGraph);
            
            final ResultSet resultSet = queryExecution.execSelect();
            
            while(resultSet.hasNext()) {
                final QuerySolution querySolution = resultSet.next();
                
                final String subject = querySolution.getResource("?s").toString();
                final String predicate = querySolution.getResource("?p").getURI();
                final String object;
                final Optional<String>  objectLang;
                final Optional<String> objectDatatype;
                final RDFNode objectNode = querySolution.get("?o");
                if(objectNode.isResource()) {
                    if(objectNode.isAnon()) {
                        //Ignore this triple
                    }
                    else {
                        object = objectNode.asResource().getURI();
                        objectLang = Optional.absent();
                        objectDatatype = Optional.absent();
                        postGISImporter.loadMetadata(datasetIdent, subject, predicate, object, objectLang, objectDatatype);
                    }
                }
                else {
                    object = objectNode.asLiteral().getLexicalForm();
                    objectLang = Optional.fromNullable(objectNode.asLiteral().getLanguage());
                    objectDatatype = Optional.fromNullable(objectNode.asLiteral().getDatatypeURI());
                    postGISImporter.loadMetadata(datasetIdent, subject, predicate, object, objectLang, objectDatatype);
                }
                
                callback.publishMetadataProgress((int) (0.5 + (100 * (double) currentCount++ / (double) totalCount)));
            }
        }
        catch (SQLException | RuntimeException ex) {
            LOG.error(ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
        finally {
            if(queryExecution != null) {
                queryExecution.close();
            }
        }
    }*/
    
    /**
     * This method fetches all triples with a subject matching parameter subjectRegex and a two triple chain with a predicate matching {@link Importer#HAS_GEOMETRY_REGEX} in the first triple and
     * {@link Importer#AS_WKT_REGEX} in the second triple.
     * Those triples are then imported into a PostGIS database using an instance of {@link PostGISImporter}.
     * 
     * @param datasetIdent {@link PostGISImporter#DATASET_A} for dataset A or {@link PostGISImporter#DATASET_B} for dataset B
     * @param sourceDataset source dataset from which to extract triples
     * @return success
     */
    public HashMap<String, String> importGeometries(final int datasetIdent, final Dataset sourceDataset) {
        boolean success = true;
        final HashMap<String, String> geomEntries = new HashMap();
        final HashMap<String, String> geomTypes = Maps.newHashMap();
        
        checkArgument(datasetIdent == DATASET_A || datasetIdent == DATASET_B, "Illegal dataset code: " + datasetIdent);
        
        final String sourceEndpoint = sourceDataset.getEndpoint();
        final String sourceGraph = sourceDataset.getGraph();
        final String subjectRegex = sourceDataset.getSubjectRegex();
        
        long endTime, startTime;
        
        checkIfValidRegexArgument(subjectRegex);    
        
        VirtGraph vSet = new VirtGraph ("jdbc:virtuoso://" + callback.getDbConfig().getDBURL() + "/CHARSET=UTF-8", callback.getDbConfig().getUsername(), callback.getDbConfig().getPassword());
        VirtuosoConnection virt_conn = (VirtuosoConnection)vSet.getConnection();
        VirtuosoPreparedStatement virt_stmt;
        //check the serialisation of the dataset with a count query. If it doesn t find WKT the serialisation, the serialisation is wgs84        
        
        //wgs84
        final String restrictionForWgs = 
                "?s ?p1 ?o1 . ?s ?p2 ?o2 "
                + "FILTER(regex(?p1, \"" + Constants.LAT_REGEX + "\", \"i\")) "
                + "FILTER(regex(?p2, \"" + Constants.LONG_REGEX + "\", \"i\"))";
        
        final String restrictionForWKT = "?os ?p1 _:a . _:a <"+Constants.AS_WKT_REGEX+"> ?g ";
        
        // Should we use the SERVICE keyword?
        boolean isEndpointLocal = Utilities.isURLToLocalInstance(sourceEndpoint);
       
            final String queryWGS;
        if (datasetIdent == DATASET_A) {
            if (isEndpointLocal) {
                queryWGS = "SELECT ?s ?o1 ?o2 "
                        + "WHERE { "
                        + "GRAPH <" + this.grConf.getLinksGraph() + "> {?s ?lp ?os} . "
                        + "GRAPH <" + sourceGraph + "> {" + restrictionForWgs + "} }";
            } else {
                queryWGS = "SELECT ?s ?o1 ?o2 "
                        + "WHERE { "
                        + "GRAPH <" + this.grConf.getLinksGraph() + "> {?s ?lp ?os} . "
                        + "SERVICE <" + sourceEndpoint + "> "
                        + "{ GRAPH <" + sourceGraph + "> {" + restrictionForWgs + "}"
                        + " } }";
            }
        } else {
            if (isEndpointLocal) {
                queryWGS = "SELECT ?s ?o1 ?o2 "
                        + "WHERE { "
                        + "GRAPH <" + this.grConf.getLinksGraph() + "> {?os ?lp ?s} . "
                        + "GRAPH <" + sourceGraph + "> {" + restrictionForWgs + "} }";
            } else {
                queryWGS = "SELECT ?s ?o1 ?o2 "
                        + "WHERE { "
                        + "GRAPH <" + this.grConf.getLinksGraph() + "> {?os ?lp ?s} . "
                        + "SERVICE <" + sourceEndpoint + "> "
                        + "{ GRAPH <" + sourceGraph + "> {" + restrictionForWgs + "}"
                        + " } }";
            }
        }
        
        boolean countWgs = checkForWGS(sourceEndpoint, sourceGraph, restrictionForWgs, "?s");       
        boolean countWKT = checkForWKT(sourceEndpoint, sourceGraph, restrictionForWKT, "?os");
        
        //int countWKT = 1;
        //int countWgs = 0;
        
        final String queryWKT;
        if (Constants.LATE_FETCH) {
            if (datasetIdent == DATASET_A) {
                if (isEndpointLocal) {
                    queryWKT = "SELECT ?os ?g WHERE { GRAPH <" + this.grConf.getLinksGraph() + "> {?os ?lp ?s} . GRAPH <" + sourceGraph + "> {" + restrictionForWKT + " } }";
                } else {
                    queryWKT = "SELECT ?os ?g WHERE { GRAPH <" + this.grConf.getLinksGraph() + "> {?os ?lp ?s} . SERVICE <" + sourceEndpoint + "> { GRAPH <" + sourceGraph + "> {" + restrictionForWKT + "} } }";
                }
            } else {
                if (isEndpointLocal) {
                    queryWKT = "SELECT ?os ?g WHERE { GRAPH <" + this.grConf.getLinksGraph() + "> {?s ?lp ?os} . GRAPH <" + sourceGraph + "> {" + restrictionForWKT + " } }";
                } else {
                    queryWKT = "SELECT ?os ?g WHERE { GRAPH <" + this.grConf.getLinksGraph() + "> {?s ?lp ?os} . SERVICE <" + sourceEndpoint + "> { GRAPH <" + sourceGraph + "> {" + restrictionForWKT + "} } }";
                }
            }
        } else {
            if (datasetIdent == DATASET_A) {
                if (isEndpointLocal) {
                    queryWKT = "SELECT ?os ?g WHERE { GRAPH <" + this.grConf.getAllLinksGraph() + "> {?os ?lp ?s} . GRAPH <" + sourceGraph + "> {" + restrictionForWKT + " } }";
                } else {
                    queryWKT = "SELECT ?os ?g WHERE { GRAPH <" + this.grConf.getAllLinksGraph() + "> {?os ?lp ?s} . SERVICE <" + sourceEndpoint + "> { GRAPH <" + sourceGraph + "> {" + restrictionForWKT + "} } }";
                }
            } else {
                if (isEndpointLocal) {
                    queryWKT = "SELECT ?os ?g WHERE { GRAPH <" + this.grConf.getAllLinksGraph() + "> {?s ?lp ?os} . GRAPH <" + sourceGraph + "> {" + restrictionForWKT + " } }";
                } else {
                    queryWKT = "SELECT ?os ?g WHERE { GRAPH <" + this.grConf.getAllLinksGraph() + "> {?s ?lp ?os} . SERVICE <" + sourceEndpoint + "> { GRAPH <" + sourceGraph + "> {" + restrictionForWKT + "} } }";
                }
            }
        }

        QueryExecution queryExecution = null;
        if ( countWgs ) { //if geosparql geometry doesn' t exist     
            System.out.println("\n\n\n\n\nSIZE " + " " + Constants.GEOM_TYPE_PRECEDENCE_TABLE.size() +"\n\n\n\n\n");
            try {
                //System.out.println("Query String "+queryString);
                System.out.println("WHAT????? -1");
                final int pointPrec = Constants.GEOM_TYPE_PRECEDENCE_TABLE.get("POINT");
                System.out.println("WHAT????? " + pointPrec);
                virt_stmt = (VirtuosoPreparedStatement)virt_conn.prepareStatement("SPARQL " + queryWGS);
                VirtuosoResultSet rs = (VirtuosoResultSet) virt_stmt.executeQuery();
                System.out.println("WHAT????? 1");
                startTime = System.nanoTime();
                System.out.println("WHAT????? 2");
                while(rs.next()) {
                System.out.println("WHAT????? 10");
                    final String subject = rs.getString(1);     
                System.out.println("WHAT????? 11");
                    // Only support one geometry per link
                    String prevType = geomTypes.get(subject);
                    if ( prevType == null )
                        prevType = "NONE";
                    System.out.println("THE OLD TYPE " + prevType + " " + Constants.GEOM_TYPE_PRECEDENCE_TABLE.get(prevType));
                    
                    if ( Constants.GEOM_TYPE_PRECEDENCE_TABLE.get(prevType) 
                            < pointPrec )
                        continue;
                    
                    final double latitude = Double.parseDouble(rs.getString(2));
                    final double longitude = Double.parseDouble(rs.getString(3));
                    final String geometry = "POINT ("+ longitude + " " + latitude +")";
                    //System.out.println("\n\n\n\n "+ geometry +" \n\n\n\n");
                    System.out.println("THE NEW TYPE " + geometry + " " + Constants.GEOM_TYPE_PRECEDENCE_TABLE.get(prevType));
                    geomEntries.put(subject, geometry);
                    geomTypes.put(subject, "POINT");
                    //success = postGISImporter.loadGeometry(datasetIdent, subject, geometry);

                }
                
                rs.close();
                virt_stmt.close();
               
                //success = postGISImporter.finishUpdates();
                //System.out.println("Count : "+currentCount);
                endTime = System.nanoTime();
                
                //postGISImporter.finishUpdates();
                
                LOG.info("Loading WGS lasted "+Utilities.nanoToSeconds(endTime-startTime));
            }
            catch (SQLException ex) {
                LOG.trace("SQLException thrown during WGS geometry update");
                LOG.debug("SQLException thrown during WGS geometry update : \n" + ex.getMessage());
                LOG.debug("SQLException thrown during WGS geometry update : \n" + ex.getSQLState());

                SQLException exception = (SQLException) ex;
                while(exception != null) {
                    LOG.trace("SQLException expanded (see DEBUG) ");
                    LOG.debug("SQLException expanded : \n" + exception.getMessage());
                    LOG.debug("SQLException expanded : \n" + exception.getSQLState());
                }

                success = false;
            }
        }
        
        if ( countWKT ) { //if geosparql geometry exists
            //System.out.println("geosparql");
            try {
                virt_stmt = (VirtuosoPreparedStatement)virt_conn.prepareStatement("SPARQL " + queryWKT);
                VirtuosoResultSet rs = (VirtuosoResultSet) virt_stmt.executeQuery();
                
                startTime =  System.nanoTime();
                
                while(rs.next()) {
                    
                    final String subject = rs.getString(1);                        
                    final String geometry = rs.getString(2);
                    //System.out.println("\n\n\n\n "+ geometry +" \n\n\n\n");
                    // Only support one geometry per link
                    String prevType = geomTypes.get(subject);
                    if ( prevType == null )
                        prevType = "NONE";
                    String newType = geometry.substring(0, geometry.indexOf("("));
                    System.out.println("THE NEW TYPE " + newType + " " + Constants.GEOM_TYPE_PRECEDENCE_TABLE.get(newType));
                    System.out.println("THE OLD TYPE " + prevType + " " + Constants.GEOM_TYPE_PRECEDENCE_TABLE.get(prevType));
                    if ( Constants.GEOM_TYPE_PRECEDENCE_TABLE.get(prevType) 
                            < Constants.GEOM_TYPE_PRECEDENCE_TABLE.get(newType) )
                        continue;
                    
                    geomTypes.put(subject, newType);
                    geomEntries.put(subject, geometry);
                    //success = postGISImporter.loadGeometry(datasetIdent, subject, geometry);

                }
                
                rs.close();
                virt_stmt.close();
               
                //success = postGISImporter.finishUpdates();
                //System.out.println("Count : "+currentCount);
                endTime = System.nanoTime();
                  
                LOG.info("Loading WKT lasted "+Utilities.nanoToSeconds(endTime-startTime));

            }
            catch (SQLException ex) {
                LOG.trace("SQLException thrown during WKT geometry update");
                LOG.debug("SQLException thrown during WKT geometry update : \n" + ex.getMessage());
                LOG.debug("SQLException thrown during WKT geometry update : \n" + ex.getSQLState());

                SQLException exception = (SQLException) ex;
                while(exception != null) {
                    LOG.trace("SQLException expanded (see DEBUG) ");
                    LOG.debug("SQLException expanded : \n" + exception.getMessage());
                    LOG.debug("SQLException expanded : \n" + exception.getSQLState());
                }

                success = false;
            }
        }    

        for ( Map.Entry<String, String> entry : geomEntries.entrySet() ) {
            String subject = entry.getKey();
            String geometry = entry.getValue();
            System.out.println("\n\n\n\n "+ subject +" \n\n\n\n");
            System.out.println("\n\n\n\n "+ geometry +" \n\n\n\n");
            success = postGISImporter.loadGeometry(datasetIdent, subject, geometry);
        }
        
        postGISImporter.finishUpdates();

        addImportedTriplets(1 + 1);
        
        if (success == false)
            return null;
        
        return geomEntries;
    }
    
    /**
     * Clean-up. Close held resources.
     * @return success
     */
    public boolean clean() {
        if(postGISImporter != null) {
            return postGISImporter.clean();
        }
        return false;
    }
    
    /**
     * Execute a SELECT COUNT to get the expected size of the result set for the purposes of calculating a progress percentage.
     * @param sourceEndpoint endpoint URL
     * @param sourceGraph graph URI
     * @param restriction SPARQL restriction to use in the SELECT COUNT query
     * @return number of times SPARQL restriction has been matched in the graph
     */
    private int queryExpectedResultSetSize(final String sourceEndpoint, final String sourceGraph, final String restriction) {
        int count = -1;
        
        final String queryString = "SELECT (COUNT (?s) as ?count) WHERE { " + restriction + " }";
        QueryExecution queryExecution = null;
        
        try {
            final Query query = QueryFactory.create(queryString);
            HttpAuthenticator authenticator = new SimpleAuthenticator("dba", "dba".toCharArray());
            //queryExecution = QueryExecutionFactory.sparqlService(sourceEndpoint, query, sourceGraph, authenticator);
            QueryEngineHTTP qeh = QueryExecutionFactory.createServiceRequest(sourceEndpoint, query, authenticator);
            qeh.addDefaultGraph(sourceGraph);
            //QueryExecution queryExecution = qeh;
            qeh.setSelectContentType(QueryEngineHTTP.supportedSelectContentTypes[3]);
            final ResultSet resultSet = qeh.execSelect();
            //final ResultSet resultSet = queryExecution.execSelect();

            while (resultSet.hasNext()) {
                final QuerySolution querySolution = resultSet.next();

                count = querySolution.getLiteral("?count").getInt();
            }
        } catch (RuntimeException ex) {
            LOG.warn(ex.getMessage(), ex);
        } finally {
            if (queryExecution != null) {
                queryExecution.close();
            }
        }
        
        return count;
    }
    
    private boolean checkForWGS(final String sourceEndpoint, final String sourceGraph, final String restriction, final String sub) {
        boolean result = false;
        
        final String queryString = "ASK { ?s <"+Constants.LAT_REGEX+"> ?o1 . ?s <"+Constants.LONG_REGEX+"> ?o2 }";
        QueryEngineHTTP qeh = null;
        try {
            final Query query = QueryFactory.create(queryString);
            HttpAuthenticator authenticator = new SimpleAuthenticator("dba", "dba".toCharArray());
            //queryExecution = QueryExecutionFactory.sparqlService(sourceEndpoint, query, sourceGraph, authenticator);

            qeh = QueryExecutionFactory.createServiceRequest(sourceEndpoint, query, authenticator);
            qeh.addDefaultGraph(sourceGraph);
            //QueryExecution queryExecution = qeh;
            qeh.setSelectContentType(QueryEngineHTTP.supportedAskContentTypes[3]);
            boolean rs = qeh.execAsk();

            System.out.println("Has WGS ------- " + rs);
            return rs;
        }
        catch (RuntimeException ex) {
            LOG.warn(ex.getMessage(), ex);
        }
        finally {
            if(qeh != null) {
                qeh.close();
            }
        }
        return result;
    }
    
    private boolean checkForWKT(final String sourceEndpoint, final String sourceGraph, final String restriction, final String sub) {
        boolean result = false;
        
        final String queryString = "ASK WHERE { ?s <"+Constants.AS_WKT_REGEX+"> ?g }";
        //final String queryString = "SELECT ?os WHERE { ?os ?p1 _:a . _:a <http://www.opengis.net/ont/geosparql#asWKT> ?g } LIMIT 1";
        QueryEngineHTTP qeh = null;
        try {
            final Query query = QueryFactory.create(queryString);
            HttpAuthenticator authenticator = new SimpleAuthenticator("dba", "dba".toCharArray());
            //queryExecution = QueryExecutionFactory.sparqlService(sourceEndpoint, query, sourceGraph, authenticator);
            System.out.println("source endpoint: " + sourceEndpoint + " query: " + query + "sourceGraph: " + sourceGraph);

            qeh = QueryExecutionFactory.createServiceRequest(sourceEndpoint, query, authenticator);
            qeh.addDefaultGraph(sourceGraph);
            //QueryExecution queryExecution = qeh;
            qeh.setSelectContentType(QueryEngineHTTP.supportedAskContentTypes[3]);
            boolean rs = qeh.execAsk();

            System.out.println("Has WKT ------- " + rs);
            return rs;
        } catch (RuntimeException ex) {
            LOG.warn(ex.getMessage(), ex);
        }
        finally {
            if(qeh != null) {
                qeh.close();
            }
        }
        return result;
    }
    
    private int checkForSerialisation(final String sourceEndpoint, final String sourceGraph, final String restriction) {
        int count = -1;
        
        final String queryString = "SELECT (COUNT (?os) as ?count) WHERE { " + restriction + " }";
        //System.out.println("Query "+queryString);
        QueryExecution queryExecution = null;
        try {
            final Query query = QueryFactory.create(queryString);
            HttpAuthenticator authenticator = new SimpleAuthenticator("dba", "dba".toCharArray());
            queryExecution = QueryExecutionFactory.sparqlService(sourceEndpoint, query, sourceGraph, authenticator);
            //System.out.println("source endpoint: " +sourceEndpoint +" query: "+ query + "sourceGraph: " + sourceGraph);

            final ResultSet resultSet = queryExecution.execSelect();
            while(resultSet.hasNext()) {
                final QuerySolution querySolution = resultSet.next();                
                count = querySolution.getLiteral("?count").getInt();
            }
        }
        catch (RuntimeException ex) {
            LOG.warn(ex.getMessage(), ex);
        }
        finally {
            if(queryExecution != null) {
                queryExecution.close();
            }
        }
        
        return count;
    }
    
    private void checkIfValidRegexArgument(final String regex) {
        String localRegex = regex;
        
        while(!localRegex.isEmpty() && localRegex.indexOf('"') != -1) {
            if(localRegex.indexOf('"') == 0 || localRegex.charAt(localRegex.indexOf('"') - 1) != '\'') {
                throw new IllegalArgumentException("Regex cannot contain unescaped double quotes.");
            }
            else {
                localRegex = localRegex.substring(localRegex.indexOf('"'));
            }
        }
    }

    public final boolean isInitialized() {
        return initialized;
    }

    public final void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public GraphConfig getGrConf() {
        return grConf;
    }

    public void setGrConf(GraphConfig grConf) {
        this.grConf = grConf;
    }
    
}
