package com.ayuget.redface.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.ayuget.redface.R;
import com.ayuget.redface.data.api.MDLink;
import com.ayuget.redface.data.api.MDService;
import com.ayuget.redface.data.api.hfr.HFRUrlParser;
import com.ayuget.redface.data.api.model.Category;
import com.ayuget.redface.data.api.model.Topic;
import com.ayuget.redface.data.api.model.TopicStatus;
import com.ayuget.redface.data.api.model.User;
import com.ayuget.redface.data.rx.EndlessObserver;
import com.ayuget.redface.data.rx.SubscriptionHandler;
import com.ayuget.redface.data.state.CategoriesStore;
import com.ayuget.redface.ui.event.EditPostEvent;
import com.ayuget.redface.ui.event.GoToTopicEvent;
import com.ayuget.redface.ui.event.InternalLinkClickedEvent;
import com.ayuget.redface.ui.event.PageRefreshRequestEvent;
import com.ayuget.redface.ui.event.PageRefreshedEvent;
import com.ayuget.redface.ui.event.QuotePostEvent;
import com.ayuget.redface.ui.event.TopicContextItemSelectedEvent;
import com.ayuget.redface.ui.fragment.DefaultFragment;
import com.ayuget.redface.ui.fragment.DetailsDefaultFragment;
import com.ayuget.redface.ui.fragment.TopicFragment;
import com.ayuget.redface.ui.fragment.TopicFragmentBuilder;
import com.ayuget.redface.ui.fragment.TopicListFragment;
import com.ayuget.redface.ui.fragment.TopicListFragmentBuilder;
import com.ayuget.redface.ui.misc.PagePosition;
import com.nispok.snackbar.Snackbar;
import com.nispok.snackbar.SnackbarManager;
import com.rengwuxian.materialedittext.MaterialEditText;
import com.squareup.otto.Subscribe;

import javax.inject.Inject;

public class TopicsActivity extends BaseDrawerActivity implements TopicListFragment.OnTopicClickedListener {
    private static final String LOG_TAG = TopicsActivity.class.getSimpleName();

    private static final String DEFAULT_FRAGMENT_TAG = "default_fragment";

    private static final String DETAILS_DEFAULT_FRAGMENT_TAG = "details_default_fragment";

    private static final String TOPICS_FRAGMENT_TAG = "topics_fragment";

    private static final String TOPIC_FRAGMENT_TAG = "topic_fragment";

    private static final String ARG_TOPIC = "topic";

    private static final String ARG_CURRENT_CATEGORY = "currentCategory";

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean twoPaneMode = false;

    private MaterialEditText goToPageEditText;

    TopicListFragment topicListFragment;

    TopicFragment topicFragment;

    DefaultFragment defaultFragment;

    private SubscriptionHandler<Integer, Topic> topicDetailsSearchHandler = new SubscriptionHandler<>();

    private SubscriptionHandler<Topic, String> quoteHandler = new SubscriptionHandler<>();

    @Inject
    CategoriesStore categoriesStore;

    @Inject
    MDService mdService;

    @Inject
    HFRUrlParser urlParser;

    boolean restoredInstanceState = false;

    private Category currentCategory;

    private PageRefreshRequestEvent refreshRequestEvent;

