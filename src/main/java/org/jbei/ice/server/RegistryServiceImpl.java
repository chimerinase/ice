package org.jbei.ice.server;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jbei.ice.client.RegistryService;
import org.jbei.ice.client.entry.view.model.SampleStorage;
import org.jbei.ice.controllers.AccountController;
import org.jbei.ice.controllers.EntryController;
import org.jbei.ice.controllers.SampleController;
import org.jbei.ice.controllers.SearchController;
import org.jbei.ice.controllers.SequenceController;
import org.jbei.ice.controllers.StorageController;
import org.jbei.ice.controllers.common.ControllerException;
import org.jbei.ice.lib.authentication.InvalidCredentialsException;
import org.jbei.ice.lib.logging.Logger;
import org.jbei.ice.lib.managers.AccountManager;
import org.jbei.ice.lib.managers.AttachmentManager;
import org.jbei.ice.lib.managers.BulkImportManager;
import org.jbei.ice.lib.managers.EntryManager;
import org.jbei.ice.lib.managers.FolderManager;
import org.jbei.ice.lib.managers.GroupManager;
import org.jbei.ice.lib.managers.ManagerException;
import org.jbei.ice.lib.managers.NewsManager;
import org.jbei.ice.lib.managers.SampleManager;
import org.jbei.ice.lib.managers.SequenceManager;
import org.jbei.ice.lib.managers.StorageManager;
import org.jbei.ice.lib.managers.TraceSequenceManager;
import org.jbei.ice.lib.managers.UtilsManager;
import org.jbei.ice.lib.models.Account;
import org.jbei.ice.lib.models.Attachment;
import org.jbei.ice.lib.models.BulkImport;
import org.jbei.ice.lib.models.Entry;
import org.jbei.ice.lib.models.Folder;
import org.jbei.ice.lib.models.Group;
import org.jbei.ice.lib.models.News;
import org.jbei.ice.lib.models.Sample;
import org.jbei.ice.lib.models.Sequence;
import org.jbei.ice.lib.models.SessionData;
import org.jbei.ice.lib.models.Storage;
import org.jbei.ice.lib.models.TraceSequence;
import org.jbei.ice.lib.parsers.GeneralParser;
import org.jbei.ice.lib.permissions.PermissionException;
import org.jbei.ice.lib.permissions.PermissionManager;
import org.jbei.ice.lib.permissions.PermissionsController;
import org.jbei.ice.lib.search.blast.ProgramTookTooLongException;
import org.jbei.ice.lib.utils.BulkImportEntryData;
import org.jbei.ice.lib.utils.PopulateInitialDatabase;
import org.jbei.ice.lib.utils.RichTextRenderer;
import org.jbei.ice.lib.vo.IDNASequence;
import org.jbei.ice.server.bulkimport.BulkImportController;
import org.jbei.ice.shared.AutoCompleteField;
import org.jbei.ice.shared.ColumnField;
import org.jbei.ice.shared.EntryAddType;
import org.jbei.ice.shared.FolderDetails;
import org.jbei.ice.shared.QueryOperator;
import org.jbei.ice.shared.dto.AccountInfo;
import org.jbei.ice.shared.dto.BlastResultInfo;
import org.jbei.ice.shared.dto.BulkImportDraftInfo;
import org.jbei.ice.shared.dto.EntryInfo;
import org.jbei.ice.shared.dto.EntryInfo.EntryType;
import org.jbei.ice.shared.dto.NewsItem;
import org.jbei.ice.shared.dto.ProfileInfo;
import org.jbei.ice.shared.dto.SampleInfo;
import org.jbei.ice.shared.dto.SearchFilterInfo;
import org.jbei.ice.shared.dto.StorageInfo;
import org.jbei.ice.shared.dto.permission.PermissionInfo;
import org.jbei.ice.shared.dto.permission.PermissionInfo.PermissionType;
import org.jbei.ice.shared.dto.permission.PermissionSuggestion;
import org.jbei.ice.web.utils.WebUtils;

import com.google.gwt.user.client.ui.SuggestOracle;
import com.google.gwt.user.client.ui.SuggestOracle.Request;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;

// TODO : this whole class needs to be redone. The logic needs to be moved to controllers/managers
public class RegistryServiceImpl extends RemoteServiceServlet implements RegistryService {

    private static final long serialVersionUID = 1L;

    @Override
    public AccountInfo login(String name, String pass) {

        try {
            SessionData sessionData = AccountController.authenticate(name, pass);
            Logger.info("User by login '" + name + "' successfully logged in");

            Account account = sessionData.getAccount();
            AccountInfo info = this.accountToInfo(account);
            if (info == null)
                return null;

            info.setSessionId(sessionData.getSessionKey());
            int entryCount = EntryManager.getEntryCountBy(info.getEmail());
            info.setUserEntryCount(entryCount);

            boolean isModerator = AccountController.isModerator(account);
            info.setModerator(isModerator);

            long visibleEntryCount;
            if (isModerator)
                visibleEntryCount = EntryManager.getAllEntryCount();
            else
                visibleEntryCount = EntryManager.getNumberOfVisibleEntries(account);

            info.setVisibleEntryCount(visibleEntryCount);

            return info;
        } catch (InvalidCredentialsException e) {
            Logger.warn("Invalid credentials provided by user: " + name);
        } catch (ControllerException e) {
            Logger.error(e);
        } catch (Exception e) {
            Logger.error(e);
        }
        return null;
    }

    @Override
    public AccountInfo sessionValid(String sid) {
        Logger.info("Checking session validity for \"" + sid + "\"");

        try {
            if (AccountController.isAuthenticated(sid)) {
                Account account = AccountController.getAccountBySessionKey(sid);
                AccountInfo info = this.accountToInfo(account);
                int entryCount = EntryManager.getEntryCountBy(info.getEmail());
                info.setUserEntryCount(entryCount);

                boolean isModerator = AccountController.isModerator(account);
                info.setModerator(isModerator);

                long visibleEntryCount;
                if (isModerator)
                    visibleEntryCount = EntryManager.getAllEntryCount();
                else
                    visibleEntryCount = EntryManager.getNumberOfVisibleEntries(account);

                info.setVisibleEntryCount(visibleEntryCount);

                return info;
            }
        } catch (ControllerException e) {
            Logger.error(e);
        } catch (ManagerException e) {
            Logger.error(e);
        }
        return null;
    }

    @Override
    public boolean logout(String sessionId) {
        Logger.info("Deauthenticating session \"" + sessionId + "\"");
        try {
            AccountController.deauthenticate(sessionId);
            return true;
        } catch (ControllerException e) {
            Logger.error(e);
            return false;
        }
    }

