package net.kdt.pojavlaunch.contentbrowser;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import net.kdt.pojavlaunch.R;

/** The kinds of content that can be searched and downloaded from the content browser tab */
public enum ContentType {
    MOD("mod", "mods", R.string.content_tab_mods),
    SHADER("shader", "shaderpacks", R.string.content_tab_shaders),
    MODPACK("modpack", null, R.string.content_tab_modpacks),
    RESOURCEPACK("resourcepack", "resourcepacks", R.string.content_tab_resourcepacks);

    /** Modrinth "project_type" value */
    public final String modrinthType;
    /** Folder inside the game directory where the files go, null for modpacks (new instance) */
    @Nullable public final String folderName;
    @StringRes public final int titleRes;

    ContentType(String modrinthType, @Nullable String folderName, int titleRes) {
        this.modrinthType = modrinthType;
        this.folderName = folderName;
        this.titleRes = titleRes;
    }
}
