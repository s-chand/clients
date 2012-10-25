/**
 * ******************************************************************************************
 * Copyright (C) 2012 - Food and Agriculture Organization of the United Nations (FAO). All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,this list of conditions
 * and the following disclaimer. 2. Redistributions in binary form must reproduce the above
 * copyright notice,this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution. 3. Neither the name of FAO nor the names of its
 * contributors may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO,PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT,STRICT LIABILITY,OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * *********************************************************************************************
 */
package org.sola.clients.swing.desktop.unittitle;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import org.jdesktop.swingbinding.JTableBinding;
import org.jdesktop.swingbinding.JTableBinding.ColumnBinding;
import org.sola.clients.beans.administrative.BaUnitBean;
import org.sola.clients.beans.application.ApplicationBean;
import org.sola.clients.beans.application.ApplicationServiceBean;
import org.sola.clients.beans.referencedata.CadastreObjectTypeBean;
import org.sola.clients.beans.referencedata.CadastreObjectTypeListBean;
import org.sola.clients.beans.referencedata.StatusConstants;
import org.sola.clients.beans.transaction.TransactionUnitParcelsBean;
import org.sola.clients.beans.unittitle.UnitParcelBean;
import org.sola.clients.beans.unittitle.UnitParcelGroupBean;
import org.sola.clients.beans.validation.ValidationResultBean;
import org.sola.clients.swing.common.tasks.SolaTask;
import org.sola.clients.swing.common.tasks.TaskManager;
import org.sola.clients.swing.gis.ui.controlsbundle.ControlsBundleForUnitParcels;
import org.sola.clients.swing.ui.ContentPanel;
import org.sola.clients.swing.ui.validation.ValidationResultForm;
import org.sola.common.messaging.ClientMessage;
import org.sola.common.messaging.MessageUtility;
import org.sola.webservices.transferobjects.EntityAction;

/**
 * Panel used to manage information for Unit Title Developments in Samoa.
 */
public class UnitParcelsPanel extends ContentPanel {

    /**
     * Constants defining the prefixes used for the unit parcel types.
     */
    private static final String PRINCIPAL_UNIT_LOT_NR_PREFIX = "PU";
    private static final String ACCESSORY_UNIT_LOT_NR_PREFIX = "AU";
    private static final String COMMON_PROPERTY_LOT_NR = "CP";
    private static final int LOT_NR_PREFIX_LEN = 2;
    /**
     * Instance variables for the panel
     */
    private ControlsBundleForUnitParcels mapControl = null;
    private boolean readOnly = true;
    private ApplicationServiceBean applicationService;
    private ApplicationBean applicationBean;
    private String unitDevelopmentNr;
    private String parcelId;

    /**
     * Constructor
     *
     * @param applicationBean The application used to create or update the Unit Plan Development.
     * Can be null if the panel is opened independently from an application/transaction. If null,
     * the panel will default to readOnly.
     * @param appService The application service being processed or null if the panel is opened
     * independently from an application/transaction.
     * @param baUnit A Property that is part of the Unit Title Development. Should be provided where
     * possible. (e.g. the First Application Property or the property found as a result of a
     * Property Search.
     * @param readOnly Indicates if the form is read only or not.
     */
    public UnitParcelsPanel(ApplicationBean applicationBean, ApplicationServiceBean appService,
            BaUnitBean baUnit, boolean readOnly) {

        this.applicationBean = applicationBean;
        this.readOnly = readOnly || applicationBean == null;
        this.applicationService = appService;
        // The Unit Development number should exclude any suffix assigned to make the application
        // number unique. 
        this.unitDevelopmentNr = applicationBean == null || applicationBean.getNr() == null ? ""
                : applicationBean.getNr().split("/", 2)[0];
        this.parcelId = baUnit != null && baUnit.getCadastreObjectList().size() > 0
                ? baUnit.getCadastreObjectList().get(0).getId() : null;

        createTransactionBean();
        initComponents();
        postInit();
    }