    @Override
    public ArrayList<EntryInfo> retrieveEntryData(String sid, ArrayList<Long> entryIds) {

        Logger.info("Retrieving entry data for " + entryIds.size() + " entries");

        try {
            Account account = this.retrieveAccountForSid(sid);
            if (account == null)
                return null;

            ArrayList<EntryInfo> results = new ArrayList<EntryInfo>();
            List<Entry> entries = null;

            entries = EntryManager.getEntriesByIdSet(entryIds);

            if (entries == null)
                return results;

            for (Entry entry : entries) {

                EntryInfo view = EntryViewFactory.createTableViewData(entry);
                if (view == null)
                    continue;

                results.add(view);
            }

            return results;
        } catch (ManagerException e) {
            Logger.error("Error retrieving entry id set", e);
            return null;
        } catch (ControllerException e) {
            Logger.error(e);
            return null;
        }
    }

    @Override
    public LinkedList<Long> sortEntryList(String sessionId, LinkedList<Long> ids,
            ColumnField field, boolean asc) {

        try {
            Account account = this.retrieveAccountForSid(sessionId);
            if (account == null)
                return null;

            Logger.info("Sorting entry list of size " + ids.size() + " by " + field.getName()
                    + (asc ? " ASC" : " DESC"));

            return EntryManager.sortList(ids, field, asc);
        } catch (ManagerException me) {
            Logger.error(me);
            return null;
        } catch (ControllerException e) {
            Logger.error(e);
            return null;
        }
    }

    @Override
    public ArrayList<FolderDetails> retrieveUserCollections(String sessionId, String userId) {
        ArrayList<FolderDetails> results = new ArrayList<FolderDetails>();

        try {
            Account account = retrieveAccountForSid(sessionId);
            if (account == null)
                return null;

            Account userAccount = AccountController.getByEmail(userId);

            // get user folder
            List<Folder> userFolders = FolderManager.getFoldersByOwner(userAccount);
            if (userFolders != null) {
                for (Folder folder : userFolders) {
                    long id = folder.getId();
                    FolderDetails details = new FolderDetails(id, folder.getName(), false);
                    int folderSize = FolderManager.getFolderSize(id);
                    details.setCount(folderSize);
                    details.setDescription(folder.getDescription());
                    results.add(details);
                }
            }

            return results;
        } catch (ControllerException ce) {
            Logger.error(ce);
            return null;
        } catch (ManagerException me) {
            Logger.error(me);
            return null;
        }
    }

    @Override
    public ArrayList<FolderDetails> retrieveCollections(String sessionId) {

        ArrayList<FolderDetails> results = new ArrayList<FolderDetails>();
        try {
            Account account = retrieveAccountForSid(sessionId);
            if (account == null)
                return null;

            Account system = AccountController.getSystemAccount();
            List<Folder> folders = FolderManager.getFoldersByOwner(system);

            for (Folder folder : folders) {
                long id = folder.getId();
                FolderDetails details = new FolderDetails(id, folder.getName(), true);
                int folderSize = FolderManager.getFolderSize(id);
                details.setCount(folderSize);
                details.setDescription(folder.getDescription());
                results.add(details);
            }

            // get user folder
            List<Folder> userFolders = FolderManager.getFoldersByOwner(account);
            if (userFolders != null) {
                for (Folder folder : userFolders) {
                    long id = folder.getId();
                    FolderDetails details = new FolderDetails(id, folder.getName(), false);
                    int folderSize = FolderManager.getFolderSize(id);
                    details.setCount(folderSize);
                    details.setDescription(folder.getDescription());
                    results.add(details);
                }
            }

            return results;

        } catch (ControllerException ce) {
            Logger.error(ce);
            return null;
        } catch (ManagerException me) {
            Logger.error(me);
            return null;
        }
    }

    @Override
    public FolderDetails retrieveEntriesForFolder(String sessionId, long folderId) {

        try {
            Account account = this.retrieveAccountForSid(sessionId);
            if (account == null)
                return null;

            Logger.info(account.getEmail() + " retrieving entries for folder " + folderId);
            Folder folder = FolderManager.get(folderId);
            if (folder == null)
                return null;

            Account system = AccountController.getSystemAccount();
            boolean isSystem = system.getEmail().equals(folder.getOwnerEmail());
            FolderDetails details = new FolderDetails(folder.getId(), folder.getName(), isSystem);

            int folderSize = FolderManager.getFolderSize(folderId);

            details.setCount(folderSize);
            details.setDescription(folder.getDescription());
            ArrayList<Long> contents = new ArrayList<Long>();

            ArrayList<BigInteger> userContents = FolderManager.getFolderContents(folderId, false);

            for (BigInteger id : userContents) {
                contents.add(id.longValue());
            }

            details.setContents(contents);
            return details;
        } catch (ManagerException e) {
            Logger.error(e);
        } catch (ControllerException e) {
            Logger.error(e);
        }
        return null;
    }

    @Override
    public FolderDetails deleteFolder(String sessionId, long folderId) {
        try {
            Account account = this.retrieveAccountForSid(sessionId);
            if (account == null)
                return null;

            Logger.info(account.getEmail() + " deleting folder " + folderId);
            Folder folder = FolderManager.get(folderId);
            if (folder == null)
                return null;

            Account system = AccountController.getSystemAccount();
            boolean isSystem = system.getEmail().equals(folder.getOwnerEmail());
            if (isSystem) {
                Logger.info("Cannot delete system folder");
                return null;
            }

            FolderDetails details = new FolderDetails(folder.getId(), folder.getName(), isSystem);
            int folderSize = FolderManager.getFolderSize(folderId);
            details.setCount(folderSize);
            details.setDescription(folder.getDescription());
            ArrayList<Long> contents = new ArrayList<Long>();
            ArrayList<BigInteger> userContents = FolderManager.getFolderContents(folderId, false);

            for (BigInteger id : userContents) {
                contents.add(id.longValue());
            }

            details.setContents(contents);
            if (FolderManager.delete(folder))
                return details;
        } catch (ManagerException e) {
            Logger.error(e);
        } catch (ControllerException e) {
            Logger.error(e);
        }
        return null;
    }

    @Override
    public FolderDetails retrieveUserEntries(String sid, String userId) {

        try {
            Account account = this.retrieveAccountForSid(sid);
            if (account == null)
                return null;

            Logger.info(account.getEmail() + " retrieving user entries");
            EntryController entryController = new EntryController(account);
            FolderDetails details = new FolderDetails(0, "My Entries", true);
            ArrayList<Long> entries = entryController.getEntryIdsByOwner(userId);
            details.setContents(entries);
            return details;
        } catch (ControllerException e) {
            Logger.error(e);
        }

        return null;
    }

