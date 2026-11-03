package net.kdt.pojavlaunch.contentbrowser;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Mods, shaders & more" tab: search Modrinth for mods, shaders, modpacks and resource packs,
 * filter by Minecraft version / category / sort order (mods and modpacks are Forge only)
 * and download them straight into a profile.
 */
public class ContentBrowserFragment extends Fragment implements ContentAdapter.Listener {
    public static final String TAG = "ContentBrowserFragment";
    /** Same order as the tabs in the layout */
    private static final ContentType[] TAB_TYPES = {ContentType.MOD, ContentType.SHADER, ContentType.MODPACK, ContentType.RESOURCEPACK};
    private static final Pattern MC_VERSION_PATTERN = Pattern.compile("^(\\d+\\.\\d+(?:\\.\\d+)?)");

    private final ContentFilters mFilters = new ContentFilters();
    private final Map<ContentType, List<ModrinthClient.Category>> mCategoryCache = new EnumMap<>(ContentType.class);

    private TextView[] mTabs;
    private EditText mSearchEditText;
    private TextView mTargetProfileView;
    private TextView mFiltersSummary;
    private ProgressBar mProgressBar;
    private TextView mStatusText;
    private RecyclerView mRecyclerView;
    private ContentAdapter mAdapter;

    private int mSearchGeneration = 0;
    private int mNextOffset = 0;
    private int mTotalHits = 0;
    private boolean mLoading = false;
    private Future<?> mSearchTask;

    @Nullable private String mTargetProfileKey;

