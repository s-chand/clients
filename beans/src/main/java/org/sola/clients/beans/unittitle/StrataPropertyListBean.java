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
package org.sola.clients.beans.unittitle;

import java.util.List;
import org.jdesktop.observablecollections.ObservableList;
import org.sola.clients.beans.AbstractBindingListBean;
import org.sola.clients.beans.controls.SolaObservableList;
import org.sola.clients.beans.converters.TypeConverters;
import org.sola.services.boundary.wsclients.WSManager;

/**
 * Holds list of {@link StrataPropertyBean} objects for display in a table or list control
 */
public class StrataPropertyListBean extends AbstractBindingListBean {

    public static final String SELECTED_STRATA_BEAN_PROPERTY = "selectedStrataProperty";
    private SolaObservableList<StrataPropertyBean> strataPropertyList;
    private StrataPropertyBean selectedStrataProperty;

    public StrataPropertyListBean() {      
        super();
        strataPropertyList = new SolaObservableList<StrataPropertyBean>();
    }

    public ObservableList<StrataPropertyBean> getStrataPropertyList() {
        return strataPropertyList;
    }

    public StrataPropertyBean getSelectedStrataProperty() {
        return selectedStrataProperty;
    }

    public void setSelectedStrataProperty(StrataPropertyBean selectedStrataProperty) {
        this.selectedStrataProperty = selectedStrataProperty;
        propertySupport.firePropertyChange(SELECTED_STRATA_BEAN_PROPERTY, null, this.selectedStrataProperty);
    }

    /**
     * Retrieves a summary of the properties that are part of the specified unit parcel group. Also
     * retrieves a summary of the properties noted in the baUnitIds list. Typically this list will
     * represent the underlying properties of the unit development and is included as a parameter in
     * case the underlying property is not linked to a cadastre_object.
     *
     * @param unitParcelGroupName The name of the parcel group (if known)
     * @param baUnitIds The identifier for any of the BA Units linked to the unit parcel group or
     * the identifier for any BA Units that should be part of the unit development.
     */
    public void getStrataProperties(String unitParcelGroupName, List<String> baUnitIds) {
        getStrataPropertyList().clear();
        TypeConverters.TransferObjectListToBeanList(
                WSManager.getInstance().getSearchService().getStrataProperties(unitParcelGroupName, baUnitIds),
                StrataPropertyBean.class, (List) getStrataPropertyList());
    }

    /**
     * Creates the Strata Properties required for a Unit Development. This method can be used to
     * create the initial set of properties based on a UnitParcelGroup as well as to create any
     * additional properties when the Unit Parcel Group is changed.
     *
     * @param serviceId The identifier for the Create Unit Title Service
     * @param group The Unit Parcel Group
     * @param baUnitIds The list of BA Units representing the underlying properties for the Unit
     * Development (Typically the list of BA Units linked to the application as Application
     * Properties). This list of BA Units is checked to ensure only valid properties are linked as
     * the underlying properties for the unit development. 
     */
    public void createStrataProperties(String serviceId, String unitParcelGroupName, List<String> baUnitIds) {
        WSManager.getInstance().getAdministrative().createStrataProperties(serviceId, unitParcelGroupName, baUnitIds);
        getStrataProperties(unitParcelGroupName, baUnitIds);
    }
}
