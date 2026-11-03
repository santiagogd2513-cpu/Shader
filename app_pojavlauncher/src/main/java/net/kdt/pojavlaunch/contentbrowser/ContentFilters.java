package net.kdt.pojavlaunch.contentbrowser;

import androidx.annotation.Nullable;

/** Everything the user can filter the search with */
public class ContentFilters {
    /** Mods and modpacks are always restricted to this mod loader */
    public static final String MOD_LOADER = "forge";

    /** Shader loaders available on Modrinth. "iris" shaders run on Forge through Oculus. */
    public static final String SHADER_LOADER_ANY = null;
    public static final String SHADER_LOADER_OPTIFINE = "optifine";
    public static final String SHADER_LOADER_IRIS = "iris";

    public static final String[] SORT_INDEXES = {"relevance", "downloads", "follows", "newest", "updated"};

    public ContentType type = ContentType.MOD;
    public String query = "";
    /** Minecraft version, e.g. "1.20.1". Null or empty = any version */
    @Nullable public String mcVersion;
    /** Modrinth category name, e.g. "adventure". Null = any */
    @Nullable public String category;
    /** Only used for shaders */
    @Nullable public String shaderLoader = SHADER_LOADER_ANY;
    public String sortIndex = "relevance";

    /** Snapshot used by background searches, so the UI can keep editing the filters */
    public ContentFilters copy() {
        ContentFilters copy = new ContentFilters();
        copy.type = type;
        copy.query = query;
        copy.mcVersion = mcVersion;
        copy.category = category;
        copy.shaderLoader = shaderLoader;
        copy.sortIndex = sortIndex;
        return copy;
    }

    public boolean hasMcVersion() {
        return mcVersion != null && !mcVersion.trim().isEmpty();
    }

    /** @return the loader used to filter search results and project versions, or null for none */
    @Nullable
    public String getLoader() {
        switch (type) {
            case MOD:
            case MODPACK:
                return MOD_LOADER;
            case SHADER:
                return shaderLoader;
            default:
                return null;
        }
    }
}
