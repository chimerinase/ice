package org.jbei.ice.lib.bulkupload;

import org.apache.commons.lang.StringUtils;
import org.jbei.ice.lib.dao.DAOFactory;
import org.jbei.ice.lib.dto.bulkupload.EntryField;
import org.jbei.ice.lib.dto.entry.EntryType;
import org.jbei.ice.lib.entry.model.Entry;
import org.jbei.ice.lib.entry.model.Plasmid;
import org.jbei.ice.lib.entry.model.Strain;
import org.jbei.ice.lib.shared.BioSafetyOption;
import org.jbei.ice.lib.shared.StatusType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Validation class for Bulk Upload Entries
 *
 * @author Hector Plahar
 */
public class BulkUploadValidation {

    private final BulkUpload upload;
    private final Set<EntryField> failedFields;

    public BulkUploadValidation(BulkUpload upload) {
        if (upload == null)
            throw new IllegalArgumentException("Cannot validate null upload");
        this.upload = upload;
        this.failedFields = new HashSet<>();
    }

    /**
     * Validates the bulk upload entries
     * <b>Note</b> This retrieves the entries for the upload at the time of validation
     *
     * @return true if all the fields of all associated entries validate, false otherwise. A call
     * can then be made to getFailedFields() to retrieve the actual fields that did not validate
     */
    public boolean isValid() {
        validate();
        return failedFields.isEmpty();
    }

    /**
     * @return the list of fields that have failed validation if called after isValid()
     * otherwise returns an empty list
     */
    public Set<EntryField> getFailedFields() {
        return this.failedFields;
    }

    /**
     * Validates the contents of a bulk upload. This is intended for use when it is being submitted for approval.
     * Validation is required on the business logic side as a result of the ability to save drafts
     */
    private void validate() {
        ArrayList<Long> contentIds = DAOFactory.getBulkUploadDAO().getEntryIds(this.upload.getId());
        for (long contentId : contentIds) {
            Entry entry = DAOFactory.getEntryDAO().get(contentId);
            validateEntry(entry);
        }
    }

    protected void validateEntry(Entry entry) {
        EntryType type = EntryType.nameToType(entry.getRecordType());
        if (type == null)
            return;

        validateCommonFields(entry);

        switch (type) {
            case STRAIN:
                validateStrain((Strain) entry);
                break;

            case PLASMID:
                validatePlasmid((Plasmid) entry);
                break;

            case ARABIDOPSIS:
                validateCommonFields(entry);
                break;
        }
    }

    /**
     * Validates the fields of a strain to ensure that the required properties are valid. If a strain has a plasmid,
     * the plasmid is validated also
     *
     * @param strain strain entry to validate
     */
    private void validateStrain(Strain strain) {
        if (!strain.getSelectionMarkers().isEmpty())
            failedFields.add(EntryField.SELECTION_MARKERS);
    }

    /**
     * Validates the plasmid specific fields. Any fields that are invalid are added to the
     * existing list of invalid
     */
    private void validatePlasmid(Plasmid plasmid) {
        if (!plasmid.getSelectionMarkers().isEmpty())
            failedFields.add(EntryField.SELECTION_MARKERS);
    }

    /**
     * validates fields that are common to all entries (e.g. BioSafety Level)
     *
     * @param entry entry whose common fields are being validated
     * @return the list of fields that did not validate if validation did not complete successfully;
     * an empty list otherwise
     */
    private Set<EntryField> validateCommonFields(Entry entry) {
        if (!BioSafetyOption.isValidOption(entry.getBioSafetyLevel()))
            failedFields.add(EntryField.BIO_SAFETY_LEVEL);

        if (StatusType.displayValueOf(entry.getStatus()).isEmpty())
            failedFields.add(EntryField.STATUS);

        if (entry.getName() == null)
            failedFields.add(EntryField.NAME);

        if (StringUtils.isBlank(entry.getCreator()))
            failedFields.add(EntryField.CREATOR);

        if (StringUtils.isBlank(entry.getCreatorEmail()))
            failedFields.add(EntryField.CREATOR_EMAIL);

        // principal investigator is required and that should create at least one funding source
        if (StringUtils.isBlank(entry.getPrincipalInvestigator()))
            failedFields.add(EntryField.PI);

        if (!StringUtils.isBlank(entry.getShortDescription()))
            failedFields.add(EntryField.SUMMARY);

        return failedFields;
    }
}
