package edu.harvard.iq.dataverse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import edu.harvard.iq.dataverse.api.AbstractApiBean;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import java.io.IOException;
import java.util.logging.Logger;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.UploadedFile;
import edu.harvard.iq.dataverse.engine.command.impl.PersistProvJsonCommand;
import edu.harvard.iq.dataverse.engine.command.impl.PersistProvFreeFormCommand;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteProvJsonCommand;
import edu.harvard.iq.dataverse.engine.command.impl.GetProvJsonCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetCommand;
import edu.harvard.iq.dataverse.util.BundleUtil;
import static edu.harvard.iq.dataverse.util.JsfHelper.JH;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.io.IOUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.faces.application.FacesMessage;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.json.JsonObject;

/**
 * This bean contains functionality for the provenance json pop up
 * This pop up can be accessed from multiple pages (editDataFile, Dataset (create), File)
 * 
 * @author madunlap
 */

@ViewScoped
@Named
public class ProvPopupFragmentBean extends AbstractApiBean implements java.io.Serializable{
    
    private static final Logger logger = Logger.getLogger(ProvPopupFragmentBean.class.getCanonicalName());
    
    private UploadedFile jsonUploadedTempFile; 
    
    //These two variables hold the state of the prov variables for the current open file before any changes would be applied by the editing "session"
    private String provJsonState;
    private String freeformTextState; 
    
    private Dataset dataset;
    private FileMetadata fileMetadata;
    
    private String freeformTextInput;
    private boolean deleteStoredJson = false;
    private DataFile popupDataFile;
    
    ProvEntityFileData dropdownSelectedEntity;
    String storedSelectedEntityName;
    
    HashMap<String,ProvEntityFileData> provJsonParsedEntities;
   
    //This map uses ChecksumValue as the key. Tried storageIdentifier but that value switches during publication
    //UpdatesEntry contains the prov json, prov freeform and whether we will delete json
    //Originally there was a Hashmap<DataFile,String> to store this data 
    //but equality is "broken" for entities like DataFile --mad 4.8.5    
    HashMap<String,UpdatesEntry> provenanceUpdates = new HashMap<>();
    
    @EJB
    DataFileServiceBean dataFileService;
    @Inject
    DataverseRequestServiceBean dvRequestService;
    @Inject
    FilePage filePage;
    @Inject
    DatasetPage datasetPage;
    @Inject
    EditDatafilesPage datafilesPage;
    @Inject
    ProvUtilFragmentBean provUtil;
        
    public void handleFileUpload(FileUploadEvent event) throws IOException {
        jsonUploadedTempFile = event.getFile();
        provJsonParsedEntities = new HashMap<>();
        provJsonState = IOUtils.toString(jsonUploadedTempFile.getInputstream());
        try {
            generateProvJsonParsedEntities();

        } catch (Exception e) {
            Logger.getLogger(ProvPopupFragmentBean.class.getName())
                    .log(Level.SEVERE, BundleUtil.getStringFromBundle("file.editProvenanceDialog.uploadError"), e);
            removeJsonAndRelatedData();
            JH.addMessage(FacesMessage.SEVERITY_ERROR, JH.localize("file.editProvenanceDialog.uploadError")); 
        } 
        if(provJsonParsedEntities.isEmpty()) {
            removeJsonAndRelatedData();
            JH.addMessage(FacesMessage.SEVERITY_ERROR, JH.localize("file.editProvenanceDialog.noEntitiesError"));
        }

    }
    
    public void updatePopupState(FileMetadata fm, Dataset dSet) throws AbstractApiBean.WrappedResponse, IOException {
        dataset = dSet;
        updatePopupState(fm);
    }
     
    public void updatePopupState(FileMetadata fm) throws WrappedResponse, IOException {       
//MAD: This should exception better
        if(null == fm) {
            throw new NullPointerException("FileMetadata initialized to null");
        }
        fileMetadata = fm;
        updatePopupState();
    }
    
