package org.jbei.ice.client.common.header;

import java.util.LinkedHashMap;

import org.jbei.ice.client.AppController;
import org.jbei.ice.client.Page;
import org.jbei.ice.client.common.FilterOperand;
import org.jbei.ice.client.common.widget.PopupHandler;
import org.jbei.ice.shared.dto.AccountInfo;
import org.jbei.ice.shared.dto.SearchFilterInfo;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.*;

public class HeaderView extends Composite {

    interface Resources extends ClientBundle {

        static Resources INSTANCE = GWT.create(Resources.class);

        @Source("org/jbei/ice/client/resource/image/logo.gif")
        ImageResource logo();

        @Source("org/jbei/ice/client/resource/image/arrow_down.png")
        ImageResource arrowDown();
    }

    private Hyperlink logout;
    private SearchCompositeBox searchInput;
    private Button searchBtn;
    private final SearchOption option;
    private final HeaderPresenter presenter;

    public HeaderView() {
        Widget searchPanel = createSearchPanel();
        FlexTable table = new FlexTable();
        table.setCellPadding(0);
        table.setCellSpacing(0);
        table.setStyleName("pad-right-10");
        table.setWidth("100%");
        initWidget(table);

        VerticalPanel vertical = new VerticalPanel();
        vertical.add(createLoggedInContents());
        vertical.add(searchPanel);
        vertical.addStyleName("float_right");
        vertical.setSpacing(4);

        HorizontalPanel horizontal = new HorizontalPanel();
        horizontal.setWidth("100%");
        horizontal.add(getImageHeader());
        horizontal.add(vertical);

        table.setWidget(0, 0, horizontal);

        // search Option
        option = new SearchOption();
        option.addStyleName("background_white");
        option.setWidth("350px");
        option.setHeight("150px");

        presenter = new HeaderPresenter(this);
    }

    public String getSelectedFilterValue() {
        final ListBox filterOptions = option.getFilterOptions();
        int index = filterOptions.getSelectedIndex();
        return filterOptions.getValue(index);
    }

    // handler for clicking search
    public void addSearchClickHandler(ClickHandler handler) {
        searchBtn.addClickHandler(handler);
    }

    public void setSearchButtonEnable(boolean enable) {
        searchBtn.setEnabled(enable);
    }

    public void setAddFilterHandler(ClickHandler handler) {
        option.getAddFilter().addClickHandler(handler);
    }

    public void setSearchOptions(LinkedHashMap<String, String> options) {
        option.setOptions(options);
    }

    private Widget getImageHeader() {
        Image img = new Image(Resources.INSTANCE.logo());
        return img;
    }

    protected Widget createSearchPanel() {
        FlexTable layout = new FlexTable();
        layout.setCellPadding(4);
        layout.setCellSpacing(1);

        if (!isUserLoggedIn()) {
            return layout;
        }

        searchInput = new SearchCompositeBox();
        searchInput.addTextBoxKeyDownHandler(new KeyDownHandler() {

            @Override
            public void onKeyDown(KeyDownEvent event) {
                if (event.getNativeKeyCode() != KeyCodes.KEY_ENTER)
                    return;
                searchBtn.click();
            }
        });

        layout.setWidget(0, 0, searchInput);
        layout.getFlexCellFormatter().setRowSpan(0, 0, 2);

        searchBtn = new Button("Search");
        searchBtn.setStyleName("quick_search_btn");
        layout.setWidget(0, 1, searchBtn);
        layout.getFlexCellFormatter().setRowSpan(0, 1, 2);
        layout.setStyleName("float_right");
        return layout;
    }

    protected boolean isUserLoggedIn() {
        return AppController.sessionId != null;
    }

    /**
     * @return top right hand corner widget. Empty when the user is not logged in
     *         TODO the logic pertaining to setting the names should be moved to a
     *         controller/presenter
     */
    private Widget createLoggedInContents() {
        HorizontalPanel panel = new HorizontalPanel();
        panel.setStyleName("float_right");
        panel.addStyleName("font-95em");

        if (AppController.accountInfo == null) {
            panel.add(new HTML(SafeHtmlUtils.EMPTY_SAFE_HTML));
            return panel;
        }

        AccountInfo info = AppController.accountInfo;

        // Welcome text
        HTML welcometxt = new HTML("Welcome,&nbsp;");
        Hyperlink link = new Hyperlink(info.getFirstName() + " " + info.getLastName(),
                                       "page=profile;id=" + info.getEmail());
        panel.add(welcometxt);
        panel.add(link);

        // pipe
        HTML pipe = new HTML("&nbsp;|&nbsp;");
        pipe.addStyleName("color_eee");
        panel.add(pipe);

        // logout link
        logout = new Hyperlink("Log Out", Page.LOGOUT.getLink());
        panel.add(logout);

        return panel;
    }

    public String getSearchInput() {
        return this.searchInput.getTextBox().getText();
    }

    public SearchCompositeBox getSearchComposite() {
        return this.searchInput;
    }

    public SearchFilterInfo getBlastInfo() {
        return presenter.getBlastInfo();
    }

    public void setFilterChangeHandler(ChangeHandler handler) {
        final ListBox filterOptions = option.getFilterOptions();
        filterOptions.addChangeHandler(handler);
    }

    public void createPullDownHandler() {
        if (this.searchInput != null) {
            PopupHandler handler = new PopupHandler(option, this.searchInput.getTextBox()
                                                                            .getElement(), -342, 8, false);
            this.searchInput.getPullDownArea().addClickHandler(handler);
        }
    }

    public void setFilterOperands(FilterOperand currentSelected) {
        option.setFilterOperands(currentSelected);
    }
}
