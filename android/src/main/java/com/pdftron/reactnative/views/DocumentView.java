package com.pdftron.reactnative.views;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.pdftron.collab.db.entity.AnnotationEntity;
import com.pdftron.collab.ui.viewer.CollabManager;
import com.pdftron.collab.ui.viewer.CollabViewerBuilder;
import com.pdftron.collab.ui.viewer.CollabViewerTabHostFragment;
import com.pdftron.common.PDFNetException;
import com.pdftron.fdf.FDFDoc;
import com.pdftron.pdf.Annot;
import com.pdftron.pdf.Field;
import com.pdftron.pdf.PDFDoc;
import com.pdftron.pdf.PDFViewCtrl;
import com.pdftron.pdf.Page;
import com.pdftron.pdf.PageLabel;
import com.pdftron.pdf.TextExtractor;
import com.pdftron.pdf.ViewChangeCollection;
import com.pdftron.pdf.config.PDFViewCtrlConfig;
import com.pdftron.pdf.config.ToolManagerBuilder;
import com.pdftron.pdf.config.ViewerConfig;
import com.pdftron.pdf.controls.PdfViewCtrlTabFragment;
import com.pdftron.pdf.controls.PdfViewCtrlTabHostFragment;
import com.pdftron.pdf.dialog.BookmarksDialogFragment;
import com.pdftron.pdf.dialog.ViewModePickerDialogFragment;
import com.pdftron.pdf.model.UserBookmarkItem;
import com.pdftron.pdf.tools.AdvancedShapeCreate;
import com.pdftron.pdf.tools.FreehandCreate;
import com.pdftron.pdf.tools.QuickMenu;
import com.pdftron.pdf.tools.QuickMenuItem;
import com.pdftron.pdf.tools.ToolManager;
import com.pdftron.pdf.utils.BookmarkManager;
import com.pdftron.pdf.utils.PdfDocManager;
import com.pdftron.pdf.utils.PdfViewCtrlSettingsManager;
import com.pdftron.pdf.utils.Utils;
import com.pdftron.pdf.utils.ViewerUtils;
import com.pdftron.reactnative.R;
import com.pdftron.reactnative.nativeviews.CustomViewerBuilder2;
import com.pdftron.reactnative.nativeviews.RNPdfViewCtrlTabFragment;
import com.pdftron.reactnative.utils.ReactUtils;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DocumentView extends com.pdftron.pdf.controls.DocumentView implements BookmarksDialogFragment.BookmarksDialogListener {

    private static final String TAG = "ahihi-" + DocumentView.class.getSimpleName();

    // EVENTS
    private static final String ON_NAV_BUTTON_PRESSED = "onLeadingNavButtonPressed";
    private static final String ON_DOCUMENT_LOADED = "onDocumentLoaded";
    private static final String ON_PAGE_CHANGED = "onPageChanged";
    private static final String ON_ZOOM_CHANGED = "onZoomChanged";
    private static final String ON_ANNOTATION_CHANGED = "onAnnotationChanged";
    private static final String ON_BOOKMARK_CHANGED = "onBookmarkChanged";
    private static final String ON_DOCUMENT_ERROR = "onDocumentError";
    private static final String ON_EXPORT_ANNOTATION_COMMAND = "onExportAnnotationCommand";

    private static final String PREV_PAGE_KEY = "previousPageNumber";
    private static final String PAGE_CURRENT_KEY = "pageNumber";

    private static final String ZOOM_KEY = "zoom";

    private static final String KEY_annotList = "annotList";
    private static final String KEY_annotId = "id";
    private static final String KEY_annotPage = "pageNumber";

    private static final String KEY_action = "action";
    private static final String KEY_action_add = "add";
    private static final String KEY_action_modify = "modify";
    private static final String KEY_action_delete = "delete";
    private static final String KEY_annotations = "annotations";
    private static final String KEY_xfdfCommand = "xfdfCommand";
    private static final String KEY_bookmark = "bookmark";


    // EVENTS END
    private final Runnable mLayoutRunnable = new Runnable() {
        @Override
        public void run() {
            measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
            layout(getLeft(), getTop(), getRight(), getBottom());
        }
    };
    private final ArrayList<ToolManager.ToolMode> mDisabledTools = new ArrayList<>();
    private final PDFViewCtrl.OnCanvasSizeChangeListener mOnCanvasSizeChangeListener = new PDFViewCtrl.OnCanvasSizeChangeListener() {
        @Override
        public void onCanvasSizeChanged() {
            WritableMap params = Arguments.createMap();
            params.putString(ON_ZOOM_CHANGED, ON_ZOOM_CHANGED);
            params.putDouble(ZOOM_KEY, getPdfViewCtrl().getZoom());
            onReceiveNativeEvent(params);
        }
    };
    private final ToolManager.AnnotationModificationListener mAnnotationModificationListener = new ToolManager.AnnotationModificationListener() {
        @Override
        public void onAnnotationsAdded(Map<Annot, Integer> map) {
            handleAnnotationChanged(KEY_action_add, map);
        }

        @Override
        public void onAnnotationsPreModify(Map<Annot, Integer> map) {

        }

        @Override
        public void onAnnotationsModified(Map<Annot, Integer> map, Bundle bundle) {
            handleAnnotationChanged(KEY_action_modify, map);
        }

        @Override
        public void onAnnotationsPreRemove(Map<Annot, Integer> map) {
            handleAnnotationChanged(KEY_action_delete, map);
        }

        @Override
        public void onAnnotationsRemoved(Map<Annot, Integer> map) {

        }

        @Override
        public void onAnnotationsRemovedOnPage(int i) {

        }

        @Override
        public void annotationsCouldNotBeAdded(String s) {

        }
    };
    private String mDocumentPath;
    private boolean mIsBase64;
    private File mTempFile;
    private FragmentManager mFragmentManagerSave; // used to deal with lifecycle issue
    private PDFViewCtrlConfig mPDFViewCtrlConfig;
    private ToolManagerBuilder mToolManagerBuilder;
    private ViewerConfig.Builder mBuilder;
    private String mCacheDir;
    private int mInitialPageNumber = -1;
    private boolean mTopToolbarEnabled = true;
    private boolean mPadStatusBar;
    private boolean mAutoSaveEnabled = true;
    // collab
    private CollabManager mCollabManager;
    private boolean mCollabEnabled;
    private String mCurrentUser;
    private String mCurrentUserName;
    // quick menu
    private ReadableArray mAnnotMenuItems;
    private final ToolManager.QuickMenuListener mQuickMenuListener = new ToolManager.QuickMenuListener() {
        @Override
        public boolean onQuickMenuClicked(QuickMenuItem quickMenuItem) {
            return false;
        }

        @Override
        public boolean onShowQuickMenu(QuickMenu quickMenu, Annot annot) {
            // remove unwanted items
            if (mAnnotMenuItems != null && annot != null) {
                ArrayList<Object> keepList = mAnnotMenuItems.toArrayList();
                List<QuickMenuItem> removeList = new ArrayList<>();
                checkQuickMenu(quickMenu.getFirstRowMenuItems(), keepList, removeList);
                checkQuickMenu(quickMenu.getSecondRowMenuItems(), keepList, removeList);
                checkQuickMenu(quickMenu.getOverflowMenuItems(), keepList, removeList);

                removeList.add(new QuickMenuItem(getContext(),R.id.qm_search));
                removeList.add(new QuickMenuItem(getContext(),R.id.qm_share));

                if(quickMenu.getSecondRowMenuItems().size() > 0){
                    QuickMenuItem icSecondDelete = new QuickMenuItem(getContext(), R.id.qm_delete,
                            QuickMenuItem.SECOND_ROW_MENU);
                    removeList.add(icSecondDelete);
                }

                quickMenu.removeMenuEntries(removeList);

                if(quickMenu.getSecondRowMenuItems().size() > 0){
                    QuickMenuItem itemDelete = new QuickMenuItem(getContext(), R.id.qm_delete,
                            QuickMenuItem.FIRST_ROW_MENU);
                    itemDelete.setIcon(R.drawable.ic_delete_black_24dp);
                    itemDelete.setOrder(1);
                    List<QuickMenuItem> addList = new ArrayList<>();
                    addList.add(itemDelete);
                    quickMenu.addMenuEntries(addList);
                }
            }else {
                ArrayList<Object> keepList = mAnnotMenuItems.toArrayList();
                List<QuickMenuItem> removeList = new ArrayList<>();
                checkQuickMenu(quickMenu.getFirstRowMenuItems(), keepList, removeList);
                checkQuickMenu(quickMenu.getSecondRowMenuItems(), keepList, removeList);
                checkQuickMenu(quickMenu.getOverflowMenuItems(), keepList, removeList);
                removeList.add(new QuickMenuItem(getContext(),R.id.qm_search));
                removeList.add(new QuickMenuItem(getContext(),R.id.qm_share));
                quickMenu.removeMenuEntries(removeList);

                List<QuickMenuItem> addList = new ArrayList<>();

                QuickMenuItem annoTTS = new QuickMenuItem(getContext(), R.id.qm_tts,
                        QuickMenuItem.FIRST_ROW_MENU);
                annoTTS.setIcon(R.drawable.annotation_icon_sound_outline);
                annoTTS.setOrder(1);
                addList.add(annoTTS);

                QuickMenuItem annoHightlight = new QuickMenuItem(getContext(), R.id.qm_highlight,
                        QuickMenuItem.FIRST_ROW_MENU);
                annoHightlight.setIcon(R.drawable.ic_annotation_highlight_black_24dp);
                annoHightlight.setOrder(2);
                addList.add(annoHightlight);

                QuickMenuItem annoUnderline = new QuickMenuItem(getContext(), R.id.qm_underline,
                        QuickMenuItem.FIRST_ROW_MENU);
                annoUnderline.setIcon(R.drawable.ic_annotation_underline_black_24dp);
                annoUnderline.setOrder(3);
                addList.add(annoUnderline);

                QuickMenuItem annoSquiggly = new QuickMenuItem(getContext(), R.id.qm_squiggly,
                        QuickMenuItem.FIRST_ROW_MENU);
                annoSquiggly.setIcon(R.drawable.ic_annotation_squiggly_black_24dp);
                annoSquiggly.setOrder(4);
                addList.add(annoSquiggly);

                QuickMenuItem annoStrikeout = new QuickMenuItem(getContext(), R.id.qm_strikeout,
                        QuickMenuItem.FIRST_ROW_MENU);
                annoStrikeout.setIcon(R.drawable.ic_annotation_strikeout_black_24dp);
                annoStrikeout.setOrder(5);
                addList.add(annoStrikeout);

                quickMenu.addMenuEntries(addList);

                if(quickMenu.getFirstRowMenuItems().size() > 6){
                    quickMenu.dismiss();
                }

            }

            return false;
        }

        @Override
        public void onQuickMenuShown() {

        }

        @Override
        public void onQuickMenuDismissed() {

        }
    };
    private boolean mShowCustomizeTool = false;
    private boolean mShouldHandleKeyboard = false;
    private final ViewTreeObserver.OnGlobalLayoutListener mOnGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            Rect r = new Rect();
            getWindowVisibleDisplayFrame(r);
            int screenHeight = getRootView().getHeight();

            // r.bottom is the position above soft keypad or device button.
            // if keypad is shown, the r.bottom is smaller than that before.
            int keypadHeight = screenHeight - r.bottom;

            if (keypadHeight > screenHeight * 0.15) { // 0.15 ratio is perhaps enough to determine keypad height.
                // keyboard is opened
                mShouldHandleKeyboard = true;
            } else {
                // keyboard is closed
                if (mShouldHandleKeyboard) {
                    mShouldHandleKeyboard = false;
                    requestLayout();
                }
            }
        }
    };
    private MenuItem menuBookmark;
    private final PDFViewCtrl.PageChangeListener mPageChangeListener = new PDFViewCtrl.PageChangeListener() {
        @Override
        public void onPageChange(int old_page, int cur_page, PDFViewCtrl.PageChangeState pageChangeState) {
            Log.d(TAG, "onPageChange: " + old_page + ", " + cur_page);

            if (old_page != cur_page || pageChangeState == PDFViewCtrl.PageChangeState.END) {
                DocumentView.this.refreshBookmarkIconAtPage(cur_page);
                WritableMap params = Arguments.createMap();
                params.putString(ON_PAGE_CHANGED, ON_PAGE_CHANGED);
                params.putInt(PREV_PAGE_KEY, old_page);
                params.putInt(PAGE_CURRENT_KEY, cur_page);
                onReceiveNativeEvent(params);
            }
        }
    };

    public DocumentView(Context context) {
        super(context);
    }

    public DocumentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DocumentView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setup(ThemedReactContext reactContext) {
        int width = ViewGroup.LayoutParams.MATCH_PARENT;
        int height = ViewGroup.LayoutParams.MATCH_PARENT;
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(width, height);
        setLayoutParams(params);

        Activity currentActivity = reactContext.getCurrentActivity();
        if (currentActivity instanceof AppCompatActivity) {
            FragmentManager fragmentManager = ((AppCompatActivity) reactContext.getCurrentActivity()).getSupportFragmentManager();
            setSupportFragmentManager(fragmentManager);
            mFragmentManagerSave = fragmentManager;
            mCacheDir = currentActivity.getCacheDir().getAbsolutePath();
            mPDFViewCtrlConfig = PDFViewCtrlConfig.getDefaultConfig(currentActivity);
        } else {
            throw new IllegalStateException("FragmentActivity required.");
        }

        mToolManagerBuilder = ToolManagerBuilder.from().setOpenToolbar(true);
        mBuilder = new ViewerConfig.Builder();
        mBuilder
                .fullscreenModeEnabled(false)
                .multiTabEnabled(false)
                .showCloseTabOption(false)
                .useSupportActionBar(true);
    }



    private boolean hasBookmarkAtPage(int page) {
        ArrayList<Integer> pages = BookmarkManager.getPdfBookmarkedPageNumbers(this.getPdfViewCtrl().getDoc());
        Log.d(TAG, "onPageChange: " + pages);
        boolean hasBookmark = pages.contains(page);
        return hasBookmark;
    }

    private void refreshBookmarkIconAtPage(int page) {
        boolean hasBookmark = this.hasBookmarkAtPage(page);
        this.menuBookmark.setIcon(hasBookmark ? R.drawable.bookmark_active : R.drawable.bookmark_inactive);
    }

    private void removeUserItemBookmarkAt(int pageNumber) {
        ArrayList<UserBookmarkItem> userBookmarkItems = ((ArrayList<UserBookmarkItem>) BookmarkManager.getPdfBookmarks(BookmarkManager.getRootPdfBookmark(this.getPdfDoc(), false)));
        int deleteTargetIndex = -1;
        for (int i = 0; i < userBookmarkItems.size(); i++) {
            if (userBookmarkItems.get(i).pageNumber == pageNumber) {
                deleteTargetIndex = i;
                break;
            }
        }
        if (deleteTargetIndex > -1) {
            try {
                userBookmarkItems.get(deleteTargetIndex).pdfBookmark.delete();
                this.handleBookmarkChange();
            } catch (PDFNetException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onToolbarCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        Log.d(TAG, "onToolbarCreateOptionsMenu: sau");
        inflater.inflate(R.menu.custom_toolbar_menu, menu);

        // handle show customize tools
        this.menuBookmark = menu.findItem(R.id.action_bookmark);
        if (menuBookmark != null) {
            menuBookmark.setVisible(this.mShowCustomizeTool);
        }
        return true;
    }

    @Override
    public boolean onToolbarOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_bookmark) {
            try {
                int pageNumber = this.getPdfViewCtrl().getCurrentPage();
                String pageTitle = this.getPdfViewCtrl().getDoc().getPageLabel(pageNumber).getLabelTitle(pageNumber);
                Log.d(TAG, pageTitle);
                long curObjNum = this.getPdfViewCtrl().getDoc().getPage(pageNumber).getSDFObj().getObjNum();
                if (this.hasBookmarkAtPage(pageNumber)) {
                    this.removeUserItemBookmarkAt(pageNumber);
                } else {
                    BookmarkManager.addPdfBookmark(this.getContext(), this.getPdfViewCtrl(), curObjNum, pageNumber);
                    this.handleBookmarkChange();
                }
                this.refreshBookmarkIconAtPage(pageNumber);
            } catch (PDFNetException e) {
                e.printStackTrace();
            }
        }
        return super.onToolbarOptionsItemSelected(item);
    }

    @Override
    protected PdfViewCtrlTabHostFragment getViewer() {
        if (mCollabEnabled) {
            // Create the Fragment using CollabViewerBuilder
            return CollabViewerBuilder.withUri(mDocumentUri, mPassword)
                    .usingConfig(mViewerConfig)
                    .usingNavIcon(mShowNavIcon ? mNavIconRes : 0)
                    .usingCustomHeaders(mCustomHeaders)
                    .build(getContext());
        }

        return CustomViewerBuilder2.withUri(mDocumentUri, mPassword)
                .usingConfig(mViewerConfig)
                .usingNavIcon(mShowNavIcon ? mNavIconRes : 0)
                .usingCustomHeaders(mCustomHeaders)
                .build(getContext()).useCustomizeTool(this.mShowCustomizeTool).setBookmarkDialogListener(this);
        // return super.getViewer();
    }

    @Override
    protected void buildViewer() {
        super.buildViewer();
        mViewerBuilder = mViewerBuilder.usingTabClass(RNPdfViewCtrlTabFragment.class);

    }

    public void setDocument(String path) {
        if (Utils.isNullOrEmpty(path)) {
            return;
        }
        mDocumentPath = path;
    }

    public void setNavResName(String resName) {
        setNavIconResName(resName);
    }

    public void setDisabledElements(ReadableArray array) {
        disableElements(array);
    }

    public void setDisabledTools(ReadableArray array) {
        disableTools(array);
    }

    public void setCustomHeaders(ReadableMap map) {
        if (null == map) {
            return;
        }
        try {
            mCustomHeaders = ReactUtils.convertMapToJson(map);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setInitialPageNumber(int pageNum) {
        mInitialPageNumber = pageNum;
    }

    public void setPageNumber(int pageNumber) {
        if (getPdfViewCtrlTabFragment() != null &&
                getPdfViewCtrlTabFragment().isDocumentReady()) {
            try {
                getPdfViewCtrl().setCurrentPage(pageNumber);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void setTopToolbarEnabled(boolean topToolbarEnabled) {
        mTopToolbarEnabled = topToolbarEnabled;
    }

    public void setBottomToolbarEnabled(boolean bottomToolbarEnabled) {
        mBuilder = mBuilder.showBottomNavBar(bottomToolbarEnabled);
    }

    public void setPageIndicatorEnabled(boolean pageIndicatorEnabled) {
        mBuilder = mBuilder.showPageNumberIndicator(pageIndicatorEnabled);
    }

    public void setReadOnly(boolean readOnly) {
        mBuilder = mBuilder.documentEditingEnabled(!readOnly);
    }

    public void setFitMode(String fitMode) {
        if (mPDFViewCtrlConfig != null) {
            PDFViewCtrl.PageViewMode mode = null;
            if ("FitPage".equals(fitMode)) {
                mode = PDFViewCtrl.PageViewMode.FIT_PAGE;
            } else if ("FitWidth".equals(fitMode)) {
                mode = PDFViewCtrl.PageViewMode.FIT_WIDTH;
            } else if ("FitHeight".equals(fitMode)) {
                mode = PDFViewCtrl.PageViewMode.FIT_HEIGHT;
            } else if ("Zoom".equals(fitMode)) {
                mode = PDFViewCtrl.PageViewMode.ZOOM;
            }
            if (mode != null) {
                mPDFViewCtrlConfig.setPageViewMode(mode);
            }
        }
    }

    public void setLayoutMode(String layoutMode) {
        String mode = null;
        PDFViewCtrl.PagePresentationMode presentationMode = null;

        if ("Single".equals(layoutMode)) {
            mode = PdfViewCtrlSettingsManager.KEY_PREF_VIEWMODE_SINGLEPAGE_VALUE;
            presentationMode = PDFViewCtrl.PagePresentationMode.SINGLE;
        } else if ("Continuous".equals(layoutMode)) {
            mode = PdfViewCtrlSettingsManager.KEY_PREF_VIEWMODE_CONTINUOUS_VALUE;
            presentationMode = PDFViewCtrl.PagePresentationMode.SINGLE_CONT;
        } else if ("Facing".equals(layoutMode)) {
            mode = PdfViewCtrlSettingsManager.KEY_PREF_VIEWMODE_FACING_VALUE;
            presentationMode = PDFViewCtrl.PagePresentationMode.FACING;
        } else if ("FacingContinuous".equals(layoutMode)) {
            mode = PdfViewCtrlSettingsManager.KEY_PREF_VIEWMODE_FACING_CONT_VALUE;
            presentationMode = PDFViewCtrl.PagePresentationMode.FACING_CONT;
        } else if ("FacingCover".equals(layoutMode)) {
            mode = PdfViewCtrlSettingsManager.KEY_PREF_VIEWMODE_FACINGCOVER_VALUE;
            presentationMode = PDFViewCtrl.PagePresentationMode.FACING_COVER;
        } else if ("FacingCoverContinuous".equals(layoutMode)) {
            mode = PdfViewCtrlSettingsManager.KEY_PREF_VIEWMODE_FACINGCOVER_CONT_VALUE;
            presentationMode = PDFViewCtrl.PagePresentationMode.FACING_COVER_CONT;
        }
        Context context = getContext();
        if (mode != null && context != null && presentationMode != null) {
            PdfViewCtrlSettingsManager.updateViewMode(context, mode);
            if (getPdfViewCtrl() != null) {
                getPdfViewCtrl().setPagePresentationMode(presentationMode);
            }
        }
    }

    public void setColorMode(Integer colorMode) {
        Log.i("Log color mode", String.valueOf(colorMode));
        Context context = getContext();
        if (colorMode != null && context != null) {
            PdfViewCtrlSettingsManager.setColorMode(context, colorMode);
        }
    }

    public void setPadStatusBar(boolean padStatusBar) {
        mPadStatusBar = padStatusBar;
    }

    public void setContinuousAnnotationEditing(boolean contEditing) {
        Context context = getContext();
        if (context != null) {
            PdfViewCtrlSettingsManager.setContinuousAnnotationEdit(context, contEditing);
        }
    }

    public void setAnnotationAuthor(String author) {
        Context context = getContext();
        if (context != null && !Utils.isNullOrEmpty(author)) {
            PdfViewCtrlSettingsManager.updateAuthorName(context, author);
            PdfViewCtrlSettingsManager.setAnnotListShowAuthor(context, true);
        }
    }

    public void setShowSavedSignatures(boolean showSavedSignatures) {
        mToolManagerBuilder = mToolManagerBuilder.setShowSavedSignatures(showSavedSignatures);
    }

    public void setIsBase64String(boolean isBase64String) {
        mIsBase64 = isBase64String;
    }

    public void setAutoSaveEnabled(boolean autoSaveEnabled) {
        mAutoSaveEnabled = autoSaveEnabled;
    }

    public void setCollabEnabled(boolean collabEnabled) {
        mCollabEnabled = collabEnabled;
    }

    public void setCurrentUser(String currentUser) {
        mCurrentUser = currentUser;
    }

    public void setCurrentUserName(String currentUserName) {
        mCurrentUserName = currentUserName;
    }

    public void setAnnotationMenuItems(ReadableArray items) {
        mAnnotMenuItems = items;
    }

    public void setPageChangeOnTap(boolean pageChangeOnTap) {
        Context context = getContext();
        if (context != null) {
            PdfViewCtrlSettingsManager.setAllowPageChangeOnTap(context, pageChangeOnTap);
        }
    }

    public void setThumbnailViewEditingEnabled(boolean thumbnailViewEditingEnabled) {
        mBuilder = mBuilder.thumbnailViewEditingEnabled(thumbnailViewEditingEnabled);
    }

    private void disableElements(ReadableArray args) {
        for (int i = 0; i < args.size(); i++) {
            String item = args.getString(i);
            if ("toolsButton".equals(item)) {
                mBuilder = mBuilder.showAnnotationToolbarOption(false);
            } else if ("searchButton".equals(item)) {
                mBuilder = mBuilder.showSearchView(false);
            } else if ("shareButton".equals(item)) {
                mBuilder = mBuilder.showShareOption(false);
            } else if ("viewControlsButton".equals(item)) {
                mBuilder = mBuilder.showDocumentSettingsOption(false);
            } else if ("thumbnailsButton".equals(item)) {
                mBuilder = mBuilder.showThumbnailView(false);
            } else if ("listsButton".equals(item)) {
                mBuilder = mBuilder
                        .showAnnotationsList(false)
                        .showOutlineList(false)
                        .showUserBookmarksList(false);
            } else if ("thumbnailSlider".equals(item)) {
                mBuilder = mBuilder.showBottomNavBar(false);
            } else if ("editPagesButton".equals(item)) {
                mBuilder = mBuilder.showEditPagesOption(false);
            } else if ("printButton".equals(item)) {
                mBuilder = mBuilder.showPrintOption(false);
            } else if ("closeButton".equals(item)) {
                mBuilder = mBuilder.showCloseTabOption(false);
            } else if ("saveCopyButton".equals(item)) {
                mBuilder = mBuilder.showSaveCopyOption(false);
            } else if ("formToolsButton".equals(item)) {
                mBuilder = mBuilder.showFormToolbarOption(false);
            } else if ("fillSignToolsButton".equals(item)) {
                mBuilder = mBuilder.showFillAndSignToolbarOption(false);
            } else if ("moreItemsButton".equals(item)) {
                mBuilder = mBuilder
                        .showEditPagesOption(false)
                        .showPrintOption(false)
                        .showCloseTabOption(false)
                        .showSaveCopyOption(false)
                        .showFormToolbarOption(false)
                        .showFillAndSignToolbarOption(false)
                        .showEditMenuOption(false)
                        .showReflowOption(false);
            } else if ("outlineListButton".equals(item)) {
                mBuilder = mBuilder.showOutlineList(false);
            } else if ("annotationListButton".equals(item)) {
                mBuilder = mBuilder.showAnnotationsList(false);
            } else if ("userBookmarkListButton".equals(item)) {
                mBuilder = mBuilder.showUserBookmarksList(false);
            } else if ("reflowButton".equals(item)) {
                mBuilder = mBuilder.showReflowOption(false);
            } else if ("editMenuButton".equals(item)) {
                mBuilder = mBuilder.showEditMenuOption(false);
            } else if ("cropPageButton".equals(item)) {
                mBuilder = mBuilder.hideViewModeItems(
                        new ViewModePickerDialogFragment.ViewModePickerItems[]{
                                ViewModePickerDialogFragment.ViewModePickerItems.ITEM_ID_USERCROP,
                        }
                );
            } else if ("reflowPageButton".equals(item)) {
                mBuilder = mBuilder.hideViewModeItems(
                        new ViewModePickerDialogFragment.ViewModePickerItems[]{
                                ViewModePickerDialogFragment.ViewModePickerItems.ITEM_ID_REFLOW,
                        }
                );
            }
        }
        // Custom default hidden
        mBuilder = mBuilder.hideViewModeItems(
                new ViewModePickerDialogFragment.ViewModePickerItems[]{
                        ViewModePickerDialogFragment.ViewModePickerItems.ITEM_ID_COLORMODE,
                        ViewModePickerDialogFragment.ViewModePickerItems.ITEM_ID_USERCROP
                }
        );
        disableTools(args);
    }

    private void disableTools(ReadableArray args) {
        for (int i = 0; i < args.size(); i++) {
            String item = args.getString(i);
            ToolManager.ToolMode mode = convStringToToolMode(item);

            if (mode != null) {
                mDisabledTools.add(mode);
            }
        }
    }

    @Nullable
    private ToolManager.ToolMode convStringToToolMode(String item) {
        ToolManager.ToolMode mode = null;
        if ("freeHandToolButton".equals(item) || "AnnotationCreateFreeHand".equals(item)) {
            mode = ToolManager.ToolMode.INK_CREATE;
        } else if ("highlightToolButton".equals(item) || "AnnotationCreateTextHighlight".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_HIGHLIGHT;
        } else if ("underlineToolButton".equals(item) || "AnnotationCreateTextUnderline".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_UNDERLINE;
        } else if ("squigglyToolButton".equals(item) || "AnnotationCreateTextSquiggly".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_SQUIGGLY;
        } else if ("strikeoutToolButton".equals(item) || "AnnotationCreateTextStrikeout".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_STRIKEOUT;
        } else if ("rectangleToolButton".equals(item) || "AnnotationCreateRectangle".equals(item)) {
            mode = ToolManager.ToolMode.RECT_CREATE;
        } else if ("ellipseToolButton".equals(item) || "AnnotationCreateEllipse".equals(item)) {
            mode = ToolManager.ToolMode.OVAL_CREATE;
        } else if ("lineToolButton".equals(item) || "AnnotationCreateLine".equals(item)) {
            mode = ToolManager.ToolMode.LINE_CREATE;
        } else if ("arrowToolButton".equals(item) || "AnnotationCreateArrow".equals(item)) {
            mode = ToolManager.ToolMode.ARROW_CREATE;
        } else if ("polylineToolButton".equals(item) || "AnnotationCreatePolyline".equals(item)) {
            mode = ToolManager.ToolMode.POLYLINE_CREATE;
        } else if ("polygonToolButton".equals(item) || "AnnotationCreatePolygon".equals(item)) {
            mode = ToolManager.ToolMode.POLYGON_CREATE;
        } else if ("cloudToolButton".equals(item) || "AnnotationCreatePolygonCloud".equals(item)) {
            mode = ToolManager.ToolMode.CLOUD_CREATE;
        } else if ("signatureToolButton".equals(item) || "AnnotationCreateSignature".equals(item)) {
            mode = ToolManager.ToolMode.SIGNATURE;
        } else if ("freeTextToolButton".equals(item) || "AnnotationCreateFreeText".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_CREATE;
        } else if ("stickyToolButton".equals(item) || "AnnotationCreateSticky".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_ANNOT_CREATE;
        } else if ("calloutToolButton".equals(item) || "AnnotationCreateCallout".equals(item)) {
            mode = ToolManager.ToolMode.CALLOUT_CREATE;
        } else if ("stampToolButton".equals(item) || "AnnotationCreateStamp".equals(item)) {
            mode = ToolManager.ToolMode.STAMPER;
        } else if ("AnnotationCreateDistanceMeasurement".equals(item)) {
            mode = ToolManager.ToolMode.RULER_CREATE;
        } else if ("AnnotationCreatePerimeterMeasurement".equals(item)) {
            mode = ToolManager.ToolMode.PERIMETER_MEASURE_CREATE;
        } else if ("AnnotationCreateAreaMeasurement".equals(item)) {
            mode = ToolManager.ToolMode.AREA_MEASURE_CREATE;
        } else if ("AnnotationCreateFileAttachment".equals(item)) {
            mode = ToolManager.ToolMode.FILE_ATTACHMENT_CREATE;
        } else if ("AnnotationCreateSound".equals(item)) {
            mode = ToolManager.ToolMode.SOUND_CREATE;
        } else if ("TextSelect".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_SELECT;
        } else if ("Pan".equals(item)) {
            mode = ToolManager.ToolMode.PAN;
        } else if ("AnnotationEdit".equals(item)) {
            mode = ToolManager.ToolMode.ANNOT_EDIT_RECT_GROUP;
        } else if ("FormCreateTextField".equals(item)) {
            mode = ToolManager.ToolMode.FORM_TEXT_FIELD_CREATE;
        } else if ("FormCreateCheckboxField".equals(item)) {
            mode = ToolManager.ToolMode.FORM_CHECKBOX_CREATE;
        } else if ("FormCreateSignatureField".equals(item)) {
            mode = ToolManager.ToolMode.FORM_SIGNATURE_CREATE;
        } else if ("FormCreateRadioField".equals(item)) {
            mode = ToolManager.ToolMode.FORM_RADIO_GROUP_CREATE;
        } else if ("FormCreateComboBoxField".equals(item)) {
            mode = ToolManager.ToolMode.FORM_COMBO_BOX_CREATE;
        } else if ("FormCreateListBoxField".equals(item)) {
            mode = ToolManager.ToolMode.FORM_LIST_BOX_CREATE;
        } else if ("AnnotationRedact".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_REDACTION;
        } else if ("AnnotationImageStamp".equals(item)) {
            mode = ToolManager.ToolMode.STAMPER;
        } else if ("AnnotationCreateRectAreaMeasurement".equals(item)) {
            mode = ToolManager.ToolMode.RECT_AREA_MEASURE_CREATE;
        } else if ("link".equals(item)) {
            mode = ToolManager.ToolMode.TEXT_LINK_CREATE;
        }
        return mode;
    }

    private void checkQuickMenu(List<QuickMenuItem> menuItems, ArrayList<Object> keepList, List<QuickMenuItem> removeList) {
        for (QuickMenuItem item : menuItems) {
            int menuId = item.getItemId();
            String menuStr = convQuickMenuIdToString(menuId);
            if (!keepList.contains(menuStr)) {
                removeList.add(item);
            }
        }
    }

    @Nullable
    private String convQuickMenuIdToString(int id) {
        String menuStr = null;
        if (id == R.id.qm_appearance) {
            menuStr = "style";
        } else if (id == R.id.qm_note) {
            menuStr = "note";
        } else if (id == R.id.qm_copy) {
            menuStr = "copy";
        } else if (id == R.id.qm_delete) {
            menuStr = "delete";
        } else if (id == R.id.qm_flatten) {
            menuStr = "flatten";
        } else if (id == R.id.qm_text) {
            menuStr = "editText";
        } else if (id == R.id.qm_edit) {
            menuStr = "editInk";
        } else if (id == R.id.qm_search) {
            menuStr = "search";
        } else if (id == R.id.qm_share) {
            menuStr = "share";
        } else if (id == R.id.qm_type) {
            menuStr = "markupType";
        } else if (id == R.id.qm_tts) {
            menuStr = "textToSpeech";
        } else if (id == R.id.qm_screencap_create) {
            menuStr = "screenCapture";
        } else if (id == R.id.qm_play_sound) {
            menuStr = "playSound";
        } else if (id == R.id.qm_open_attachment) {
            menuStr = "openAttachment";
        }
        return menuStr;
    }

    private ViewerConfig getConfig() {
        if (mCacheDir != null) {
            mBuilder.openUrlCachePath(mCacheDir)
                    .saveCopyExportPath(mCacheDir);
        }

        if (mDisabledTools.size() > 0) {
            ToolManager.ToolMode[] modes = mDisabledTools.toArray(new ToolManager.ToolMode[0]);
            if (modes.length > 0) {
                mToolManagerBuilder = mToolManagerBuilder.disableToolModes(modes);
            }
        }
        return mBuilder
                .pdfViewCtrlConfig(mPDFViewCtrlConfig)
                .toolManagerBuilder(mToolManagerBuilder)
                .build();
    }

    @Override
    public void requestLayout() {
        super.requestLayout();

        post(mLayoutRunnable);
    }

    @Override
    protected void onAttachedToWindow() {
        if (null == mFragmentManager) {
            setSupportFragmentManager(mFragmentManagerSave);
        }
        // TODO, update base64 when ViewerBuilder supports byte array
        Uri fileUri = ReactUtils.getUri(getContext(), mDocumentPath, mIsBase64);
        if (fileUri != null) {
            setDocumentUri(fileUri);
            setViewerConfig(getConfig());
            if (mIsBase64 && fileUri.getPath() != null) {
                mTempFile = new File(fileUri.getPath());
            }
        }
        super.onAttachedToWindow();

        // since we are using this component as an individual component,
        // we don't want to fit system window, unless user specifies
        if (!mPadStatusBar) {
            View host = findViewById(R.id.pdfviewctrl_tab_host);
            if (host != null) {
                host.setFitsSystemWindows(false);
            }
            View tabContent = findViewById(R.id.realtabcontent);
            if (tabContent != null) {
                tabContent.setFitsSystemWindows(false);
            }
            View appBar = findViewById(R.id.app_bar_layout);
            if (appBar != null) {
                appBar.setFitsSystemWindows(false);
            }
            View annotToolbar = findViewById(R.id.annotationToolbar);
            if (annotToolbar != null) {
                annotToolbar.setFitsSystemWindows(false);
            }
        }

        if (mPdfViewCtrlTabHostFragment != null) {
            if (!mTopToolbarEnabled) {
                mPdfViewCtrlTabHostFragment.setToolbarTimerDisabled(true);
                if (mPdfViewCtrlTabHostFragment.getToolbar() != null) {
                    mPdfViewCtrlTabHostFragment.getToolbar().setVisibility(GONE);
                }
            }
        }

        getViewTreeObserver().addOnGlobalLayoutListener(mOnGlobalLayoutListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (getPdfViewCtrl() != null) {
            getPdfViewCtrl().removePageChangeListener(mPageChangeListener);
            getPdfViewCtrl().removeOnCanvasSizeChangeListener(mOnCanvasSizeChangeListener);
        }
        if (getToolManager() != null) {
            getToolManager().removeAnnotationModificationListener(mAnnotationModificationListener);
        }
        if (getPdfViewCtrlTabFragment() != null) {
            getPdfViewCtrlTabFragment().removeQuickMenuListener(mQuickMenuListener);
        }

        super.onDetachedFromWindow();

        getViewTreeObserver().removeOnGlobalLayoutListener(mOnGlobalLayoutListener);

        if (mTempFile != null && mTempFile.exists()) {
            mTempFile.delete();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mPdfViewCtrlTabHostFragment != null) {
            mPdfViewCtrlTabHostFragment.onActivityResult(requestCode, resultCode, data);
        }
        if (getPdfViewCtrlTabFragment() != null) {
            getPdfViewCtrlTabFragment().onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onNavButtonPressed() {
        onReceiveNativeEvent(ON_NAV_BUTTON_PRESSED, ON_NAV_BUTTON_PRESSED);
    }

    @Override
    public boolean canShowFileInFolder() {
        return false;
    }

    @Override
    public boolean canShowFileCloseSnackbar() {
        return false;
    }

    @Override
    public boolean canRecreateActivity() {
        return false;
    }

    private void handleAnnotationChanged(String action, Map<Annot, Integer> map) {
        WritableMap params = Arguments.createMap();
        params.putString(ON_ANNOTATION_CHANGED, ON_ANNOTATION_CHANGED);
        params.putString(KEY_action, action);

        WritableArray annotList = Arguments.createArray();
        for (Map.Entry<Annot, Integer> entry : map.entrySet()) {
            Annot key = entry.getKey();

            String uid = null;
            try {
                uid = key.getUniqueID() != null ? key.getUniqueID().getAsPDFText() : null;
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (uid != null) {
                Integer value = entry.getValue();
                WritableMap annotData = Arguments.createMap();
                annotData.putString(KEY_annotId, uid);
                annotData.putInt(KEY_annotPage, value);
                annotList.pushMap(annotData);
            }
        }

        params.putArray(KEY_annotations, annotList);
        onReceiveNativeEvent(params);
    }

    private void handleBookmarkChange() {
        try {
            WritableMap params = Arguments.createMap();
            params.putString(ON_BOOKMARK_CHANGED, ON_BOOKMARK_CHANGED);
            params.putString(KEY_bookmark, BookmarkManager.exportPdfBookmarks(this.getPdfViewCtrl().getDoc()));
            onReceiveNativeEvent(params);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTabDocumentLoaded(String tag) {
        super.onTabDocumentLoaded(tag);

        if (mInitialPageNumber > 0) {
            try {
                getPdfViewCtrl().setCurrentPage(mInitialPageNumber);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        if (!mAutoSaveEnabled) {
            getPdfViewCtrlTabFragment().setSavingEnabled(mAutoSaveEnabled);
        }

        onReceiveNativeEvent(ON_DOCUMENT_LOADED, tag);

        getPdfViewCtrl().addPageChangeListener(mPageChangeListener);
        getPdfViewCtrl().addOnCanvasSizeChangeListener(mOnCanvasSizeChangeListener);

        getToolManager().addAnnotationModificationListener(mAnnotationModificationListener);

        getPdfViewCtrlTabFragment().addQuickMenuListener(mQuickMenuListener);

        // collab
        if (mPdfViewCtrlTabHostFragment instanceof CollabViewerTabHostFragment) {
            CollabViewerTabHostFragment collabHost = (CollabViewerTabHostFragment) mPdfViewCtrlTabHostFragment;
            mCollabManager = collabHost.getCollabManager();
            if (mCollabManager != null) {
                if (mCurrentUser != null) {
                    mCollabManager.setCurrentUser(mCurrentUser, mCurrentUserName);
                    mCollabManager.setCurrentDocument(mDocumentPath);
                    mCollabManager.setCollabManagerListener(new CollabManager.CollabManagerListener() {
                        @Override
                        public void onSendAnnotation(String s, ArrayList<AnnotationEntity> arrayList, String s1, @Nullable String s2) {
                            if (mCollabManager != null) {
                                WritableMap params = Arguments.createMap();
                                params.putString(ON_EXPORT_ANNOTATION_COMMAND, ON_EXPORT_ANNOTATION_COMMAND);
                                params.putString(KEY_action, s);
                                params.putString(KEY_xfdfCommand, mCollabManager.getLastXfdf());
                                onReceiveNativeEvent(params);
                            }
                        }
                    });
                }
            }
        }
    }

    @Override
    public boolean onOpenDocError() {
        super.onOpenDocError();

        String error = "Unknown error";
        if (getPdfViewCtrlTabFragment() != null) {
            int messageId = com.pdftron.pdf.tools.R.string.error_opening_doc_message;
            int errorCode = getPdfViewCtrlTabFragment().getTabErrorCode();
            switch (errorCode) {
                case PdfDocManager.DOCUMENT_SETDOC_ERROR_ZERO_PAGE:
                    messageId = R.string.error_empty_file_message;
                    break;
                case PdfDocManager.DOCUMENT_SETDOC_ERROR_OPENURL_CANCELLED:
                    messageId = R.string.download_cancelled_message;
                    break;
                case PdfDocManager.DOCUMENT_SETDOC_ERROR_WRONG_PASSWORD:
                    messageId = R.string.password_not_valid_message;
                    break;
                case PdfDocManager.DOCUMENT_SETDOC_ERROR_NOT_EXIST:
                    messageId = R.string.file_does_not_exist_message;
                    break;
                case PdfDocManager.DOCUMENT_SETDOC_ERROR_DOWNLOAD_CANCEL:
                    messageId = R.string.download_size_cancelled_message;
                    break;
            }
            error = mPdfViewCtrlTabHostFragment.getString(messageId);
        }
        onReceiveNativeEvent(ON_DOCUMENT_ERROR, error);
        return  true;
    }

    public void importAnnotationCommand(String xfdfCommand, boolean initialLoad) throws PDFNetException {
        if (mCollabManager != null) {
            mCollabManager.importAnnotationCommand(xfdfCommand, initialLoad);
        } else {
            throw new PDFNetException("", 0L, TAG, "importAnnotationCommand", "set collabEnabled to true is required.");
        }
    }

    public String toText(int number) throws PDFNetException {
        PDFViewCtrl pdfViewCtrl = getPdfViewCtrl();
        PDFDoc pdfDoc = pdfViewCtrl.getDoc();
        Page page = pdfDoc.getPage(number);
        TextExtractor txt = new TextExtractor();
        txt.begin(page);

        // Extract words one by one.
        TextExtractor.Word word;
        for (TextExtractor.Line line = txt.getFirstLine(); line.isValid(); line = line.getNextLine()) {
            for (word = line.getFirstWord(); word.isValid(); word = word.getNextWord()) {
                //word.getString();
            }
        }
        return txt.getAsText();
    }

    public void importBookmar(String bookmark) {
        try {
            PDFViewCtrl pdfViewCtrl = getPdfViewCtrl();
            PDFDoc pdfDoc = pdfViewCtrl.getDoc();
            BookmarkManager.removeRootPdfBookmark(pdfViewCtrl, true);
            BookmarkManager.importPdfBookmarks(pdfViewCtrl, bookmark);
            hasBookmarkAtPage(pdfViewCtrl.getCurrentPage());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void importAnnotations(String xfdf) throws PDFNetException {
        PDFViewCtrl pdfViewCtrl = getPdfViewCtrl();

        PDFDoc pdfDoc = pdfViewCtrl.getDoc();

        boolean shouldUnlockRead = false;
        try {
            pdfViewCtrl.docLockRead();
            shouldUnlockRead = true;

            if (pdfDoc.hasDownloader()) {
                // still downloading file, let's wait for next call
                return;
            }
        } finally {
            if (shouldUnlockRead) {
                pdfViewCtrl.docUnlockRead();
            }
        }

        boolean shouldUnlock = false;
        try {
            pdfViewCtrl.docLock(true);
            shouldUnlock = true;

            FDFDoc fdfDoc = FDFDoc.createFromXFDF(xfdf);
            pdfDoc.fdfUpdate(fdfDoc);
            pdfViewCtrl.update(true);
        } finally {
            if (shouldUnlock) {
                pdfViewCtrl.docUnlock();
            }
        }
    }

    public String exportAnnotations(ReadableMap options) throws Exception {
        PDFViewCtrl pdfViewCtrl = getPdfViewCtrl();
        boolean shouldUnlockRead = false;
        try {
            pdfViewCtrl.docLockRead();
            shouldUnlockRead = true;

            PDFDoc pdfDoc = pdfViewCtrl.getDoc();
            if (null == options || !options.hasKey(KEY_annotList)) {
                FDFDoc fdfDoc = pdfDoc.fdfExtract(PDFDoc.e_both);
                return fdfDoc.saveAsXFDF();
            } else {
                ReadableArray arr = options.getArray(KEY_annotList);
                ArrayList<Annot> annots = new ArrayList<>(arr.size());
                for (int i = 0; i < arr.size(); i++) {
                    ReadableMap annotData = arr.getMap(i);
                    String id = annotData.getString(KEY_annotId);
                    int page = annotData.getInt(KEY_annotPage);
                    if (!Utils.isNullOrEmpty(id)) {
                        Annot ann = ViewerUtils.getAnnotById(getPdfViewCtrl(), id, page);
                        if (ann != null && ann.isValid()) {
                            annots.add(ann);
                        }
                    }
                }
                if (annots.size() > 0) {
                    FDFDoc fdfDoc = pdfDoc.fdfExtract(annots);
                    return fdfDoc.saveAsXFDF();
                }
                return "";
            }
        } finally {
            if (shouldUnlockRead) {
                pdfViewCtrl.docUnlockRead();
            }
        }
    }

    public String saveDocument() {
        if (getPdfViewCtrlTabFragment() != null) {
            getPdfViewCtrlTabFragment().setSavingEnabled(true);
            getPdfViewCtrlTabFragment().save(false, true, true);
            getPdfViewCtrlTabFragment().setSavingEnabled(mAutoSaveEnabled);
            if (mIsBase64 && mTempFile != null) {
                try {
                    byte[] data = FileUtils.readFileToByteArray(mTempFile);
                    return Base64.encodeToString(data, Base64.DEFAULT);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return "";
                }
            } else {
                return getPdfViewCtrlTabFragment().getFilePath();
            }
        }
        return null;
    }

    public void flattenAnnotations(boolean formsOnly) throws PDFNetException {
        // go back to pan tool first so it will commit currently typing text boxes
        PDFViewCtrl pdfViewCtrl = getPdfViewCtrl();
        if (pdfViewCtrl.getToolManager() instanceof ToolManager) {
            ToolManager toolManager = (ToolManager) pdfViewCtrl.getToolManager();
            toolManager.setTool(toolManager.createTool(ToolManager.ToolMode.PAN, toolManager.getTool()));
        }

        PDFDoc pdfDoc = pdfViewCtrl.getDoc();

        boolean shouldUnlock = false;
        try {
            pdfViewCtrl.docLock(true);
            shouldUnlock = true;

            pdfDoc.flattenAnnotations(formsOnly);
        } finally {
            if (shouldUnlock) {
                pdfViewCtrl.docUnlock();
            }
        }
    }

    public int getPageCount() throws PDFNetException {
        return getPdfDoc().getPageCount();
    }

    public void setValueForFields(ReadableMap readableMap) throws PDFNetException {
        PDFViewCtrl pdfViewCtrl = getPdfViewCtrl();
        PDFDoc pdfDoc = pdfViewCtrl.getDoc();

        boolean shouldUnlock = false;
        try {
            pdfViewCtrl.docLock(true);
            shouldUnlock = true;

            ReadableMapKeySetIterator iterator = readableMap.keySetIterator();
            while (iterator.hasNextKey()) {
                String fieldName = iterator.nextKey();

                if (fieldName == null) continue;

                // loop through all fields looking for a matching name
                // in case multiple form fields share the same name
                Field field = pdfDoc.getField(fieldName);
                if (field != null && field.isValid()) {
                    setFieldValue(field, fieldName, readableMap);
                }
            }
        } finally {
            if (shouldUnlock) {
                pdfViewCtrl.docUnlock();
            }
        }
    }

    // write lock required around this method
    private void setFieldValue(@NonNull Field field, @NonNull String fieldName, @NonNull ReadableMap readableMap) throws PDFNetException {
        PDFViewCtrl pdfViewCtrl = getPdfViewCtrl();
        int fieldType = field.getType();
        switch (readableMap.getType(fieldName)) {
            case Boolean: {
                boolean fieldValue = readableMap.getBoolean(fieldName);
                if (Field.e_check == fieldType) {
                    ViewChangeCollection view_change = field.setValue(fieldValue);
                    pdfViewCtrl.refreshAndUpdate(view_change);
                }
            }
            break;
            case Number: {
                if (Field.e_text == fieldType) {
                    double fieldValue = readableMap.getDouble(fieldName);
                    ViewChangeCollection view_change = field.setValue(String.valueOf(fieldValue));
                    pdfViewCtrl.refreshAndUpdate(view_change);
                }
            }
            break;
            case String: {
                String fieldValue = readableMap.getString(fieldName);
                if (fieldValue != null &&
                        (Field.e_text == fieldType || Field.e_radio == fieldType)) {
                    ViewChangeCollection view_change = field.setValue(fieldValue);
                    pdfViewCtrl.refreshAndUpdate(view_change);
                }
            }
            break;
            case Null:
            case Map:
            case Array:
                break;
        }
    }

    public void setFlagForFields(ReadableArray fields, Integer flag, Boolean value) throws PDFNetException {
        PDFViewCtrl pdfViewCtrl = getPdfViewCtrl();
        PDFDoc pdfDoc = pdfViewCtrl.getDoc();

        boolean shouldUnlock = false;
        try {
            pdfViewCtrl.docLock(true);
            shouldUnlock = true;

            int fieldCount = fields.size();

            for (int i = 0; i < fieldCount; i++) {
                String fieldName = fields.getString(i);
                if (fieldName == null) continue;

                Field field = pdfDoc.getField(fieldName);
                if (field != null && field.isValid()) {
                    field.setFlag(flag, value);
                }
            }

            pdfViewCtrl.update(true);
        } finally {
            if (shouldUnlock) {
                pdfViewCtrl.docUnlock();
            }
        }
    }

    public void setToolMode(String item) {
        if (getToolManager() != null) {
            ToolManager.ToolMode mode = convStringToToolMode(item);
            getToolManager().setTool(getToolManager().createTool(mode, null));
        }
    }

    public void setPageLabel(int mapping) throws PDFNetException {
        PDFViewCtrl pdfViewCtrl = getPdfViewCtrl();
        PDFDoc pdfDoc = pdfViewCtrl.getDoc();
        PageLabel L1 = PageLabel.create(pdfDoc, PageLabel.e_alphabetic_uppercase, "", 1);
        pdfDoc.setPageLabel(1, L1);

        PageLabel L = PageLabel.create(pdfDoc, PageLabel.e_decimal, "", 1);
        pdfDoc.setPageLabel(mapping, L);
    }

    public boolean commitTool() {
        if (getToolManager() != null) {
            ToolManager.Tool currentTool = getToolManager().getTool();
            if (currentTool instanceof FreehandCreate) {
                ((FreehandCreate) currentTool).commitAnnotation();
                getToolManager().setTool(getToolManager().createTool(ToolManager.ToolMode.PAN, null));
                return true;
            } else if (currentTool instanceof AdvancedShapeCreate) {
                ((AdvancedShapeCreate) currentTool).commit();
                getToolManager().setTool(getToolManager().createTool(ToolManager.ToolMode.PAN, null));
                return true;
            }
        }
        return false;
    }

    public PdfViewCtrlTabFragment getPdfViewCtrlTabFragment() {
        if (mPdfViewCtrlTabHostFragment != null) {
            return mPdfViewCtrlTabHostFragment.getCurrentPdfViewCtrlFragment();
        }
        return null;
    }

    public PDFViewCtrl getPdfViewCtrl() {
        if (getPdfViewCtrlTabFragment() != null) {
            return getPdfViewCtrlTabFragment().getPDFViewCtrl();
        }
        return null;
    }

    public PDFDoc getPdfDoc() {
        if (getPdfViewCtrlTabFragment() != null) {
            return getPdfViewCtrlTabFragment().getPdfDoc();
        }
        return null;
    }

    public ToolManager getToolManager() {
        if (getPdfViewCtrlTabFragment() != null) {
            return getPdfViewCtrlTabFragment().getToolManager();
        }
        return null;
    }

    public void onReceiveNativeEvent(String key, String message) {
        WritableMap event = Arguments.createMap();
        event.putString(key, message);
        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                getId(),
                "topChange",
                event);
    }

    public void onReceiveNativeEvent(WritableMap event) {
        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                getId(),
                "topChange",
                event);
    }

    public void openNavigationUIControl() {
        ((RNPdfViewCtrlTabFragment) this.getPdfViewCtrlTabFragment()).openNavigationUIControl();
    }

    public void showCustomizeTool(boolean showCustomizeTool) {
        this.mShowCustomizeTool = showCustomizeTool;
        Log.d(TAG, "showCustomizeTool: truoc");
    }

    @Override
    public void onBookmarksDialogWillDismiss(int i) {
        Log.d(TAG, "onBookmarksDialogWillDismiss: ");

    }

    @Override
    public void onBookmarksDialogDismissed(int i) {
        int pageNumber = this.getPdfViewCtrl().getCurrentPage();
        this.refreshBookmarkIconAtPage(pageNumber);
    }
}
