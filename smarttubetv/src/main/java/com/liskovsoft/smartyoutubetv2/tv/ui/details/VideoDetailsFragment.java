package com.liskovsoft.smartyoutubetv2.tv.ui.details;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.DetailsSupportFragment;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.DetailsOverviewLogoPresenter;
import androidx.leanback.widget.DetailsOverviewRow;
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.SparseArrayObjectAdapter;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.DetailsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.DetailsView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.old.data.VideoContract;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.old.model.VideoCursorMapper;
import com.liskovsoft.smartyoutubetv2.tv.presenter.DetailsDescriptionPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

/*
 * VideoDetailsFragment extends DetailsFragment, a Wrapper fragment for leanback details screens.
 * It shows a detailed view of video and its metadata plus related videos.
 */
public class VideoDetailsFragment extends DetailsSupportFragment implements DetailsView {

    private static final int NO_NOTIFICATION = -1;
    private static final int ACTION_WATCH_TRAILER = 1;
    private static final int ACTION_RENT = 2;
    private static final int ACTION_BUY = 3;

    // ID for loader that loads related videos.
    private static final int RELATED_VIDEO_LOADER = 1;

    // ID for loader that loads the video from global search.
    private int mGlobalSearchVideoId = 2;

    private Video mSelectedVideo;
    private ArrayObjectAdapter mAdapter;
    private ClassPresenterSelector mPresenterSelector;
    private BackgroundManager mBackgroundManager;
    private Drawable mDefaultBackground;
    private DisplayMetrics mMetrics;
    private FullWidthDetailsOverviewSharedElementHelper mHelper;
    private final VideoCursorMapper mVideoCursorMapper = new VideoCursorMapper();
    private DetailsPresenter mDetailsPresenter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDetailsPresenter = DetailsPresenter.instance(getContext());
        mDetailsPresenter.setView(this);

