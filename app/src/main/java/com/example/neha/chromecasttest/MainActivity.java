package com.example.neha.chromecasttest;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.widget.Toolbar;
import android.util.JsonReader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private MediaRouter mediaRouter;
    private MediaRouteSelector mediaRouteSelector;
    private String APP_ID = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID;

    private final MediaRouter.Callback mediaRouterCallback = new MediaRouter.Callback() {
        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
            CastDevice device = CastDevice.getFromBundle(route.getExtras());
            //setSelectedDevice(device);
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route) {
            //setSelectedDevice(null);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mediaRouter = MediaRouter.getInstance(getApplicationContext());
        mediaRouteSelector = new MediaRouteSelector.Builder().addControlCategory(CastMediaControlIntent.categoryForCast(APP_ID)).build();

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        String dataUri = intent.getDataString();
        String[] uris;
        if (dataUri != null) {
            uris = new String[] {dataUri};
        } else {
            ArrayList<String> uriList = new ArrayList<>();
            AssetManager assetManager = getAssets();
            try {
                for (String asset : assetManager.list("")) {
                    if (asset.endsWith(".exolist.json")) {
                        uriList.add("asset:///" + asset);
                    }
                }
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "Error here", Toast.LENGTH_LONG)
                        .show();
            }
            uris = new String[uriList.size()];
            uriList.toArray(uris);
            Arrays.sort(uris);
        }

        SampleListLoader loaderTask = new SampleListLoader();
        loaderTask.execute(uris);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }


    @Override
    protected void onStart() {
        super.onStart();
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    @Override
    protected void onStop() {
        //setSelectedDevice(null);
        mediaRouter.removeCallback(mediaRouterCallback);
        super.onStop();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mediaRouteSelector);

        return true;
    }

    private void onSampleGroups(final List<SampleGroup> groups, boolean sawError) {
        if (sawError) {
            Toast.makeText(getApplicationContext(), "Error here", Toast.LENGTH_LONG)
                    .show();
        }
        ExpandableListView sampleList = (ExpandableListView) findViewById(R.id.sample_list);
        sampleList.setAdapter(new SampleAdapter(this, groups));
        sampleList.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View view, int groupPosition,
                                        int childPosition, long id) {
                   onSampleSelected(groups.get(groupPosition).samples.get(childPosition));
                return true;
            }
        });
    }

   private void onSampleSelected(Sample sample) {
       // startActivity(sample.buildIntent(this));
    }

    private final class SampleListLoader extends AsyncTask<String, Void, List<SampleGroup>> {

        private boolean sawError;

        @Override
        protected List<SampleGroup> doInBackground(String... uris) {
            List<SampleGroup> result = new ArrayList<>();
            Context context = getApplicationContext();
            String userAgent = Util.getUserAgent(context, "ExoPlayerDemo");
            DataSource dataSource = new DefaultDataSource(context, null, userAgent, false);
            for (String uri : uris) {
                DataSpec dataSpec = new DataSpec(Uri.parse(uri));
                InputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
                try {
                    readSampleGroups(new JsonReader(new InputStreamReader(inputStream, "UTF-8")), result);
                } catch (Exception e) {
                    Log.e("NEHA", "Error loading sample list: " + uri, e);
                    sawError = true;
                } finally {
                    Util.closeQuietly(dataSource);
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(List<SampleGroup> result) {
            onSampleGroups(result, sawError);
        }

        private void readSampleGroups(JsonReader reader, List<SampleGroup> groups) throws IOException {
            reader.beginArray();
            while (reader.hasNext()) {
                readSampleGroup(reader, groups);
            }
            reader.endArray();
        }

        private void readSampleGroup(JsonReader reader, List<SampleGroup> groups) throws IOException {
            String groupName = "";
            ArrayList<Sample> samples = new ArrayList<>();

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case "name":
                        groupName = reader.nextString();
                        break;
                    case "samples":
                        reader.beginArray();
                        while (reader.hasNext()) {
                            samples.add(readEntry(reader, false));
                        }
                        reader.endArray();
                        break;
                    case "_comment":
                        reader.nextString(); // Ignore.
                        break;
                    default:
                        throw new ParserException("Unsupported name: " + name);
                }
            }
            reader.endObject();

            SampleGroup group = getGroup(groupName, groups);
            group.samples.addAll(samples);
        }

        private Sample readEntry(JsonReader reader, boolean insidePlaylist) throws IOException {
            String sampleName = null;
            String uri = null;
            String extension = null;
            UUID drmUuid = null;
            String drmLicenseUrl = null;
            String[] drmKeyRequestProperties = null;
            boolean preferExtensionDecoders = false;
            ArrayList<UriSample> playlistSamples = null;

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case "name":
                        sampleName = reader.nextString();
                        break;
                    case "uri":
                        uri = reader.nextString();
                        break;
                    case "extension":
                        extension = reader.nextString();
                        break;
                    case "drm_scheme":
                        Assertions.checkState(!insidePlaylist, "Invalid attribute on nested item: drm_scheme");
                        drmUuid = getDrmUuid(reader.nextString());
                        break;
                    case "drm_license_url":
                        Assertions.checkState(!insidePlaylist,
                                "Invalid attribute on nested item: drm_license_url");
                        drmLicenseUrl = reader.nextString();
                        break;
                    case "drm_key_request_properties":
                        Assertions.checkState(!insidePlaylist,
                                "Invalid attribute on nested item: drm_key_request_properties");
                        ArrayList<String> drmKeyRequestPropertiesList = new ArrayList<>();
                        reader.beginObject();
                        while (reader.hasNext()) {
                            drmKeyRequestPropertiesList.add(reader.nextName());
                            drmKeyRequestPropertiesList.add(reader.nextString());
                        }
                        reader.endObject();
                        drmKeyRequestProperties = drmKeyRequestPropertiesList.toArray(new String[0]);
                        break;
                    case "prefer_extension_decoders":
                        Assertions.checkState(!insidePlaylist,
                                "Invalid attribute on nested item: prefer_extension_decoders");
                        preferExtensionDecoders = reader.nextBoolean();
                        break;
                    case "playlist":
                        Assertions.checkState(!insidePlaylist, "Invalid nesting of playlists");
                        playlistSamples = new ArrayList<>();
                        reader.beginArray();
                        while (reader.hasNext()) {
                            playlistSamples.add((UriSample) readEntry(reader, true));
                        }
                        reader.endArray();
                        break;
                    default:
                        throw new ParserException("Unsupported attribute name: " + name);
                }
            }
            reader.endObject();

            if (playlistSamples != null) {
                UriSample[] playlistSamplesArray = playlistSamples.toArray(
                        new UriSample[playlistSamples.size()]);
                return new UriSample.PlaylistSample(sampleName, drmUuid, drmLicenseUrl, drmKeyRequestProperties,
                        preferExtensionDecoders, playlistSamplesArray);
            } else {
                return new UriSample(sampleName, drmUuid, drmLicenseUrl, drmKeyRequestProperties,
                        preferExtensionDecoders, uri, extension);
            }
        }

        private SampleGroup getGroup(String groupName, List<SampleGroup> groups) {
            for (int i = 0; i < groups.size(); i++) {
                if (Util.areEqual(groupName, groups.get(i).title)) {
                    return groups.get(i);
                }
            }
            SampleGroup group = new SampleGroup(groupName);
            groups.add(group);
            return group;
        }

        private UUID getDrmUuid(String typeString) throws ParserException {
            switch (Util.toLowerInvariant(typeString)) {
                case "widevine":
                    return C.WIDEVINE_UUID;
                case "playready":
                    return C.PLAYREADY_UUID;
                case "cenc":
                    return C.CLEARKEY_UUID;
                default:
                    try {
                        return UUID.fromString(typeString);
                    } catch (RuntimeException e) {
                        throw new ParserException("Unsupported drm type: " + typeString);
                    }
            }
        }

    }

    private static final class SampleAdapter extends BaseExpandableListAdapter {

        private final Context context;
        private final List<SampleGroup> sampleGroups;

        public SampleAdapter(Context context, List<SampleGroup> sampleGroups) {
            this.context = context;
            this.sampleGroups = sampleGroups;
        }

        @Override
        public Sample getChild(int groupPosition, int childPosition) {
            return getGroup(groupPosition).samples.get(childPosition);
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                                 View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent,
                        false);
            }
            ((TextView) view).setText(getChild(groupPosition, childPosition).name);
            return view;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return getGroup(groupPosition).samples.size();

        }

        @Override
        public SampleGroup getGroup(int groupPosition) {
            return sampleGroups.get(groupPosition);
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                                 ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(context).inflate(android.R.layout.simple_expandable_list_item_1,
                        parent, false);
            }
            ((TextView) view).setText(getGroup(groupPosition).title);
            return view;
        }

        @Override
        public int getGroupCount() {
            return sampleGroups.size();
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

    }

    private static final class SampleGroup {

        public final String title;
        public final List<Sample> samples;

        public SampleGroup(String title) {
            this.title = title;
            this.samples = new ArrayList<>();
        }

    }

    private abstract static class Sample {

        public final String name;
        public final boolean preferExtensionDecoders;
        public final UUID drmSchemeUuid;
        public final String drmLicenseUrl;
        public final String[] drmKeyRequestProperties;

        public Sample(String name, UUID drmSchemeUuid, String drmLicenseUrl,
                      String[] drmKeyRequestProperties, boolean preferExtensionDecoders) {
            this.name = name;
            this.drmSchemeUuid = drmSchemeUuid;
            this.drmLicenseUrl = drmLicenseUrl;
            this.drmKeyRequestProperties = drmKeyRequestProperties;
            this.preferExtensionDecoders = preferExtensionDecoders;
        }

     /*  public Intent buildIntent(Context context) {
            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra(PlayerActivity.PREFER_EXTENSION_DECODERS, preferExtensionDecoders);
            if (drmSchemeUuid != null) {
                intent.putExtra(PlayerActivity.DRM_SCHEME_UUID_EXTRA, drmSchemeUuid.toString());
                intent.putExtra(PlayerActivity.DRM_LICENSE_URL, drmLicenseUrl);
                intent.putExtra(PlayerActivity.DRM_KEY_REQUEST_PROPERTIES, drmKeyRequestProperties);
            }
            return intent;
        }*/

    }

    private static final class UriSample extends Sample {

        public final String uri;
        public final String extension;

        public UriSample(String name, UUID drmSchemeUuid, String drmLicenseUrl,
                         String[] drmKeyRequestProperties, boolean preferExtensionDecoders, String uri,
                         String extension) {
            super(name, drmSchemeUuid, drmLicenseUrl, drmKeyRequestProperties, preferExtensionDecoders);
            this.uri = uri;
            this.extension = extension;
        }

        /*     @Override
             public Intent buildIntent(Context context) {
                 return super.buildIntent(context)
                         .setData(Uri.parse(uri))
                         .putExtra(PlayerActivity.EXTENSION_EXTRA, extension)
                         .setAction(PlayerActivity.ACTION_VIEW);
             }

         }
     */
        private static final class PlaylistSample extends Sample {

            public final UriSample[] children;

            public PlaylistSample(String name, UUID drmSchemeUuid, String drmLicenseUrl,
                                  String[] drmKeyRequestProperties, boolean preferExtensionDecoders,
                                  UriSample... children) {
                super(name, drmSchemeUuid, drmLicenseUrl, drmKeyRequestProperties, preferExtensionDecoders);
                this.children = children;
            }

    /*    @Override
        public Intent buildIntent(Context context) {
            String[] uris = new String[children.length];
            String[] extensions = new String[children.length];
            for (int i = 0; i < children.length; i++) {
                uris[i] = children[i].uri;
                extensions[i] = children[i].extension;
            }
            return super.buildIntent(context)
                    .putExtra(PlayerActivity.URI_LIST_EXTRA, uris)
                    .putExtra(PlayerActivity.EXTENSION_LIST_EXTRA, extensions)
                    .setAction(PlayerActivity.ACTION_VIEW_LIST);
        }*/

        }

    }
}


