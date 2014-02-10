package gr.athenainnovation.imis.fusion.gis.gui;

import gr.athenainnovation.imis.fusion.gis.gui.listeners.DBConfigListener;
import gr.athenainnovation.imis.fusion.gis.gui.listeners.ErrorListener;
import gr.athenainnovation.imis.fusion.gis.gui.workers.DBConfig;
import gr.athenainnovation.imis.fusion.gis.gui.workers.Dataset;
import gr.athenainnovation.imis.fusion.gis.gui.workers.ImporterWorker;
import gr.athenainnovation.imis.fusion.gis.postgis.DatabaseInitialiser;
import gr.athenainnovation.imis.fusion.gis.postgis.PostGISImporter;
import gr.athenainnovation.imis.fusion.gis.gui.workers.GraphConfig;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.log4j.Logger;

/**
 * Handles importing of RDF graph into a PostGIS db.
 */
public class ImporterPanel extends javax.swing.JPanel implements DBConfigListener {
    private static final Logger LOG = Logger.getLogger(ImporterPanel.class);
    
    private final ErrorListener errorListener;
    private DBConfig dbConfig;
    private GraphConfig graphConfig;

    private List<DBConfigListener> dbConfigListeners = new ArrayList<>();    
    private String graphB;
    private String graphA;
    private Dataset datasetA;
    private Dataset datasetB;
    
    
    /**
     * Creates new form ImporterPanel
     * @param errorListener error message listener
     */
    public ImporterPanel(final ErrorListener errorListener) {
        super();
        this.errorListener = errorListener;
        initComponents();
        
        setDatasetA(new Dataset(endpointAField.getText(), graphAField.getText(), subjectRegexAField.getText()));
        setDatasetB(new Dataset(endpointAField.getText(), graphBField.getText(), subjectRegexBField.getText()));
    }
    
    
    @Override
    public void notifyNewDBConfiguration(final DBConfig dbConfig) {
        this.dbConfig = dbConfig;

        importButton.setEnabled(true);
    }
    
    @Override
    public void notifyNewGraphConfiguration(final GraphConfig graphConfig) {
        this.graphConfig = graphConfig;

    }
    
    @Override
    public void resetDBConfiguration() {
        importButton.setEnabled(false);
    }
    