        prepareBackgroundManager();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mDetailsPresenter.onViewInitialized();
    }

    private void init() {
        if (mSelectedVideo != null || !hasGlobalSearchIntent()) {
            removeNotification(getActivity().getIntent()
                    .getIntExtra(VideoDetailsActivity.NOTIFICATION_ID, NO_NOTIFICATION));
            setupAdapter();
            setupDetailsOverviewRow();
            setupMovieListRow();
            updateBackground(mSelectedVideo.bgImageUrl);

            // When a Related Video item is clicked.
            setOnItemViewClickedListener(new ItemViewClickedListener());
        }
    }

    private void removeNotification(int notificationId) {
        if (notificationId != NO_NOTIFICATION) {
            NotificationManager notificationManager = (NotificationManager) getActivity()
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(notificationId);
        }
    }

    @Override
    public void onStop() {
        mBackgroundManager.release();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDetailsPresenter.onViewDestroyed();
    }

    /**
     * Check if there is a global search intent. If there is, load that video.
     */
    private boolean hasGlobalSearchIntent() {
        Intent intent = getActivity().getIntent();
        String intentAction = intent.getAction();
        String globalSearch = getString(R.string.global_search);

        if (globalSearch.equalsIgnoreCase(intentAction)) {
            Uri intentData = intent.getData();
            String videoId = intentData.getLastPathSegment();

            Bundle args = new Bundle();
            args.putString(VideoContract.VideoEntry._ID, videoId);
            // TODO: ???
            //getLoaderManager().initLoader(mGlobalSearchVideoId++, args, this);
            return true;
        }
        return false;
    }

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.attach(getActivity().getWindow());
        mDefaultBackground = ContextCompat.getDrawable(getActivity(), R.drawable.default_background_gradient);
        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void updateBackground(String uri) {
        RequestOptions options = new RequestOptions()
                .centerCrop()
                .error(mDefaultBackground);

        Glide.with(this)
                .asBitmap()
                .load(uri)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .apply(options)
                .into(new SimpleTarget<Bitmap>(mMetrics.widthPixels, mMetrics.heightPixels) {
                    @Override
                    public void onResourceReady(
                            Bitmap resource,
                            Transition<? super Bitmap> transition) {
                        mBackgroundManager.setBitmap(resource);
                    }
                });
    }

    private void setupAdapter() {
        // Set detail background and style.
        FullWidthDetailsOverviewRowPresenter detailsPresenter =
                new FullWidthDetailsOverviewRowPresenter(new DetailsDescriptionPresenter(),
                        new MovieDetailsOverviewLogoPresenter());

        detailsPresenter.setBackgroundColor(
                ContextCompat.getColor(getActivity(), R.color.card_selected_background_yellow));
        detailsPresenter.setInitialState(FullWidthDetailsOverviewRowPresenter.STATE_HALF);

        // Hook up transition element.
        mHelper = new FullWidthDetailsOverviewSharedElementHelper();
        mHelper.setSharedElementEnterTransition(getActivity(),
                VideoDetailsActivity.SHARED_ELEMENT_NAME);
        detailsPresenter.setListener(mHelper);
        detailsPresenter.setParticipatingEntranceTransition(false);
        prepareEntranceTransition();

        detailsPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                if (action.getId() == ACTION_WATCH_TRAILER) {
                    Intent intent = new Intent(getActivity(), PlaybackActivity.class);
                    intent.putExtra(VideoDetailsActivity.VIDEO, mSelectedVideo);
                    startActivity(intent);
                } else {
                    Toast.makeText(getActivity(), action.toString(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        mPresenterSelector = new ClassPresenterSelector();
        mPresenterSelector.addClassPresenter(DetailsOverviewRow.class, detailsPresenter);
        mPresenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
        mAdapter = new ArrayObjectAdapter(mPresenterSelector);
        setAdapter(mAdapter);
    }

    //@Override
    //public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    //    switch (id) {
    //        case RELATED_VIDEO_LOADER: {
    //            String category = args.getString(VideoContract.VideoEntry.COLUMN_CATEGORY);
    //            return new CursorLoader(
    //                    getActivity(),
    //                    VideoContract.VideoEntry.CONTENT_URI,
    //                    null,
    //                    VideoContract.VideoEntry.COLUMN_CATEGORY + " = ?",
    //                    new String[]{category},
    //                    null
    //            );
    //        }
    //        default: {
    //            // Loading video from global search.
    //            String videoId = args.getString(VideoContract.VideoEntry._ID);
    //            return new CursorLoader(
    //                    getActivity(),
    //                    VideoContract.VideoEntry.CONTENT_URI,
    //                    null,
    //                    VideoContract.VideoEntry._ID + " = ?",
    //                    new String[]{videoId},
    //                    null
    //            );
    //        }
    //    }
    //
    //}

    //@Override
    //public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    //    if (cursor != null && cursor.moveToNext()) {
    //        switch (loader.getId()) {
    //            case RELATED_VIDEO_LOADER: {
    //                mVideoCursorAdapter.changeCursor(cursor);
    //                break;
    //            }
    //            default: {
    //                // Loading video from global search.
    //                mSelectedVideo = (Video) mVideoCursorMapper.convert(cursor);
    //
    //                setupAdapter();
    //                setupDetailsOverviewRow();
    //                setupMovieListRow();
    //                updateBackground(mSelectedVideo.bgImageUrl);
    //
    //                // When a Related Video item is clicked.
    //                setOnItemViewClickedListener(new ItemViewClickedListener());
    //            }
    //        }
    //    }
    //}

    //@Override
    //public void onLoaderReset(Loader<Cursor> loader) {
    //    mVideoCursorAdapter.changeCursor(null);
    //}

    @Override
    public void openVideo(Video video) {
        mSelectedVideo = video;
        init();
    }

    static class MovieDetailsOverviewLogoPresenter extends DetailsOverviewLogoPresenter {

        static class ViewHolder extends DetailsOverviewLogoPresenter.ViewHolder {
            public ViewHolder(View view) {
                super(view);
            }

            public FullWidthDetailsOverviewRowPresenter getParentPresenter() {
                return mParentPresenter;
            }

            public FullWidthDetailsOverviewRowPresenter.ViewHolder getParentViewHolder() {
                return mParentViewHolder;
            }
        }

        @Override
        public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
            ImageView imageView = (ImageView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.lb_fullwidth_details_overview_logo, parent, false);

            Resources res = parent.getResources();
            int width = res.getDimensionPixelSize(R.dimen.detail_thumb_width);
            int height = res.getDimensionPixelSize(R.dimen.detail_thumb_height);
            imageView.setLayoutParams(new ViewGroup.MarginLayoutParams(width, height));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

            return new ViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
            DetailsOverviewRow row = (DetailsOverviewRow) item;
            ImageView imageView = ((ImageView) viewHolder.view);
            imageView.setImageDrawable(row.getImageDrawable());
            if (isBoundToImage((ViewHolder) viewHolder, row)) {
                MovieDetailsOverviewLogoPresenter.ViewHolder vh =
                        (MovieDetailsOverviewLogoPresenter.ViewHolder) viewHolder;
                vh.getParentPresenter().notifyOnBindLogo(vh.getParentViewHolder());
            }
        }
    }

    private void setupDetailsOverviewRow() {
        final DetailsOverviewRow row = new DetailsOverviewRow(mSelectedVideo);

        RequestOptions options = new RequestOptions()
                .error(R.drawable.default_background_gradient)
                .dontAnimate();

        Glide.with(this)
                .asBitmap()
                .load(mSelectedVideo.cardImageUrl)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .apply(options)
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(
                            Bitmap resource,
                            Transition<? super Bitmap> transition) {
                        row.setImageBitmap(getActivity(), resource);
                        startEntranceTransition();
                    }
                });

        SparseArrayObjectAdapter adapter = new SparseArrayObjectAdapter();

        adapter.set(ACTION_WATCH_TRAILER, new Action(ACTION_WATCH_TRAILER, getResources()
                .getString(R.string.watch_trailer_1),
                getResources().getString(R.string.watch_trailer_2)));
        adapter.set(ACTION_RENT, new Action(ACTION_RENT, getResources().getString(R.string.rent_1),
                getResources().getString(R.string.rent_2)));
        adapter.set(ACTION_BUY, new Action(ACTION_BUY, getResources().getString(R.string.buy_1),
                getResources().getString(R.string.buy_2)));
        row.setActionsAdapter(adapter);

        mAdapter.add(row);
    }

    private void setupMovieListRow() {
        String subcategories[] = {getString(R.string.related_movies)};

        // Generating related video list.
        String category = mSelectedVideo.category;

        Bundle args = new Bundle();
        args.putString(VideoContract.VideoEntry.COLUMN_CATEGORY, category);
        //getLoaderManager().initLoader(RELATED_VIDEO_LOADER, args, this);

        HeaderItem header = new HeaderItem(0, subcategories[0]);

        // TODO: add real rows
        //mAdapter.add(new ListRow(header, mVideoCursorAdapter));
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Video) {
                Video video = (Video) item;
                Intent intent = new Intent(getActivity(), VideoDetailsActivity.class);
                intent.putExtra(VideoDetailsActivity.VIDEO, video);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        VideoDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                getActivity().startActivity(intent, bundle);
            }
        }
    }
}
