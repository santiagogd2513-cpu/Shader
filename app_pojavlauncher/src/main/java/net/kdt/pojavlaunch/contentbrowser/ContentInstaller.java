package net.kdt.pojavlaunch.contentbrowser;

import androidx.annotation.Nullable;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Downloads a mod / shader / resource pack version into a game directory.
 * For mods, the required dependencies are resolved and downloaded too (for the same
 * Minecraft version and mod loader).
 * Blocking: run it on a background thread.
 */
public class ContentInstaller {
    private static final int MAX_DEPENDENCY_DEPTH = 8;

    public static class Result {
        public File targetFolder;
        public final List<String> installedFiles = new ArrayList<>();
        public final List<String> skippedFiles = new ArrayList<>();
        /** Required dependencies that could not be found for this version/loader */
        public final List<String> missingDependencies = new ArrayList<>();
    }

    private final ContentType mType;
    @Nullable private final String mLoader;
    @Nullable private final String mMcVersion;
    private final File mTargetFolder;
    private final Set<String> mVisitedProjects = new HashSet<>();
    private final Result mResult = new Result();
    private final byte[] mBuffer = new byte[65536];

    /**
     * @param type content type (decides the folder: mods, shaderpacks, resourcepacks)
     * @param gameDir game directory of the profile to install into
     * @param loader loader used to pick dependency versions (forge for mods)
     * @param mcVersion Minecraft version used to pick dependency versions
     */
    public ContentInstaller(ContentType type, File gameDir, @Nullable String loader, @Nullable String mcVersion) {
        if(type.folderName == null) throw new IllegalArgumentException("Modpacks are installed as new instances");
        mType = type;
        mLoader = loader;
        mMcVersion = mcVersion;
        mTargetFolder = new File(gameDir, type.folderName);
        mResult.targetFolder = mTargetFolder;
    }

    public Result install(ModrinthClient.Version version) throws IOException {
        try {
            if(!mTargetFolder.isDirectory() && !mTargetFolder.mkdirs())
                throw new IOException("Cannot create folder " + mTargetFolder.getAbsolutePath());
            mVisitedProjects.add(version.projectId);
            installRecursive(version, 0);
            return mResult;
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_CONTENT);
        }
    }

    private void installRecursive(ModrinthClient.Version version, int depth) throws IOException {
        downloadVersionFile(version);
        // Dependencies only make sense for mods
        if(mType != ContentType.MOD || depth >= MAX_DEPENDENCY_DEPTH) return;

        for(ModrinthClient.Dependency dependency : version.dependencies) {
            if(!"required".equals(dependency.type)) continue;
            if(dependency.projectId != null && mVisitedProjects.contains(dependency.projectId)) continue;

            ModrinthClient.Version dependencyVersion = resolveDependency(dependency);
            if(dependencyVersion == null) {
                String name = dependency.projectId != null
                        ? ModrinthClient.getProjectTitle(dependency.projectId)
                        : (dependency.fileName != null ? dependency.fileName : "?");
                mResult.missingDependencies.add(name);
                continue;
            }
            if(!mVisitedProjects.add(dependencyVersion.projectId)) continue;
            installRecursive(dependencyVersion, depth + 1);
        }
    }

    @Nullable
    private ModrinthClient.Version resolveDependency(ModrinthClient.Dependency dependency) {
        try {
            // A dependency pinned to a specific version
            if(dependency.versionId != null) {
                return ModrinthClient.getVersion(dependency.versionId);
            }
            if(dependency.projectId == null) return null;
            // Otherwise, the newest version for the same loader + Minecraft version
            List<ModrinthClient.Version> versions = ModrinthClient.getVersions(dependency.projectId, mLoader, mMcVersion);
            return pickBest(versions);
        } catch (IOException e) {
            return null;
        }
    }

    /** Prefer the newest release, then the newest beta/alpha */
    @Nullable
    public static ModrinthClient.Version pickBest(List<ModrinthClient.Version> versions) {
        for(ModrinthClient.Version version : versions) {
            if("release".equals(version.versionType)) return version;
        }
        return versions.isEmpty() ? null : versions.get(0);
    }

    private void downloadVersionFile(ModrinthClient.Version version) throws IOException {
        ModrinthClient.VersionFile file = version.getPrimaryFile();
        if(file == null) throw new IOException("Version " + version.name + " has no files");
        String safeName = file.fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        File target = new File(mTargetFolder, safeName);

        if(target.exists() && file.sha1 != null && Tools.compareSHA1(target, file.sha1)) {
            mResult.skippedFiles.add(safeName);
            return;
        }

        DownloaderProgressWrapper progress = new DownloaderProgressWrapper(
                R.string.content_download_progress, ProgressLayout.INSTALL_CONTENT);
        progress.extraString = safeName;
        // Delete a stale/corrupt copy first, ensureSha1 skips existing files without a hash
        if(target.exists() && file.sha1 == null && !target.delete())
            throw new IOException("Cannot replace " + target.getAbsolutePath());
        DownloadUtils.ensureSha1(target, file.sha1, () -> {
            DownloadUtils.downloadFileMonitored(file.url, target, mBuffer, progress);
            return null;
        });
        mResult.installedFiles.add(safeName);
    }
}
