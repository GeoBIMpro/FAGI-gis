package gr.athenainnovation.imis.fusion.gis.virtuoso;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hp.hpl.jena.graph.BulkUpdateHandler;
import com.hp.hpl.jena.query.ParameterizedSparqlString;
import com.hp.hpl.jena.shared.JenaException;
import com.hp.hpl.jena.update.UpdateExecutionFactory;
import com.hp.hpl.jena.update.UpdateFactory;
import com.hp.hpl.jena.update.UpdateProcessor;
import com.hp.hpl.jena.update.UpdateRequest;
import gr.athenainnovation.imis.fusion.gis.core.Link;
import gr.athenainnovation.imis.fusion.gis.gui.workers.DBConfig;
import static gr.athenainnovation.imis.fusion.gis.gui.workers.FusionState.ANSI_RESET;
import static gr.athenainnovation.imis.fusion.gis.gui.workers.FusionState.ANSI_YELLOW;
import gr.athenainnovation.imis.fusion.gis.gui.workers.GraphConfig;
import gr.athenainnovation.imis.fusion.gis.utils.Constants;
import gr.athenainnovation.imis.fusion.gis.utils.Log;
import gr.athenainnovation.imis.fusion.gis.utils.Patterns;
import gr.athenainnovation.imis.fusion.gis.utils.Utilities;
import static gr.athenainnovation.imis.fusion.gis.utils.Utilities.isURLToLocalInstance;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.didion.jwnl.JWNL;
import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.IndexWord;
import net.didion.jwnl.data.IndexWordSet;
import net.didion.jwnl.data.PointerType;
import net.didion.jwnl.data.Synset;
import net.didion.jwnl.data.Word;
import net.didion.jwnl.data.relationship.AsymmetricRelationship;
import net.didion.jwnl.data.relationship.Relationship;
import net.didion.jwnl.data.relationship.RelationshipFinder;
import net.didion.jwnl.data.relationship.RelationshipList;
import net.didion.jwnl.dictionary.Dictionary;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.jena.atlas.web.auth.HttpAuthenticator;
import org.apache.jena.atlas.web.auth.SimpleAuthenticator;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.util.Version;
import virtuoso.jdbc4.VirtuosoConnection;
import virtuoso.jdbc4.VirtuosoException;
import virtuoso.jdbc4.VirtuosoPreparedStatement;
import virtuoso.jena.driver.VirtGraph;
import virtuoso.jena.driver.VirtuosoUpdateFactory;
import virtuoso.jena.driver.VirtuosoUpdateRequest;

/**
 * Forms appropriate triples from the PostGIS database and inserts them in the
 * virtuoso specified graph.
 *
 */
public final class VirtuosoImporter {

    private static final Logger LOG = Log.getClassFAGILogger(VirtuosoImporter.class);

    
    private final TripleHandler trh;

    private String transformationID;

    private final Connection                        virt_conn;
    private final VirtGraph                          set;
    
    private final DBConfig                           db_c;
    private final GraphConfig                        gr_c;

    private int                                     wordnetDepth;
    private int                                     maxParentDepth;

    private double                                  raiseToPower;
    private double                                  wordWeight;
    private double                                  textWeight;
    private double                                  typeWeight;
    private double                                  simThreshold;

    // Ωιρτθοσο Ιμπορτερ Στατε
    private boolean                                 initialized = false;

    // Used during property matching
    private HashMap< String, MetadataChain>         propertiesA;
    private HashMap< String, MetadataChain>         propertiesB;
    private HashMap<String, HashSet<ScoredMatch>>   foundA = new HashMap<>();
    private HashMap<String, HashSet<ScoredMatch>>   foundB = new HashMap<>();
    private HashSet<String>                         recoveredWords = null;
    private HashSet<String>                         uniquePropertiesA = new HashSet<>();
    private HashSet<String>                         uniquePropertiesB = new HashSet<>();
    private HashSet<String>                         nonMatchedPropertiesA = new HashSet<>();
    private HashSet<String>                         nonMatchedPropertiesB = new HashSet<>();

    public VirtuosoImporter(final DBConfig dbConfig, String transformationID, final String fusedGraph, final boolean checkboxIsSelected, final GraphConfig graphConfig) {
        db_c = dbConfig;
        gr_c = graphConfig;

        this.transformationID = transformationID;

        propertiesA = Maps.newHashMap();
        propertiesB = Maps.newHashMap();

        set = getVirtuosoSet(fusedGraph, dbConfig.getDBURL(), dbConfig.getUsername(), dbConfig.getPassword());
        virt_conn = set.getConnection();

        try {
            Scanner scn = null;
            if (SystemUtils.IS_OS_MAC_OSX) {
                scn = new Scanner(new File("/home/nick/Projects/fagi-gis.ini"));
            }
            if (SystemUtils.IS_OS_LINUX) {
                scn = new Scanner(new File("/home/nick/Projects/fagi-gis.ini"));
            }
            if (SystemUtils.IS_OS_WINDOWS) {
                scn = new Scanner(new File("/home/nick/Projects/fagi-gis.ini"));
            }
            while (scn.hasNext()) {
                String prop = scn.next();
                String[] vals = prop.split(":");
                vals[1].trim();
                vals[0].trim();
                //System.out.println(vals[0] + " " + vals[1]);
                if (vals[0].equals("wordnet-depth")) {
                    wordnetDepth = Integer.parseInt(vals[1]);
                } else if (vals[0].equals("max-parent")) {
                    maxParentDepth = Integer.parseInt(vals[1]);
                } else if (vals[0].equals("raise-to-power")) {
                    raiseToPower = Integer.parseInt(vals[1]);
                } else if (vals[0].equals("text-match-weight")) {
                    textWeight = Double.parseDouble(vals[1]);
                } else if (vals[0].equals("word-match-weight")) {
                    wordWeight = Double.parseDouble(vals[1]);
                } else if (vals[0].equals("type-match-weight")) {
                    typeWeight = Double.parseDouble(vals[1]);
                } else if (vals[0].equals("sim-threshold")) {
                    simThreshold = Double.parseDouble(vals[1]);
                }
            }
        } catch (IOException ioe) {
            //System.out.println("File not found");
            wordnetDepth = 5;
            maxParentDepth = 4;
            raiseToPower = 1.0;
            wordWeight = 1.0f;
            textWeight = 1.0f;
            typeWeight = 1.0f;
            simThreshold = 1.0f;
        }

        trh = new BulkLoader(graphConfig, fusedGraph, dbConfig.getDBName(), set, graphConfig.getEndpointT());
        //trh = new BulkLoader(graphConfig, gr_c.getTargetTempGraph(), dbConfig.getDBName(), set, graphConfig.getEndpointT());
        trh.init();

    }

    public String getTransformationID() {
        return transformationID;
    }

    public void setTransformationID(String transformationID) {
        this.transformationID = transformationID;
    }