    private void reset() {
        datasetAStatusField.setText("Dataset A not done...");
        datasetBStatusField.setText("Dataset B not done...");
        datasetAProgressBar.setValue(0);
        datasetBProgressBar.setValue(0);
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        subjectRegexAField = new javax.swing.JTextField();
        graphAField = new javax.swing.JTextField();
        endpointAField = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        subjectRegexBField = new javax.swing.JTextField();
        graphBField = new javax.swing.JTextField();
        endpointBField = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        jPanel4 = new javax.swing.JPanel();
        datasetAStatusField = new javax.swing.JLabel();
        datasetAProgressBar = new javax.swing.JProgressBar();
        datasetBProgressBar = new javax.swing.JProgressBar();
        datasetBStatusField = new javax.swing.JLabel();
        importButton = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        setGraphsButton = new javax.swing.JButton();
        resetGraphsButton = new javax.swing.JButton();

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Source dataset A"));

        jLabel1.setText("Endpoint:");

        jLabel2.setText("Graph");

        graphAField.setText("http://localhost:8890/A");

        endpointAField.setText("http://localhost:8890/sparql");
        endpointAField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                endpointAFieldActionPerformed(evt);
            }
        });

        jLabel3.setText("Subject regex:");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                            .addComponent(jLabel2)
                            .addGap(76, 76, 76))
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                            .addComponent(jLabel3)
                            .addGap(18, 18, 18)))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addGap(51, 51, 51)))
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(endpointAField)
                    .addComponent(subjectRegexAField)
                    .addComponent(graphAField, javax.swing.GroupLayout.DEFAULT_SIZE, 438, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(endpointAField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addGap(13, 13, 13)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(graphAField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2))
                .addGap(52, 52, 52)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(subjectRegexAField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Source dataset B"));

        jLabel4.setText("Endpoint:");

        jLabel5.setText("Graph");

        graphBField.setText("http://localhost:8890/B");

        endpointBField.setText("http://localhost:8890/sparql");

        jLabel6.setText("Subject regex:");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jLabel6, javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(jLabel5))
                    .addComponent(jLabel4))
                .addGap(18, 18, 18)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(endpointBField)
                    .addComponent(subjectRegexBField, javax.swing.GroupLayout.DEFAULT_SIZE, 438, Short.MAX_VALUE)
                    .addComponent(graphBField))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(endpointBField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addGap(13, 13, 13)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(graphBField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5))
                .addGap(42, 42, 42)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(subjectRegexBField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder("Import"));

        datasetAStatusField.setText("Dataset A not done...");

        datasetBStatusField.setText("Dataset B not done...");

        importButton.setText("Import");
        importButton.setEnabled(false);
        importButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(importButton, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(datasetAStatusField)
                            .addComponent(datasetBStatusField))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 137, Short.MAX_VALUE)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(datasetBProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)
                            .addComponent(datasetAProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGap(2, 2, 2)
                        .addComponent(datasetAProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(datasetAStatusField))
                .addGap(18, 18, 18)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(datasetBProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(datasetBStatusField))
                .addGap(18, 18, 18)
                .addComponent(importButton)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        setGraphsButton.setText("Set");
        setGraphsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setGraphsButtonActionPerformed(evt);
            }
        });

        resetGraphsButton.setText("Reset");
        resetGraphsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetGraphsButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(resetGraphsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(setGraphsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
                .addGap(0, 13, Short.MAX_VALUE)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(setGraphsButton)
                    .addComponent(resetGraphsButton)))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(90, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void importButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importButtonActionPerformed
        reset();
        graphA = graphAField.getText(); 
        graphB = graphBField.getText();
        
        try {
            final Dataset sourceDatasetA = new Dataset(endpointAField.getText(), graphA, subjectRegexAField.getText());
            final Dataset sourceDatasetB = new Dataset(endpointBField.getText(), graphB, subjectRegexBField.getText());                                    
            //temp
            setDatasetA(sourceDatasetA);
            setDatasetB(sourceDatasetB);
            
            final DatabaseInitialiser databaseInitialiser = new DatabaseInitialiser();
            databaseInitialiser.initialise(dbConfig);
            
            
            final ImporterWorker datasetAImportWorker = new ImporterWorker(dbConfig, PostGISImporter.DATASET_A, sourceDatasetA) {
                @Override
                protected void done() {
                    // Call get despite return type being Void to prevent SwingWorker from swallowing exceptions
                    try {
                        get();
                        datasetAStatusField.setText("Dataset import done!");
                    }
                    catch (RuntimeException ex) {
                        if(ex.getCause() == null) {
                            LOG.warn(ex.getMessage(), ex);
                            errorListener.notifyError(ex.getMessage());
                        }
                        else {
                            LOG.warn(ex.getCause().getMessage(), ex.getCause());
                            errorListener.notifyError(ex.getCause().getMessage());
                        }
                        datasetAStatusField.setText("Worker terminated abnormally.");
                    }
                    catch (InterruptedException ex) {
                        LOG.warn(ex.getMessage());
                        errorListener.notifyError(ex.getMessage());
                        datasetAStatusField.setText("Worker terminated abnormally.");
                    }
                    catch (ExecutionException ex) {
                        LOG.warn(ex.getCause().getMessage());
                        errorListener.notifyError(ex.getCause().getMessage());
                        datasetAStatusField.setText("Worker terminated abnormally.");
                    }
                    finally {
                        LOG.info("Dataset A import worker has terminated.");
                    }
                }
            };
            
            datasetAImportWorker.addPropertyChangeListener(new PropertyChangeListener() {
                @Override public void propertyChange(PropertyChangeEvent evt) {
                    if("progress".equals(evt.getPropertyName())) {
                        datasetAProgressBar.setValue((Integer) evt.getNewValue());
                    }
                }
            });
            
            final ImporterWorker datasetBImportWorker = new ImporterWorker(dbConfig, PostGISImporter.DATASET_B, sourceDatasetB) {
                @Override
                protected void done() {
                    // Call get despite return type being Void to prevent SwingWorker from swallowing exceptions
                    try {
                        get();
                        datasetBStatusField.setText("Dataset import done!");
                    }
                    catch (RuntimeException ex) {
                        if(ex.getCause() == null) {
                            LOG.warn(ex.getMessage(), ex);
                            errorListener.notifyError(ex.getMessage());
                        }
                        else {
                            LOG.warn(ex.getCause().getMessage(), ex.getCause());
                            errorListener.notifyError(ex.getCause().getMessage());
                        }
                        datasetBStatusField.setText("Worker terminated abnormally.");
                    }
                    catch (InterruptedException ex) {
                        LOG.warn(ex.getMessage());
                        errorListener.notifyError(ex.getMessage());
                        datasetAStatusField.setText("Worker terminated abnormally.");
                    }
                    catch (ExecutionException ex) {
                        LOG.warn(ex.getCause().getMessage());
                        errorListener.notifyError(ex.getCause().getMessage());
                        datasetAStatusField.setText("Worker terminated abnormally.");
                    }
                    finally {
                        LOG.info("Dataset B import worker has terminated.");
                    }
                }
            };
            
            datasetBImportWorker.addPropertyChangeListener(new PropertyChangeListener() {
                @Override public void propertyChange(PropertyChangeEvent evt) {
                    if("progress".equals(evt.getPropertyName())) {
                        datasetBProgressBar.setValue((Integer) evt.getNewValue());
                    }
                }
            });
            
            importButton.setEnabled(false);
            datasetAImportWorker.execute();
            datasetBImportWorker.execute();
            importButton.setEnabled(true);
        }
        catch (RuntimeException ex) {
            if(ex.getCause() == null) {
                LOG.error(ex.getClass() + " : " + ex.getMessage(), ex);
                errorListener.notifyError(ex.getClass() + " : " + ex.getMessage());
            }
            else {
                LOG.error(ex.getCause().getClass() + " : " + ex.getCause().getMessage(), ex.getCause());
                errorListener.notifyError(ex.getCause().getClass() + " : " + ex.getCause().getMessage());
            }
        }
        catch (SQLException ex) {
            LOG.error(ex.getMessage(), ex);
            errorListener.notifyError(ex.getMessage());
        }
    }//GEN-LAST:event_importButtonActionPerformed

    private void setGraphsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setGraphsButtonActionPerformed
        
        graphConfig = new GraphConfig(graphAField.getText(),graphBField.getText(), endpointAField.getText(), endpointBField.getText());
        publishGraphConfig(graphConfig);
        
        setDatasetA(new Dataset(endpointAField.getText(), graphAField.getText(), subjectRegexAField.getText()));
        setDatasetB(new Dataset(endpointBField.getText(), graphBField.getText(), subjectRegexBField.getText()));
        graphA = graphAField.getText(); 
        graphB = graphBField.getText();
        graphAField.setEnabled(false);
        graphBField.setEnabled(false);
        endpointAField.setEnabled(false);
        endpointBField.setEnabled(false);
        subjectRegexAField.setEnabled(false);
        subjectRegexBField.setEnabled(false);
    }//GEN-LAST:event_setGraphsButtonActionPerformed

    public void registerListener(final DBConfigListener listener) {
        dbConfigListeners.add(listener);
    }
    
    private void publishGraphConfig(final GraphConfig graphConfig) {

        for(DBConfigListener listener : dbConfigListeners) {
            listener.notifyNewGraphConfiguration(graphConfig);
        }
 
    }
    
    private void resetGraphsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetGraphsButtonActionPerformed
        graphA = graphAField.getText(); 
        graphB = graphBField.getText();
        graphAField.setEnabled(true);
        graphBField.setEnabled(true);
        endpointAField.setEnabled(true);
        endpointBField.setEnabled(true);
        subjectRegexAField.setEnabled(true);
        subjectRegexBField.setEnabled(true);
    }//GEN-LAST:event_resetGraphsButtonActionPerformed

    private void endpointAFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_endpointAFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_endpointAFieldActionPerformed

    
    public String getGraphA(){
        
        return graphA;
    }
    
    public String getGraphB(){
        
        return graphB;
    }
    
    
    private void setDatasetA(Dataset dataset){
        datasetA = dataset;
    } 
    
    private void setDatasetB(Dataset dataset){
        datasetB = dataset;
    } 
    
    //temp
    public Dataset getDatasetA(){
        return datasetA;
    } 
    
    //temp
    public Dataset getDatasetB(){
        return datasetB;
    } 
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JProgressBar datasetAProgressBar;
    private javax.swing.JLabel datasetAStatusField;
    private javax.swing.JProgressBar datasetBProgressBar;
    private javax.swing.JLabel datasetBStatusField;
    private javax.swing.JTextField endpointAField;
    private javax.swing.JTextField endpointBField;
    private javax.swing.JTextField graphAField;
    public javax.swing.JTextField graphBField;
    private javax.swing.JButton importButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JButton resetGraphsButton;
    private javax.swing.JButton setGraphsButton;
    private javax.swing.JTextField subjectRegexAField;
    private javax.swing.JTextField subjectRegexBField;
    // End of variables declaration//GEN-END:variables
}
