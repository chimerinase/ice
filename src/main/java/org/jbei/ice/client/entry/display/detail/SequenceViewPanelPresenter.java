package org.jbei.ice.client.entry.display.detail;

import org.jbei.ice.client.entry.display.view.DeleteSequenceHandler;
import org.jbei.ice.lib.shared.dto.entry.PartData;

import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.IsWidget;
import gwtupload.client.IUploader.OnFinishUploaderHandler;

/**
 * Presenter for {@link SequenceViewPanel}
 *
 * @author Hector Plahar
 */
public class SequenceViewPanelPresenter {

    public interface ISequenceView {

        void addSubmitSequencePasteHandler(ClickHandler handler);

        void updateSequenceContents();

        void updateSequenceHeaders();

        String getSequence();

        PartData getPartData();

        void setHasSequence(boolean hasSequence);

        void hideDialog();

        void showSequenceDeleteLink(DeleteSequenceHandler deleteHandler);

        void setFinishHandler(OnFinishUploaderHandler handler);

        IsWidget asWidget();
    }

    private final ISequenceView view;
    private boolean canEdit;

    public SequenceViewPanelPresenter(ISequenceView view) {
        this.view = view;
    }

    public void addSequencePasteHandler(ClickHandler handler) {
        this.view.addSubmitSequencePasteHandler(handler);
    }

    public void addSequenceFileUploadHandler(OnFinishUploaderHandler handler) {
        this.view.setFinishHandler(handler);
    }

    public void updateSequenceView() {
        this.view.hideDialog();
        this.view.updateSequenceHeaders();
        this.view.updateSequenceContents();
    }

    public String getSequence() {
        return view.getSequence();
    }

    public PartData getPartData() {
        return view.getPartData();
    }

    public ISequenceView getView() {
        return this.view;
    }

    public void setHasSequence(boolean result) {
        view.setHasSequence(result);
    }

    public void setIsCanEdit(boolean canEdit, DeleteSequenceHandler deleteHandler) {
        this.canEdit = canEdit;
        if (canEdit && view.getPartData().isHasSequence())
            view.showSequenceDeleteLink(deleteHandler);
    }

    public boolean isCanEdit() {
        return this.canEdit;
    }
}