    //This updates the popup for the selected file each time its open
    //You should call the one that takes fileMetadata if you are calling this fragment from another page.
    public void updatePopupState() throws AbstractApiBean.WrappedResponse, IOException {
        if(null == fileMetadata) {
            throw new NullPointerException("FileMetadata cannot be null when calling updatePopupState");
        }
        if(null == dataset ) {
            dataset = fileMetadata.getDatasetVersion().getDataset(); //DatasetVersion is null here on file upload page...
        }
        
        popupDataFile = fileMetadata.getDataFile();
        deleteStoredJson = false;
        provJsonState = null;
        provJsonParsedEntities = new HashMap<>();
        setDropdownSelectedEntity(null);
        freeformTextState = fileMetadata.getProvFreeForm();
        storedSelectedEntityName = popupDataFile.getProvEntityName();
     
        if(provenanceUpdates.containsKey(popupDataFile.getChecksumValue())) { //If there is already staged provenance info 
            provJsonState = provenanceUpdates.get(popupDataFile.getChecksumValue()).provJson;
            if(null != provenanceUpdates.get(popupDataFile.getChecksumValue()).provFreeform) {
                freeformTextState = provenanceUpdates.get(popupDataFile.getChecksumValue()).provFreeform;
            }

            if(null != provenanceUpdates.get(popupDataFile.getChecksumValue()).provJson) {
                generateProvJsonParsedEntities(); //calling this each time is somewhat inefficient, but storing the state is a lot of lifting.
                setDropdownSelectedEntity(provJsonParsedEntities.get(storedSelectedEntityName)); 
            }
            
        } else if(null != popupDataFile.getCreateDate()){ //Is this file fully uploaded and already has prov data saved?   
            JsonObject provJsonObject = execCommand(new GetProvJsonCommand(dvRequestService.getDataverseRequest(), popupDataFile));
            if(null != provJsonObject) {
                provJsonState = provUtil.getPrettyJsonString(provJsonObject);
                
                generateProvJsonParsedEntities();
                setDropdownSelectedEntity(provJsonParsedEntities.get(storedSelectedEntityName));
            }

        } else { //clear the listed uploaded file
            jsonUploadedTempFile = null;
        }
        freeformTextInput = freeformTextState;
    }
    