    public void tempFunc() throws SQLException {
        final String askWik = "sparql with <http://localhost:8890/wik> select distinct(?p) where { ?s ?p ?o } ";
        final String askUni = "sparql with <http://localhost:8890/uni> select distinct(?p) where { ?s ?p ?o } ";
        final String askOsm = "sparql with <http://localhost:8890/osm> select distinct(?p) where { ?s ?p ?o } ";

        PrintWriter wOsm = null;
        PrintWriter wUni = null;
        PrintWriter wWik = null;
        HashMap<String, List<String>> hs = new HashMap<>();
        try {
            //create a temporary file
            File oOsm = new File("osm_predicates.txt");
            File oWik = new File("wik_predicates.txt");
            File oUni = new File("uni_predicates.txt");

            // This will output the full path where the file will be written to...
            //System.out.println(oOsm.getCanonicalPath());
            wOsm = new PrintWriter(new FileWriter(oOsm));
            wUni = new PrintWriter(new FileWriter(oUni));
            wWik = new PrintWriter(new FileWriter(oWik));

            PreparedStatement tempStmt;
            ResultSet rs;

            tempStmt = virt_conn.prepareStatement(askWik);
            rs = tempStmt.executeQuery();
            PrintWriter wRef = wWik;

            while (rs.next()) {
                String s = rs.getString(1);
                //System.out.println(s);
                s = URLDecoder.decode(s, "UTF-8");
                //System.out.println(s);

                String main = StringUtils.substringAfter(s, "#");
                if (main.equals("")) {
                    main = StringUtils.substring(s, StringUtils.lastIndexOf(s, "/") + 1);
                }

                wRef.print(s + " -> " + main);

                Matcher mat = Patterns.PATTERN_WORD_BREAKER.matcher(main);
                List<String> toks = new ArrayList<String>();
                while (mat.find()) {
                    toks.add(mat.group());
                }

                if (toks.isEmpty()) {
                    wRef.println();
                    continue;
                }

                wRef.print(" : [ ");
                for (int i = 0; i < toks.size() - 1; i++) {
                    if (!hs.containsKey(toks.get(i))) {
                        List<String> lst = new ArrayList<>();
                        lst.add(toks.get(i));

                        hs.put(toks.get(i), lst);
                    } else {
                        boolean found = false;
                        for (String ret : hs.get(toks.get(i))) {
                            if (ret.equals(toks.get(i))) {
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            hs.get(toks.get(i)).add(toks.get(i));
                        }
                    }
                    wRef.print(toks.get(i) + ", ");
                }
                wRef.println(toks.get(toks.size() - 1) + " ]");

                if (toks.get(toks.size() - 1).equals("SIREN")) //System.out.println(toks.get(toks.size()-1));
                {
                    if (!hs.containsKey(toks.get(toks.size() - 1))) {
                        List<String> lst = new ArrayList<>();
                        lst.add(toks.get(toks.size() - 1));

                        hs.put(toks.get(toks.size() - 1), lst);
                    } else {
                        boolean found = false;
                        for (String ret : hs.get(toks.get(toks.size() - 1))) {
                            if (ret.equals(toks.get(toks.size() - 1))) {
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            hs.get(toks.get(toks.size() - 1)).add(toks.get(toks.size() - 1));
                        }
                    }
                }
            }

            tempStmt.close();
            rs.close();

            tempStmt = virt_conn.prepareStatement(askOsm);
            rs = tempStmt.executeQuery();
            wRef = wOsm;

            while (rs.next()) {
                String s = rs.getString(1);
                //System.out.println(s);
                s = URLDecoder.decode(s, "UTF-8");
                //System.out.println(s);

                String main = StringUtils.substringAfter(s, "#");
                if (main.equals("")) {
                    main = StringUtils.substring(s, StringUtils.lastIndexOf(s, "/") + 1);
                }

                wRef.print(s + " -> " + main);

                Matcher mat = Patterns.PATTERN_WORD_BREAKER.matcher(main);
                List<String> toks = new ArrayList<String>();
                while (mat.find()) {
                    toks.add(mat.group());
                }

                if (toks.isEmpty()) {
                    wRef.println();
                    continue;
                }

                wRef.print(" : [ ");
                for (int i = 0; i < toks.size() - 1; i++) {
                    if (!hs.containsKey(toks.get(i))) {
                        List<String> lst = new ArrayList<>();
                        lst.add(toks.get(i));

                        hs.put(toks.get(i), lst);
                    } else {
                        boolean found = false;
                        for (String ret : hs.get(toks.get(i))) {
                            if (ret.equals(toks.get(i))) {
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            hs.get(toks.get(i)).add(toks.get(i));
                        }
                    }
                    wRef.print(toks.get(i) + ", ");
                }
                wRef.println(toks.get(toks.size() - 1) + " ]");

                if (!hs.containsKey(toks.get(toks.size() - 1))) {
                    List<String> lst = new ArrayList<>();
                    lst.add(toks.get(toks.size() - 1));

                    hs.put(toks.get(toks.size() - 1), lst);
                } else {
                    boolean found = false;
                    for (String ret : hs.get(toks.get(toks.size() - 1))) {
                        if (ret.equals(toks.get(toks.size() - 1))) {
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        hs.get(toks.get(toks.size() - 1)).add(toks.get(toks.size() - 1));
                    }
                }
            }

            tempStmt.close();
            rs.close();

            tempStmt = virt_conn.prepareStatement(askUni);
            rs = tempStmt.executeQuery();
            wRef = wUni;

            while (rs.next()) {
                String s = rs.getString(1);
                //System.out.println(s);
                s = URLDecoder.decode(s, "UTF-8");
                //System.out.println(s);

                String main = StringUtils.substringAfter(s, "#");
                if (main.equals("")) {
                    main = StringUtils.substring(s, StringUtils.lastIndexOf(s, "/") + 1);
                }

                wRef.print(s + " -> " + main);

                Matcher mat = Patterns.PATTERN_WORD_BREAKER.matcher(main);
                List<String> toks = new ArrayList<String>();
                while (mat.find()) {
                    toks.add(mat.group());
                }

                if (toks.isEmpty()) {
                    wRef.println();
                    continue;
                }

                wRef.print(" : [ ");
                for (int i = 0; i < toks.size() - 1; i++) {
                    if (!hs.containsKey(toks.get(i))) {
                        List<String> lst = new ArrayList<>();
                        lst.add(toks.get(i));

                        hs.put(toks.get(i), lst);
                    } else {
                        boolean found = false;
                        for (String ret : hs.get(toks.get(i))) {
                            if (ret.equals(toks.get(i))) {
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            hs.get(toks.get(i)).add(toks.get(i));
                        }
                    }
                    wRef.print(toks.get(i) + ", ");
                }
                wRef.println(toks.get(toks.size() - 1) + " ]");

                if (!hs.containsKey(toks.get(toks.size() - 1))) {
                    List<String> lst = new ArrayList<>();
                    lst.add(toks.get(toks.size() - 1));

                    hs.put(toks.get(toks.size() - 1), lst);
                } else {
                    boolean found = false;
                    for (String ret : hs.get(toks.get(toks.size() - 1))) {
                        if (ret.equals(toks.get(toks.size() - 1))) {
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        hs.get(toks.get(toks.size() - 1)).add(toks.get(toks.size() - 1));
                    }
                }
            }

            tempStmt.close();
            rs.close();

            File oAll = new File("all_words.txt");

            PrintWriter wAll = new PrintWriter(new FileWriter(oAll));
            for (Map.Entry pairs : hs.entrySet()) {
                List<String> lst = (List<String>) pairs.getValue();
            }

            wAll.close();
            wOsm.close();
            wWik.close();
            wUni.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                // Close the writer regardless of what happens...
                wOsm.close();
                wUni.close();
                wWik.close();
            } catch (Exception e) {
            }
        }
    }

    //inserts the data in virtuoso
    public void importGeometriesToVirtuoso(final String fusedGraph) {   //imports the geometries in the fused graph                     

        Statement stmt = null;
        Connection connection = null;
        //System.out.println("Upload of geometries about to commence");
        try {
            connection = DriverManager.getConnection(Constants.DB_URL + db_c.getDBName(), db_c.getDBUsername(), db_c.getDBPassword());
            //String deleteQuery;
            String subject;
            String fusedGeometry;
            stmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);

            //select from source dataset in postgis. The transformations will take place from A to B dataset.
            String selectFromPostGIS = "SELECT DISTINCT subject_a, subject_b, ST_AsText(geom) FROM fused_geometries";
            try (ResultSet rs = stmt.executeQuery(selectFromPostGIS)) {
                clearBulkLoadHistory();
                System.out.println("Query executed");

                //List<Triple> lst = new ArrayList<>();
                int p = 0;
                //System.out.println("Happens");
                while (rs.next()) {
                    if ( gr_c.isDominantA())
                        subject = rs.getString("subject_a");
                    else
                        subject = rs.getString("subject_b");
                    
                    fusedGeometry = rs.getString("ST_AsText");
                    //System.out.println("Inserting "+subject + " " + fusedGeometry);
                    if (!(transformationID.equals("Keep both"))) {
                        //if transformation is NOT "keep both" -> delete previous geometry

                        trh.deleteAllWgs84(subject);
                        trh.deleteAllGeom(subject);
                        trh.addGeomTriple(subject, fusedGeometry);

                    } else {
                        if (rs.isFirst()) {
                            trh.deleteAllGeom(subject);
                            trh.addGeomTriple(subject, fusedGeometry);
                            //System.out.println("Geom added for "+subject);
                        } else {
                            //insert second geometry
                            //System.out.println("Geom added for "+subject);
                            trh.addGeomTriple(subject, fusedGeometry);
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            //out.close();
            LOG.warn(ex.getMessage(), ex);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ex) {
                    java.util.logging.Logger.getLogger(VirtuosoImporter.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ex) {
                    java.util.logging.Logger.getLogger(VirtuosoImporter.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    //inserts the data in virtuoso
    public void importGeometriesToVirtuoso() { //imports the geometries in the graph A                       
        importGeometriesToVirtuoso(null);
    }

    public static boolean isLocalEndpoint(String url) throws UnknownHostException {
        // Check if the address is a valid special local or loop back
        int start = url.lastIndexOf("//");
        int last = url.substring(start).indexOf(":");
        //System.out.println(start+" "+last);
        String address = url.substring(start + 2, last + start);
        //System.out.println("Address "+address);
        InetAddress addr = InetAddress.getByName(address);
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
            return true;
        }

        // Check if the address is defined on any interface
        try {
            return NetworkInterface.getByInetAddress(addr) != null;
        } catch (SocketException e) {
            return false;
        }
    }

    public boolean insertLinksMetadataChains(List<Link> links, final String fusedGraph, boolean scanProperties, boolean swpped) { //metadata go in the new fusedGraph. the method is called from FuserWorker 
        boolean success = true;

        long starttime, endtime;
        long startTime, endTime;

        // For compatibility with the CLI version
        if (!scanProperties) {
            createLinksGraph(links);
        }

        //createDelWGSGraph(((FileBulkLoader)trh).getDelWGSList());
        //createDelGeomGraph(((FileBulkLoader)trh).getDelGeomList());
        StringBuilder getFromB = new StringBuilder();
        StringBuilder getFromA = new StringBuilder();

        final String dropMetaAGraph = "SPARQL CLEAR GRAPH <" + gr_c.getMetadataGraphA() + ">";
        final String dropMetaBGraph = "SPARQL CLEAR GRAPH <" + gr_c.getMetadataGraphB() + ">";
        final String createMetaAGraph = "SPARQL CREATE GRAPH <" + gr_c.getMetadataGraphA() + ">";
        final String createMetaBGraph = "SPARQL CREATE GRAPH <" + gr_c.getMetadataGraphB() + ">";

        PreparedStatement tempStmt;
        try {
            tempStmt = virt_conn.prepareStatement(dropMetaAGraph);

            tempStmt.execute();
            tempStmt.close();
            tempStmt = virt_conn.prepareStatement(dropMetaBGraph);
            tempStmt.execute();
            tempStmt.close();
            tempStmt = virt_conn.prepareStatement(createMetaAGraph);
            //tempStmt.execute();
            tempStmt.close();
            tempStmt = virt_conn.prepareStatement(createMetaBGraph);
            //tempStmt.execute();
            tempStmt.close();

        } catch (SQLException ex) {

            LOG.trace("SQLException thrown during temp graph creation");
            LOG.debug("SQLException thrown during temp graph creation : " + ex.getMessage());
            LOG.debug("SQLException thrown during temp graph creation : " + ex.getSQLState());

            success = false;

            return success;

        }

        starttime = System.nanoTime();
        //testThreads(links);
        endtime = System.nanoTime();
        //LOG.info(ANSI_YELLOW + "Thread test lasted " + ((endtime - starttime) / 1000000000f) + "" + ANSI_RESET);

        boolean isEndpointALocal = false;
        boolean isEndpointBLocal = false;

        isEndpointALocal = isURLToLocalInstance(gr_c.getEndpointA()); //"localhost" for localhost

        isEndpointBLocal = isURLToLocalInstance(gr_c.getEndpointB()); //"localhost" for localhost

        //gr_c.setLocalA(isEndpointALocal);
        //gr_c.setLocalB(isEndpointBLocal);
        //System.out.println("is it " + isEndpointALocal);
        //System.out.println(isEndpointBLocal);

        starttime = System.nanoTime();
        getFromA.append("sparql INSERT\n");
        if (scanProperties) {
            getFromA.append("  { GRAPH <").append(gr_c.getMetadataGraphA()).append("> {\n");
        } else {
            getFromA.append("  { GRAPH <").append(gr_c.getTargetGraph()).append("" + "> {\n");
        }
        if (gr_c.isDominantA()) {
            getFromA.append(" ?s ?p ?o1 . \n");
        } else {
            getFromA.append(" ?o ?p ?o1 . \n");
        }
        getFromA.append(" ?o1 ?p4 ?o3 .\n");
        getFromA.append(" ?o3 ?p5 ?o4 .\n");
        getFromA.append(" ?o4 ?p6 ?o5\n");
        getFromA.append("} }\nWHERE\n");
        getFromA.append("{\n");
        getFromA.append(" GRAPH <" + gr_c.getLinksGraph() + "> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } .\n");
        if (isEndpointALocal) {
            getFromA.append(" GRAPH <").append(gr_c.getGraphA()).append("> { {?s ?p ?o1} OPTIONAL { ?o1 ?p4 ?o3 . OPTIONAL { ?o3 ?p5 ?o4 . OPTIONAL { ?o4 ?p6 ?o5 .} } } }\n");
        } else {
            getFromA.append(" SERVICE <" + gr_c.getEndpointA() + "> { GRAPH <").append(gr_c.getGraphA()).append("> { {?s ?p ?o1} OPTIONAL { ?o1 ?p4 ?o3 . OPTIONAL { ?o3 ?p5 ?o4 . OPTIONAL { ?o4 ?p6 ?o5 .} } } } }\n");
        }
        getFromA.append("\n");
        getFromA.append("  FILTER(!regex(?p,\"http://www.opengis.net/ont/geosparql#hasGeometry\",\"i\")) \n");
        getFromA.append("  FILTER(!regex(?p, \"http://www.opengis.net/ont/geosparql#asWKT\", \"i\"))\n");
        getFromA.append("  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#lat\", \"i\")) \n");
        getFromA.append("  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#long\", \"i\"))\n");
        getFromA.append("  FILTER(!regex(?p4,\"http://www.opengis.net/ont/geosparql#hasGeometry\",\"i\")) \n");
        getFromA.append("  FILTER(!regex(?p4, \"http://www.opengis.net/ont/geosparql#asWKT\", \"i\"))\n");
        getFromA.append("  FILTER(!regex(?p4, \"http://www.w3.org/2003/01/geo/wgs84_pos#lat\", \"i\")) \n");
        getFromA.append("  FILTER(!regex(?p4, \"http://www.w3.org/2003/01/geo/wgs84_pos#long\", \"i\"))\n");
        getFromA.append("}");

        getFromB.append("sparql INSERT\n");
        if (scanProperties) {
            getFromB.append("  { GRAPH <").append(gr_c.getMetadataGraphB()).append("> {\n");
        } else {
            getFromB.append("  { GRAPH <").append(gr_c.getTargetGraph()).append("" + "> {\n");
        }
        if (gr_c.isDominantA()) {
            getFromB.append(" ?s ?p ?o1 . \n");
        } else {
            getFromB.append(" ?o ?p ?o1 . \n");
        }
        getFromB.append(" ?o1 ?p4 ?o3 .\n");
        getFromB.append(" ?o3 ?p5 ?o4 .\n");
        getFromB.append(" ?o4 ?p6 ?o5\n");
        getFromB.append("} }\nWHERE\n");
        getFromB.append("{\n");
        getFromB.append(" GRAPH <" + gr_c.getLinksGraph() + "> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } .\n");
        if (isEndpointBLocal) {
            getFromB.append(" GRAPH <").append(gr_c.getGraphB()).append("> { {?o ?p ?o1} OPTIONAL { ?o1 ?p4 ?o3 . OPTIONAL { ?o3 ?p5 ?o4 . OPTIONAL { ?o4 ?p6 ?o5 .} } } }\n");
        } else {
            getFromB.append(" SERVICE <" + gr_c.getEndpointB() + "> { GRAPH <").append(gr_c.getGraphB()).append("> { {?o ?p ?o1} OPTIONAL { ?o1 ?p4 ?o3 . OPTIONAL { ?o3 ?p5 ?o4 . OPTIONAL { ?o4 ?p6 ?o5 .} } } } }\n");
        }
        getFromB.append("\n");
        getFromB.append("  FILTER(!regex(?p,\"http://www.opengis.net/ont/geosparql#hasGeometry\",\"i\")) \n");
        getFromB.append("  FILTER(!regex(?p, \"http://www.opengis.net/ont/geosparql#asWKT\", \"i\"))\n");
        getFromB.append("  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#lat\", \"i\")) \n");
        getFromB.append("  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#long\", \"i\"))\n");
        getFromB.append("  FILTER(!regex(?p4,\"http://www.opengis.net/ont/geosparql#hasGeometry\",\"i\")) \n");
        getFromB.append("  FILTER(!regex(?p4, \"http://www.opengis.net/ont/geosparql#asWKT\", \"i\"))\n");
        getFromB.append("  FILTER(!regex(?p4, \"http://www.w3.org/2003/01/geo/wgs84_pos#lat\", \"i\")) \n");
        getFromB.append("  FILTER(!regex(?p4, \"http://www.w3.org/2003/01/geo/wgs84_pos#long\", \"i\"))\n");
        getFromB.append("}");

        System.out.println("GET FROM B \n" + getFromB);
        System.out.println("GET FROM A \n" + getFromA);

        int count = 0;
        int i = 0;

        while (i < links.size()) {

            success = createLinksGraphBatch(links, i);

            if (!success) {
                break;
            }
/*
            int tries = 0;
                startTime = System.nanoTime();
                while (tries < Constants.MAX_SPARQL_TRIES) {
                   
                        LOG.debug("SQLException thrown during temp graph populating Try : " + (tries + 1));
                endTime = System.nanoTime();
                    
                LOG.info("Uploading A lasted "+Utilities.nanoToSeconds(endTime-startTime));*/
            
            int tries = 0;
            startTime = System.nanoTime();
            while (tries < Constants.MAX_SPARQL_TRIES) {
                try (PreparedStatement getFromBStmt = virt_conn.prepareStatement(getFromA.toString())) {
                    starttime = System.nanoTime();

                    getFromBStmt.executeUpdate();

                } catch (SQLException ex) {

                    LOG.trace("SQLException thrown during temp graph B populating");
                    LOG.debug("SQLException thrown during temp graph B populating Try : " + (tries + 1));
                    LOG.debug("SQLException thrown during temp graph B populating : " + ex.getMessage());
                    LOG.debug("SQLException thrown during temp graph B populating : " + ex.getSQLState());

                    tries++;
                    
                }
            }

            if ( tries == Constants.MAX_SPARQL_TRIES) {
                success = false;
                return success;
            }
            
            endTime = System.nanoTime();

            LOG.info("Uploading A lasted " + Utilities.nanoToSeconds(endTime - startTime));

            tries = 0;
            startTime = System.nanoTime();
            while (tries < Constants.MAX_SPARQL_TRIES) {
                try (PreparedStatement getFromAStmt = virt_conn.prepareStatement(getFromA.toString())) {
                    starttime = System.nanoTime();

                    getFromAStmt.executeUpdate();

                } catch (SQLException ex) {

                    LOG.trace("SQLException thrown during temp graph A populating");
                    LOG.debug("SQLException thrown during temp graph A populating Try : " + (tries + 1));
                    LOG.debug("SQLException thrown during temp graph A populating : " + ex.getMessage());
                    LOG.debug("SQLException thrown during temp graph A populating : " + ex.getSQLState());

                    tries++;

                }
            }
            
            if ( tries == Constants.MAX_SPARQL_TRIES) {
                success = false;
                return success;
            }
            
            endTime = System.nanoTime();

            LOG.info("Uploading B lasted " + Utilities.nanoToSeconds(endTime - startTime));
            
            
            //endtime = System.nanoTime();
            //LOG.info("Metadata main parsed in " + (endtime - starttime) / 1000000000f);
            i += Constants.BATCH_SIZE;
            count++;
        }

        //System.out.println("THE BOOL IS " + success);
        return success;

        //endtime =  System.nanoTime();
        //LOG.info("Metadata parsed in "+(endtime-starttime)/1000000000f);       
        //endtime =  System.nanoTime();
        //LOG.info(ANSI_YELLOW+"Metadata parsed in "+((endtime-starttime)/1000000000f) +""+ANSI_RESET);
    }

    public int compareTypes(String l, String r) {
        if (Patterns.PATTERN_INT.matcher(l).find() && Patterns.PATTERN_INT.matcher(r).find()) {
            return 1;
        }
        if (Patterns.PATTERN_DATE.matcher(l).find() && Patterns.PATTERN_DATE.matcher(r).find()) {
            return 1;
        }
        if (Patterns.PATTERN_TEXT.matcher(l).find() && Patterns.PATTERN_TEXT.matcher(r).find()) {
            return 1;
        }
        if (Patterns.PATTERN_DECIMAL.matcher(l).find() && Patterns.PATTERN_DECIMAL.matcher(r).find()) {
            return 1;
        }
        if (Patterns.PATTERN_WORD.matcher(l).find() && Patterns.PATTERN_WORD.matcher(r).find()) {
            return 1;
        }

        return 0;
    }

    private class SchemaNormalizer {

        Schema sRef;
        float textDist;
        float semDist;
        float typeDist;

        public SchemaNormalizer(Schema sRef, float textDist, float semDist, float typeDist) {
            this.sRef = sRef;
            this.textDist = textDist;
            this.semDist = semDist;
            this.typeDist = typeDist;
        }

    }

    HashMap<String, Schema> schemasA = Maps.newHashMap();
    HashMap<String, Schema> schemasB = Maps.newHashMap();

    private void scanMatches() {
        
        // expand properties chains
        expandChain(schemasA, propertiesA, "");
        expandChain(schemasB, propertiesB, "");
        
        /*
        for (Map.Entry pairs : schemasA.entrySet()) {
            String chain = (String) pairs.getKey();
            Schema sche = (Schema) pairs.getValue();
            System.out.println("A " + chain + " " + sche.predicate + " Size " + sche.indexes.size());
        }
        for (Map.Entry pairs : schemasB.entrySet()) {
            String chain = (String) pairs.getKey();
            Schema sche = (Schema) pairs.getValue();
            System.out.println("B " + chain + " " + sche.predicate + " Size " + sche.indexes.size());
        }
        */
        
        List<SchemaMatcher> matchers = new ArrayList<>();
        int countA;
        int countB;
        float score;
        float sim;

        Map<String, List<SchemaNormalizer>> scorer = Maps.newHashMap();
        for (Map.Entry pairsA : schemasA.entrySet()) {
            //String chainA = (String)pairsA.getKey();
            Schema scheA = (Schema) pairsA.getValue();

            nonMatchedPropertiesA.add(scheA.predicate);

            if (scheA.indexes.isEmpty()) {
                //System.out.println("Empty Index A");
                continue;
            }

            float maxDist = -1.0f;
            float maxScore = -1.0f;
            for (Map.Entry pairsB : schemasB.entrySet()) {
                //String chainB = (String)pairsB.getKey();
                Schema scheB = (Schema) pairsB.getValue();
                //System.out.println("Would Match "+scheA.predicate + scheB.predicate);
                nonMatchedPropertiesB.add(scheB.predicate);
                score = 0;
                sim = 0;

                if (scheB.indexes.isEmpty()) {
                    //System.out.println("Empty Index A");
                    continue;
                }

                SchemaMatcher m = new SchemaMatcher(scheA, scheB);
                countA = 0;

                //System.out.println("Matching "+scheA.predicate + scheB.predicate);
                //System.out.println("Main "+main);
                for (IndexWord iwA : scheA.indexes) {
                    countA++;
                    countB = 0;
                    for (IndexWord iwB : scheB.indexes) {
                        //System.out.println("Scoring : " + iwA.getLemma() + " and " + iwB.getLemma());
                        countB++;
                        float tmpScore = calculateAsymmetricRelationshipOperation(iwA, iwB, m);
                        score += tmpScore;
                        //System.out.println("Score : "+tmpScore);                       
                    }
                }
                //System.out.println();
                float jaro_dist = 0;
                float jaro_dist_norm;
                int jaroCount = 0;
                for (String iwA : scheA.words) {
                    countA++;
                    countB = 0;
                    for (String iwB : scheB.words) {
                        jaroCount++;
                        //System.out.println("Jaroing : "+iwA+ " and "+ iwB);   
                        jaro_dist += (float) StringUtils.getJaroWinklerDistance(iwA, iwB);
                    }
                }

                jaro_dist_norm = jaro_dist / (jaroCount);
                //System.out.println("Jaro : " + jaro_dist_norm);
                //scheB.texDist = jaro_dist_norm;
                if (jaro_dist_norm > maxDist) {
                    maxDist = jaro_dist_norm;
                }
                score = score / (scheA.indexes.size() * scheB.indexes.size());
                //scheB.semDist = score;
                if (score > maxScore) {
                    maxScore = score;
                }

                //scheB.typeDist = 0.0f;
                float sim_score = (score + jaro_dist) / 2;
                //if (sim_score > 0.5) {
                int same_type = compareTypes(scheA.objectStr, scheB.objectStr);
                    //System.out.print("Sim Score : "+sim_score+" "+same_type+" ");
                //System.out.print("Type Score : "+scheA.objectStr+" and "+scheB.objectStr+" "+sim_score+" "+same_type+" ");
                //System.out.print("Jaro distance of "+jaro_dist_norm+" ");
                //System.out.print("Mul : "+scheA.indexes.size()+" x "+scheB.indexes.size() + " = "+(scheA.indexes.size()*scheB.indexes.size())+" ");
                //System.out.println("Final : " + (score + jaro_dist_norm + same_type)/3);
                //scheB.typeDist = same_type;
                sim_score = (score + jaro_dist_norm + 0.5f * same_type) / 3;
                //}
                //System.out.println("Probs ::::::: "+score);
                SchemaNormalizer snorm = new SchemaNormalizer(scheB, jaro_dist_norm, score, same_type);
                if (!scorer.containsKey(scheA.predicate + scheB.predicate)) {
                    List<SchemaNormalizer> scheLst = new ArrayList<SchemaNormalizer>();
                    scheLst.add(snorm);
                    scorer.put(scheA.predicate + scheB.predicate, scheLst);
                }
                scorer.get(scheA.predicate + scheB.predicate).add(snorm);
                //m.score = sim_score;
                if (!m.matches.isEmpty()) {
                    //System.out.println(m.matches.get(0));
                    matchers.add(m);
                }
            }

            scheA.texDist = maxDist;
            scheA.semDist = maxScore;
        }

        //System.out.println("Matches "+matchers.size());
        //for (SchemaMatcher ma : matchers) {
        //System.out.println(ma);
        //}
        //System.out.println("Total Matches "+foundA.size());
        HashMap<String, Schema> schemaPtrsA = Maps.newHashMap();
        HashMap<String, Schema> schemaPtrsB = Maps.newHashMap();
        //System.out.println("------------------------------------------");
        //System.out.println("            NON MATCHED ENTITIES          ");
        //System.out.println("------------------------------------------");
        for (SchemaMatcher ma : matchers) {
            if (!ma.matches.isEmpty()) {
                //if (!foundA.containsKey(ma.sA.predicate)) {

                //System.out.println(ma.sA.predicate);
                //System.out.println(ma.sB.predicate);
                //System.out.println(nonMatchedPropertiesA);
                //System.out.println(nonMatchedPropertiesB);
                nonMatchedPropertiesA.remove(ma.sA.predicate);
                nonMatchedPropertiesB.remove(ma.sB.predicate);

                HashSet<ScoredMatch> matchesA = foundA.get(ma.sA.predicate);
                HashSet<ScoredMatch> matchesB = foundB.get(ma.sB.predicate);

                SchemaNormalizer selectedSche = null;
                List<SchemaNormalizer> snormLst = scorer.get(ma.sA.predicate + ma.sB.predicate);
                for (SchemaNormalizer snorm : snormLst) {
                    if (ma.sB.predicate.equals(snorm.sRef.predicate)) {
                        selectedSche = snorm;
                        break;
                    }
                }
                //System.out.println("Scoring "+selectedSche.semDist+" "+selectedSche.textDist+" "+selectedSche.typeDist);
                //System.out.println("Scoring "+ma.sA.semDist+" "+ma.sA.texDist);
                float sim_score = 0;
                if (!ma.sA.predicate.equals(selectedSche.sRef.predicate)) {
                    if (ma.sA.semDist < 0.00000001f) {
                        ma.sA.semDist = 1.0f;
                    }
                    if (ma.sA.texDist < 0.00000001f) {
                        ma.sA.texDist = 1.0f;
                    }

                    sim_score = (float) (wordWeight * ((selectedSche.semDist / ma.sA.semDist))
                            + (textWeight * (selectedSche.textDist / ma.sA.texDist))
                            + (typeWeight * (selectedSche.typeDist))) / 3.0f;

                    //System.out.println("Scoring "+ma.sA.predicate+" "+selectedSche.sRef.predicate+" = "+ sim_score);
                } else {
                    sim_score = 1.0f;
                }
                ma.score = sim_score;

                schemaPtrsA.put(ma.sA.predicate, ma.sA);
                schemaPtrsA.put(ma.sB.predicate, ma.sB);

                if (matchesA == null) {
                    matchesA = Sets.newHashSet();
                    foundA.put(ma.sA.predicate, matchesA);
                }
                if (matchesB == null) {
                    matchesB = Sets.newHashSet();
                    foundB.put(ma.sB.predicate, matchesB);
                }

                ScoredMatch scoredA = new ScoredMatch(ma.sB.predicate, ma.score);
                ScoredMatch scoredB = new ScoredMatch(ma.sA.predicate, ma.score);

                if (!matchesA.contains(scoredA)) {
                    matchesA.add(scoredA);
                }
                if (!matchesB.contains(scoredA)) {
                    matchesB.add(scoredB);
                }

                //}
                //System.out.println("Matched "+ma.sA.predicate+" with "+ ma.sB.predicate + " = "+ma.score);
            }
        }
    }

    private boolean expandChain(HashMap<String, Schema> lst, HashMap< String, MetadataChain> chains, String chainStr) {
        boolean success = true;
        Dictionary dictionary = Dictionary.getInstance();
        for (Map.Entry pairs : chains.entrySet()) {
            MetadataChain chain = (MetadataChain) pairs.getValue();
            
            // Chains are comma separated
            String pad;
            if (chain.chains != null) {
                pad = ",";
                expandChain(lst, chain.chains, chainStr + chain.predicate + pad);
            }

            if (lst.containsKey(chainStr)) {
                continue;
            }

            String pred = chainStr.concat(chain.predicate);

            //String que = "";
            List<String> arrl = new ArrayList<>();
            //System.out.println("Chain Link : " + chain.link);

            //URL normalizer can possibly be truned on here
            String normalizedLink;
            try {
                normalizedLink = URLDecoder.decode(chain.link, "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                LOG.trace("UnsupportedEncodingException thrown during parsing of \"" + chain.link + "\"");
                LOG.debug("UnsupportedEncodingException thrown during parsing of \"" + chain.link + "\" " + ex.getMessage());
                    
                // A bad decoding should not break the whole FAGI 
                continue;
            }
            
            Matcher mat = Patterns.PATTERN_WORD_BREAKER.matcher(normalizedLink);
            while (mat.find()) {
                arrl.add(mat.group());
            }
            //System.out.print("Found:");
            /*for (String s : arrl) {
                System.out.print(" " + s);
            }
            System.out.println();*/

            Schema m = new Schema();
            m.predicate = pred;
            m.objectStr = chain.objectStr;
            //System.out.println("breaking "+chain.link);
            Analyzer englishAnalyzer = new EnglishAnalyzer(Version.LUCENE_36);
            QueryParser englishParser = new QueryParser(Version.LUCENE_36, "", englishAnalyzer);
            for (String a : arrl) {
                m.addWord(a);
                if (recoveredWords.contains(a.toLowerCase())) {
                    //System.out.println("Cancelling " + a);
                    continue;
                }

                //System.out.print("Value "+a+" ");
                //System.out.println("Value : " + a + " stemmed : " + englishParser.parse(a).toString());
                IndexWordSet wordSet;
                try {
                    wordSet = dictionary.lookupAllIndexWords(englishParser.parse(a).toString());
                } catch (ParseException ex) {
                    LOG.trace("ParseException thrown during parsing of \"" + a + "\"");
                    LOG.debug("ParseException thrown during parsing of \"" + a + "\" " + ex.getMessage());
                    
                    // A bad word stem should not break the whole FAGI 
                    continue;
                } catch (JWNLException ex) {
                    LOG.trace("JWNLException thrown during parsing of \"" + a + "\"");
                    LOG.debug("JWNLException thrown during parsing of \"" + a + "\" " + ex.getMessage());
                    
                    // A bad word look up should not break the whole FAGI 
                    continue;
                }
                
                //IndexWordSet wordSet = dictionary.lookupAllIndexWords(a);
                if (wordSet == null) {
                    continue;
                }
                IndexWord[] indices = wordSet.getIndexWordArray();
                IndexWord best = null;
                int bestInt = 0;
                for (IndexWord idx : indices) {
                    //System.out.println("POS label " + idx.getPOS().getLabel());
                    if (idx.getPOS().getLabel().equals("noun")) {
                        best = idx;
                        bestInt = 3;
                    } else if (idx.getPOS().getLabel().equals("adjective") && bestInt < 3) {
                        best = idx;
                        bestInt = 2;
                    } else if (idx.getPOS().getLabel().equals("verb") && bestInt < 2) {
                        best = idx;
                        bestInt = 1;
                    }
                }

                if (best == null) {
                    //System.out.println("Null Best for " + englishParser.parse(a).toString());
                    continue;
                }

                m.addIndex(best);
            }
            
            //System.out.println("Inserting predicate: "+ pred);
            lst.put(pred, m);
        }
        //System.out.println();
        
        return success;
    }

    private void scanChain(HashMap< String, MetadataChain> cont, List<String> chain, List<String> objectChain) {
        //System.out.print("Chain: ");
        if (chain.isEmpty()) {
            return;
        }

        String link = chain.get(0);
        if (link == null) {
            return;
        }

        MetadataChain root = null;
        if (cont.containsKey(link)) {
            root = cont.get(link);
        } else {
            String main = StringUtils.substringAfter(link, "#");
            if (main.equals("")) {
                main = StringUtils.substring(link, StringUtils.lastIndexOf(link, "/") + 1);
            }

            //Analyzer englishAnalyzer =  new EnglishAnalyzer(Version.LUCENE_36);
            //QueryParser englishParser = new QueryParser(Version.LUCENE_36, "", englishAnalyzer);
            //System.out.println("Parsed "+englishParser.parse(main));
            //System.out.println("Main "+main);
            root = new MetadataChain(main, link, objectChain.get(0));
            cont.put(root.predicate, root);
            //System.out.println(main+" "+root.predicate+" "+link);
        }

        for (int i = 1; i < chain.size(); i++) {
            //System.out.println("Oxi file");
            link = chain.get(i);
            if (link == null) {
                //System.out.println("null");
                return;
            }

            if (root.chains != null) {
                if (root.chains.containsKey(link)) {
                    root = root.chains.get(link);
                    continue;
                }
            }

            String main = StringUtils.substringAfter(link, "#");
            if (main.equals("")) {
                main = StringUtils.substring(link, StringUtils.lastIndexOf(link, "/") + 1);
            }
            //System.out.println(main);

            MetadataChain newChain = new MetadataChain(main, link, objectChain.get(i));
            root.addCahin(newChain);
            root = newChain;
            //System.out.print(link+" ");
        }

        //System.out.println(root);
    }

    public SchemaMatchState scanProperties(int optDepth, String linkA, String linkB, Boolean swapped) {
        boolean success = true;
        
        try {
            if (SystemUtils.IS_OS_MAC_OSX) {
                JWNL.initialize(new ByteArrayInputStream(getJWNL(Constants.PATH_TO_WORDNET_OS_X).getBytes(StandardCharsets.UTF_8)));

            }
            if (SystemUtils.IS_OS_LINUX) {
                JWNL.initialize(new ByteArrayInputStream(getJWNL(Constants.PATH_TO_WORDNET_LINUX).getBytes(StandardCharsets.UTF_8)));
            }
            if (SystemUtils.IS_OS_WINDOWS) {
                JWNL.initialize(new ByteArrayInputStream(getJWNL(Constants.PATH_TO_WORDNET_WINDOWS).getBytes(StandardCharsets.UTF_8)));
            }
        } catch (JWNLException ex) {
            LOG.trace("JWNLException thrown during set up of the Dictionary");
            LOG.debug("JWNLException thrown during set up of the Dictionary : \n" + ex.getMessage());
        }
        
        try (
            // StopWords are stored in a serialized HashSet
            InputStream file = this.getClass().getResourceAsStream("/stopWords.ser");
            InputStream buffer = new BufferedInputStream(file);
            ObjectInput input = new ObjectInputStream(buffer);) {
            
            recoveredWords = (HashSet) input.readObject();

        } catch (ClassNotFoundException ex) {
            LOG.trace("ClassNotFoundException thrown during loading of the StopWords file");
            LOG.debug("ClassNotFoundException thrown during loading of the StopWords file : \n" + ex.getMessage());
            
            recoveredWords = new HashSet();
        } catch (IOException ex) {
            LOG.trace("IOException thrown during loading of the StopWords file");
            LOG.debug("IOException thrown during loading of the StopWords file : \n" + ex.getMessage());
            
            recoveredWords = new HashSet();
        }

        // Clear previous findings
        foundA.clear();
        foundB.clear();
        
        // If it' s a per link matching
        if ( linkA != null && linkB != null ) {
            // If we are using Late Fetch Optimisation we need to create
            // the temp graphs here
            if (Constants.LATE_FETCH) {
                StringBuilder getFromB = new StringBuilder();
                StringBuilder getFromA = new StringBuilder();

                boolean isEndpointALocal;
                boolean isEndpointBLocal;

                isEndpointALocal = isURLToLocalInstance(gr_c.getEndpointA()); //"localhost" for localhost

                isEndpointBLocal = isURLToLocalInstance(gr_c.getEndpointB()); //"localhost" for localhost

                getFromA.append("SPARQL INSERT\n");
                getFromA.append("  { GRAPH <").append(gr_c.getMetadataGraphA()).append("> {\n");
                if (gr_c.isDominantA()) {
                    getFromA.append(" <"+linkA+"> ?p ?o1 . \n");
                } else {
                    getFromA.append(" <"+linkB+"> ?p ?o1 . \n");
                }
                getFromA.append(" ?o1 ?p4 ?o3 .\n");
                getFromA.append(" ?o3 ?p5 ?o4 .\n");
                getFromA.append(" ?o4 ?p6 ?o5\n");
                getFromA.append("} }\nWHERE\n");
                getFromA.append("{\n");               
                if (isEndpointALocal) {
                    getFromA.append(" GRAPH <").append(gr_c.getGraphA()).append("> { {<"+linkA+"> ?p ?o1} OPTIONAL { ?o1 ?p4 ?o3 . OPTIONAL { ?o3 ?p5 ?o4 . OPTIONAL { ?o4 ?p6 ?o5 .} } } }\n");
                } else {
                    getFromA.append(" SERVICE <" + gr_c.getEndpointA() + "> { GRAPH <").append(gr_c.getGraphA()).append("> { {<"+linkA+"> ?p ?o1} OPTIONAL { ?o1 ?p4 ?o3 . OPTIONAL { ?o3 ?p5 ?o4 . OPTIONAL { ?o4 ?p6 ?o5 .} } } } }\n");
                }
                getFromA.append("\n");
                getFromA.append("  FILTER(!regex(?p,\"http://www.opengis.net/ont/geosparql#hasGeometry\",\"i\")) \n");
                getFromA.append("  FILTER(!regex(?p, \"http://www.opengis.net/ont/geosparql#asWKT\", \"i\"))\n");
                getFromA.append("  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#lat\", \"i\")) \n");
                getFromA.append("  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#long\", \"i\"))\n");
                getFromA.append("  FILTER(!regex(?p4,\"http://www.opengis.net/ont/geosparql#hasGeometry\",\"i\")) \n");
                getFromA.append("  FILTER(!regex(?p4, \"http://www.opengis.net/ont/geosparql#asWKT\", \"i\"))\n");
                getFromA.append("  FILTER(!regex(?p4, \"http://www.w3.org/2003/01/geo/wgs84_pos#lat\", \"i\")) \n");
                getFromA.append("  FILTER(!regex(?p4, \"http://www.w3.org/2003/01/geo/wgs84_pos#long\", \"i\"))\n");
                getFromA.append("}");

                getFromB.append("sparql INSERT\n");
                getFromB.append("  { GRAPH <").append(gr_c.getMetadataGraphB()).append("> {\n");
                if (gr_c.isDominantA()) {
                    getFromB.append(" <"+linkA+"> ?p ?o1 . \n");
                } else {
                    getFromB.append(" <"+linkB+"> ?p ?o1 . \n");
                }
                getFromB.append(" ?o1 ?p4 ?o3 .\n");
                getFromB.append(" ?o3 ?p5 ?o4 .\n");
                getFromB.append(" ?o4 ?p6 ?o5 \n");
                getFromB.append("} }\nWHERE\n");
                getFromB.append("{\n"); 
                if (isEndpointBLocal) {
                    getFromB.append(" GRAPH <").append(gr_c.getGraphB()).append("> { {<"+linkB+"> ?p ?o1} OPTIONAL { ?o1 ?p4 ?o3 . OPTIONAL { ?o3 ?p5 ?o4 . OPTIONAL { ?o4 ?p6 ?o5 .} } } }\n");
                } else {
                    getFromB.append(" SERVICE <" + gr_c.getEndpointB() + "> { GRAPH <").append(gr_c.getGraphB()).append("> { {<"+linkB+"> ?p ?o1} OPTIONAL { ?o1 ?p4 ?o3 . OPTIONAL { ?o3 ?p5 ?o4 . OPTIONAL { ?o4 ?p6 ?o5 .} } } } }\n");
                }
                getFromB.append("\n");
                getFromB.append("  FILTER(!regex(?p,\"http://www.opengis.net/ont/geosparql#hasGeometry\",\"i\")) \n");
                getFromB.append("  FILTER(!regex(?p, \"http://www.opengis.net/ont/geosparql#asWKT\", \"i\"))\n");
                getFromB.append("  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#lat\", \"i\")) \n");
                getFromB.append("  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#long\", \"i\"))\n");
                getFromB.append("  FILTER(!regex(?p4,\"http://www.opengis.net/ont/geosparql#hasGeometry\",\"i\")) \n");
                getFromB.append("  FILTER(!regex(?p4, \"http://www.opengis.net/ont/geosparql#asWKT\", \"i\"))\n");
                getFromB.append("  FILTER(!regex(?p4, \"http://www.w3.org/2003/01/geo/wgs84_pos#lat\", \"i\")) \n");
                getFromB.append("  FILTER(!regex(?p4, \"http://www.w3.org/2003/01/geo/wgs84_pos#long\", \"i\"))\n");
                getFromB.append("}");

                System.out.println("GET FROM B \n" + getFromB);
                System.out.println("GET FROM A \n" + getFromA);
             
                // Populate with data from the Sample Liink set
                try (PreparedStatement populateDataA = virt_conn.prepareStatement(getFromA.toString());
                        PreparedStatement populateDataB = virt_conn.prepareStatement(getFromB.toString())) {
                    //starttime = System.nanoTime();

                    populateDataA.executeUpdate();
                    populateDataB.executeUpdate();

                } catch (SQLException ex) {

                    LOG.trace("SQLException thrown during temp graph populating");
                    LOG.debug("SQLException thrown during temp graph populating : " + ex.getMessage());
                    LOG.debug("SQLException thrown during temp graph populating : " + ex.getSQLState());

                    success = false;

                }

            }
            
            String link;
            if (gr_c.isDominantA()) {
                link = linkA;
            } else {
                link = linkB;
            }
            for (int i = 0; i < optDepth + 1; i++) {
                //System.out.println("DEPTH: "+i);
                StringBuilder query = new StringBuilder();

                query.append("SPARQL SELECT ?pa1 ?oa1 ");
                for (int j = 0; j < i; j++) {
                    int ind = j + 2;
                    int prev = ind - 1;
                    query.append("?pa").append(ind).append(" ?oa").append(ind).append(" ");
                }
                query.append("?pb1 ?ob1 ");
                for (int j = 0; j < i; j++) {
                    int ind = j + 2;
                    query.append("?pb").append(ind).append(" ?ob").append(ind).append(" ");
                }
                query.append("\nWHERE\n{\n" + " { GRAPH <").append(gr_c.getMetadataGraphA()).append("> { <" + link + "> ?pa1 ?oa1 ");
                for (int j = 0; j < i; j++) {
                    int ind = j + 2;
                    int prev = ind - 1;
                    query.append(" . ?oa").append(prev).append(" ?pa").append(ind).append(" ?oa").append(ind).append(" ");
                }

                query.append(" } ");

                query.append("}\n UNION { \n" + "   GRAPH <").append(gr_c.getMetadataGraphB()).append("> { <" + link + "> ?pb1 ?ob1 ");
                for (int j = 0; j < i; j++) {
                    int ind = j + 2;
                    int prev = ind - 1;
                    query.append(" . ?ob").append(prev).append(" ?pb").append(ind).append(" ?ob").append(ind).append(" ");
                }

                query.append("} }\n"
                        + "}");

                //System.out.println("DEPTH: " + i);
                //System.out.println("QUERY FOR PREDICATES : " + query.toString());
                
                try (PreparedStatement fetchProperties = virt_conn.prepareStatement(query.toString());
                        ResultSet propertiesRS = fetchProperties.executeQuery()) {

                    String prevSubject = link;
                    while (propertiesRS.next()) {
                        List<String> chainA = new ArrayList<>();
                        List<String> chainB = new ArrayList<>();
                        List<String> objectChainA = new ArrayList<>();
                        List<String> objectChainB = new ArrayList<>();
                        for (int j = 0; j <= i; j++) {
                            int step_over = 2 * (i + 1);

                            String predicateA = propertiesRS.getString(2 * (j + 1) - 1);
                            String objectA = propertiesRS.getString(2 * (j + 1));
                            String predicateB = propertiesRS.getString(2 * (j + 1) + step_over - 1);
                            String objectB = propertiesRS.getString(2 * (j + 1) + step_over);

                            if (predicateA != null) {
                                //predicateA = URLDecoder.decode(predicateA, "UTF-8");
                            }
                            if (predicateB != null) {
                                //predicateB = URLDecoder.decode(predicateB, "UTF-8");
                            }

                            /*
                             if (predicateA != null) {
                             if (!uniquePropertiesA.contains(predicateA)) {
                             //uniquePropertiesA.add(predicateA);
                             }
                             if (predicateA.contains("posSeq")) {
                             continue;
                             }
                             if (predicateA.contains("asWKT")) {
                             continue;
                             }
                             if (predicateA.contains("geometry")) {
                             continue;
                             }
                             }
                             if (predicateB != null) {
                             if (!uniquePropertiesB.contains(predicateB)) {
                             //uniquePropertiesB.add(predicateB);
                             }
                             if (predicateB.contains("posSeq")) {
                             continue;
                             }
                             if (predicateB.contains("asWKT")) {
                             continue;
                             }
                             if (predicateB.contains("geometry")) {
                             continue;
                             }
                             }
                             */
                            chainA.add(predicateA);
                            objectChainA.add(objectA);
                            chainB.add(predicateB);
                            objectChainB.add(objectB);
                        }

                        scanChain(propertiesA, chainA, objectChainA);
                        scanChain(propertiesB, chainB, objectChainB);

                    }

                } catch (SQLException ex) {
                    LOG.trace("SQLException thrown during set up of the Dictionary");
                    LOG.debug("SQLException thrown during set up of the Dictionary : \n" + ex.getMessage());
                    LOG.debug("SQLException thrown during set up of the Dictionary : \n" + ex.getSQLState());
                }
                
                scanMatches();
                propertiesA.clear();
                propertiesB.clear();

                        //propertiesRS.close();
                   
                //fetchProperties.close();
            }
        } else {
            // For bulk matching we need to create a Sample Links Graph
            // because matching on all links would be very slow
            final String dropSamplesGraph = "SPARQL DROP SILENT GRAPH <" + gr_c.getSampleLinksGraph() + ">";
            final String createSamplesGraph = "SPARQL CREATE GRAPH <" + gr_c.getSampleLinksGraph() + ">";
            final String createLinksSample = "SPARQL INSERT\n"
                    + " { GRAPH <" + gr_c.getSampleLinksGraph() + "> {\n"
                    + " ?s ?p ?o\n"
                    + "} }\n"
                    + "WHERE\n"
                    + "{\n"
                    + "{\n"
                    + "SELECT ?s ?p ?o WHERE {\n"
                    + " GRAPH <" + gr_c.getLinksGraph() + "> { ?s ?p ?o }\n"
                    + "} limit " + Constants.SAMPLE_SIZE + "\n"
                    + "}}";

            try (PreparedStatement dropSamplesStmt = virt_conn.prepareStatement(dropSamplesGraph);
                PreparedStatement insertLinksSample = virt_conn.prepareStatement(createLinksSample);
                PreparedStatement createSamplesStmt = virt_conn.prepareStatement(createSamplesGraph) ) {

                dropSamplesStmt.execute();
                createSamplesStmt.execute();
                insertLinksSample.execute();

            } catch (SQLException ex) {
                LOG.trace("SQLException thrown during sample creation");
                LOG.debug("SQLException thrown during sample creation : \n" + ex.getMessage());
                LOG.debug("SQLException thrown during sample creation : \n" + ex.getSQLState());
                                
                return null;
            }
            
            // If we are using Late Fetch Optimisation we need to create
            // the temp graphs here
            if (Constants.LATE_FETCH) {
                StringBuilder getFromB = new StringBuilder();
                StringBuilder getFromA = new StringBuilder();

                final String dropMetaAGraph = "SPARQL DROP SILENT GRAPH <" + gr_c.getMetadataGraphA() + ">";
                final String dropMetaBGraph = "SPARQL DROP SILENT GRAPH <" + gr_c.getMetadataGraphB() + ">";
                final String createMetaAGraph = "SPARQL CREATE GRAPH <" + gr_c.getMetadataGraphA() + ">";
                final String createMetaBGraph = "SPARQL CREATE GRAPH <" + gr_c.getMetadataGraphB() + ">";

                try (PreparedStatement dropMetaAStmt = virt_conn.prepareStatement(dropMetaAGraph);
                    PreparedStatement dropMetaBStmt = virt_conn.prepareStatement(dropMetaBGraph);
                    PreparedStatement createMetaAStmt = virt_conn.prepareStatement(createMetaAGraph);
                    PreparedStatement createMetaBStmt = virt_conn.prepareStatement(createMetaBGraph) ) {
                    
                    //System.out.println("INSIDE META CREATION");
                    dropMetaAStmt.execute();
                    //System.out.println("INSIDE META CREATION 1");
                    dropMetaBStmt.execute();
                    //System.out.println("INSIDE META CREATION 2");
                    createMetaAStmt.execute();
                    //System.out.println("INSIDE META CREATION 3");
                    createMetaBStmt.execute();
                    //System.out.println("OUTSIDE META CREATION");
                    
                } catch (SQLException ex) {

                    LOG.trace("SQLException thrown during temp graph creation");
                    LOG.debug("SQLException thrown during temp graph creation : " + ex.getMessage());
                    LOG.debug("SQLException thrown during temp graph creation : " + ex.getSQLState());

                }

                //testThreads(links);
                //LOG.info(ANSI_YELLOW + "Thread test lasted " + ((endtime - starttime) / 1000000000f) + "" + ANSI_RESET);

                boolean isEndpointALocal;
                boolean isEndpointBLocal;

                isEndpointALocal = isURLToLocalInstance(gr_c.getEndpointA()); //"localhost" for localhost

                isEndpointBLocal = isURLToLocalInstance(gr_c.getEndpointB()); //"localhost" for localhost


                getFromA.append("SPARQL INSERT\n");
                getFromA.append("  { GRAPH <").append(gr_c.getMetadataGraphA()).append("> {\n");
                if (gr_c.isDominantA()) {
                    getFromA.append(" ?s ?p ?o1 . \n");
                } else {
                    getFromA.append(" ?o ?p ?o1 . \n");
                }
                getFromA.append(" ?o1 ?p4 ?o3 .\n");
                getFromA.append(" ?o3 ?p5 ?o4 .\n");
                getFromA.append(" ?o4 ?p6 ?o5\n");
                getFromA.append("} }\nWHERE\n");
                getFromA.append("{\n");
                getFromA.append(" GRAPH <" + gr_c.getSampleLinksGraph() + "> { ");
                //if ( swapped )
                    getFromA.append(" ?s <"+Constants.SAME_AS+"> ?o } .\n");     
                //else
                //    getFromA.append(" ?o <"+Constants.SAME_AS+"> ?s } .\n");                
                if (isEndpointALocal) {
                    getFromA.append(" GRAPH <").append(gr_c.getGraphA()).append("> { {?s ?p ?o1} OPTIONAL { ?o1 ?p4 ?o3 . OPTIONAL { ?o3 ?p5 ?o4 . OPTIONAL { ?o4 ?p6 ?o5 .} } } }\n");
                } else {
                    getFromA.append(" SERVICE <" + gr_c.getEndpointA() + "> { GRAPH <").append(gr_c.getGraphA()).append("> { {?s ?p ?o1} OPTIONAL { ?o1 ?p4 ?o3 . OPTIONAL { ?o3 ?p5 ?o4 . OPTIONAL { ?o4 ?p6 ?o5 .} } } } }\n");
                }
                getFromA.append("\n");
                getFromA.append("  FILTER(!regex(?p,\"http://www.opengis.net/ont/geosparql#hasGeometry\",\"i\")) \n");
                getFromA.append("  FILTER(!regex(?p, \"http://www.opengis.net/ont/geosparql#asWKT\", \"i\"))\n");
                getFromA.append("  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#lat\", \"i\")) \n");
                getFromA.append("  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#long\", \"i\"))\n");
                getFromA.append("  FILTER(!regex(?p4,\"http://www.opengis.net/ont/geosparql#hasGeometry\",\"i\")) \n");
                getFromA.append("  FILTER(!regex(?p4, \"http://www.opengis.net/ont/geosparql#asWKT\", \"i\"))\n");
                getFromA.append("  FILTER(!regex(?p4, \"http://www.w3.org/2003/01/geo/wgs84_pos#lat\", \"i\")) \n");
                getFromA.append("  FILTER(!regex(?p4, \"http://www.w3.org/2003/01/geo/wgs84_pos#long\", \"i\"))\n");
                getFromA.append("}");

                getFromB.append("sparql INSERT\n");
                getFromB.append("  { GRAPH <").append(gr_c.getMetadataGraphB()).append("> {\n");
                if (gr_c.isDominantA()) {
                    getFromB.append(" ?s ?p ?o1 . \n");
                } else {
                    getFromB.append(" ?o ?p ?o1 . \n");
                }
                getFromB.append(" ?o1 ?p4 ?o3 .\n");
                getFromB.append(" ?o3 ?p5 ?o4 .\n");
                getFromB.append(" ?o4 ?p6 ?o5 \n");
                getFromB.append("} }\nWHERE\n");
                getFromB.append("{\n");
                getFromB.append(" GRAPH <" + gr_c.getSampleLinksGraph() + "> { ");
                //if ( swapped )
                    getFromB.append(" ?s <"+Constants.SAME_AS+"> ?o } .\n");     
                //else
                    //getFromB.append(" ?o <"+Constants.SAME_AS+"> ?s } .\n");  
                if (isEndpointBLocal) {
                    getFromB.append(" GRAPH <").append(gr_c.getGraphB()).append("> { {?o ?p ?o1} OPTIONAL { ?o1 ?p4 ?o3 . OPTIONAL { ?o3 ?p5 ?o4 . OPTIONAL { ?o4 ?p6 ?o5 .} } } }\n");
                } else {
                    getFromB.append(" SERVICE <" + gr_c.getEndpointB() + "> { GRAPH <").append(gr_c.getGraphB()).append("> { {?o ?p ?o1} OPTIONAL { ?o1 ?p4 ?o3 . OPTIONAL { ?o3 ?p5 ?o4 . OPTIONAL { ?o4 ?p6 ?o5 .} } } } }\n");
                }
                getFromB.append("\n");
                getFromB.append("  FILTER(!regex(?p,\"http://www.opengis.net/ont/geosparql#hasGeometry\",\"i\")) \n");
                getFromB.append("  FILTER(!regex(?p, \"http://www.opengis.net/ont/geosparql#asWKT\", \"i\"))\n");
                getFromB.append("  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#lat\", \"i\")) \n");
                getFromB.append("  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#long\", \"i\"))\n");
                getFromB.append("  FILTER(!regex(?p4,\"http://www.opengis.net/ont/geosparql#hasGeometry\",\"i\")) \n");
                getFromB.append("  FILTER(!regex(?p4, \"http://www.opengis.net/ont/geosparql#asWKT\", \"i\"))\n");
                getFromB.append("  FILTER(!regex(?p4, \"http://www.w3.org/2003/01/geo/wgs84_pos#lat\", \"i\")) \n");
                getFromB.append("  FILTER(!regex(?p4, \"http://www.w3.org/2003/01/geo/wgs84_pos#long\", \"i\"))\n");
                getFromB.append("}");

                System.out.println("GET FROM B \n" + getFromB);
                System.out.println("GET FROM B \n" + getFromA);
             
                // Populate with data from the Sample Liink set
                try (PreparedStatement populateDataA = virt_conn.prepareStatement(getFromA.toString());
                        PreparedStatement populateDataB = virt_conn.prepareStatement(getFromB.toString())) {
                    //starttime = System.nanoTime();

                    populateDataA.executeUpdate();
                    populateDataB.executeUpdate();

                } catch (SQLException ex) {

                    LOG.trace("SQLException thrown during temp graph populating");
                    LOG.debug("SQLException thrown during temp graph populating : " + ex.getMessage());
                    LOG.debug("SQLException thrown during temp graph populating : " + ex.getSQLState());

                    success = false;

                }

            //endtime = System.nanoTime();
            //LOG.info("Metadata main parsed in " + (endtime - starttime) / 1000000000f);
            //i += Constants.BATCH_SIZE;
            //count++;
            //
            }
            
            foundA.clear();
            foundB.clear();
            for (int i = 0; i < optDepth + 1; i++) {
                
                // Dynamically construct properties query
                StringBuilder query = new StringBuilder();
                query.append("sparql SELECT distinct(?s) ?pa1 ?oa1 ");
                for (int j = 0; j < i; j++) {
                    int ind = j + 2;
                    int prev = ind - 1;
                    query.append("?pa").append(ind).append(" ?oa").append(ind).append(" ");
                }
                query.append("?pb1 ?ob1 ");
                for (int j = 0; j < i; j++) {
                    int ind = j + 2;
                    query.append("?pb").append(ind).append(" ?ob").append(ind).append(" ");
                }
                query.append(" WHERE \n {\n");
                query.append("SELECT ?s ?pa1 ?oa1 ");
                for (int j = 0; j < i; j++) {
                    int ind = j + 2;
                    int prev = ind - 1;
                    query.append("?pa").append(ind).append(" ?oa").append(ind).append(" ");
                }
                query.append("?pb1 ?ob1 ");
                for (int j = 0; j < i; j++) {
                    int ind = j + 2;
                    int prev = ind - 1;
                    query.append("?pb").append(ind).append(" ?ob").append(ind).append(" ");
                }
                if (gr_c.isDominantA()) {
                    query.append("\nWHERE\n{\n  GRAPH <" + gr_c.getSampleLinksGraph() + "> { ?s ?p ?o }\n");
                } else {
                    query.append("\nWHERE\n{\n  GRAPH <" + gr_c.getSampleLinksGraph() + "> { ?o ?p ?s }\n");
                }
                query.append(" { GRAPH <").append(gr_c.getMetadataGraphA()).append("> { {?s ?pa1 ?oa1} ");
                for (int j = 0; j < i; j++) {
                    int ind = j + 2;
                    int prev = ind - 1;
                    query.append(" . ?oa").append(prev).append(" ?pa").append(ind).append(" ?oa").append(ind).append("  ");
                }
                for (int j = 0; j < i; j++) {
                    query.append(" ");
                }
                query.append("}\n } UNION { \n" + "   GRAPH <").append(gr_c.getMetadataGraphB()).append("> { {?s ?pb1 ?ob1} ");
                for (int j = 0; j < i; j++) {
                    int ind = j + 2;
                    int prev = ind - 1;
                    query.append(" . ?ob").append(prev).append(" ?pb").append(ind).append(" ?ob").append(ind).append("  ");
                }
                for (int j = 0; j < i; j++) {
                    query.append(" ");
                }
                query.append("} }\n"
                        + "}\n"
                        + "} ORDER BY (?s)");

                System.out.println("Properties Query : " + query.toString());
                
                String prevSubject = "";
                try (PreparedStatement fetchProperties = virt_conn.prepareStatement(query.toString());
                        ResultSet propertiesRS = fetchProperties.executeQuery()) {

                    List<String> chainA = new ArrayList<>();
                    List<String> chainB = new ArrayList<>();
                    List<String> objectChainA = new ArrayList<>();
                    List<String> objectChainB = new ArrayList<>();
                        
                    while (propertiesRS.next()) {
                        final String subject = propertiesRS.getString(1);
                        //propertiesRS.
                        if (!prevSubject.equals(subject) && !prevSubject.equals("")) {
                        //if (i == optDepth) {
                            //System.out.println(subject);
                            scanMatches();
                            propertiesA.clear();
                            propertiesB.clear();
                            //}
                        }
                        
                        chainA.clear();
                        chainB.clear();
                        objectChainA.clear();
                        objectChainB.clear();
                        
                        for (int j = 0; j <= i; j++) {
                            int step_over = 2 * (i + 1);

                            final String predicateA = propertiesRS.getString(2 * (j + 1));
                            final String objectA = propertiesRS.getString(2 * (j + 1) + 1);
                            final String predicateB = propertiesRS.getString(2 * (j + 1) + step_over);
                            final String objectB = propertiesRS.getString(2 * (j + 1) + 1 + step_over);
                            /*if (objectA != null) {
                             System.out.println("Object A "+objectA+" "+PATTERN_INT.asPredicate().test(objectA));
                             }
                             if (objectB != null) {
                             System.out.println("Object B "+objectB+" "+PATTERN_INT.asPredicate().test(objectB));
                             }*/

                            if (predicateA != null) {
                                //predicateA = URLDecoder.decode(predicateA, "UTF-8");
                            }
                            if (predicateB != null) {
                                //predicateB = URLDecoder.decode(predicateB, "UTF-8");
                            }

                            if (predicateA != null) {
                                if (!nonMatchedPropertiesA.contains(predicateA)) {
                                    //nonMatchedPropertiesA.add(predicateA);
                                }
                                if (!uniquePropertiesA.contains(predicateA)) {
                                    uniquePropertiesA.add(predicateA);
                                }
                                if (predicateA.contains("posSeq")) {
                                    //continue;
                                }
                                if (predicateA.contains("asWKT")) {
                                    //continue;
                                }
                                if (predicateA.contains("geometry")) {
                                    //continue;
                                }
                            }
                            if (predicateB != null) {
                                if (!nonMatchedPropertiesB.contains(predicateB)) {
                                    //nonMatchedPropertiesB.add(predicateB);
                                }
                                if (!uniquePropertiesB.contains(predicateB)) {
                                    uniquePropertiesB.add(predicateB);
                                }
                                if (predicateB.contains("posSeq")) {
                                    //continue;
                                }
                                if (predicateB.contains("asWKT")) {
                                    //continue;
                                }
                                if (predicateB.contains("geometry")) {
                                    //continue;
                                }
                            }

                            chainA.add(predicateA);
                            objectChainA.add(objectA);
                            chainB.add(predicateB);
                            objectChainB.add(objectB);

                        //System.out.println(" "+predicateA+" "+objectA);
                            //System.out.println(" "+predicateB+" "+objectB);
                        }
                        
                    //System.out.println("Chain A "+chainA);
                        //System.out.println("Chain B "+chainB);
                        
                        scanChain(propertiesA, chainA, objectChainA);
                        scanChain(propertiesB, chainB, objectChainB);

                        prevSubject = subject;
                    }
                } catch (SQLException ex) {
                    LOG.trace("SQLException thrown during set up of the Dictionary");
                    LOG.debug("SQLException thrown during set up of the Dictionary : \n" + ex.getMessage());
                    LOG.debug("SQLException thrown during set up of the Dictionary : \n" + ex.getSQLState());

                    return null;
                }
                
                scanMatches();
                propertiesA.clear();
                propertiesB.clear();

            }
        }

        // Close WordNet resources
        if (JWNL.isInitialized()) {
            JWNL.shutdown();
        }
        
        // A frequncy map of ontology substring in each predicate
        HashMap<String, Integer> freqMap = Maps.newHashMap();
        
        // Find dominant ontology in Dataset A
        for (String key : uniquePropertiesA) {
            //System.out.println(key);
            
            // Get meaningful part
            String onto = Utilities.getPredicateOntology(key);
            
            // If previously encountered, bump the number
            if (freqMap.containsKey(onto)) {
                freqMap.put(onto, freqMap.get(onto) + 1);
            } else {
                freqMap.put(onto, 1);
            }
            
        }
        
        int max = -1;
        String domOntologyA = "";
        for (Map.Entry<String, Integer> entry : freqMap.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            if (value > max) {
                if (key.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns")) {
                    continue;
                }
                max = value;
                domOntologyA = key;
            }
            //System.out.println("Entry "+key+" : "+value);
        }
        
        // Clear frequencies
        freqMap.clear();
        
        // Find dominant ontology in Dataset A
        for (String key : uniquePropertiesB) {
            //System.out.println(key);
            String onto = StringUtils.substringBefore(key, "#");
            onto = onto.concat("#");
            if (onto.equals(key)) {
                onto = StringUtils.substring(key, 0, StringUtils.lastIndexOf(key, "/"));
                onto = onto.concat("/");
            }
            //System.out.println("Onto "+onto+" "+StringUtils.lastIndexOf(key, "/"));
            if (freqMap.containsKey(onto)) {
                freqMap.put(onto, freqMap.get(onto) + 1);
            } else {
                freqMap.put(onto, 1);
            }
        }
        
        max = -1;
        String domOntologyB = "";
        for (Map.Entry<String, Integer> entry : freqMap.entrySet()) {
            String key = entry.getKey();
            Integer value = (Integer) entry.getValue();
            if (value > max) {
                if (key.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns")) {
                    continue;
                }
                max = value;
                domOntologyB = key;
            }
            //System.out.println("Entry "+key+" : "+value);
        }

        //System.out.println("Dominant from A "+domOntologyA+", Dominant from B "+domOntologyB);
        //System.out.println();
        //for (String key : uniquePropertiesB) {
        //    System.out.println(key);
        //}
        //System.out.println("Found A");
        
        /*
        Iterator iter = foundA.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry pairs = (Map.Entry) iter.next();
            HashSet<ScoredMatch> set = (HashSet<ScoredMatch>) pairs.getValue();
            //System.out.println("KEY: "+pairs.getKey());
            for (ScoredMatch s : set) {
                //System.out.println(s.getRep());
            }
        }
        //System.out.println("Found B");
        iter = foundB.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry pairs = (Map.Entry) iter.next();
            HashSet<ScoredMatch> set = (HashSet<ScoredMatch>) pairs.getValue();
                //System.out.println("KEY: "+pairs.getKey());
            //for(ScoredMatch s : set) {
            //System.out.println(s.getRep());
            //}
        }
        */
        
        return new SchemaMatchState(foundA, foundB, domOntologyA, domOntologyB, nonMatchedPropertiesA, nonMatchedPropertiesB);
    }

    //int maxParentDepth = 3;
    private int scanSense(Synset i, Synset j) {
        //RelationshipList list = RelationshipFinder.getInstance().findRelationships(i, j, PointerType.HYPERNYM);
        //RelationshipList listLvl1 = RelationshipFinder.getInstance().findRelationships(i, j, PointerType.HYPERNYM, 1);
        RelationshipList listLvl2;
        try {
            listLvl2 = RelationshipFinder.getInstance().findRelationships(i, j, PointerType.HYPERNYM, wordnetDepth);
        } catch (JWNLException ex) {
            return -1;
        }
	//System.out.println("Hypernym relationship between \"" + start.getLemma() + "\" and \"" + end.getLemma() + "\":");
        //int ret = -1;
        int tom;
        //int count = 0;
        int min = 999999;

        for (Object o : listLvl2) {
            //System.out.println("List Size " + list.size());
            //count++;
            Relationship rel = (Relationship) o;
            tom = ((AsymmetricRelationship) rel).getCommonParentIndex();
            if (tom < min) {
                min = tom;
            }
            //System.out.println("Common Parent Index: " + tom);
            //System.out.println("Depth 2: " + rel.getDepth());
            //rel.getNodeList().print();
        }

        if (min > maxParentDepth) {
            return -1;
        } else {
            return min;
        }
    }

    private float calculateAsymmetricRelationshipOperation(IndexWord start, IndexWord end, SchemaMatcher m) {
		// Try to find a relationship between the first sense of <var>start</var> and the first sense of <var>end</var>
        //System.out.println("Asymetric relationship between \"" + start.getLemma() + "\" and \"" + end.getLemma() + "\":");	
        if (start == null || end == null) {
            return (float) 0.0;
        }
        //System.out.println("Immediate Relationship "+RelationshipFinder.getInstance().getImmediateRelationship(start, end));
        //System.out.println("Immediate Relationship "+RelationshipFinder.getInstance().getImmediateRelationship(end, start));
        if (start.getLemma().equals(end.getLemma())) {
            m.matches.add(end.getLemma());
            return (float) 1.0;
        }
        //System.out.println("NEW WORD");
        //System.out.print(start.getLemma()+" VS "); 
        //System.out.println(end.getLemma()); 
        Synset[] setA = null;
        Synset[] setB = null;
        try {
            setA = start.getSenses();
            setB = end.getSenses();
        } catch (JWNLException ex) {
            
        }
        
        if (setA == null || setB == null) {
            return (float) 0.0;
        }
        int min = 99999;

        int total = 0;
        int count = 0;
        for (Synset i : setA) {
            //System.out.println("YOLO 2 "+i);
            //System.out.println("START SYM");
            Word[] cruise = i.getWords();
            for (Word al : cruise) {
                //System.out.print(al.getLemma()+" ");
            }
            //System.out.println();
            //System.out.println("END SYM");

            count++;
            for (Synset j : setB) {
                //System.out.println("Header "+i.getLexFileName()+" VS "+j.getGloss());
                //System.out.println("YOLO 3 "+j);
                int ret = scanSense(i, j);
                if (ret < 0) {
                    continue;
                }
                //System.out.println("RETURN "+ret);
                if (ret <= min) {
                    //if(ret < min)
                    //System.out.println();
                    min = ret;
                        //System.out.println("MIN "+ret);
                    //System.out.println("sim("+start.getLemma() + "," + end.getLemma()+") = "+1.0/ret);
                    //System.out.println("MIN\n"+i+"\n"+j);

                    //System.out.println();
                    for (Word relevant : j.getWords()) {
                        m.matches.add(relevant.getLemma());
                    }
                }
                //total = ret;
            }
        }

        //System.out.println("END WORD "+maxParentDepth);
        if (min > maxParentDepth) {
            return (float) 0;
        } else if (min == 0) {
            return (float) 0;
        } else {
            //return (float) (1.0f - (min/(float)maxParentDepth) );
            //System.out.println("MIN : "+min);
            //ystem.out.println("POW : "+Math.pow((float) (1.0f - (min/(float)maxParentDepth) ), raiseToPower));
            //System.out.println("THE POW : "+raiseToPower);
            return (float) Math.pow((float) (1.0f - (min / (float) (maxParentDepth + 1))), raiseToPower);
        }
    }

    public void printChains() {
        Iterator it = propertiesA.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry) it.next();
            //System.out.println(pairs.getKey() + " = " + (MetadataChain)pairs.getValue());
            it.remove(); // avoids a ConcurrentModificationException
        }
    }

    private String getJWNL(String pathToWordnet) {

        String jwnlXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jwnl_properties language=\"en\">\n"
                + "	<version publisher=\"Princeton\" number=\"3.0\" language=\"en\"/>\n"
                + "	<dictionary class=\"net.didion.jwnl.dictionary.FileBackedDictionary\">\n"
                + "		<param name=\"morphological_processor\" value=\"net.didion.jwnl.dictionary.morph.DefaultMorphologicalProcessor\">\n"
                + "			<param name=\"operations\">\n"
                + "				<param value=\"net.didion.jwnl.dictionary.morph.LookupExceptionsOperation\"/>\n"
                + "				<param value=\"net.didion.jwnl.dictionary.morph.DetachSuffixesOperation\">\n"
                + "					<param name=\"noun\" value=\"|s=|ses=s|xes=x|zes=z|ches=ch|shes=sh|men=man|ies=y|\"/>\n"
                + "					<param name=\"verb\" value=\"|s=|ies=y|es=e|es=|ed=e|ed=|ing=e|ing=|\"/>\n"
                + "					<param name=\"adjective\" value=\"|er=|est=|er=e|est=e|\"/>\n"
                + "                    <param name=\"operations\">\n"
                + "                        <param value=\"net.didion.jwnl.dictionary.morph.LookupIndexWordOperation\"/>\n"
                + "                        <param value=\"net.didion.jwnl.dictionary.morph.LookupExceptionsOperation\"/>\n"
                + "                    </param>\n"
                + "				</param>\n"
                + "				<param value=\"net.didion.jwnl.dictionary.morph.TokenizerOperation\">\n"
                + "					<param name=\"delimiters\">\n"
                + "						<param value=\" \"/>\n"
                + "						<param value=\"-\"/>\n"
                + "					</param>\n"
                + "					<param name=\"token_operations\">\n"
                + "                        <param value=\"net.didion.jwnl.dictionary.morph.LookupIndexWordOperation\"/>\n"
                + "						<param value=\"net.didion.jwnl.dictionary.morph.LookupExceptionsOperation\"/>\n"
                + "						<param value=\"net.didion.jwnl.dictionary.morph.DetachSuffixesOperation\">\n"
                + "							<param name=\"noun\" value=\"|s=|ses=s|xes=x|zes=z|ches=ch|shes=sh|men=man|ies=y|\"/>\n"
                + "							<param name=\"verb\" value=\"|s=|ies=y|es=e|es=|ed=e|ed=|ing=e|ing=|\"/>\n"
                + "							<param name=\"adjective\" value=\"|er=|est=|er=e|est=e|\"/>\n"
                + "                            <param name=\"operations\">\n"
                + "                                <param value=\"net.didion.jwnl.dictionary.morph.LookupIndexWordOperation\"/>\n"
                + "                                <param value=\"net.didion.jwnl.dictionary.morph.LookupExceptionsOperation\"/>\n"
                + "                            </param>\n"
                + "						</param>\n"
                + "					</param>\n"
                + "				</param>\n"
                + "			</param>\n"
                + "		</param>\n"
                + "		<param name=\"dictionary_element_factory\" value=\"net.didion.jwnl.princeton.data.PrincetonWN17FileDictionaryElementFactory\"/>\n"
                + "		<param name=\"file_manager\" value=\"net.didion.jwnl.dictionary.file_manager.FileManagerImpl\">\n"
                + "			<param name=\"file_type\" value=\"net.didion.jwnl.princeton.file.PrincetonRandomAccessDictionaryFile\"/>\n"
                + "			<param name=\"dictionary_path\" value=\"" + pathToWordnet + "\"/>\n"
                + "		</param>\n"
                + "	</dictionary>\n"
                + "	<resource class=\"PrincetonResource\"/>\n"
                + "</jwnl_properties>";

        return jwnlXML;
    }

    public void insertLinksMetadata(List<Link> links) { //metadata go in the graphA, not the new one. the method is called without the fusedGraph from FuserWorker
        //keep metadata subjects according to the transformation    
        /*
         long starttime, endtime;
         createLinksGraph(links);
         String getFromB;
         createDelWGSGraph(((FileBulkLoader)trh).getDelWGSList());
         createDelGeomGraph(((FileBulkLoader)trh).getDelGeomList());
         starttime = System.nanoTime();
        
         boolean isMyDesiredIp = false;
        
         final URI u;
         try {
         u = new URI("https://help.github.com/articles/error-permission-denied-publickey");
         // URI u = new URI("/works/with/me/too");
         // URI u = new URI("/can/../do/./more/../sophis?ticated=stuff+too");
         if(u.isAbsolute())
         {
         //System.out.println("Yes, i am absolute!");
         }
         else
         {
         //System.out.println("Ohh noes, it's a relative URI!");
         }
         try {
         InetAddress localhost = InetAddress.getLocalHost();
         LOG.info(" IP Addr: " + localhost.getHostAddress());
         // Just in case this host has multiple IP addresses....
         InetAddress[] allMyIps = InetAddress.getAllByName(localhost.getCanonicalHostName());
         if (allMyIps != null && allMyIps.length > 1) {
         LOG.info(" Full list of IP addresses:");
         for (InetAddress allMyIp : allMyIps) {
         LOG.info("    " + allMyIp);
         }
         }
         } catch (UnknownHostException e) {
         LOG.info(" (error retrieving server host name)");
         }

         try {
         LOG.info("Full list of Network Interfaces:");
         for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
         NetworkInterface intf = en.nextElement();
         LOG.info("    " + intf.getName() + " " + intf.getDisplayName());
         for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
         LOG.info("        " + enumIpAddr.nextElement().toString());
         }
         }
         } catch (SocketException e) {
         LOG.info(" (error retrieving network interface list)");
         }
         } catch (URISyntaxException ex) {
         java.util.logging.Logger.getLogger(VirtuosoImporter.class.getName()).log(Level.SEVERE, null, ex);
         }

         if (endpointLoc.equals(endpointB)) {
         getFromB = "INSERT\n"
         + "  { GRAPH <"+targetGraph+"> {\n"
         + " ?s ?p ?o1 . \n"
         + " ?o1 ?p4 ?o3 .\n"
         + " ?o3 ?p5 ?o4\n"
         + "} }\nWHERE\n"
         + "{\n"
         + " GRAPH <"+gr_c.getLinksGraph() +"> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } .\n"
         + " GRAPH <"+graphB+"> { {?o ?p ?o1} OPTIONAL { ?o1 ?p4 ?o3. } OPTIONAL { ?o3 ?p5 ?o4 .} }\n"
         + "\n"
         + "  FILTER(!regex(?p,\"http://www.opengis.net/ont/geosparql#hasGeometry\",\"i\")) \n"
         + "  FILTER(!regex(?p, \"http://www.opengis.net/ont/geosparql#asWKT\", \"i\"))\n"
         + "  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#lat\", \"i\")) \n"
         + "  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#long\", \"i\"))\n"
         + "}";
         } else {
         getFromB = "SELECT ?s ?p ?o1 ?p4 ?o3 ?p5 ?o4\n"
         + "WHERE\n"
         + "{\n"
         + " SERVICE <"+endpointLoc+"> { GRAPH <"+gr_c.getLinksGraph()+ "> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } }\n"
         + " GRAPH <"+graphB+"> { {?o ?p ?o1} OPTIONAL { ?o1 ?p4 ?o3. } OPTIONAL { ?o3 ?p5 ?o4 .} }\n"
         + "\n"
         + "  FILTER(!regex(?p,\"http://www.opengis.net/ont/geosparql#hasGeometry\",\"i\")) \n"
         + "  FILTER(!regex(?p, \"http://www.opengis.net/ont/geosparql#asWKT\", \"i\"))\n"
         + "  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#lat\", \"i\")) \n"
         + "  FILTER(!regex(?p, \"http://www.w3.org/2003/01/geo/wgs84_pos#long\", \"i\"))\n"
         + "}";
         }
        
         //int count = 0;
         int i = 0;
         while (i < links.size()) {
            
         createLinksGraphBatch(links, i);            
         starttime =  System.nanoTime();           
         UpdateRequest insertFromB = UpdateFactory.create(getFromB);
         UpdateProcessor insertRemoteB = UpdateExecutionFactory.createRemoteForm(insertFromB, endpointB);
         insertRemoteB.execute();
        
         endtime =  System.nanoTime();
         LOG.info("Metadata parsed in "+(endtime-starttime)/1000000000f);
         i += Constants.BATCH_SIZE;
         //count++;
         }
         endtime =  System.nanoTime();
         LOG.info(ANSI_YELLOW+"Metadata parsed in "+((endtime-starttime)/1000000000f)+""+ANSI_RESET);
         */
    }

     /**
     * Use the Triple Handler to upload data to Virtuoso
     *
     */
    public void finishUpload() {
        trh.finish();
    }
    
     /**
     * Create connection to Virtuoso through Jena
     *
     */
    private VirtGraph getVirtuosoSet(String graph, String url, String username, String password) {
        //Class.forName("virtuoso.jdbc4.Driver");
        VirtGraph vSet = null;
        try {
            vSet = new VirtGraph(graph, "jdbc:virtuoso://" + url + "/CHARSET=UTF-8", username, password);
        } catch (JenaException ex) {
            LOG.trace("JenaException thrown during VirtGraph creatinon");
            LOG.debug("JenaException thrown during VirtGraph creatinon : " + ex.getMessage());
        }
        
        return vSet;
    }

    /**
     * Clean up Virtuoso Importer
     *
     * @return success
     */
    public boolean clean() {
        boolean success = true;
        try {
            set.close();
        } catch (JenaException ex) {
            LOG.trace("JenaException thrown during VirtGraph cleanup");
            LOG.debug("JenaException thrown during VirtGraph cleanup : " + ex.getMessage());

            success = false;
        }
        //LOG.info(ANSI_YELLOW+"Virtuoso import is done."+ANSI_RESET);

        return success;

    }

    /**
     * Virtuoso specific PL/SQL Never should have gone that way.......
     *
     * @deprecated Learn from our mistakes...
     */
    private void clearBulkLoadHistory() {
        //PreparedStatement clearBulkLoadTblStmt;
        //clearBulkLoadTblStmt = virt_conn.prepareStatement(clearBulkLoadTbl);                        
        //clearBulkLoadTblStmt.executeUpdate();
    }

    /**
     * Create a a graph holding a batch of links starting at nextIndex
     *
     * @param lst A List of Link objects to be inserted.
     * @param nextIndex Offset in the list
     */
    private boolean createLinksGraphBatch(List<Link> lst, int nextIndex) {
        boolean success = true;

        final String dropGraph = "sparql DROP SILENT GRAPH <" + gr_c.getLinksGraph() + ">";
        final String createGraph = "sparql CREATE GRAPH <" + gr_c.getLinksGraph() + ">";

        PreparedStatement dropStmt = null;
        PreparedStatement createStmt = null;
        try {
            long starttime, endtime;
            dropStmt = virt_conn.prepareStatement(dropGraph);
            dropStmt.execute();

            createStmt = virt_conn.prepareStatement(createGraph);
            createStmt.execute();

        } catch (SQLException ex) {

            LOG.trace("SQLException thrown during temp graph creation");
            LOG.debug("SQLException thrown during temp graph creation : " + ex.getMessage());
            LOG.debug("SQLException thrown during temp graph creation : " + ex.getSQLState());

            success = false;

            return success;

        } finally {
            try {
                if (dropStmt != null) {
                    dropStmt.close();
                }
                if (createStmt != null) {
                    createStmt.close();
                }
            } catch (SQLException ex) {
                LOG.trace("SQLException thrown during statement closing");
                LOG.debug("SQLException thrown during statement closing : " + ex.getMessage());
                LOG.debug("SQLException thrown during statement closing : " + ex.getSQLState());
            }
        }
        //BulkInsertLinksBatch(lst, nextIndex);
        success = SPARQLInsertLinksBatch(lst, nextIndex);

        //System.out.println("THE BOOL OUT IS " + success);

        return success;
    }

    /**
     * Create a a graph holding all provided links
     *
     * @param lst A List of Link objcts to be inserted.
     * @return success
     */
    public boolean createLinksGraph(List<Link> lst) {
        boolean success = true;

        final String dropGraph = "SPARQL DROP SILENT GRAPH <" + gr_c.getAllLinksGraph() + ">";
        final String createGraph = "SPARQL CREATE GRAPH <" + gr_c.getAllLinksGraph() + ">";

        PreparedStatement dropStmt = null;
        PreparedStatement createStmt = null;
        try {
            long starttime, endtime;
            dropStmt = virt_conn.prepareStatement(dropGraph);
            dropStmt.execute();

            createStmt = virt_conn.prepareStatement(createGraph);
            createStmt.execute();

        } catch (SQLException ex) {

            LOG.trace("SQLException thrown during temp graph creation");
            LOG.debug("SQLException thrown during temp graph creation : " + ex.getMessage());
            LOG.debug("SQLException thrown during temp graph creation : " + ex.getSQLState());

            success = false;

            return success;

        } finally {
            try {
                if (dropStmt != null) {
                    dropStmt.close();
                }
                if (createStmt != null) {
                    createStmt.close();
                }
            } catch (SQLException ex) {
                LOG.trace("SQLException thrown during statement closing");
                LOG.debug("SQLException thrown during statement closing : " + ex.getMessage());
                LOG.debug("SQLException thrown during statement closing : " + ex.getSQLState());
            }
        }

        //BulkInsertLinks(lst);
        success = SPARQLInsertLinks(lst);

        return success;
    }

    /**
     * Insert Links thorugh files
     *
     * @deprecated Old insert API thorugh files. Required changes in Virtuoso
     * INI
     * @param lst A List of Link objects.
     * @param nextIndex Offset in the list
     */
    @Deprecated
    private void BulkInsertLinksBatch(List<Link> lst, int nextIndex) {
        /*
         long starttime, endtime;
         set2 = getVirtuosoSet(""+gr_c.getLinksGraph()+ "", db_c.getDBURL(), db_c.getUsername(), db_c.getPassword());
         BulkUpdateHandler buh2 = set2.getBulkUpdateHandler();
         LOG.info(ANSI_YELLOW+"Loaded "+lst.size()+" links"+ANSI_RESET);
        
         starttime = System.nanoTime();
         File f = new File(bulkInsertDir+"bulk_inserts/selected_links.nt");
         if (f.exists()){
         f.delete();
         }  
        
         //f.mkdirs();
         //f.getParentFile().mkdirs();
         PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)));
         links_graph = gr_c.getAllLinksGraph();
         String dir = bulkInsertDir.replace("\\", "/");
         //System.out.println("DIRECTORY "+dir);
         final String bulk_insert = "DB.DBA.TTLP_MT (file_to_string_output ('"+dir+"bulk_inserts/selected_links.nt'), '', "+"'"+links_graph+"')";
        
         int i = nextIndex;
         //System.out.println(i);
         if ( lst.size() > 0 ) {
         while (i < lst.size() && i < nextIndex + Constants.BATCH_SIZE ) {
         Link link = lst.get(i);
         String triple = "<"+link.getNodeA()+"> <"+Constants.SAME_AS+"> <"+link.getNodeB()+"> .";
        
         out.println(triple);
         i++;
         }
         out.close();
            
         PreparedStatement uploadBulkFileStmt;
         uploadBulkFileStmt = virt_conn.prepareStatement(bulk_insert);                        
         uploadBulkFileStmt.executeUpdate();
         }
         //System.out.println(i);
         endtime =  System.nanoTime();
         LOG.info(ANSI_YELLOW+"Links Graph created in "+((endtime-starttime)/1000000000f)+""+ANSI_RESET);
        
         starttime = System.nanoTime();
         virt_conn.commit();
         endtime =  System.nanoTime();
         LOG.info(ANSI_YELLOW+"Links Graph created in "+((endtime-starttime)/1000000000f)+""+ANSI_RESET);
         */
    }

    /**
     * Bulk Insert a batch of Links thrugh SPARQL
     *
     * @param lst A List of Link objects.
     * @param nextIndex Offset in the list
     * @return success
     */
    private boolean SPARQLInsertLinksBatch(List<Link> l, int nextIndex) {
        boolean success = true;

        StringBuilder sb = new StringBuilder();
        sb.append("SPARQL WITH <" + gr_c.getLinksGraph() + "> INSERT {");
        sb.append("`iri(??)` <" + Constants.SAME_AS + "> `iri(??)` . } ");
        System.out.println("Statement " + sb.toString());
        VirtuosoConnection conn = (VirtuosoConnection) set.getConnection();

        try {
            try (VirtuosoPreparedStatement vstmt = (VirtuosoPreparedStatement) conn.prepareStatement(sb.toString())) {
                int start = nextIndex;
                int end = nextIndex + Constants.BATCH_SIZE;
                if (end > l.size()) {
                    end = l.size();
                }

                for (int i = start; i < end; ++i) {
                    Link link = l.get(i);
                    vstmt.setString(1, link.getNodeA());
                    vstmt.setString(2, link.getNodeB());

                    vstmt.addBatch();
                }

                vstmt.executeBatch();
            }
        } catch (VirtuosoException ex) {

            LOG.trace("VirtuosoException thrown during batch link graph creation");
            LOG.debug("VirtuosoException thrown during batch link graph creation : " + ex.getMessage());
            LOG.debug("VirtuosoException thrown during batch link graph creation : " + ex.getSQLState());

            success = false;

            return success;

        } catch (BatchUpdateException ex) {
            LOG.trace("BatchUpdateException thrown during batch link graph creation");
            LOG.debug("BatchUpdateException thrown during batch link graph creation : " + ex.getMessage());
            LOG.debug("BatchUpdateException thrown during batch link graph creation : " + ex.getSQLState());

            success = false;

            return success;
        }
        
        System.out.println("THE BOOL IN IS " + success);

        return success;
    }

    /**
     * Bulk Insert ALL Links thrugh SPARQL
     *
     * @param lst A List of Link objects.
     * @return success
     */
    private boolean SPARQLInsertLinks(List<Link> l) {
        boolean success = true;

        StringBuilder sb = new StringBuilder();
        sb.append("SPARQL WITH <" + gr_c.getAllLinksGraph() + "> INSERT {");
        sb.append("`iri(??)` <" + Constants.SAME_AS + "> `iri(??)` . } ");
        System.out.println("Statement " + sb.toString());
        VirtuosoConnection conn = (VirtuosoConnection) set.getConnection();

        try {
            try (VirtuosoPreparedStatement vstmt = (VirtuosoPreparedStatement) conn.prepareStatement(sb.toString())) {
                int start = 0;
                int end = l.size();

                for (int i = start; i < end; ++i) {
                    Link link = l.get(i);
                    vstmt.setString(1, link.getNodeA());
                    vstmt.setString(2, link.getNodeB());

                    vstmt.addBatch();
                }

                vstmt.executeBatch();
            }
        } catch (VirtuosoException ex) {

            LOG.trace("VirtuosoException thrown during link graph creation");
            LOG.debug("VirtuosoException thrown during link graph creation : " + ex.getMessage());
            LOG.debug("VirtuosoException thrown during link graph creation : " + ex.getSQLState());

            success = false;

            return success;

        } catch (BatchUpdateException ex) {
            LOG.trace("BatchUpdateException thrown during link graph creation");
            LOG.debug("BatchUpdateException thrown during link graph creation : " + ex.getMessage());
            LOG.debug("BatchUpdateException thrown during link graph creation : " + ex.getSQLState());

            success = false;

            return success;
        }

        return success;
    }

    /**
     * Insert ALL Links thorugh files
     *
     * @deprecated Old insert API thorugh files. Required changes in Virtuoso
     * INI
     * @param lst A List of Link objects.
     */
    @Deprecated
    private void BulkInsertLinks(List<Link> lst) {
        /*
         set2 = getVirtuosoSet(gr_c.getAllLinksGraph(), db_c.getDBURL(), db_c.getUsername(), db_c.getPassword());
         BulkUpdateHandler buh2 = set2.getBulkUpdateHandler();
         LOG.info(ANSI_YELLOW+"Loaded "+lst.size()+" links"+ANSI_RESET);
         long starttime, endtime;
        
         starttime = System.nanoTime();
         File f = new File(bulkInsertDir+"bulk_inserts/selected_links.nt");
         //f.mkdirs();
         //f.getParentFile().mkdirs();
         PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)));
         String dir = bulkInsertDir.replace("\\", "/");
         //System.out.println("DIRECTORY "+dir);
         final String bulk_insert = "DB.DBA.TTLP_MT (file_to_string_output ('"+dir+"bulk_inserts/selected_links.nt'), '', "
         +"'"+ gr_c.getAllLinksGraph()+"')";
         //int stop = 0;
         if ( lst.size() > 0 ) {
            
         for(Link link : lst) {
         //if (stop++ > 1000) break;
         String triple = "<"+link.getNodeA()+"> <"+Constants.SAME_AS+"> <"+link.getNodeB()+"> .";
        
         out.println(triple);
         }
            
         out.close();
            
         PreparedStatement uploadBulkFileStmt;
         uploadBulkFileStmt = virt_conn.prepareStatement(bulk_insert);                        
         uploadBulkFileStmt.executeUpdate();
         }
        
         endtime =  System.nanoTime();
         LOG.info(ANSI_YELLOW+"Links Graph created in "+((endtime-starttime)/1000000000f)+""+ANSI_RESET);
        
         starttime = System.nanoTime();

         virt_conn.commit();
         //endtime =  System.nanoTime();
         LOG.info(ANSI_YELLOW+"Links Graph created in "+((endtime-starttime)/1000000000f)+""+ANSI_RESET);
         */
    }

    /**
     * Create a a graph holding the subjects of entities that contained WGS
     * Geometry and need to be deleted
     *
     * @deprecated Worked only on local graphs. Replaced with Updates through
     * SPARQL and JDBC
     * @param lst A List of String subjects to be removed.
     */
    @Deprecated
    private void createDelWGSGraph(List<String> lst) {
        /*
         final String dropGraph = "sparql DROP SILENT GRAPH <http://localhost:8890/DAV/del_wgs>";
         final String createGraph = "sparql CREATE GRAPH <http://localhost:8890/DAV/del_wgs>";
    
         PreparedStatement dropStmt;
         long starttime, endtime;
         dropStmt = virt_conn.prepareStatement(dropGraph);
         dropStmt.execute();
        
         PreparedStatement createStmt;
         createStmt = virt_conn.prepareStatement(createGraph);
         createStmt.execute();
         starttime = System.nanoTime();
         File f = new File(bulkInsertDir+"bulk_inserts/deleted_wgs.nt");
         //f.getParentFile().mkdirs();
         PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)));
         String dir = bulkInsertDir.replace("\\", "/");
         //System.out.println("DIRECTORY "+dir);
         final String bulk_insert = "DB.DBA.TTLP_MT (file_to_string_output ('"+dir+"bulk_inserts/deleted_wgs.nt'), '', "+"'"+del_wgs_graph+"')";

         if ( lst.size() > 0 ) {

         for(String sub : lst) {
         String triple = "<"+sub+"> <del> <a> .";
        
         out.println(triple);
         }
         out.close();
            
         PreparedStatement uploadBulkFileStmt;
         uploadBulkFileStmt = virt_conn.prepareStatement(bulk_insert);                        
         uploadBulkFileStmt.executeUpdate();
         }
        
         endtime =  System.nanoTime();
         LOG.info(ANSI_YELLOW+"Delete WGS Graph created in "+((endtime-starttime)/1000000000f)+""+ANSI_RESET);
         */
    }

    /**
     * Create a a graph holding the subjects of entities that contained WKT
     * Geometry and need to be deleted
     *
     * @deprecated Worked only on local graphs. Replaced with Updates through
     * SPARQL and JDBC
     * @param lst A List of String subjects to be removed.
     */
    @Deprecated
    private void createDelGeomGraph(List<String> lst) {
        /*
         final String dropGraph = "sparql DROP SILENT GRAPH <http://localhost:8890/DAV/del_geom>";
         final String createGraph = "sparql CREATE GRAPH <http://localhost:8890/DAV/del_geom>";
    
         PreparedStatement dropStmt;
         long starttime, endtime;
         dropStmt = virt_conn.prepareStatement(dropGraph);
         dropStmt.execute();
        
         PreparedStatement createStmt;
         createStmt = virt_conn.prepareStatement(createGraph);
         createStmt.execute();
         starttime = System.nanoTime();
         File f = new File(bulkInsertDir+"bulk_inserts/deleted_geom.nt");
         //f.getParentFile().mkdirs();
         PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)));
         String dir = bulkInsertDir.replace("\\", "/");
         //System.out.println("DIRECTORY "+dir);
         final String bulk_insert = "DB.DBA.TTLP_MT (file_to_string_output ('"+dir+"bulk_inserts/deleted_geom.nt'), '', "+"'"+del_geom_graph+"')";

         if ( lst.size() > 0 ) {

         for(String sub : lst) {
         String triple = "<"+sub+"> <del> <"+sub+"_geom> .";
        
         out.println(triple);
         }
         out.close();
            
         PreparedStatement uploadBulkFileStmt;
         uploadBulkFileStmt = virt_conn.prepareStatement(bulk_insert);                        
         uploadBulkFileStmt.executeUpdate();
         }
        
         endtime =  System.nanoTime();
         LOG.info(ANSI_YELLOW+"Delete Geom Graph created in "+((endtime-starttime)/1000000000f)+""+ANSI_RESET);
         */
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

}