    boolean canLaunchReplyActivity = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_topics, savedInstanceState);

        if (getIntent().getData() != null) {
            restoredInstanceState = true;

            String url = getIntent().getData().toString();

            urlParser.parseUrl(url).ifTopicLink(new MDLink.IfIsTopicLink() {
                @Override
                public void call(final Category category, final int topicId, final int topicPage, final PagePosition pagePosition) {
                    Log.d(LOG_TAG, String.format("Parsed link for category='%s', topic='%d', page='%d'", category.getName(), topicId, topicPage));
                    onGoToTopicEvent(new GoToTopicEvent(category, topicId, topicPage, pagePosition));
                }
            });
        }
    }

    @Override
    protected void onInitUiState() {
        Log.d(LOG_TAG, "Initializing state for TopicsActivity");

        if (findViewById(R.id.details_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-large and
            // res/values-sw600dp). If this view is present, then the
            // activity should be in two-pane mode.
            twoPaneMode = true;
        }
    }

    @Override
    protected void onSetupUiState() {
        Log.d(LOG_TAG, "Setting up initial state for TopicsActivity");

        defaultFragment = DefaultFragment.newInstance();

        if (twoPaneMode) {
            DetailsDefaultFragment detailsDefaultFragment = DetailsDefaultFragment.newInstance();

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, defaultFragment, DEFAULT_FRAGMENT_TAG)
                    .replace(R.id.details_container, detailsDefaultFragment, DETAILS_DEFAULT_FRAGMENT_TAG)
                    .commit();
        }
        else {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, defaultFragment, DEFAULT_FRAGMENT_TAG)
                    .commit();
        }
    }

    @Override
    protected void onRestoreUiState(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "Restoring UI state for TopicsActivity");

        // This will prevent categories loading eventfrom the navigation drawer to
        // mess-up with the UI (unnecessary reload) when the activity is re-created from
        // a previous state (like rotating the phone...)
        restoredInstanceState = true;
        currentCategory = savedInstanceState.getParcelable(ARG_CURRENT_CATEGORY);

        topicListFragment = (TopicListFragment) getSupportFragmentManager().findFragmentByTag(TOPICS_FRAGMENT_TAG);
        topicFragment = (TopicFragment) getSupportFragmentManager().findFragmentByTag(TOPIC_FRAGMENT_TAG);

        if (topicListFragment != null) {
            // Register the callbacks again
            topicListFragment.addOnTopicClickedListener(this);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (currentCategory != null) {
            outState.putParcelable(ARG_CURRENT_CATEGORY, currentCategory);
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        if (refreshRequestEvent != null) {
            Log.d(LOG_TAG, "Posting refreshRequestEvent");
            bus.post(refreshRequestEvent);
            refreshRequestEvent = null;
        }
    }

    /**
     * Callback invoked when categories have been loaded from cache or network.
     */
    @Override
    public void onCategoriesLoaded() {
        Log.d(LOG_TAG, "Categories have been loaded");

        if (currentCategory == null) {
            Log.d(LOG_TAG, "Loading default category");
            loadDefaultCategory();
        }
        else {
            Log.d(LOG_TAG, "Ignoring categories loaded event, state has been restored");
        }
    }

    /**
     * Loads default category
     */
    public void loadDefaultCategory() {
        int defaultCatId = getSettings().getDefaultCategoryId();

        Category defaultCategory = categoriesStore.getCategoryById(defaultCatId);

        if (defaultCategory == null) {
            Log.w(LOG_TAG, String.format("Category '%d' not found in cache", defaultCatId));
        } else {
            onCategoryClicked(defaultCategory);
        }
    }

    @Override
    public void onCategoryClicked(Category category) {
        currentCategory = category;

        Log.d(LOG_TAG, String.format("Loading category '%s', with topicFilter='%s'", category.getName(), getSettings().getDefaultTopicFilter().toString()));
        topicListFragment = new TopicListFragmentBuilder(category).topicFilter(getSettings().getDefaultTopicFilter()).build();
        topicListFragment.addOnTopicClickedListener(this);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.container, topicListFragment, TOPICS_FRAGMENT_TAG);
        transaction.commit();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        canLaunchReplyActivity = true;

        if (requestCode == UIConstants.REPLY_REQUEST_CODE) {
            boolean wasEdit = (data != null) && data.getBooleanExtra(UIConstants.ARG_REPLY_WAS_EDIT, false);

            if (data != null && resultCode == Activity.RESULT_OK) {
                SnackbarManager.show(
                        Snackbar.with(this)
                                .text(wasEdit ? R.string.message_successfully_edited : R.string.reply_successfully_posted)
                                .textColorResource(R.color.tabs_text_color)
                );

                // Refresh page
                Topic topic = data.getParcelableExtra(UIConstants.ARG_REPLY_TOPIC);

                if (topic == null) {
                    Log.e(LOG_TAG, "topic is null in onActivityResult");
                }
                else {
                    Log.d(LOG_TAG, String.format("Requesting refresh for topic : %s", topic.getSubject()));

                    // Deferring event posting until onResume() is called, otherwise inner fragments
                    // won't get the event.
                    refreshRequestEvent = new PageRefreshRequestEvent(topic);
                }
            }
            else if (resultCode == UIConstants.REPLY_RESULT_KO) {
                SnackbarManager.show(
                        Snackbar.with(this)
                                .text(wasEdit? R.string.message_edit_failure : R.string.reply_post_failure)
                                .colorResource(R.color.theme_primary_light)
                                .textColorResource(R.color.tabs_text_color)
                );
            }
        }
    }

    @Override
    public void onTopicClicked(Topic topic) {
        int pageToLoad;
        PagePosition pagePosition;

        if (topic.getStatus() == TopicStatus.FAVORITE_NEW_CONTENT || topic.getStatus() == TopicStatus.READ_NEW_CONTENT || topic.getStatus() == TopicStatus.FLAGGED_NEW_CONTENT) {
            pageToLoad = topic.getLastReadPostPage();
            pagePosition = new PagePosition(topic.getLastReadPostId());
        }
        else {
            pageToLoad = 1;
            pagePosition = new PagePosition(PagePosition.TOP);
        }

        loadTopic(topic, pageToLoad, pagePosition);
    }

    /**
     * Loads a topic in the appropriate panel for a given page and position
     */
    protected void loadTopic(Topic topic, int page, PagePosition pagePosition) {
        Log.d(LOG_TAG, String.format("Loading topic '%s' (page %d)", topic.getSubject(), page));
        topicFragment = new TopicFragmentBuilder(page, topic).currentPagePosition(pagePosition).build();

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        int topicFragmentContainer = twoPaneMode ? R.id.details_container : R.id.container;

        if (!twoPaneMode) {
            Log.d(LOG_TAG, "Setting slide animation for topicFragment");
            transaction.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right);
        }

        transaction.replace(topicFragmentContainer, topicFragment, TOPIC_FRAGMENT_TAG);
        transaction.addToBackStack(TOPIC_FRAGMENT_TAG);
        transaction.commit();
    }

    protected void loadAnonymousTopic(Topic topic, int page, PagePosition pagePosition) {
        TopicFragment anonymousTopicFragment = new TopicFragmentBuilder(page, topic).currentPagePosition(pagePosition).build();
        int topicFragmentContainer = twoPaneMode ? R.id.details_container : R.id.container;

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(topicFragmentContainer, anonymousTopicFragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    @Override
    protected void onUserSwitched(User newUser) {
        if (currentCategory != null) {
            onCategoryClicked(currentCategory);
        }
    }

    @Override
    public void onBackPressed() {
        Log.d(LOG_TAG, "On Back Pressed");
        boolean consumedEvent = false;

        if (topicFragment != null) {
            consumedEvent = topicFragment.onBackPressed();
        }

        if (!consumedEvent) {
            super.onBackPressed();
        }
    }

    /**
     * Called when an item of the topic contextual menu has been clicked. This menu is accessible via long-press
     * on a topic and gives additional actions to the user.
     */
    @Subscribe public void onTopicContextItemSelected(TopicContextItemSelectedEvent event) {
        Log.d(LOG_TAG, String.format("Received topic contextItem event : %d for topic %s", event.getItemId(), event.getTopic().getSubject()));

        switch (event.getItemId()) {
            case UIConstants.TOPIC_ACTION_GO_TO_FIRST_PAGE:
                loadTopic(event.getTopic(), 1, new PagePosition(PagePosition.TOP));
                break;
            case UIConstants.TOPIC_ACTION_GO_TO_LAST_READ_PAGE:
                loadTopic(event.getTopic(), event.getTopic().getLastReadPostPage(), new PagePosition(event.getTopic().getLastReadPostId()));
                break;
            case UIConstants.TOPIC_ACTION_GO_TO_SPECIFIC_PAGE:
                showGoToPageDialog(event.getTopic());
                break;
            case UIConstants.TOPIC_ACTION_REPLY_TO_TOPIC:
                Intent intent = new Intent(this, ReplyActivity.class);
                intent.putExtra(ARG_TOPIC, event.getTopic());
                startActivity(intent);
                break;
            case UIConstants.TOPIC_ACTION_GO_TO_LAST_PAGE:
                loadTopic(event.getTopic(), event.getTopic().getPagesCount(), new PagePosition(PagePosition.TOP));
                break;
        }
    }

    /**
     * This event is fired if an internal link to a different post that the one displayed has been
     * clicked.
     */
    @Subscribe
    public void onGoToTopicEvent(final GoToTopicEvent event) {
        subscribe(topicDetailsSearchHandler.load(event.getTopicId(), mdService.getTopic(userManager.getActiveUser(), event.getCategory(), event.getTopicId()), new EndlessObserver<Topic>() {
            @Override
            public void onNext(Topic topic) {
                if (topic != null) {
                    topic.setCategory(event.getCategory());
                    loadAnonymousTopic(topic, event.getTopicPage(), event.getPagePosition());
                }
            }
        }));
    }

    /**
     * Callback used to handle internal URLs. This callback does not handle the link itself, but updates the page position of the
     * appropriate topic page in order to restore correct position on back press if a new topic is loaded (meaning a new topicFragment).
     * Kinda hacky, but seems to work...
     */
    @Subscribe
    public void onInternalLinkClicked(InternalLinkClickedEvent event) {
        if (topicFragment != null && event.getTopic() == topicFragment.getTopic() && event.getPage() == topicFragment.getCurrentPage()) {
            topicFragment.setCurrentPagePosition(event.getPagePosition());
        }
    }

    @Subscribe public void onQuotePost(final QuotePostEvent event) {
        Log.d(LOG_TAG, String.format("@%d : quote event received for topic '%s' (quoting)", System.identityHashCode(this), event.getTopic().getSubject()));
        subscribe(quoteHandler.load(event.getTopic(), mdService.getQuote(userManager.getActiveUser(), event.getTopic(), event.getPostId()), new EndlessObserver<String>() {
            @Override
            public void onNext(String quoteBBCode) {
                Log.d(LOG_TAG, String.format("@%d : Starting reply activity for topic '%s' (quoting)", System.identityHashCode(TopicsActivity.this), event.getTopic().getSubject()));
                startReplyActivity(event.getTopic(), quoteBBCode);
            }
        }));
    }

    @Subscribe public void onEditPost(final EditPostEvent event) {
        subscribe(quoteHandler.load(event.getTopic(), mdService.getPostContent(userManager.getActiveUser(), event.getTopic(), event.getPostId()), new EndlessObserver<String>() {
            @Override
            public void onNext(String messageBBCode) {
                Log.d(LOG_TAG, String.format("Starting reply activity for topic '%s' (editing)", event.getTopic().getSubject()));
                startEditActivity(event.getTopic(), event.getPostId(), messageBBCode);
            }
        }));
    }

    /**
     * Starts the reply activity with or without an initial content
     */
    private synchronized void startReplyActivity(Topic topic, String initialContent) {
        if (canLaunchReplyActivity()) {
            setCanLaunchReplyActivity(false);

            Intent intent = new Intent(this, ReplyActivity.class);
            intent.putExtra(ARG_TOPIC, topic);

            if (initialContent != null) {
                intent.putExtra(UIConstants.ARG_REPLY_CONTENT, initialContent);
            }

            startActivityForResult(intent, UIConstants.REPLY_REQUEST_CODE);
        }
    }

    /**
     * Starts the edit activity
     */
    private synchronized void startEditActivity(Topic topic, int postId, String actualContent) {
        if (canLaunchReplyActivity) {
            setCanLaunchReplyActivity(false);

            Intent intent = new Intent(this, EditPostActivity.class);

            intent.putExtra(ARG_TOPIC, topic);
            intent.putExtra(UIConstants.ARG_EDITED_POST_ID, postId);
            intent.putExtra(UIConstants.ARG_REPLY_CONTENT, actualContent);

            startActivityForResult(intent, UIConstants.REPLY_REQUEST_CODE);
        }
    }

    /**
     * Shows the "Go to page" dialog where the user can enter the page he wants to consult.
     * @param topic topic concerned by the action
     */
    public void showGoToPageDialog(final Topic topic) {
        MaterialDialog dialog = new MaterialDialog.Builder(this)
                .customView(R.layout.dialog_go_to_page, true)
                .positiveText(R.string.dialog_go_to_page_positive_text)
                .negativeText(android.R.string.cancel)
                .theme(themeManager.getMaterialDialogTheme())
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        int pageNumber = Integer.valueOf(goToPageEditText.getText().toString());
                        loadTopic(topic, pageNumber, new PagePosition(PagePosition.TOP));
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                    }
                }).build();


        final View positiveAction = dialog.getActionButton(DialogAction.POSITIVE);
        goToPageEditText = (MaterialEditText) dialog.getCustomView().findViewById(R.id.page_number);

        goToPageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().length() > 0) {
                    int pageNumber = Integer.valueOf(s.toString());
                    positiveAction.setEnabled(pageNumber >= 1 && pageNumber <= topic.getPagesCount());
                }
                else {
                    positiveAction.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        dialog.show();
        positiveAction.setEnabled(false);
    }

    public boolean isTwoPaneMode() {
        return twoPaneMode;
    }

    public boolean canLaunchReplyActivity() {
        return canLaunchReplyActivity;
    }

    public void setCanLaunchReplyActivity(boolean canLaunchReplyActivity) {
        this.canLaunchReplyActivity = canLaunchReplyActivity;
    }
}