    @Override
    public FolderDetails retrieveAllVisibleEntryIDs(String sid) {
        Account account = null;

        try {
            account = retrieveAccountForSid(sid);
            if (account == null)
                return null;

            Logger.info(account.getEmail() + " retrieving all visible entry ids");
            EntryController entryController = new EntryController(account);

            ArrayList<Long> entries;
            if (AccountManager.isModerator(account))
                entries = entryController.getAllEntryIDs();
            else {
                entries = new ArrayList<Long>();
                entries.addAll(entryController.getAllVisibleEntryIDs(account));
            }

            FolderDetails details = new FolderDetails(-1, "Available Entries", true);
            details.setContents(entries);
            return details;
        } catch (ControllerException e) {
            Logger.error(e);
        } catch (ManagerException e) {
            Logger.error(e);
        }
        return null;
    }

    protected Account retrieveAccountForSid(String sid) throws ControllerException {
        boolean isAuthenticated = AccountController.isAuthenticated(sid);
        if (!isAuthenticated) {
            Logger.info("Session failed authentication: " + sid);
            return null;
        }

        return AccountController.getAccountBySessionKey(sid);
    }

    @Override
    public HashMap<AutoCompleteField, ArrayList<String>> retrieveAutoCompleteData(String sid) {
        HashMap<AutoCompleteField, ArrayList<String>> data = new HashMap<AutoCompleteField, ArrayList<String>>();

        // origin of replication
        ArrayList<String> origin = new ArrayList<String>();
        origin.addAll(UtilsManager.getUniqueOriginOfReplications());
        data.put(AutoCompleteField.ORIGIN_OF_REPLICATION, origin);

        // selection markers
        try {
            ArrayList<String> markers = new ArrayList<String>();
            markers.addAll(UtilsManager.getUniqueSelectionMarkers());
            data.put(AutoCompleteField.SELECTION_MARKERS, markers);
        } catch (ManagerException e) {
            Logger.error(e);
        }

        // promoters
        ArrayList<String> promoters = new ArrayList<String>();
        promoters.addAll(UtilsManager.getUniquePromoters());
        data.put(AutoCompleteField.PROMOTERS, promoters);

        // plasmid names
        ArrayList<String> plasmidNames = new ArrayList<String>();
        plasmidNames.addAll(UtilsManager.getUniquePublicPlasmidNames());
        data.put(AutoCompleteField.PLASMID_NAME, plasmidNames);

        return data;
    }

    @Override
    public EntryInfo retrieveEntryDetails(String sid, long id) {
        try {
            Account account = retrieveAccountForSid(sid);
            if (account == null)
                return null;

            Entry entry = EntryManager.get(id);
            if (entry == null)
                return null;

            if (!PermissionManager.hasReadPermission(entry, account))
                return null;

            ArrayList<Attachment> attachments = AttachmentManager.getByEntry(entry);
            ArrayList<Sample> samples = SampleManager.getSamplesByEntry(entry);
            List<TraceSequence> sequences = TraceSequenceManager.getByEntry(entry);

            Map<Sample, LinkedList<Storage>> sampleMap = new HashMap<Sample, LinkedList<Storage>>();
            for (Sample sample : samples) {
                Storage storage = sample.getStorage();

                LinkedList<Storage> storageList = new LinkedList<Storage>();

                List<Storage> storages = StorageManager.getStoragesUptoScheme(storage);
                if (storages != null)
                    storageList.addAll(storages);
                Storage scheme = StorageManager.getSchemeContainingParentStorage(storage);
                if (scheme != null)
                    storageList.add(scheme);

                sampleMap.put(sample, storageList);
            }

            boolean hasSequence = (SequenceManager.getByEntry(entry) != null);

            EntryInfo info = EntryToInfoFactory.getInfo(account, entry, attachments, sampleMap,
                sequences, hasSequence);

            //
            //  the parsed versions are separated out into complementary fields
            //
            String html = RichTextRenderer.richTextToHtml(info.getLongDescriptionType(),
                info.getLongDescription());
            String parsed = getParsedNotes(html);
            info.setLongDescription(info.getLongDescription());
            info.setParsedDescription(parsed);
            String parsedShortDesc = WebUtils.linkifyText(account, info.getShortDescription());
            info.setLinkifiedShortDescription(parsedShortDesc);
            String parsedLinks = WebUtils.linkifyText(account, info.getLinks());
            info.setLinkifiedLinks(parsedLinks);

            // group with write permissions
            info.setCanEdit(PermissionManager.hasWritePermission(entry, account));

            return info;

        } catch (ManagerException e) {
            Logger.error(e);
            return null;
        } catch (ControllerException e) {
            Logger.error(e);
            return null;
        }
    }

    private String getParsedNotes(String s) {
        if (s == null) {
            return null;
        }

        final StringBuilder buffer = new StringBuilder();
        int newlineCount = 0;

        buffer.append("<p>");
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);