    //Stores the provenance changes decided upon in the popup to be saved when all edits across files are done.
    public String stagePopupChanges(boolean saveInPopup) throws IOException{
        UpdatesEntry stagingEntry = provenanceUpdates.get(popupDataFile.getChecksumValue());
        if(stagingEntry == null) {
            stagingEntry = new UpdatesEntry(popupDataFile, null, false, null);// = new UpdatesEntry(popupDataFile, null, null);  
        }
        if(null == freeformTextInput && null != freeformTextState) { //MAD: Still unsure if we need this
            freeformTextInput = "";
        }
        if(null != freeformTextInput && !freeformTextInput.equals(freeformTextState)) {
            stagingEntry.provFreeform = freeformTextInput;
        } 
        if(deleteStoredJson) {
            stagingEntry.deleteJson = true;
            
            popupDataFile.setProvEntityName(null); //we need to make sure dataFile attribute is in the correct state
        }        
        if(null != jsonUploadedTempFile && "application/json".equalsIgnoreCase(jsonUploadedTempFile.getContentType())) { //delete and create again can both happen at once
            stagingEntry.provJson = IOUtils.toString(jsonUploadedTempFile.getInputstream());
            stagingEntry.deleteJson = false;
            //stagingEntry = new UpdatesEntry(popupDataFile, jsonString, false, null);
//            jsonProvenanceUpdates.put(popupDataFile.getStorageIdentifier(), new UpdatesEntry(popupDataFile, jsonString, null));
            jsonUploadedTempFile = null;
            
            //storing the entity name associated with the DataFile. This is required data to get this far.
            //popupDataFile.setProvEntityName(dropdownSelectedEntity.getEntityName());
        } 
//MAD: The logic on when to save the entity name was and may be still broken, tho less so //null != storedSelectedEntityName && !storedSelectedEntityName.equals(dropdownSelectedEntity.getEntityName())
        if(null != dropdownSelectedEntity && !(null != storedSelectedEntityName && storedSelectedEntityName.equals(dropdownSelectedEntity.getEntityName()))) {
            popupDataFile.setProvEntityName(dropdownSelectedEntity.getEntityName());
        }
        
        if(stagingEntry.provJson != null || stagingEntry.deleteJson != false || stagingEntry.provFreeform != null) { //if the entry isn't empty by our standards
            provenanceUpdates.put(popupDataFile.getChecksumValue(), stagingEntry);
        }
        
        if(saveInPopup) { //file page needs to save here
            try {
                saveStagedProvJson(true);
                stagingEntry = provenanceUpdates.get(popupDataFile.getChecksumValue()); //reloading as it can be set in saveStagedProvJson
                if(null != stagingEntry && null != stagingEntry.provFreeform) {
                    return filePage.saveProvFreeform(stagingEntry.provFreeform, stagingEntry.dataFile);
                }  
            } catch (AbstractApiBean.WrappedResponse|CommandException ex) {
                filePage.showProvError();
                Logger.getLogger(ProvPopupFragmentBean.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        return null;

    }
    
    public void saveStagedProvJson(boolean saveContext) throws AbstractApiBean.WrappedResponse {
        for (Map.Entry<String, UpdatesEntry> m : provenanceUpdates.entrySet()) {
            UpdatesEntry mapEntry = m.getValue();
            DataFile df = mapEntry.dataFile;
            String provString = mapEntry.provJson;

            if(mapEntry.deleteJson) {
                df = execCommand(new DeleteProvJsonCommand(dvRequestService.getDataverseRequest(), df, saveContext));
            } else if(null != provString) {
                df = execCommand(new PersistProvJsonCommand(dvRequestService.getDataverseRequest(), df, provString, df.getProvEntityName(), saveContext));
            } 
            mapEntry.dataFile = df;
            provenanceUpdates.put(mapEntry.dataFile.getChecksumValue(), mapEntry); //MAD: Modifying the set as we go through it is probably causing errors
        }
    }
    
    public void saveStageProvFreeformToLatestVersion() {
        for (Map.Entry<String, UpdatesEntry> mapEntry : provenanceUpdates.entrySet()) {
            String freeformText = mapEntry.getValue().provFreeform;
            FileMetadata fm = mapEntry.getValue().dataFile.getFileMetadata();
            fm.setProvFreeForm(freeformText);

        }
    }
    
    //Called by editFilesPage to update its metadata with stored prov freeform values for multiple DataFiles
    Boolean updatePageMetadatasWithProvFreeform(List<FileMetadata> fileMetadatas) {
        Boolean changes = false;
        for(FileMetadata fm : fileMetadatas) {
            UpdatesEntry ue = provenanceUpdates.get(fm.getDataFile().getChecksumValue());
            if(null != ue) {
                fm.setProvFreeForm(ue.provFreeform);
                changes = true;
            }
        }
        return changes;
    }

    //This is used both to trigger delete of json and to clear the popup before closing it.
    //setting delete to true for the popup closing is ok because we re-set it to false on open every time.
    public void removeJsonAndRelatedData() {
        deleteStoredJson = false;
        if (provJsonState != null) {
            deleteStoredJson = true;
        }
        jsonUploadedTempFile = null;
        provJsonState = null;      
        dropdownSelectedEntity = null;
        storedSelectedEntityName = null;
        provJsonParsedEntities = new HashMap<>();
    }
    
    public boolean getJsonUploadedState() {
        return null != jsonUploadedTempFile || null != provJsonState;   
    }
    
//MAD: This doesn't catch a case where the json was created and then deleted before publish.
    // The deleted time wouldn't show the correct block, but in that case we are reverting to our original state
    public boolean isJsonUpdated() {
        return (null != jsonUploadedTempFile || deleteStoredJson) 
            || (null != dropdownSelectedEntity && !(null != storedSelectedEntityName && storedSelectedEntityName.equals(dropdownSelectedEntity.getEntityName())));
    }
    
    public boolean isFreeformUpdated() {
       return (null != freeformTextInput && !(freeformTextInput.equals(freeformTextState)))
                || (null == freeformTextInput && null != freeformTextState) ;
    }
        
    public String getFreeformTextInput() {
        return freeformTextInput;
    }
    
    public void setFreeformTextInput(String freeformText) {
        freeformTextInput = freeformText;
    }
    
    public String getFreeformTextStored() {
        return freeformTextState;
    }
    
    public void setFreeformTextStored(String freeformText) {
        freeformTextState = freeformText;
    }
    

    //These checks are for the render logic so don't strictly just check published state
    //Tweak these carefully
    public boolean isDataFilePublishedRendering() {
        return null != popupDataFile && popupDataFile.isReleased();
        //return null != fileMetadata && fileMetadata.getDatasetVersion().isPublished();
    }
//MAD: RENAME: techincally this only checks if the current files metadata is a draft, but is only used rendering confirmation popup
    public boolean isDatasetInDraftRendering() {        
        return null != fileMetadata && fileMetadata.getDatasetVersion().isDraft();
    }
    public boolean provJsonAlreadyPublishedRendering() {
        if(null == popupDataFile) { //is hit on loading, returns true to prevent loading of upload elements
            return true;
        }
        for(DataFile df : dataset.getFiles()) {
            if(df.getChecksumType().equals(popupDataFile.getChecksumType())
               && df.getChecksumValue().equals(popupDataFile.getChecksumValue())) { //our popup file exists in the dataset
                //MAD: Unsure if I should be checking empty as well
                
                if(df.getFileMetadatas().size() == 1 && df.getFileMetadata().getDatasetVersion().isDraft()) { 
                    //On file upload, the dataset has the file in the draft so we need a different check
                    return false;
                }
                
                if(null != df.getProvEntityName() && !df.getProvEntityName().isEmpty()) { //we use entity name to see that the prov json was added before
                    return true;
                }
            }
        }

        return false;
//return (null != popupDataFile 
//                && null != popupDataFile.getFileMetadata()); 
                //&& popupDataFile.getProvCplId() != 0); //add when we integrate with provCPL
    }
    public boolean isDataFilePublishedWithDraftRendering() {
        //checks popupDataFile is null in first call
        return isDataFilePublishedRendering() && popupDataFile.getFileMetadata().getDatasetVersion().isDraft();
    }

    public ProvEntityFileData getDropdownSelectedEntity() {
        return dropdownSelectedEntity;
    }

    public void setDropdownSelectedEntity(ProvEntityFileData entity) {
        this.dropdownSelectedEntity = entity;
    }
        
    public void generateProvJsonParsedEntities() throws IOException { 
        provJsonParsedEntities = new HashMap<>(); //MAD: DID NOTHING
        provJsonParsedEntities = provUtil.startRecurseNames(provJsonState);
    }
        
    public ArrayList<ProvEntityFileData> getProvJsonParsedEntitiesArray() throws IOException {
        return new ArrayList<>(provJsonParsedEntities.values());
    }
        
    public ArrayList<ProvEntityFileData> searchParsedEntities(String query) throws IOException {
        ArrayList<ProvEntityFileData> fd = new ArrayList<>();
        
        for ( ProvEntityFileData s : getProvJsonParsedEntitiesArray()) {
            if(s.entityName.contains(query) || s.fileName.contains(query) || s.fileType.contains(query)) {
                fd.add(s);
            }
        }
        fd.sort(null);
        
        return fd;
    }
    
    public ProvEntityFileData getEntityByEntityName(String entityName) {
        return provJsonParsedEntities.get(entityName);
    }
    
     //for storing datafile and provjson in a map value
    class UpdatesEntry {
        String provJson;
        DataFile dataFile;
        String provFreeform;
        Boolean deleteJson;
        
        UpdatesEntry(DataFile dataFile, String provJson, Boolean deleteJson, String provFreeform) {
            this.provJson = provJson;
            this.dataFile = dataFile;
            this.provFreeform = provFreeform;
            this.deleteJson = deleteJson;
        }
    }
    
    public void showJsonPreviewNewWindow() throws IOException, WrappedResponse {
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();
        
        ec.responseReset(); 
        ec.setResponseContentType("application/json;charset=UTF-8"); 
        //ec.setResponseContentLength(contentLength);
        String fileName = "prov-json.json";
        ec.setResponseHeader("Content-Disposition", "inline; filename=\"" + fileName + "\""); 

        OutputStream output = ec.getResponseOutputStream();
        
        OutputStreamWriter osw = new OutputStreamWriter(output, "UTF-8");
        osw.write(provJsonState); //the button calling this will only be rendered if provJsonState exists (e.g. a file is uploaded)
        osw.close();
        fc.responseComplete();

    }
}