    /**
     * Default method used to initialize a transaction bean
     *
     * @return
     */
    private TransactionUnitParcelsBean createTransactionBean() {
        if (transactionBean == null) {
            transactionBean = new TransactionUnitParcelsBean();
        }
        return transactionBean;
    }

    /**
     * Post Initialization method. Load the form with data and setup the buttons and panel title.
     */
    private void postInit() {

        initializeMap();
        loadTransaction();

        // Set the title for the form
        ResourceBundle bundle = ResourceBundle.getBundle(this.getClass().getPackage().getName() + ".Bundle");
        if (applicationBean != null && applicationService != null) {
            headerPanel1.setTitleText(String.format(bundle.getString("UnitParcelsPanel.headerPanel1.titleText.Service"),
                    applicationBean.getNr(), applicationService.getRequestType().getDisplayValue()));
        } else {
            headerPanel1.setTitleText(String.format(bundle.getString("UnitParcelsPanel.headerPanel1.titleText.Property"),
                    transactionBean.getUnitParcelGroup().getName()));
        }

        // Only show Principal and Accessory unit parcel types in the dropdown list. Every Unit Plan
        // Development must include a Common Property parcel so this parcel is setup by default and
        // cannot be removed.
        cadastreObjectTypeListBean1.setIncludedCodes(CadastreObjectTypeBean.CODE_ACCESSORY_UNIT,
                CadastreObjectTypeBean.CODE_PRINCIPAL_UNIT);
        cadastreObjectTypeListBean1.setSelectedCadastreObjectTypeCode(CadastreObjectTypeBean.CODE_PRINCIPAL_UNIT);

        // Setup a listener for selection of a unit parcel from the list and customize the unit
        // parcel buttons based on the details of the selected unit. 
        transactionBean.getUnitParcelGroup().addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(UnitParcelGroupBean.SELECTED_UNIT_PARCEL_PROPERTY)) {
                    customizeUnitParcelButtons((UnitParcelBean) evt.getNewValue());
                }
            }
        });

        // Setup a listener for selection of a unit parcel type and customize the unit parcel 
        // number accordingly. 
        cadastreObjectTypeListBean1.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(CadastreObjectTypeListBean.SELECTED_CADASTRE_OBJECT_TYPE_PROPERTY)) {
                    customizeUnitParcelNr((CadastreObjectTypeBean) evt.getNewValue());
                }
            }
        });


        // Set the default state for the on screen buttons. 
        btnSave.setEnabled(!readOnly);
        btnAddUnit.setEnabled(!readOnly);
        btnRemoveUnit.setEnabled(false);
        btnReinstateUnit.setEnabled(false);

        txtUnitFirstPart.setEditable(!readOnly);
        txtUnitFirstPart.setEnabled(!readOnly);
        txtUnitArea.setEditable(!readOnly);
        txtUnitArea.setEnabled(!readOnly);
        cbxParcelType.setEditable(!readOnly);
        cbxParcelType.setEnabled(!readOnly);
        txtUnitLastPart.setEditable(false);
        txtUnitLastPart.setEnabled(false);
        if (!readOnly) {
            txtUnitLastPart.setText(transactionBean.getUnitParcelGroup().getName());
            txtUnitFirstPart.setText(calculateLotNumber(CadastreObjectTypeBean.CODE_PRINCIPAL_UNIT));
        }

        // Load the map data
        loadMap();
    }

    /**
     * Creates the map bundle, initializes it and links it to the panel
     */
    private void initializeMap() {
        this.mapControl = new ControlsBundleForUnitParcels();
        this.mapControl.setup(null, readOnly);
        this.mapPanel.setLayout(new BorderLayout());
        this.mapPanel.add(this.mapControl, BorderLayout.CENTER);
    }

    /**
     * Loads the transaction for the Unit Title Development from the database. If there isn't a
     * transaction, a default transaction object is configured.
     */
    public void loadTransaction() {
        if (applicationService != null) {
            // Get the transaction object using the service id
            transactionBean.setFromServiceId(applicationService.getId());
            transactionBean.reload();
        }
        // Create a Unit Parcel Group if there isn't one on the transcation
        if (transactionBean.getUnitParcelGroup().isNew() && parcelId != null) {
            // Retrieve the Unit Parcel Group for the given parcel from the database
            UnitParcelGroupBean group = UnitParcelGroupBean.getUnitParcelGroupByParcelId(parcelId);
            if (group != null) {
                transactionBean.setUnitParcelGroup(group);
            }
        }
        if (!readOnly) {
            if (transactionBean.getUnitParcelGroup().getName() == null) {
                // Set the name of the Unit Parcel Group of the application Bean has been provided. 
                transactionBean.getUnitParcelGroup().setName(unitDevelopmentNr);
            } else if (unitDevelopmentNr == null) {
                unitDevelopmentNr = transactionBean.getUnitParcelGroup().getName();
            }

            // Check if there is a common property parcel and if not, add one. 
            if (!hasCommonProperty()) {
                UnitParcelBean commonPropBean = new UnitParcelBean();
                commonPropBean.setTypeCode(CadastreObjectTypeBean.CODE_COMMON_PROPERTY);
                commonPropBean.setUnitParcelStatusCode(StatusConstants.PENDING);
                commonPropBean.setNameFirstpart(COMMON_PROPERTY_LOT_NR);
                commonPropBean.setNameLastpart(transactionBean.getUnitParcelGroup().getName());
                transactionBean.getUnitParcelGroup().getUnitParcelList().addAsNew(commonPropBean);
            }
        }
    }

    /**
     * Checks if a common property is configured for the Unit Plan Development.
     *
     * @return
     */
    private boolean hasCommonProperty() {
        boolean result = false;
        if (transactionBean != null && transactionBean.getUnitParcelGroup() != null) {
            for (UnitParcelBean bean : transactionBean.getUnitParcelGroup().getFilteredUnitParcelList()) {
                if (CadastreObjectTypeBean.CODE_COMMON_PROPERTY.equals(bean.getTypeCode())) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Customizes the Unit Parcel Buttons based on the selected UnitParcelBean.
     *
     * @param bean
     */
    private void customizeUnitParcelButtons(UnitParcelBean bean) {
        btnRemoveUnit.setEnabled(false);
        btnReinstateUnit.setEnabled(false);
        if (bean != null && !readOnly) {

            boolean officialAreaEdit = true;
            boolean nameFirstPartEdit = false;

            // Don't allow the Common Property to be removed from the Unit Plan. 
            if (!CadastreObjectTypeBean.CODE_COMMON_PROPERTY.equals(bean.getTypeCode())) {
                if (bean.isDeleteOnApproval()) {
                    // The parcel is flagged for removal. Do no let the user change the official area
                    btnReinstateUnit.setEnabled(true);
                    officialAreaEdit = false;
                } else {
                    btnRemoveUnit.setEnabled(true);
                }
            }

            // Pending parcels can have their Name First Part updated. 
            if (StatusConstants.PENDING.equals(bean.getUnitParcelStatusCode())) {
                nameFirstPartEdit = true;
            }

            // Control the columns that may be edited for the selected unit parcel. 
            JTableBinding tabBinding = (JTableBinding) bindingGroup.getBinding("bindTblUnits");
            if (tabBinding != null) {
                for (ColumnBinding colBinding : (List<ColumnBinding>) tabBinding.getColumnBindings()) {
                    if (colBinding.getColumnName().equals("Official Area")) {
                        colBinding.setEditable(officialAreaEdit);
                    } else if (colBinding.getColumnName().equals("Name Firstpart")) {
                        colBinding.setEditable(nameFirstPartEdit);
                    }
                }
            }
        }
    }

    /**
     * Sets the next available lot number for a unit parcel based on the type of unit
     *
     * @param typeBean Bean indicating the type of unit to set the lot number for.
     */
    private void customizeUnitParcelNr(CadastreObjectTypeBean typeBean) {
        txtUnitFirstPart.setText(calculateLotNumber(typeBean.getCode()));
    }

    /**
     * Loads the map with the underlying parcels and zooms to the area for the application.
     */
    private void loadMap() {
        mapControl.setUnderlyingParcels(transactionBean.getUnitParcelGroup().getFilteredParcelList());
        byte[] applicationLocation = applicationBean == null ? null : applicationBean.getLocation();
        mapControl.zoomToTargetArea(applicationLocation);
    }

    /**
     * Calculates the next available Lot Number for a unit parcel based on the unit parcel type
     * code. Note that the lot number for unit parcels should be of the form PU# for Principal Units
     * and AU# for Accessory Units. The user is able to change this name if it is not suitable.
     *
     * @param unitParcelTypeCode The type of unit to calculate the lot number for.
     */
    private String calculateLotNumber(String unitParcelTypeCode) {
        String result = "";
        if (unitParcelTypeCode != null && !unitParcelTypeCode.trim().isEmpty()) {
            Integer lotNr = 0;
            for (UnitParcelBean bean : transactionBean.getUnitParcelGroup().getUnitParcelList()) {
                if (unitParcelTypeCode.equals(bean.getTypeCode())) {
                    // Assume the unit has a 2 character prefix (AU or PU)
                    String tmpStr = bean.getNameFirstpart().substring(LOT_NR_PREFIX_LEN).trim();
                    try {
                        Integer tmpInt = Integer.parseInt(tmpStr);
                        lotNr = lotNr < tmpInt ? tmpInt : lotNr;
                    } catch (NumberFormatException nfe) {
                        // Unable to convert the remainder of the lot number to a valid integer. 
                        // Ignore the error. 
                    }
                }
            }
            lotNr++;
            if (unitParcelTypeCode.equals(CadastreObjectTypeBean.CODE_PRINCIPAL_UNIT)) {
                result = PRINCIPAL_UNIT_LOT_NR_PREFIX + lotNr.toString();
            } else {
                result = ACCESSORY_UNIT_LOT_NR_PREFIX + lotNr.toString();
            }
        }
        return result;
    }

    /**
     * Checks the underlying parcels displayed in the map and synchronizes the Unit Plan Parcel List
     * with any changes.
     */
    private void updateUnderlyingParcels() {
        List<String> parcelIds = this.mapControl.getUnderlyingParcels();
        for (UnitParcelBean bean : transactionBean.getUnitParcelGroup().getParcelList()) {
            if (!parcelIds.contains(bean.getId())) {
                // The underlying parcel is being removed from the unit plan, so check if it
                // should be deleted immediately (if the link is only pending) or deleted on approval
                if (StatusConstants.PENDING.equals(bean.getUnitParcelStatusCode())) {
                    bean.setEntityAction(EntityAction.DISASSOCIATE);
                } else {
                    bean.setDeleteOnApproval(true);
                    bean.setEntityAction(EntityAction.UPDATE);
                }
            } else {
                // Make sure the parcel is not flagged for removal
                bean.setEntityAction(null);
                bean.setDeleteOnApproval(false);
            }
        }
        // Check for any new underlying parcels to add to the list. 
        for (String underlyingParcelId : parcelIds) {
            UnitParcelBean newBean = new UnitParcelBean();
            newBean.setId(underlyingParcelId);
            newBean.setUnitParcelStatusCode(StatusConstants.PENDING);
            if (!transactionBean.getUnitParcelGroup().getParcelList().contains(newBean)) {
                transactionBean.getUnitParcelGroup().getParcelList().addAsNew(newBean);
            }
        }
    }

    /**
     * Save the Unit Plan transaction to the database. Triggered by btnSave
     */
    private void saveTransaction() {

        // Get the list of underlying parcels from the map adn update as required to track changes
        // to the underlying parcels. 
        updateUnderlyingParcels();

        final List result = new ArrayList<ValidationResultBean>();
        SolaTask<Void, Void> t = new SolaTask<Void, Void>() {

            @Override
            public Void doTask() {
                setMessage(MessageUtility.getLocalizedMessageText(ClientMessage.PROGRESS_MSG_SAVING));
                // Capture the validation messages from the save - if any. 
                result.addAll(transactionBean.saveTransaction());
                // Reload the transactionBean with the updated details. 
                transactionBean.reload();
                return null;
            }

            @Override
            public void taskDone() {
                String message = MessageUtility.getLocalizedMessageText(ClientMessage.GENERAL_SAVE_SUCCESSFUL);
                // Show the validation results form. if there was a critical validation failure, the
                // services would have thrown an exception, so assume the validation passed. 
                ValidationResultForm resultForm = new ValidationResultForm(
                        null, true, result, true, message);
                resultForm.setLocationRelativeTo(tabPane);
                resultForm.setVisible(true);
                // Update the lot number in case a lot was deleted during the save. 
                if (cadastreObjectTypeListBean1.getSelectedCadastreObjectType() != null) {
                    customizeUnitParcelNr(cadastreObjectTypeListBean1.getSelectedCadastreObjectType());
                }
            }
        };
        TaskManager.getInstance().runTask(t);
    }

    /**
     * Adds a new unit parcel to the list of unit parcels for the development. Triggered by
     * btnAddUnit.
     */
    private void addUnitParcel() {
        // Onlly create a new unit if the lot number and parcel type are set
        if (txtUnitFirstPart.getText() != null && !txtUnitFirstPart.getText().trim().isEmpty()
                && cadastreObjectTypeListBean1.getSelectedCadastreObjectType() != null) {
            UnitParcelBean bean = new UnitParcelBean();
            bean.setNameFirstpart(txtUnitFirstPart.getText());
            bean.setNameLastpart(transactionBean.getUnitParcelGroup().getName());
            bean.setUnitParcelStatusCode(StatusConstants.PENDING);
            bean.setCadastreObjectType(cadastreObjectTypeListBean1.getSelectedCadastreObjectType());
            if (txtUnitArea.getText() != null && !txtUnitArea.getText().trim().isEmpty()) {
                bean.setOfficialArea(txtUnitArea.getText());
            }
            transactionBean.getUnitParcelGroup().getUnitParcelList().addAsNew(bean);
            transactionBean.getUnitParcelGroup().setSelectedUnitParcel(bean);
            // Reset the lot number to the next avaiable number. 
            customizeUnitParcelNr(bean.getCadastreObjectType());
        }
    }

    /**
     * Removes a unit parcel from the Unit Plan Development. Triggered by btnRemoveUnit.
     */
    private void removeUnitParcel() {
        UnitParcelBean bean = transactionBean.getUnitParcelGroup().getSelectedUnitParcel();
        if (StatusConstants.PENDING.equals(bean.getUnitParcelStatusCode())) {
            // Mark the parcel for deletion and force the unit parcel list to be filtered so that
            // the deleted parcel is removed from the list. 
            bean.setEntityAction(EntityAction.DELETE);
            transactionBean.getUnitParcelGroup().getUnitParcelList().filter();
        } else {
            // The unit parcel cannot be deleted. Instead it must be disassociated from the
            // Unit Plan when the transaction is approved. 
            bean.setDeleteOnApproval(true);
        }
        transactionBean.getUnitParcelGroup().setSelectedUnitParcel(null);
    }

    /**
     * Reverses the delete on approval flag if set for a unit parcel. Triggered by the
     * btnReinstateUnit button
     */
    private void reinstateUnitParcel() {
        UnitParcelBean bean = transactionBean.getUnitParcelGroup().getSelectedUnitParcel();
        if (bean.isDeleteOnApproval()) {
            bean.setDeleteOnApproval(false);
        }
        transactionBean.getUnitParcelGroup().setSelectedUnitParcel(null);
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT
     * modify this code. The content of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        jPanel3 = new javax.swing.JPanel();
        groupPanel2 = new org.sola.clients.swing.ui.GroupPanel();
        transactionBean = createTransactionBean();
        cadastreObjectTypeListBean1 = new org.sola.clients.beans.referencedata.CadastreObjectTypeListBean();
        headerPanel1 = new org.sola.clients.swing.ui.HeaderPanel();
        jToolBar1 = new javax.swing.JToolBar();
        btnSave = new javax.swing.JButton();
        tabPane = new javax.swing.JTabbedPane();
        unitsTab = new javax.swing.JPanel();
        pnlUnits = new javax.swing.JPanel();
        jToolBar2 = new javax.swing.JToolBar();
        btnAddUnit = new javax.swing.JButton();
        btnRemoveUnit = new javax.swing.JButton();
        btnReinstateUnit = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        tblUnits = new javax.swing.JTable();
        jPanel1 = new javax.swing.JPanel();
        txtUnitFirstPart = new javax.swing.JTextField();
        txtUnitLastPart = new javax.swing.JTextField();
        txtUnitArea = new javax.swing.JFormattedTextField();
        cbxParcelType = new javax.swing.JComboBox();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        mapTab = new javax.swing.JPanel();
        mapPanel = new javax.swing.JPanel();

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );

        setCloseOnHide(true);
        setHeaderPanel(headerPanel1);

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/sola/clients/swing/desktop/unittitle/Bundle"); // NOI18N
        headerPanel1.setTitleText(bundle.getString("UnitParcelsPanel.headerPanel1.titleText")); // NOI18N

        jToolBar1.setFloatable(false);
        jToolBar1.setRollover(true);

        btnSave.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/common/save.png"))); // NOI18N
        btnSave.setText(bundle.getString("UnitParcelsPanel.btnSave.text")); // NOI18N
        btnSave.setFocusable(false);
        btnSave.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveActionPerformed(evt);
            }
        });
        jToolBar1.add(btnSave);

        jToolBar2.setFloatable(false);
        jToolBar2.setRollover(true);

        btnAddUnit.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/common/add.png"))); // NOI18N
        btnAddUnit.setText(bundle.getString("UnitParcelsPanel.btnAddUnit.text")); // NOI18N
        btnAddUnit.setFocusable(false);
        btnAddUnit.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnAddUnit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddUnitActionPerformed(evt);
            }
        });
        jToolBar2.add(btnAddUnit);

        btnRemoveUnit.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/common/remove.png"))); // NOI18N
        btnRemoveUnit.setText(bundle.getString("UnitParcelsPanel.btnRemoveUnit.text")); // NOI18N
        btnRemoveUnit.setFocusable(false);
        btnRemoveUnit.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnRemoveUnit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRemoveUnitActionPerformed(evt);
            }
        });
        jToolBar2.add(btnRemoveUnit);

        btnReinstateUnit.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/common/undo.png"))); // NOI18N
        btnReinstateUnit.setText(bundle.getString("UnitParcelsPanel.btnReinstateUnit.text")); // NOI18N
        btnReinstateUnit.setFocusable(false);
        btnReinstateUnit.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnReinstateUnit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnReinstateUnitActionPerformed(evt);
            }
        });
        jToolBar2.add(btnReinstateUnit);

        org.jdesktop.beansbinding.ELProperty eLProperty = org.jdesktop.beansbinding.ELProperty.create("${unitParcelGroup.filteredUnitParcelList}");
        org.jdesktop.swingbinding.JTableBinding jTableBinding = org.jdesktop.swingbinding.SwingBindings.createJTableBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, transactionBean, eLProperty, tblUnits, "bindTblUnits");
        org.jdesktop.swingbinding.JTableBinding.ColumnBinding columnBinding = jTableBinding.addColumnBinding(org.jdesktop.beansbinding.ELProperty.create("${nameFirstpart}"));
        columnBinding.setColumnName("Name Firstpart");
        columnBinding.setColumnClass(String.class);
        columnBinding.setEditable(false);
        columnBinding = jTableBinding.addColumnBinding(org.jdesktop.beansbinding.ELProperty.create("${nameLastpart}"));
        columnBinding.setColumnName("Name Lastpart");
        columnBinding.setColumnClass(String.class);
        columnBinding.setEditable(false);
        columnBinding = jTableBinding.addColumnBinding(org.jdesktop.beansbinding.ELProperty.create("${officialArea}"));
        columnBinding.setColumnName("Official Area");
        columnBinding.setColumnClass(String.class);
        columnBinding.setEditable(false);
        columnBinding = jTableBinding.addColumnBinding(org.jdesktop.beansbinding.ELProperty.create("${cadastreObjectType.displayValue}"));
        columnBinding.setColumnName("Cadastre Object Type.display Value");
        columnBinding.setColumnClass(String.class);
        columnBinding.setEditable(false);
        columnBinding = jTableBinding.addColumnBinding(org.jdesktop.beansbinding.ELProperty.create("${unitParcelStatus.displayValue}"));
        columnBinding.setColumnName("Unit Parcel Status.display Value");
        columnBinding.setColumnClass(String.class);
        columnBinding.setEditable(false);
        columnBinding = jTableBinding.addColumnBinding(org.jdesktop.beansbinding.ELProperty.create("${deleteOnApproval}"));
        columnBinding.setColumnName("Delete On Approval");
        columnBinding.setColumnClass(Boolean.class);
        columnBinding.setEditable(false);
        bindingGroup.addBinding(jTableBinding);
        jTableBinding.bind();org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, transactionBean, org.jdesktop.beansbinding.ELProperty.create("${unitParcelGroup.selectedUnitParcel}"), tblUnits, org.jdesktop.beansbinding.BeanProperty.create("selectedElement"));
        bindingGroup.addBinding(binding);

        jScrollPane1.setViewportView(tblUnits);
        tblUnits.getColumnModel().getColumn(0).setHeaderValue(bundle.getString("UnitParcelsPanel.tblUnits.columnModel.title0_1")); // NOI18N
        tblUnits.getColumnModel().getColumn(1).setHeaderValue(bundle.getString("UnitParcelsPanel.tblUnits.columnModel.title1_1")); // NOI18N
        tblUnits.getColumnModel().getColumn(2).setHeaderValue(bundle.getString("UnitParcelsPanel.tblUnits.columnModel.title5_1")); // NOI18N
        tblUnits.getColumnModel().getColumn(3).setHeaderValue(bundle.getString("UnitParcelsPanel.tblUnits.columnModel.title2_1")); // NOI18N
        tblUnits.getColumnModel().getColumn(4).setHeaderValue(bundle.getString("UnitParcelsPanel.tblUnits.columnModel.title3_1")); // NOI18N
        tblUnits.getColumnModel().getColumn(5).setHeaderValue(bundle.getString("UnitParcelsPanel.tblUnits.columnModel.title4_1")); // NOI18N

        txtUnitFirstPart.setText(bundle.getString("UnitParcelsPanel.txtUnitFirstPart.text")); // NOI18N

        txtUnitLastPart.setEditable(false);
        txtUnitLastPart.setText(bundle.getString("UnitParcelsPanel.txtUnitLastPart.text")); // NOI18N

        txtUnitArea.setText(bundle.getString("UnitParcelsPanel.txtUnitArea.text")); // NOI18N

        eLProperty = org.jdesktop.beansbinding.ELProperty.create("${cadastreObjectTypeList}");
        org.jdesktop.swingbinding.JComboBoxBinding jComboBoxBinding = org.jdesktop.swingbinding.SwingBindings.createJComboBoxBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, cadastreObjectTypeListBean1, eLProperty, cbxParcelType, "");
        bindingGroup.addBinding(jComboBoxBinding);
        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, cadastreObjectTypeListBean1, org.jdesktop.beansbinding.ELProperty.create("${selectedCadastreObjectType}"), cbxParcelType, org.jdesktop.beansbinding.BeanProperty.create("selectedItem"));
        bindingGroup.addBinding(binding);

        jLabel1.setText(bundle.getString("UnitParcelsPanel.jLabel1.text")); // NOI18N

        jLabel2.setText(bundle.getString("UnitParcelsPanel.jLabel2.text")); // NOI18N

        jLabel3.setText(bundle.getString("UnitParcelsPanel.jLabel3.text")); // NOI18N

        jLabel4.setText(bundle.getString("UnitParcelsPanel.jLabel4.text")); // NOI18N

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtUnitFirstPart)
                    .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(txtUnitLastPart, javax.swing.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(txtUnitArea))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, 229, Short.MAX_VALUE)
                    .addComponent(cbxParcelType, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jLabel2)
                    .addComponent(jLabel3)
                    .addComponent(jLabel4))
                .addGap(3, 3, 3)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtUnitFirstPart, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtUnitLastPart, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtUnitArea, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cbxParcelType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(67, 67, 67))
        );

        javax.swing.GroupLayout pnlUnitsLayout = new javax.swing.GroupLayout(pnlUnits);
        pnlUnits.setLayout(pnlUnitsLayout);
        pnlUnitsLayout.setHorizontalGroup(
            pnlUnitsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(pnlUnitsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlUnitsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 871, Short.MAX_VALUE)
                    .addComponent(jToolBar2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        pnlUnitsLayout.setVerticalGroup(
            pnlUnitsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlUnitsLayout.createSequentialGroup()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, 52, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(4, 4, 4)
                .addComponent(jToolBar2, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 490, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout unitsTabLayout = new javax.swing.GroupLayout(unitsTab);
        unitsTab.setLayout(unitsTabLayout);
        unitsTabLayout.setHorizontalGroup(
            unitsTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnlUnits, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        unitsTabLayout.setVerticalGroup(
            unitsTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnlUnits, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        tabPane.addTab(bundle.getString("UnitParcelsPanel.unitsTab.TabConstraints.tabTitle"), unitsTab); // NOI18N

        javax.swing.GroupLayout mapPanelLayout = new javax.swing.GroupLayout(mapPanel);
        mapPanel.setLayout(mapPanelLayout);
        mapPanelLayout.setHorizontalGroup(
            mapPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 891, Short.MAX_VALUE)
        );
        mapPanelLayout.setVerticalGroup(
            mapPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 588, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout mapTabLayout = new javax.swing.GroupLayout(mapTab);
        mapTab.setLayout(mapTabLayout);
        mapTabLayout.setHorizontalGroup(
            mapTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 891, Short.MAX_VALUE)
            .addGroup(mapTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(mapPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        mapTabLayout.setVerticalGroup(
            mapTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 588, Short.MAX_VALUE)
            .addGroup(mapTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(mapPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        tabPane.addTab(bundle.getString("UnitParcelsPanel.mapTab.TabConstraints.tabTitle"), mapTab); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(headerPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jToolBar1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(tabPane)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(headerPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jToolBar1, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tabPane))
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void btnSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveActionPerformed
        saveTransaction();
    }//GEN-LAST:event_btnSaveActionPerformed

    private void btnAddUnitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddUnitActionPerformed
        addUnitParcel();
    }//GEN-LAST:event_btnAddUnitActionPerformed

    private void btnRemoveUnitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRemoveUnitActionPerformed
        removeUnitParcel();
    }//GEN-LAST:event_btnRemoveUnitActionPerformed

    private void btnReinstateUnitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnReinstateUnitActionPerformed
        reinstateUnitParcel();
    }//GEN-LAST:event_btnReinstateUnitActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAddUnit;
    private javax.swing.JButton btnReinstateUnit;
    private javax.swing.JButton btnRemoveUnit;
    private javax.swing.JButton btnSave;
    private org.sola.clients.beans.referencedata.CadastreObjectTypeListBean cadastreObjectTypeListBean1;
    private javax.swing.JComboBox cbxParcelType;
    private org.sola.clients.swing.ui.GroupPanel groupPanel2;
    public org.sola.clients.swing.ui.HeaderPanel headerPanel1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JToolBar jToolBar1;
    private javax.swing.JToolBar jToolBar2;
    private javax.swing.JPanel mapPanel;
    private javax.swing.JPanel mapTab;
    private javax.swing.JPanel pnlUnits;
    private javax.swing.JTabbedPane tabPane;
    private javax.swing.JTable tblUnits;
    public org.sola.clients.beans.transaction.TransactionUnitParcelsBean transactionBean;
    private javax.swing.JFormattedTextField txtUnitArea;
    private javax.swing.JTextField txtUnitFirstPart;
    private javax.swing.JTextField txtUnitLastPart;
    private javax.swing.JPanel unitsTab;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables
}