            switch (c) {
            case '\n':
                newlineCount++;
                break;

            case '\r':
                break;

            default:
                if (newlineCount == 1) {
                    buffer.append("<br/>");
                } else if (newlineCount > 1) {
                    buffer.append("</p><p>");
                }

                buffer.append(c);
                newlineCount = 0;
                break;
            }
        }
        if (newlineCount == 1) {
            buffer.append("<br/>");
        } else if (newlineCount > 1) {
            buffer.append("</p><p>");
        }
        buffer.append("</p>");
        return buffer.toString();

    }

    @Override
    public AccountInfo retrieveAccountInfoForSession(String sid) {
        Account account;
        try {
            account = retrieveAccountForSid(sid);
            return accountToInfo(account);
        } catch (ControllerException e) {
            Logger.error(e);
        }

        return null;
    }

    private AccountInfo accountToInfo(Account account) {
        if (account == null)
            return null;

        AccountInfo info = new AccountInfo();
        info.setEmail(account.getEmail());
        info.setFirstName(account.getFirstName());
        info.setLastName(account.getLastName());
        info.setInstitution(account.getInstitution());
        info.setDescription(account.getDescription());

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d yyyy");
        Date memberSinceDate = account.getCreationTime();
        if (memberSinceDate != null)
            info.setSince(dateFormat.format(memberSinceDate));

        return info;
    }

    //
    // SEARCH
    // 

    @Override
    public ArrayList<Long> retrieveSearchResults(String sid, ArrayList<SearchFilterInfo> filters) {
        ArrayList<Long> results = new ArrayList<Long>();

        if (filters == null || filters.isEmpty())
            return results;

        ArrayList<QueryFilter> queryFilters = new ArrayList<QueryFilter>();
        for (SearchFilterInfo filter : filters) {
            QueryFilter queryFilter = new QueryFilter(filter);
            queryFilters.add(queryFilter);
        }

        try {

            Account account = this.retrieveAccountForSid(sid);
            if (account == null)
                return null;

            SearchController search = new SearchController(account);
            Set<Long> filterResults = search.runSearch(queryFilters);
            results.addAll(filterResults);
            return results;
        } catch (ControllerException e) {
            Logger.error(e);
            return null;
        }
    }

    @Override
    public ArrayList<BlastResultInfo> blastSearch(String sid, String query, QueryOperator program) {
        try {
            Account account = this.retrieveAccountForSid(sid);
            if (account == null)
                return null;

            SearchController searchController = new SearchController(account);
            switch (program) {
            case BLAST_N:
                return searchController.runBlastN(query);
            case TBLAST_X:
                return searchController.runTblastx(query);
                //                String proteinQuery = SequenceUtils.translateToProtein(query);  as far as I can tell this is only for display to user

            default:
                return null;
            }

        } catch (ControllerException ce) {
            Logger.error(ce);
            return null;
        } catch (ProgramTookTooLongException e) {
            Logger.error(e);
            return null;
        }
    }

    //
    // STORAGE
    //

    @Override
    public ArrayList<StorageInfo> retrieveChildren(String sid, long id) {
        ArrayList<StorageInfo> result = new ArrayList<StorageInfo>();
        List<Storage> children = null;

        try {
            Storage currentStorage = StorageManager.get(id, true);
            children = new LinkedList<Storage>(currentStorage.getChildren());

            for (Storage storage : children) {

                StorageInfo info = new StorageInfo();
                String index = storage.getIndex();
                if (index == null || index.isEmpty())
                    info.setDisplay(storage.getName());
                else
                    info.setDisplay(storage.getName() + " " + index);
                info.setId(storage.getId());
                result.add(info);

                ArrayList<Sample> samples = SampleManager.getSamplesByStorage(storage);
                if (samples == null || samples.isEmpty())
                    continue;

                ArrayList<SampleInfo> sampleInfos = new ArrayList<SampleInfo>();
                for (Sample sample : samples) {
                    SampleInfo sampleInfo = new SampleInfo();
                    sampleInfo.setLabel(sample.getLabel());
                    sampleInfo.setSampleId("" + sample.getId());
                    sampleInfos.add(sampleInfo);
                }
                info.setSamples(sampleInfos);
            }

            return result;
        } catch (ManagerException e) {
            Logger.error(e);
            return null;
        }
    }

    @Override
    public ArrayList<StorageInfo> retrieveStorageRoot(String sid) {
        try {

            ArrayList<StorageInfo> result = new ArrayList<StorageInfo>();

            for (Storage storage : StorageManager.getAllStorageRoot()) {
                StorageInfo info = new StorageInfo();
                info.setDisplay(storage.getName());
                info.setId(storage.getId());
                result.add(info);
            }

            return result;

        } catch (ManagerException e) {
            Logger.error(e);
            return null;
        }
    }

    //
    // SAMPLES
    //

    // Uses email identifier from session if parameter instance is null
    @Override
    public LinkedList<Long> retrieveSamplesByDepositor(String sid, String email, ColumnField field,
            boolean asc) {

        Account account = null;
        try {
            account = this.retrieveAccountForSid(sid);
        } catch (ControllerException e) {
            e.printStackTrace();
        }
        if (account == null)
            return null;

        if (field == null)
            field = ColumnField.CREATED;

        String depositor = (email == null) ? account.getEmail() : email;
        SampleController sampleController = new SampleController(account);

        // sort param
        try {
            return sampleController.retrieveSamplesByDepositor(depositor, field, asc);
        } catch (ControllerException e) {
            Logger.error(e);
            return null;
        }
    }

    @Override
    public LinkedList<SampleInfo> retrieveSampleInfo(String sid, LinkedList<Long> sampleIds,
            ColumnField sortField, boolean asc) {
        LinkedList<SampleInfo> data = null;
        Account account = null;

        try {
            account = this.retrieveAccountForSid(sid);
            if (account == null)
                return null;

            SampleController sampleController = new SampleController(account);

            LinkedList<Sample> results = sampleController.retrieveSamplesByIdSet(sampleIds, asc);
            if (results != null) {
                data = new LinkedList<SampleInfo>();

                for (Sample sample : results) { // TODO
                    SampleInfo info = new SampleInfo();
                    info.setSampleId(String.valueOf(sample.getId()));
                    info.setCreationTime(sample.getCreationTime());
                    EntryInfo view = EntryViewFactory.createTipView(account, sample.getEntry());
                    info.setEntryInfo(view);
                    info.setLabel(sample.getLabel());
                    info.setNotes(sample.getNotes());
                    Storage storage = sample.getStorage();
                    if (storage != null) {
                        info.setLocationId(String.valueOf(storage.getId()));
                        info.setLocation(storage.getIndex());
                    }
                    data.add(info);
                }
            }

            return data;
        } catch (ControllerException e) {
            Logger.error(e);
            return null;
        }
    }

    @Override
    public FolderDetails retrieveFolderDetails(String sid, long folderId) {
        try {
            Folder folder = FolderManager.get(folderId);
            long id = folder.getId();
            boolean isSystemFolder = folder.getOwnerEmail().equals(
                AccountManager.getSystemAccount().getEmail());
            FolderDetails details = new FolderDetails(id, folder.getName(), isSystemFolder);
            int folderSize = FolderManager.getFolderSize(id);
            details.setCount(folderSize);
            details.setDescription(folder.getDescription());
            return details;

        } catch (ManagerException e) {
            Logger.error(e.getMessage());
            return null;
        }
    }

    @Override
    public FolderDetails createUserCollection(String sid, String name, String description,
            ArrayList<Long> contents) {
        try {
            Account account = this.retrieveAccountForSid(sid);
            Folder folder = new Folder(name);
            folder.setOwnerEmail(account.getEmail());
            folder.setDescription(description);
            folder = FolderManager.save(folder);
            FolderDetails details = new FolderDetails(folder.getId(), folder.getName(), false);
            details.setDescription(folder.getDescription());

            if (contents != null && !contents.isEmpty()) {

                ArrayList<Entry> entrys = EntryManager.getEntriesByIdSet(contents);
                FolderManager.addFolderContents(folder.getId(), entrys);
                details.setContents(contents);
                details.setCount(contents.size());
            }

            return details;
        } catch (ControllerException e) {
            Logger.error(e.getMessage());
            return null;
        } catch (ManagerException e) {
            Logger.error(e.getMessage());
            return null;
        }
    }

    @Override
    public ArrayList<FolderDetails> moveToUserCollection(String sid, long source,
            ArrayList<Long> destination, ArrayList<Long> entryIds) {

        try {
            ArrayList<Entry> entrys = EntryManager.getEntriesByIdSet(entryIds);
            if (FolderManager.removeFolderContents(source, entryIds) != null) {
                ArrayList<FolderDetails> results = new ArrayList<FolderDetails>();

                for (long folderId : destination) {
                    Folder folder = FolderManager.addFolderContents(folderId, entrys);
                    FolderDetails details = new FolderDetails(folder.getId(), folder.getName(),
                            false);
                    int folderSize = FolderManager.getFolderSize(folder.getId());
                    details.setCount(folderSize);
                    details.setDescription(folder.getDescription());
                    results.add(details);
                }

                Folder sourceFolder = FolderManager.get(source);
                int folderSize = FolderManager.getFolderSize(source);
                FolderDetails sourceDetails = new FolderDetails(sourceFolder.getId(),
                        sourceFolder.getName(), false);
                sourceDetails.setCount(folderSize);
                results.add(sourceDetails);
                return results;
            }
        } catch (ManagerException e) {
            Logger.error(e);
        }
        return null;
    }

    @Override
    public FolderDetails removeFromUserCollection(String sid, long source, ArrayList<Long> entryIds) {
        try {
            Folder folder = FolderManager.removeFolderContents(source, entryIds);
            if (folder == null)
                return null;

            FolderDetails details = new FolderDetails(folder.getId(), folder.getName(), false);
            details.setCount(folder.getContents().size());
            details.setDescription(folder.getDescription());
            return details;
        } catch (ManagerException e) {
            Logger.error(e);
            return null;
        }
    }

    @Override
    public ArrayList<FolderDetails> addEntriesToCollection(String sid, ArrayList<Long> destination,
            ArrayList<Long> entryIds) {

        ArrayList<FolderDetails> results = new ArrayList<FolderDetails>();

        try {
            ArrayList<Entry> entrys = EntryManager.getEntriesByIdSet(entryIds);
            for (long folderId : destination) {
                Folder folder = FolderManager.addFolderContents(folderId, entrys);
                FolderDetails details = new FolderDetails(folder.getId(), folder.getName(), false);
                details.setCount(folder.getContents().size());
                details.setDescription(folder.getDescription());
                results.add(details);
            }
            return results;
        } catch (ManagerException e) {
            Logger.error(e);
            return null;
        }
    }

    @Override
    public ProfileInfo retrieveProfileInfo(String sid, String userId) {

        try {
            Account account = retrieveAccountForSid(sid);
            if (account == null)
                return null;

            account = AccountManager.getByEmail(userId);
            if (account == null)
                return null;

            AccountInfo accountInfo = accountToInfo(account);

            // get the count for samples
            int sampleCount = SampleManager.getSampleCountBy(accountInfo.getEmail());
            accountInfo.setUserSampleCount(sampleCount);
            boolean isModerator = AccountManager.isModerator(account);
            long visibleEntryCount;
            if (isModerator)
                visibleEntryCount = EntryManager.getAllEntryCount();
            else
                visibleEntryCount = EntryManager.getNumberOfVisibleEntries(account);
            accountInfo.setVisibleEntryCount(visibleEntryCount);
            int entryCount = EntryManager.getEntryCountBy(accountInfo.getEmail());
            accountInfo.setUserEntryCount(entryCount);

            ProfileInfo profile = new ProfileInfo();
            profile.setAccountInfo(accountInfo);

            if (entryCount > 0) {
                // get user entries
                ArrayList<Long> entries = EntryManager.getEntriesByOwner(userId);
                profile.setUserEntries(entries);
            }

            if (sampleCount > 0) {
                // get user samples
                ArrayList<Long> samples = SampleManager.getSampleIdsByOwner(userId);
                profile.setUserSamples(samples);
            }

            return profile;

        } catch (ManagerException e) {
            Logger.error(e);
            return null;
        } catch (ControllerException e) {
            Logger.error(e);
            return null;
        }
    }

    @Override
    public AccountInfo retrieveAccountInfo(String sid, String userId) {
        try {
            this.retrieveAccountForSid(sid);
            Account account = retrieveAccountForSid(sid);
            if (account == null)
                return null;

            account = AccountManager.getByEmail(userId);
            if (account == null)
                return null;

            AccountInfo info = accountToInfo(account);

            // get the count for samples
            int sampleCount = SampleManager.getSampleCountBy(info.getEmail());
            info.setUserSampleCount(sampleCount);
            boolean isModerator = AccountManager.isModerator(account);
            long visibleEntryCount;
            if (isModerator)
                visibleEntryCount = EntryManager.getAllEntryCount();
            else
                visibleEntryCount = EntryManager.getNumberOfVisibleEntries(account);
            info.setVisibleEntryCount(visibleEntryCount);
            int entryCount = EntryManager.getEntryCountBy(info.getEmail());
            info.setUserEntryCount(entryCount);

            return info;
        } catch (ManagerException e) {
            Logger.error(e);
            return null;
        } catch (ControllerException e) {
            Logger.error(e);
            return null;
        }
    }

    @Override
    public ArrayList<BulkImportDraftInfo> retrieveImportDraftData(String sid, String email) {
        try {
            Account account = retrieveAccountForSid(sid);
            if (account == null)
                return null;

            ArrayList<BulkImport> results = BulkImportManager.retrieveByUser(account);
            ArrayList<BulkImportDraftInfo> info = new ArrayList<BulkImportDraftInfo>();

            if (results != null) {
                for (BulkImport draft : results) {
                    BulkImportDraftInfo draftInfo = new BulkImportDraftInfo();
                    List<BulkImportEntryData> primary = draft.getPrimaryData();
                    if (primary != null)
                        draftInfo.setCount(draft.getPrimaryData().size());
                    else
                        draftInfo.setCount(-1);
                    draftInfo.setCreated(draft.getCreationTime());
                    draftInfo.setId(draft.getId());
                    draftInfo.setName(draft.getName());
                    draftInfo.setType(EntryAddType.stringToType(draft.getType()));
                    info.add(draftInfo);
                }
            }

            return info;

        } catch (ControllerException ce) {
            Logger.error(ce);
            return null;
        } catch (ManagerException me) {
            Logger.error(me);
            return null;
        }
    }

    @Override
    public BulkImportDraftInfo retrieveBulkImport(String sid, long id) {

        BulkImport bi;
        Account account;

        try {
            account = retrieveAccountForSid(sid);
            if (account == null)
                return null;

            bi = BulkImportManager.retrieveById(id);
        } catch (ManagerException me) {
            Logger.error(me);
            return null;
        } catch (ControllerException e) {
            Logger.error(e);
            return null;
        }

        BulkImportDraftInfo draftInfo = new BulkImportDraftInfo();
        draftInfo.setCount(bi.getPrimaryData().size());
        draftInfo.setCreated(bi.getCreationTime());
        draftInfo.setId(bi.getId());
        draftInfo.setName(bi.getName());
        EntryAddType type = EntryAddType.stringToType(bi.getType());
        if (type != null)
            draftInfo.setType(type);

        // primary data
        ArrayList<EntryInfo> primary = new ArrayList<EntryInfo>();
        String ownerEmail = bi.getAccount().getEmail();

        List<BulkImportEntryData> data = bi.getPrimaryData();
        for (BulkImportEntryData datum : data) {
            Entry entry = datum.getEntry();
            entry.setOwnerEmail(ownerEmail);
            // TODO : attachments etc as parameters to the following method call
            EntryInfo info = EntryToInfoFactory.getInfo(account, entry, null, null, null, false);
            primary.add(info);
        }
        draftInfo.setPrimary(primary);

        // secondary data (if any)
        List<BulkImportEntryData> data2 = bi.getSecondaryData();
        if (data2 != null && !data2.isEmpty()) {
            ArrayList<EntryInfo> secondary = new ArrayList<EntryInfo>();
            for (BulkImportEntryData datum : data2) {
                Entry entry2 = datum.getEntry();
                entry2.setOwnerEmail(ownerEmail);

                // TODO : attachments etc as parameters to the following method call
                EntryInfo info = EntryToInfoFactory.getInfo(account, entry2, null, null, null,
                    false);
                secondary.add(info);
            }

            draftInfo.setSecondary(secondary);
        }

        return draftInfo;
    }

    @Override
    public BulkImportDraftInfo updateBulkImportDraft(String sessionId, long id, String email,
            String name, ArrayList<EntryInfo> primary, ArrayList<EntryInfo> secondary) {
        Account account;
        try {
            account = retrieveAccountForSid(sessionId);
            if (account == null)
                return null;

            BulkImportController controller = new BulkImportController(account);
            BulkImport draft = controller.createBulkImport(account, primary, secondary, email);
            draft.setName(name);
            BulkImport result = BulkImportManager.updateBulkImportRecord(id, draft);

            // result to DTO
            BulkImportDraftInfo draftInfo = new BulkImportDraftInfo();
            draftInfo.setId(result.getId());
            draftInfo.setCount(result.getPrimaryData().size());
            draftInfo.setCreated(result.getCreationTime());
            draftInfo.setName(result.getName());
            return draftInfo;

        } catch (ManagerException e) {
            Logger.error(e);
            return null;
        } catch (ControllerException e) {
            Logger.error(e);
            return null;
        }
    }

    @Override
    public BulkImportDraftInfo saveBulkImportDraft(String sid, String email, String name,
            ArrayList<EntryInfo> primary, ArrayList<EntryInfo> secondary) {

        Account account;
        try {
            account = retrieveAccountForSid(sid);
            if (account == null)
                return null;

            if (primary.isEmpty())
                return null;

            BulkImportController controller = new BulkImportController(account);
            BulkImport draft = controller.createBulkImport(account, primary, secondary, email);
            draft.setName(name);
            BulkImport result = BulkImportManager.createBulkImportRecord(draft);

            // result to DTO
            BulkImportDraftInfo draftInfo = new BulkImportDraftInfo();
            draftInfo.setId(result.getId());
            draftInfo.setCount(result.getPrimaryData().size());
            draftInfo.setCreated(result.getCreationTime());
            draftInfo.setName(result.getName());
            return draftInfo;

        } catch (ManagerException e) {
            Logger.error(e);
            return null;
        } catch (ControllerException e) {
            Logger.error(e);
            return null;
        }
    }

    @Override
    public boolean submitBulkImport(String sid, String email, ArrayList<EntryInfo> primary,
            ArrayList<EntryInfo> secondary) {
        try {
            Account account = retrieveAccountForSid(sid);
            if (account == null)
                return false;

            if (primary.isEmpty())
                return false;

            BulkImportController controller = new BulkImportController(account);
            BulkImport bulkImport = controller.createBulkImport(account, primary, secondary, email);
            BulkImportManager.submitBulkImportForVerification(bulkImport);
            return true;

        } catch (ControllerException ce) {
            Logger.error(ce);
            return false;
        } catch (ManagerException me) {
            Logger.error(me);
            return false;
        }
    }

    @Override
    public ArrayList<Long> createEntry(String sid, HashSet<EntryInfo> infoSet) {

        Logger.info("Creating \"" + infoSet.size() + "\" entries");
        ArrayList<Long> result = new ArrayList<Long>();
        Account account = null;

        try {
            account = retrieveAccountForSid(sid);
            if (account == null)
                return result;
        } catch (ControllerException ce) {
            Logger.error(ce);
            return result;
        }

        EntryController controller = new EntryController(account);
        SampleController sampleController = new SampleController(account);
        StorageController storageController = new StorageController(account);

        for (EntryInfo info : infoSet) {
            Entry entry = InfoToModelFactory.infoToEntry(info, null);
            try {
                entry = controller.createEntry(entry);
            } catch (ControllerException ce) {
                Logger.error(ce);
                continue;
            }

            ArrayList<SampleStorage> sampleMap = info.getSampleStorage();

            if (sampleMap != null) {
                for (SampleStorage sampleStorage : sampleMap) {
                    SampleInfo sampleInfo = sampleStorage.getSample();
                    LinkedList<StorageInfo> locations = sampleStorage.getStorageList();

                    Sample sample = sampleController.createSample(sampleInfo.getLabel(),
                        account.getEmail(), sampleInfo.getNotes());
                    sample.setEntry(entry);

                    if (locations == null || locations.isEmpty()) {

                        // create sample, but not location
                        try {
                            Logger.info("Creating sample without location");
                            sampleController.saveSample(sample);
                        } catch (PermissionException e) {
                            Logger.error(e);
                            sample = null;
                        } catch (ControllerException e) {
                            Logger.error(e);
                            sample = null;
                        }
                    } else {
                        // create sample and location
                        String[] labels = new String[locations.size()];
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < labels.length; i++) {
                            labels[i] = locations.get(i).getDisplay();
                            sb.append(labels[i]);
                            if (i - 1 < labels.length)
                                sb.append("/");
                        }

                        Logger.info("Creating sample with locations " + sb.toString());
                        Storage storage = null;
                        try {
                            Storage scheme = StorageManager.get(
                                Long.parseLong(sampleInfo.getLocationId()), false);
                            storage = StorageManager.getLocation(scheme, labels);
                            storage = storageController.update(storage);
                            sample.setStorage(storage);
                        } catch (NumberFormatException e) {
                            Logger.error(e);
                            continue;
                        } catch (ManagerException e) {
                            Logger.error(e);
                            continue;
                        } catch (ControllerException e) {
                            Logger.error(e);
                            continue;
                        }
                    }

                    if (sample != null) {
                        try {
                            sampleController.saveSample(sample);
                        } catch (ControllerException e) {
                            Logger.error(e);
                        } catch (PermissionException e) { // having to deal with permission exceptions do not make sense in this context since entry was created by user
                            Logger.error(e);
                        }
                    }
                }
            }

            result.add(entry.getId());
        }

        return result;
    }

    @Override
    public SampleStorage createSample(String sessionId, SampleStorage sampleStorage, long entryId) {

        Logger.info("Creating sample for entry with id " + entryId);
        Account account = null;

        try {
            account = retrieveAccountForSid(sessionId);
            if (account == null)
                return null;
        } catch (ControllerException ce) {
            Logger.error(ce);
            return null;
        }

        EntryController controller = new EntryController(account);
        SampleController sampleController = new SampleController(account);
        StorageController storageController = new StorageController(account);

        Entry entry = null;
        try {
            entry = controller.get(entryId);
            if (entry == null) {
                Logger.error("Could not retrieve entry with id " + entryId
                        + ". Skipping sample creation");
                return null;
            }
        } catch (ControllerException e) {
            Logger.error(e);
            return null;
        } catch (PermissionException e) {
            Logger.error(e);
            return null;
        }

        SampleInfo sampleInfo = sampleStorage.getSample();
        LinkedList<StorageInfo> locations = sampleStorage.getStorageList();

        Sample sample = sampleController.createSample(sampleInfo.getLabel(), account.getEmail(),
            sampleInfo.getNotes());
        sample.setEntry(entry);

        if (locations == null || locations.isEmpty()) {
            Logger.info("Creating sample without location");

            // create sample, but not location
            try {
                sample = sampleController.saveSample(sample);
                sampleStorage.getSample().setSampleId(sample.getId() + "");
                sampleStorage.getSample().setDepositor(account.getEmail());
                return sampleStorage;
            } catch (PermissionException e) {
                Logger.error(e);
                return null;
            } catch (ControllerException e) {
                Logger.error(e);
                return null;
            }
        }

        // create sample and location
        String[] labels = new String[locations.size()];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < labels.length; i++) {
            labels[i] = locations.get(i).getDisplay();
            sb.append(labels[i]);
            if (i - 1 < labels.length)
                sb.append("/");
        }

        Logger.info("Creating sample with locations " + sb.toString());

        Storage storage = null;
        try {
            Storage scheme = StorageManager.get(Long.parseLong(sampleInfo.getLocationId()), false);
            storage = StorageManager.getLocation(scheme, labels);
            storage = storageController.update(storage);
            sample.setStorage(storage);
            sample = sampleController.saveSample(sample);
            sampleStorage.getSample().setSampleId(sample.getId() + "");
            sampleStorage.getSample().setDepositor(account.getEmail());
            return sampleStorage;
        } catch (NumberFormatException e) {
            Logger.error(e);
        } catch (ManagerException e) {
            Logger.error(e);
        } catch (ControllerException e) {
            Logger.error(e);
        } catch (PermissionException e) {
            Logger.error(e);
        }

        return null;
    }

    @Override
    public boolean updateEntry(String sid, EntryInfo info) {

        try {
            Account account = retrieveAccountForSid(sid);
            if (account == null)
                return false;

            EntryController controller = new EntryController(account);
            Entry existing = controller.getByRecordId(info.getRecordId());

            Entry entry = InfoToModelFactory.infoToEntry(info, existing);

            if (controller.hasWritePermission(entry)) {
                controller.save(existing);
                return true;
            }

        } catch (ControllerException e) {
            Logger.error(e);
        } catch (PermissionException e) {
            Logger.error(e);
        }
        return false;
    }

    @Override
    public HashMap<SampleInfo, ArrayList<String>> retrieveStorageSchemes(String sessionId,
            EntryType type) {

        HashMap<SampleInfo, ArrayList<String>> schemeMap = new HashMap<SampleInfo, ArrayList<String>>();

        List<Storage> schemes = StorageManager.getStorageSchemesForEntryType(type.getName());
        for (Storage scheme : schemes) {

            SampleInfo sampleInfo = new SampleInfo();
            sampleInfo.setLocation(scheme.getName());
            sampleInfo.setLocationId(String.valueOf(scheme.getId()));

            ArrayList<String> schemeOptions = new ArrayList<String>();
            Storage storage;

            try {
                storage = StorageManager.get(scheme.getId(), false);

                if (storage != null && storage.getSchemes() != null) {
                    for (Storage storageScheme : storage.getSchemes()) {
                        schemeOptions.add(storageScheme.getName());
                    }
                }
            } catch (ManagerException e) {
                Logger.error(e);
                continue;
            }

            switch (type) {
            case STRAIN:
                sampleInfo.setLabel(PopulateInitialDatabase.DEFAULT_STRAIN_STORAGE_SCHEME_NAME);
                break;
            case PLASMID:
                sampleInfo.setLabel(PopulateInitialDatabase.DEFAULT_PLASMID_STORAGE_SCHEME_NAME);
                break;
            case PART:
                sampleInfo.setLabel(PopulateInitialDatabase.DEFAULT_PART_STORAGE_SCHEME_NAME);
                break;
            case ARABIDOPSIS:
                sampleInfo
                        .setLabel(PopulateInitialDatabase.DEFAULT_ARABIDOPSIS_STORAGE_SCHEME_NAME);
                break;
            }

            schemeMap.put(sampleInfo, schemeOptions);
        }

        return schemeMap;
    }

    @Override
    public SuggestOracle.Response getPermissionSuggestions(Request req) {

        SuggestOracle.Response resp = new SuggestOracle.Response();
        List<Suggestion> suggestions = new ArrayList<Suggestion>(req.getLimit());

        try {
            // TODO : split tokens if there are spaces. this is for a manager
            Set<Account> accounts = AccountManager.getMatchingAccounts(req.getQuery(),
                req.getLimit());
            for (Account account : accounts) {
                PermissionSuggestion object = new PermissionSuggestion(PermissionType.READ_ACCOUNT,
                        account.getId(), account.getFullName());
                suggestions.add(object);
            }
            Set<Group> groups = GroupManager.getMatchingGroups(req.getQuery(), req.getLimit());
            for (Group group : groups) {
                PermissionSuggestion object = new PermissionSuggestion(PermissionType.READ_GROUP,
                        group.getId(), group.getLabel());
                suggestions.add(object);
            }
        } catch (ManagerException e) {
            Logger.error(e);
        }

        resp.setSuggestions(suggestions);
        return resp;
    }

    @Override
    public ArrayList<PermissionInfo> retrievePermissionData(String sessionId, Long entryId) {

        ArrayList<PermissionInfo> results = null;
        Entry entry = null;

        final Account account;
        try {
            account = retrieveAccountForSid(sessionId);
            if (account == null)
                return null;

            entry = EntryManager.get(entryId);
            if (entry == null)
                return null;

            if (!PermissionManager.hasReadPermission(entry, account))
                return null;
        } catch (ControllerException e) {
            Logger.error(e);
        } catch (ManagerException me) {
            Logger.error(me);
        }

        results = new ArrayList<PermissionInfo>();

        try {
            Set<Account> readAccounts = PermissionManager.getReadUser(entry);
            for (Account readAccount : readAccounts) {
                results.add(new PermissionInfo(PermissionType.READ_ACCOUNT, readAccount.getId(),
                        readAccount.getFullName()));
            }
        } catch (ManagerException me) {
            Logger.error(me);
        }

        try {
            Set<Account> writeAccounts = PermissionManager.getWriteUser(entry);
            for (Account writeAccount : writeAccounts) {
                results.add(new PermissionInfo(PermissionType.WRITE_ACCOUNT, writeAccount.getId(),
                        writeAccount.getFullName()));
            }
        } catch (ManagerException me) {
            Logger.error(me);
        }

        try {
            Set<Group> readGroups = PermissionManager.getReadGroup(entry);
            for (Group group : readGroups) {
                results.add(new PermissionInfo(PermissionType.READ_GROUP, group.getId(), group
                        .getLabel()));
            }
        } catch (ManagerException me) {
            Logger.error(me);
        }

        try {
            Set<Group> writeGroups = PermissionManager.getWriteGroup(entry);
            for (Group group : writeGroups) {
                results.add(new PermissionInfo(PermissionType.WRITE_GROUP, group.getId(), group
                        .getLabel()));
            }
        } catch (ManagerException me) {
            Logger.error(me);
        }
        return results;
    }

    @Override
    public ArrayList<NewsItem> retrieveNewsItems(String sessionId) {

        Account account;
        try {
            account = retrieveAccountForSid(sessionId);
            if (account == null)
                return null;
        } catch (ControllerException e) {
            Logger.error(e);
        }

        ArrayList<NewsItem> items = new ArrayList<NewsItem>();
        ArrayList<News> results;

        try {
            results = NewsManager.retrieveAll();
            for (News news : results) {
                NewsItem item = new NewsItem(String.valueOf(news.getId()), news.getCreationTime(),
                        news.getTitle(), news.getBody());
                items.add(item);
            }
        } catch (ManagerException e) {
            Logger.error(e);
        }

        return items;
    }

    @Override
    public NewsItem createNewsItem(String sessionId, NewsItem item) {

        Account account;
        try {
            account = retrieveAccountForSid(sessionId);
            if (account == null)
                return null;

            News news = new News();
            news.setTitle(item.getHeader());
            news.setBody(item.getBody());

            News saved = NewsManager.save(news);
            item.setCreationDate(saved.getCreationTime());
            item.setId(String.valueOf(saved.getId()));
            return item;
        } catch (ManagerException e) {
            Logger.error(e);
        } catch (ControllerException e) {
            Logger.error(e);
        }

        return null;
    }

    @Override
    public FolderDetails updateFolder(String sid, long folderId, FolderDetails update) {
        Account account;
        try {
            account = retrieveAccountForSid(sid);
            if (account == null)
                return null;

            Folder folder = FolderManager.get(folderId);
            if (folder == null)
                return null;

            Logger.info("Updating folder " + folder.getName() + " with id " + folder.getId());
            folder.setName(update.getName());
            folder.setDescription(update.getDescription());
            Folder updated = FolderManager.update(folder);
            update.setId(updated.getId());
            return update;
        } catch (ManagerException e) {
            Logger.error(e);
        } catch (ControllerException e) {
            Logger.error(e);
        }

        return null;
    }

    @Override
    public boolean addPermission(String sessionId, long entryId, PermissionInfo permissionInfo) {

        Account account;
        try {
            account = retrieveAccountForSid(sessionId);
            if (account == null)
                return false;

            Logger.info("Updating permissions for entry with id \"" + entryId + "\"");
            EntryController entryController = new EntryController(account);
            PermissionsController permissionController = new PermissionsController(account);
            Entry entry = entryController.get(entryId);
            if (entry == null)
                return false;

            permissionController.addPermission(permissionInfo.getType(), entry,
                permissionInfo.getId());
            return true;

        } catch (ControllerException e) {
            Logger.error(e);
        } catch (PermissionException e) {
            Logger.error(e);
        }

        return false;
    }

    @Override
    public boolean removePermission(String sessionId, long entryId, PermissionInfo permissionInfo) {

        Account account;
        try {
            account = retrieveAccountForSid(sessionId);
            if (account == null)
                return false;

            Logger.info("Removing permissions for entry with id \"" + entryId + "\"");
            EntryController entryController = new EntryController(account);
            Entry entry = entryController.get(entryId);
            if (entry == null)
                return false;

            PermissionsController permissionController = new PermissionsController(account);
            permissionController.removePermission(permissionInfo.getType(), entry,
                permissionInfo.getId());
            return true;

        } catch (ControllerException e) {
            Logger.error(e);
        } catch (PermissionException e) {
            Logger.error(e);
        }

        return false;
    }

    @Override
    public boolean saveSequence(String sessionId, long entryId, String sequenceUser) {

        Account account;
        Entry entry;

        try {
            account = retrieveAccountForSid(sessionId);
            if (account == null)
                return false;

            EntryController entryController = new EntryController(account);
            entry = entryController.get(entryId);
            if (entry == null) {
                Logger.error("Could not retrieve entry with id " + entryId);
                return false;
            }
        } catch (ControllerException e) {
            Logger.error(e);
            return false;
        } catch (PermissionException e) {
            Logger.error(e);
            return false;
        }

        SequenceController sequenceController = new SequenceController(account);
        IDNASequence dnaSequence = SequenceController.parse(sequenceUser);

        if (dnaSequence == null || dnaSequence.getSequence().equals("")) {
            String errorMsg = "Couldn't parse sequence file! Supported formats: "
                    + GeneralParser.getInstance().availableParsersToString()
                    + ". "
                    + "If you believe this is an error, please contact the administrator with your file";

            Logger.error(errorMsg);
            return false;
        }

        Sequence sequence = null;

        try {
            sequence = SequenceController.dnaSequenceToSequence(dnaSequence);
            sequence.setSequenceUser(sequenceUser);
            sequence.setEntry(entry);
            return sequenceController.save(sequence) != null;
        } catch (ControllerException e) {
            Logger.error(e);
        } catch (PermissionException e) {
            Logger.error(e);
        }
        return false;
    }
}
