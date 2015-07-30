/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gr.athenainnovation.imis.fagi.gis.service;

import com.google.common.collect.Lists;
import gr.athenainnovation.imis.fusion.gis.core.GeometryFuser;
import gr.athenainnovation.imis.fusion.gis.core.Link;
import gr.athenainnovation.imis.fusion.gis.gui.workers.DBConfig;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 *
 * @author nick
 */
@WebServlet(name = "PreviewServlet", urlPatterns = {"/PreviewServlet"})
public class PreviewServlet extends HttpServlet {

    private static final String DB_URL = "jdbc:postgresql:";
    private PreparedStatement stmt = null;
    private Connection dbConn = null;
    private ResultSet rs = null;
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        try {
            /* TODO output your page here. You may use following sample code. */
            String[] selectedLinks = request.getParameterValues("links[]");
            HttpSession sess = request.getSession(true);            
            DBConfig dbConf = (DBConfig)sess.getAttribute("db_conf");
            HashMap<String, String> hashLinks = (HashMap<String, String>)sess.getAttribute("links");
            
            System.out.println("Hash "+hashLinks);
            List<Link> lst = new ArrayList<Link>();
            for ( String s : selectedLinks) {
                if (hashLinks.containsKey(s)) {
                    Link l = new Link(s, hashLinks.get(s));
                    lst.add(l);
                }
            }
            
            final GeometryFuser geometryFuser = new GeometryFuser();
            try {
                geometryFuser.connect(dbConf);
                geometryFuser.loadLinks(lst);
            }
            catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            finally {
                geometryFuser.clean();
            } 
            
            try{
                Class.forName("org.postgresql.Driver");     
            } catch (ClassNotFoundException ex) {
                System.out.println(ex.getMessage());      
                out.println("Class of postgis failed");
                out.close();
            
                return;
            }
             
            try {
                String url = DB_URL.concat(dbConf.getDBName());
                dbConn = DriverManager.getConnection(url, dbConf.getDBUsername(), dbConf.getDBPassword());
                //dbConn.setAutoCommit(false);
            } catch(SQLException sqlex) {
                System.out.println(sqlex.getMessage());      
                out.println("Connection to postgis failed");
                out.close();
            
                return;
            }
            
            StringBuilder geomColl = new StringBuilder();
            
            String queryGeomsA = "SELECT links.nodea as la, links.nodeb as lb, ST_asText(a.geom) as g\n" +
                                "FROM links INNER JOIN dataset_a_geometries AS a\n" +
                                "ON (links.nodea = a.subject)";
            
            String queryGeomsB = "SELECT links.nodea as la, links.nodeb as lb, ST_asText(b.geom) as g\n" +
                                 "FROM links INNER JOIN dataset_b_geometries AS b\n" +
                                 "ON (links.nodeb = b.subject)";
                  
            String slectLinkedGeoms = "SELECT links.nodea as la, links.nodeb as lb, ST_asText(a_g) as ga, ST_asText(b_g) as gb\n" +
                                        "FROM links \n" +
                                        "INNER JOIN (SELECT dataset_a_geometries.subject AS a_s,\n" +
                                        "		   dataset_b_geometries.subject AS b_s,\n" +
                                        "		   dataset_a_geometries.geom AS a_g,\n" +
                                        "		   dataset_b_geometries.geom AS b_g\n" +
                                        "		FROM dataset_a_geometries, dataset_b_geometries) AS geoms \n" +
                                        "		ON(links.nodea = geoms.a_s AND links.nodeb = geoms.b_s)";
            
            stmt = dbConn.prepareStatement(slectLinkedGeoms);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                String gb = rs.getString("gb");
                String lb = rs.getString("lb");
                String ga = rs.getString("ga");
                String la = rs.getString("la");
                //System.out.println(la);
                //System.out.println(ga);
                //System.out.println(lb);
                //System.out.println(gb);
                geomColl.append(lb);
                geomColl.append(";");
                geomColl.append(gb);
                geomColl.append(";");
                geomColl.append(la);
                geomColl.append(";");
                geomColl.append(ga);
                geomColl.append(";");
            }
            
            rs.close();
            stmt.close();
            dbConn.close();
            /*
            PreparedStatement stmt = dbConn.prepareStatement(queryGeomsB);
            ResultSet rs = stmt.executeQuery();
                       
            while (rs.next()) {
                String g = rs.getString("g");
                String lb = rs.getString("lb");
                System.out.println(lb);
                geomColl.append(lb);
                geomColl.append(";");
                geomColl.append(g);
                geomColl.append(";");
            }
            
            geomColl.append("::");
            
            stmt = dbConn.prepareStatement(queryGeomsA);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                String g = rs.getString("g");
                String la = rs.getString("la");
                System.out.println(la);
                geomColl.append(la);
                geomColl.append(";");
                geomColl.append(g);
                geomColl.append(";");
            }
            
            geomColl.append("::");
            
            rs.close();
            stmt.close();
            */
            rs.close();
            stmt.close();
            
            out.print(geomColl);
        } finally {
            out.close();
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            processRequest(request, response);
        } catch (SQLException ex) {
            Logger.getLogger(PreviewServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            processRequest(request, response);
        } catch (SQLException ex) {
            Logger.getLogger(PreviewServlet.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException ex) {
                    Logger.getLogger(PreviewServlet.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ex) {
                    Logger.getLogger(PreviewServlet.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            if (dbConn != null) {
                try {
                    dbConn.close();
                } catch (SQLException ex) {
                    Logger.getLogger(PreviewServlet.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}