    public ContentBrowserFragment() {
        super(R.layout.fragment_content_browser);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mTabs = new TextView[]{
                view.findViewById(R.id.content_tab_mods),
                view.findViewById(R.id.content_tab_shaders),
                view.findViewById(R.id.content_tab_modpacks),
                view.findViewById(R.id.content_tab_resourcepacks)
        };
        for(int i = 0; i < mTabs.length; i++) {
            final ContentType type = TAB_TYPES[i];
            mTabs[i].setOnClickListener(v -> selectType(type));
        }

        mSearchEditText = view.findViewById(R.id.content_search_edittext);
        ImageButton searchButton = view.findViewById(R.id.content_search_button);
        ImageButton filterButton = view.findViewById(R.id.content_filter_button);
        mTargetProfileView = view.findViewById(R.id.content_target_profile);
        mFiltersSummary = view.findViewById(R.id.content_filters_summary);
        mProgressBar = view.findViewById(R.id.content_progressbar);
        mStatusText = view.findViewById(R.id.content_status_text);
        mRecyclerView = view.findViewById(R.id.content_list);

        mAdapter = new ContentAdapter(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if(dy <= 0 || mLoading || mNextOffset >= mTotalHits) return;
                if(layoutManager.findLastVisibleItemPosition() >= mAdapter.getItemCount() - 6) {
                    runSearch(false);
                }
            }
        });

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if(actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || event != null) {
                submitQuery();
                return true;
            }
            return false;
        });
        searchButton.setOnClickListener(v -> submitQuery());
        filterButton.setOnClickListener(v -> showFilterDialog());
        mTargetProfileView.setOnClickListener(v -> showProfilePicker());

        // Default target: the profile currently selected in the launcher
        if(LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
        String current = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        setTargetProfile(current != null && getProfiles().containsKey(current) ? current : firstProfileKey());

        selectType(ContentType.MOD);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if(mSearchTask != null) mSearchTask.cancel(true);
        mSearchGeneration++;
    }

    /* ------------------------------------------------------------------ */
    /* Search                                                              */
    /* ------------------------------------------------------------------ */

    private void selectType(ContentType type) {
        if(mFilters.type != type) mFilters.category = null; // categories differ per type
        mFilters.type = type;
        for(int i = 0; i < mTabs.length; i++) mTabs[i].setSelected(TAB_TYPES[i] == type);
        preloadCategories(type);
        runSearch(true);
    }

    private void submitQuery() {
        mFilters.query = mSearchEditText.getText().toString();
        mSearchEditText.clearFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm != null) imm.hideSoftInputFromWindow(mSearchEditText.getWindowToken(), 0);
        runSearch(true);
    }

    private void runSearch(boolean reset) {
        if(reset) {
            mSearchGeneration++;
            if(mSearchTask != null) mSearchTask.cancel(true);
            mAdapter.clear();
            mNextOffset = 0;
            mTotalHits = 0;
        }
        updateFiltersSummary();
        final int generation = mSearchGeneration;
        final int offset = mNextOffset;
        final ContentFilters filters = mFilters.copy();
        mLoading = true;
        mProgressBar.setVisibility(View.VISIBLE);
        mStatusText.setVisibility(View.GONE);

        mSearchTask = PojavApplication.sExecutorService.submit(() -> {
            ModrinthClient.SearchPage page = null;
            Exception error = null;
            try {
                page = ModrinthClient.search(filters, offset);
            } catch (Exception e) {
                error = e;
            }
            final ModrinthClient.SearchPage result = page;
            final Exception finalError = error;
            Tools.runOnUiThread(() -> {
                if(generation != mSearchGeneration || !isAdded()) return;
                mLoading = false;
                mProgressBar.setVisibility(View.GONE);
                if(result == null) {
                    if(mAdapter.getItemCount() == 0) {
                        showStatus(getString(R.string.content_search_error,
                                finalError == null ? "?" : finalError.getMessage()));
                    } else {
                        Toast.makeText(requireContext(), R.string.content_search_error_short, Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                mTotalHits = result.totalHits;
                mNextOffset = result.nextOffset;
                mAdapter.addAll(result.projects);
                if(mAdapter.getItemCount() == 0) showStatus(getString(R.string.content_no_results));
            });
        });
    }

    private void showStatus(String text) {
        mStatusText.setText(text);
        mStatusText.setVisibility(View.VISIBLE);
    }

    private void updateFiltersSummary() {
        Context context = requireContext();
        StringBuilder summary = new StringBuilder();
        summary.append(mFilters.hasMcVersion() ? mFilters.mcVersion : context.getString(R.string.content_filter_any_version));
        String loader = mFilters.getLoader();
        if(loader != null) summary.append(" · ").append(loaderDisplayName(loader));
        if(mFilters.category != null) summary.append(" · ").append(mFilters.category);
        summary.append(" · ").append(context.getResources().getStringArray(R.array.content_sort_names)[sortIndexPosition()]);
        mFiltersSummary.setText(summary);
    }

    private int sortIndexPosition() {
        for(int i = 0; i < ContentFilters.SORT_INDEXES.length; i++)
            if(ContentFilters.SORT_INDEXES[i].equals(mFilters.sortIndex)) return i;
        return 0;
    }

    private static String loaderDisplayName(String loader) {
        switch (loader) {
            case "forge": return "Forge";
            case "optifine": return "OptiFine";
            case "iris": return "Iris / Oculus";
            default: return loader;
        }
    }

    private void preloadCategories(ContentType type) {
        if(mCategoryCache.containsKey(type)) return;
        PojavApplication.sExecutorService.execute(() -> {
            try {
                List<ModrinthClient.Category> categories = ModrinthClient.getCategories(type);
                Tools.runOnUiThread(() -> mCategoryCache.put(type, categories));
            } catch (Exception ignored) {
                // Category filter just stays at "any"
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* Filters dialog                                                      */
    /* ------------------------------------------------------------------ */

    private void showFilterDialog() {
        Context context = requireContext();
        View view = View.inflate(context, R.layout.dialog_content_filters, null);
        EditText versionEdit = view.findViewById(R.id.content_filter_version_edittext);
        Button versionPick = view.findViewById(R.id.content_filter_version_pick);
        TextView loaderLabel = view.findViewById(R.id.content_filter_loader_label);
        TextView loaderFixed = view.findViewById(R.id.content_filter_loader_fixed);
        Spinner shaderLoader = view.findViewById(R.id.content_filter_shader_loader);
        Spinner category = view.findViewById(R.id.content_filter_category);
        Spinner sort = view.findViewById(R.id.content_filter_sort);

        versionEdit.setText(mFilters.hasMcVersion() ? mFilters.mcVersion : "");
        versionPick.setOnClickListener(v -> VersionSelectorDialog.open(context, true,
                (versionId, isSnapshot) -> versionEdit.setText(versionId)));

        // Loader: Forge only for mods/modpacks, choice for shaders, nothing for resource packs
        boolean isShader = mFilters.type == ContentType.SHADER;
        boolean isForgeOnly = mFilters.type == ContentType.MOD || mFilters.type == ContentType.MODPACK;
        loaderLabel.setVisibility(isShader || isForgeOnly ? View.VISIBLE : View.GONE);
        loaderFixed.setVisibility(isForgeOnly ? View.VISIBLE : View.GONE);
        shaderLoader.setVisibility(isShader ? View.VISIBLE : View.GONE);
        final String[] shaderLoaderValues = {ContentFilters.SHADER_LOADER_ANY,
                ContentFilters.SHADER_LOADER_OPTIFINE, ContentFilters.SHADER_LOADER_IRIS};
        shaderLoader.setAdapter(spinnerAdapter(context.getResources().getStringArray(R.array.content_shader_loader_names)));
        for(int i = 0; i < shaderLoaderValues.length; i++) {
            String value = shaderLoaderValues[i];
            if(value == null ? mFilters.shaderLoader == null : value.equals(mFilters.shaderLoader))
                shaderLoader.setSelection(i);
        }

        // Categories (loaded from Modrinth)
        List<ModrinthClient.Category> categories = mCategoryCache.get(mFilters.type);
        final List<String> categoryValues = new ArrayList<>();
        List<String> categoryNames = new ArrayList<>();
        categoryValues.add(null);
        categoryNames.add(context.getString(R.string.content_filter_any));
        if(categories != null) for(ModrinthClient.Category c : categories) {
            categoryValues.add(c.name);
            categoryNames.add(prettify(c.name));
        }
        category.setAdapter(spinnerAdapter(categoryNames.toArray(new String[0])));
        int categoryPosition = categoryValues.indexOf(mFilters.category);
        category.setSelection(Math.max(categoryPosition, 0));

        sort.setAdapter(spinnerAdapter(context.getResources().getStringArray(R.array.content_sort_names)));
        sort.setSelection(sortIndexPosition());

        new AlertDialog.Builder(context)
                .setTitle(R.string.content_filters_title)
                .setView(view)
                .setPositiveButton(R.string.content_filters_apply, (d, w) -> {
                    String version = versionEdit.getText().toString().trim();
                    mFilters.mcVersion = version.isEmpty() ? null : version;
                    if(isShader) mFilters.shaderLoader = shaderLoaderValues[shaderLoader.getSelectedItemPosition()];
                    int selectedCategory = category.getSelectedItemPosition();
                    mFilters.category = selectedCategory >= 0 && selectedCategory < categoryValues.size()
                            ? categoryValues.get(selectedCategory) : null;
                    mFilters.sortIndex = ContentFilters.SORT_INDEXES[Math.max(sort.getSelectedItemPosition(), 0)];
                    runSearch(true);
                })
                .setNeutralButton(R.string.content_filters_reset, (d, w) -> {
                    mFilters.mcVersion = profileMcVersion(getTargetProfile());
                    mFilters.category = null;
                    mFilters.shaderLoader = ContentFilters.SHADER_LOADER_ANY;
                    mFilters.sortIndex = "relevance";
                    runSearch(true);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private ArrayAdapter<String> spinnerAdapter(String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private static String prettify(String categoryName) {
        String name = categoryName.replace('-', ' ');
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /* ------------------------------------------------------------------ */
    /* Target profile                                                      */
    /* ------------------------------------------------------------------ */

    private Map<String, MinecraftProfile> getProfiles() {
        if(LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
        return LauncherProfiles.mainProfileJson.profiles;
    }

    @Nullable
    private String firstProfileKey() {
        for(String key : getProfiles().keySet()) return key;
        return null;
    }

    @Nullable
    private MinecraftProfile getTargetProfile() {
        return mTargetProfileKey == null ? null : getProfiles().get(mTargetProfileKey);
    }

    private void setTargetProfile(@Nullable String key) {
        mTargetProfileKey = key;
        MinecraftProfile profile = getTargetProfile();
        if(profile == null) {
            mTargetProfileView.setText(R.string.content_no_profile);
            return;
        }
        mTargetProfileView.setText(getString(R.string.content_install_into, profileDisplayName(profile)));
        // Show content for the profile's Minecraft version by default
        String mcVersion = profileMcVersion(profile);
        if(mcVersion != null) mFilters.mcVersion = mcVersion;
    }

    private static String profileDisplayName(MinecraftProfile profile) {
        String version = profile.lastVersionId == null ? "?" : profile.lastVersionId;
        if(profile.name == null || profile.name.trim().isEmpty()) return version;
        return profile.name + " (" + version + ")";
    }

    @Nullable
    private static String profileMcVersion(@Nullable MinecraftProfile profile) {
        if(profile == null || profile.lastVersionId == null) return null;
        Matcher matcher = MC_VERSION_PATTERN.matcher(profile.lastVersionId);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean isForgeProfile(@Nullable MinecraftProfile profile) {
        return profile != null && profile.lastVersionId != null
                && profile.lastVersionId.toLowerCase(Locale.ROOT).contains("forge");
    }

    private void showProfilePicker() {
        Map<String, MinecraftProfile> profiles = getProfiles();
        if(profiles.isEmpty()) {
            Toast.makeText(requireContext(), R.string.content_no_profile, Toast.LENGTH_LONG).show();
            return;
        }
        final List<String> keys = new ArrayList<>(profiles.keySet());
        String[] names = new String[keys.size()];
        int checked = -1;
        for(int i = 0; i < keys.size(); i++) {
            names[i] = profileDisplayName(profiles.get(keys.get(i)));
            if(keys.get(i).equals(mTargetProfileKey)) checked = i;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.content_pick_profile)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    setTargetProfile(keys.get(which));
                    dialog.dismiss();
                    runSearch(true);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /* ------------------------------------------------------------------ */
    /* Download                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public void onDownloadClicked(ModrinthClient.Project project) {
        Context context = requireContext();
        if(project.type == ContentType.MODPACK) {
            // Same rule as the original launcher: installing a modpack also installs Minecraft + Forge
            if(Tools.isLocalProfile(context) || Tools.isDemoProfile(context)) {
                Toast.makeText(context, R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
                return;
            }
        } else if(getTargetProfile() == null) {
            Toast.makeText(context, R.string.content_no_profile, Toast.LENGTH_LONG).show();
            return;
        }
        if(ProgressKeeper.getTaskCount() > 0) {
            Toast.makeText(context, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return;
        }

        if(project.type == ContentType.MOD && !isForgeProfile(getTargetProfile())) {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.content_not_forge_title)
                    .setMessage(R.string.content_not_forge_message)
                    .setPositiveButton(R.string.content_continue_anyway, (d, w) -> fetchVersions(project))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        fetchVersions(project);
    }

    private void fetchVersions(ModrinthClient.Project project) {
        final String loader = mFilters.getLoader();
        final String mcVersion = mFilters.hasMcVersion() ? mFilters.mcVersion.trim() : null;
        mProgressBar.setVisibility(View.VISIBLE);
        PojavApplication.sExecutorService.execute(() -> {
            List<ModrinthClient.Version> versions = null;
            Exception error = null;
            try {
                versions = ModrinthClient.getVersions(project.id, loader, mcVersion);
            } catch (Exception e) {
                error = e;
            }
            final List<ModrinthClient.Version> result = versions;
            final Exception finalError = error;
            Tools.runOnUiThread(() -> {
                if(!isAdded()) return;
                mProgressBar.setVisibility(mLoading ? View.VISIBLE : View.GONE);
                if(result == null) {
                    Tools.dialog(requireContext(), getString(R.string.content_error_title),
                            getString(R.string.content_search_error, finalError == null ? "?" : finalError.getMessage()));
                    return;
                }
                if(result.isEmpty()) {
                    Tools.dialog(requireContext(), getString(R.string.content_no_versions_title),
                            getString(R.string.content_no_versions_message, project.title,
                                    mcVersion == null ? getString(R.string.content_filter_any_version) : mcVersion,
                                    loader == null ? "-" : loaderDisplayName(loader)));
                    return;
                }
                showVersionPicker(project, result, mcVersion, loader);
            });
        });
    }

    private void showVersionPicker(ModrinthClient.Project project, List<ModrinthClient.Version> versions,
                                   @Nullable String mcVersion, @Nullable String loader) {
        ModrinthClient.Version best = ContentInstaller.pickBest(versions);
        String[] names = new String[versions.size()];
        int bestIndex = 0;
        for(int i = 0; i < versions.size(); i++) {
            ModrinthClient.Version version = versions.get(i);
            StringBuilder name = new StringBuilder(version.name == null ? version.versionNumber : version.name);
            name.append("  [").append(joinShort(version.gameVersions)).append("]");
            if(version.versionType != null && !"release".equals(version.versionType))
                name.append(" (").append(version.versionType).append(")");
            if(version == best) {
                name.append(" ★");
                bestIndex = i;
            }
            names[i] = name.toString();
        }
        final int[] selected = {bestIndex};
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.content_pick_version, project.title))
                .setSingleChoiceItems(names, bestIndex, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.content_download, (d, w) ->
                        install(project, versions.get(selected[0]), mcVersion, loader))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String joinShort(List<String> values) {
        if(values.isEmpty()) return "?";
        if(values.size() <= 3) return TextUtils.join(", ", values);
        return values.get(0) + " … " + values.get(values.size() - 1);
    }

    private void install(ModrinthClient.Project project, ModrinthClient.Version version,
                         @Nullable String filterMcVersion, @Nullable String loader) {
        Context appContext = requireContext().getApplicationContext();

        if(project.type == ContentType.MODPACK) {
            ModrinthClient.VersionFile file = version.getPrimaryFile();
            if(file == null) return;
            String mc = version.gameVersions.isEmpty() ? "?" : version.gameVersions.get(0);
            ModItem item = new ModItem(Constants.SOURCE_MODRINTH, true, project.id, project.title,
                    project.description, project.iconUrl);
            ModDetail detail = new ModDetail(item, new String[]{version.name}, new String[]{mc},
                    new String[]{file.url}, new String[]{file.sha1});
            Toast.makeText(appContext, getString(R.string.content_downloading, project.title), Toast.LENGTH_SHORT).show();
            new ModrinthApi().handleInstallation(requireContext(), detail, 0);
            return;
        }

        MinecraftProfile profile = getTargetProfile();
        if(profile == null) return;
        File gameDir = Tools.getGameDirPath(profile);
        // Dependencies must match the Minecraft version actually used
        String dependencyMcVersion = filterMcVersion;
        if(dependencyMcVersion == null) {
            String profileVersion = profileMcVersion(profile);
            if(profileVersion != null && version.gameVersions.contains(profileVersion)) dependencyMcVersion = profileVersion;
            else if(!version.gameVersions.isEmpty()) dependencyMcVersion = version.gameVersions.get(0);
        }
        final String depVersion = dependencyMcVersion;

        Toast.makeText(appContext, getString(R.string.content_downloading, project.title), Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                ContentInstaller.Result result = new ContentInstaller(project.type, gameDir, loader, depVersion).install(version);
                Tools.runOnUiThread(() -> showInstallResult(appContext, project, result));
            } catch (Exception e) {
                Tools.runOnUiThread(() -> {
                    if(isAdded()) Tools.showError(requireContext(), R.string.content_error_title, e);
                    else Toast.makeText(appContext, R.string.content_error_title, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showInstallResult(Context appContext, ModrinthClient.Project project, ContentInstaller.Result result) {
        StringBuilder message = new StringBuilder();
        message.append(getStringSafe(appContext, R.string.content_installed_into, result.targetFolder.getAbsolutePath()));
        if(!result.installedFiles.isEmpty()) {
            message.append("\n\n").append(appContext.getString(R.string.content_installed_files));
            for(String file : result.installedFiles) message.append("\n • ").append(file);
        }
        if(!result.skippedFiles.isEmpty()) {
            message.append("\n\n").append(appContext.getString(R.string.content_already_installed));
            for(String file : result.skippedFiles) message.append("\n • ").append(file);
        }
        if(!result.missingDependencies.isEmpty()) {
            message.append("\n\n").append(appContext.getString(R.string.content_missing_dependencies));
            for(String dep : result.missingDependencies) message.append("\n • ").append(dep);
        }
        if(isAdded()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.content_installed_title, project.title))
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else {
            Toast.makeText(appContext, appContext.getString(R.string.content_installed_title, project.title), Toast.LENGTH_LONG).show();
        }
    }

    private static String getStringSafe(Context context, int res, Object... args) {
        return context.getString(res, args);
    }
}
